package connection

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrNotFound     = errors.New("connection: not found")
	ErrCannotSelf   = errors.New("connection: cannot connect to yourself")
	ErrUserNotFound = errors.New("connection: target user not found")
)

// Repository is the PostgreSQL-backed persistence layer for
// connection requests and accepted connections.
type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// FindUserIDByUsername looks up a profile-complete user's ID by
// username — used for both QR-code lookups and manual requests.
func (r *Repository) FindUserIDByUsername(ctx context.Context, username string) (string, error) {
	var id string
	err := r.pool.QueryRow(ctx,
		`SELECT id FROM users WHERE username = $1 AND profile_completed = true`, username,
	).Scan(&id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "", ErrUserNotFound
		}
		return "", fmt.Errorf("connection repository: find by username: %w", err)
	}
	return id, nil
}

// AreConnected reports whether two users already have an accepted
// connection — used to skip re-sending a request that's redundant.
func (r *Repository) AreConnected(ctx context.Context, userA, userB string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(
			SELECT 1 FROM connections
			WHERE status = 'accepted'
			AND ((requester_id = $1 AND addressee_id = $2) OR (requester_id = $2 AND addressee_id = $1))
		)`, userA, userB,
	).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("connection repository: are connected: %w", err)
	}
	return exists, nil
}

// CreateRequest inserts a pending connection request. If the other
// person already sent *us* a request, it's accepted immediately
// instead of creating a duplicate pending pair — this is what makes
// mutual QR scanning connect instantly.
func (r *Repository) CreateRequest(ctx context.Context, requesterID, addresseeID string) (Status, error) {
	if requesterID == addresseeID {
		return "", ErrCannotSelf
	}

	var reverseID string
	err := r.pool.QueryRow(ctx,
		`SELECT id FROM connections WHERE requester_id = $1 AND addressee_id = $2 AND status = 'pending'`,
		addresseeID, requesterID,
	).Scan(&reverseID)
	if err == nil {
		if _, execErr := r.pool.Exec(ctx,
			`UPDATE connections SET status = 'accepted', responded_at = now() WHERE id = $1`, reverseID,
		); execErr != nil {
			return "", fmt.Errorf("connection repository: auto-accept: %w", execErr)
		}
		return StatusAccepted, nil
	} else if !errors.Is(err, pgx.ErrNoRows) {
		return "", fmt.Errorf("connection repository: check reverse: %w", err)
	}

	_, err = r.pool.Exec(ctx,
		`INSERT INTO connections (requester_id, addressee_id, status)
		 VALUES ($1, $2, 'pending')
		 ON CONFLICT (requester_id, addressee_id) DO NOTHING`,
		requesterID, addresseeID,
	)
	if err != nil {
		return "", fmt.Errorf("connection repository: create request: %w", err)
	}
	return StatusPending, nil
}

// PendingForUser returns every request waiting on this user to respond.
func (r *Repository) PendingForUser(ctx context.Context, userID string) ([]Request, error) {
	const q = `
		SELECT c.id, u.id, u.username, u.display_name, u.avatar_url, c.created_at
		FROM connections c
		JOIN users u ON u.id = c.requester_id
		WHERE c.addressee_id = $1 AND c.status = 'pending'
		ORDER BY c.created_at DESC`

	rows, err := r.pool.Query(ctx, q, userID)
	if err != nil {
		return nil, fmt.Errorf("connection repository: pending: %w", err)
	}
	defer rows.Close()

	var out []Request
	for rows.Next() {
		var req Request
		if err := rows.Scan(&req.ID, &req.FromUserID, &req.FromUsername, &req.FromDisplayName, &req.FromAvatarURL, &req.CreatedAt); err != nil {
			return nil, fmt.Errorf("connection repository: pending scan: %w", err)
		}
		out = append(out, req)
	}
	return out, rows.Err()
}

// Respond accepts or rejects a pending request. Only the addressee
// may respond, and only while it's still pending.
func (r *Repository) Respond(ctx context.Context, requestID, addresseeID string, accept bool) error {
	status := StatusRejected
	if accept {
		status = StatusAccepted
	}
	tag, err := r.pool.Exec(ctx,
		`UPDATE connections SET status = $1, responded_at = now()
		 WHERE id = $2 AND addressee_id = $3 AND status = 'pending'`,
		status, requestID, addresseeID,
	)
	if err != nil {
		return fmt.Errorf("connection repository: respond: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// ListAccepted returns every accepted connection for a user, as
// contacts (from their point of view), most recently connected first.
func (r *Repository) ListAccepted(ctx context.Context, userID string) ([]Contact, error) {
	const q = `
		SELECT
			CASE WHEN c.requester_id = $1 THEN c.addressee_id ELSE c.requester_id END AS other_id,
			u.username, u.display_name, u.avatar_url, c.responded_at
		FROM connections c
		JOIN users u ON u.id = CASE WHEN c.requester_id = $1 THEN c.addressee_id ELSE c.requester_id END
		WHERE (c.requester_id = $1 OR c.addressee_id = $1) AND c.status = 'accepted'
		ORDER BY c.responded_at DESC`

	rows, err := r.pool.Query(ctx, q, userID)
	if err != nil {
		return nil, fmt.Errorf("connection repository: list accepted: %w", err)
	}
	defer rows.Close()

	var out []Contact
	for rows.Next() {
		var c Contact
		if err := rows.Scan(&c.UserID, &c.Username, &c.DisplayName, &c.AvatarURL, &c.ConnectedAt); err != nil {
			return nil, fmt.Errorf("connection repository: list accepted scan: %w", err)
		}
		out = append(out, c)
	}
	return out, rows.Err()
}
