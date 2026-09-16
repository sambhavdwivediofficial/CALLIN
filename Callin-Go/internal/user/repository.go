package user

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("user: not found")

// Repository is the PostgreSQL-backed persistence layer for users
// and their registered devices.
type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

func (r *Repository) Create(ctx context.Context, u *User) error {
	const q = `
		INSERT INTO users (username, email, password_hash, display_name)
		VALUES ($1, $2, $3, $4)
		RETURNING id, created_at, updated_at`

	return r.pool.QueryRow(ctx, q, u.Username, u.Email, u.PasswordHash, u.DisplayName).
		Scan(&u.ID, &u.CreatedAt, &u.UpdatedAt)
}

func (r *Repository) GetByID(ctx context.Context, id string) (*User, error) {
	return r.scanOne(ctx, `
		SELECT id, username, email, password_hash, display_name, avatar_url, created_at, updated_at
		FROM users WHERE id = $1`, id)
}

func (r *Repository) GetByUsernameOrEmail(ctx context.Context, identifier string) (*User, error) {
	return r.scanOne(ctx, `
		SELECT id, username, email, password_hash, display_name, avatar_url, created_at, updated_at
		FROM users WHERE username = $1 OR email = $1`, identifier)
}

func (r *Repository) scanOne(ctx context.Context, q string, args ...any) (*User, error) {
	var u User
	err := r.pool.QueryRow(ctx, q, args...).Scan(
		&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.DisplayName, &u.AvatarURL,
		&u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("user repository: %w", err)
	}
	return &u, nil
}

// List returns every user except the caller, for the contacts/search screen.
func (r *Repository) List(ctx context.Context, excludeUserID string, limit int) ([]Public, error) {
	const q = `
		SELECT id, username, display_name, avatar_url
		FROM users
		WHERE id != $1
		ORDER BY display_name
		LIMIT $2`

	rows, err := r.pool.Query(ctx, q, excludeUserID, limit)
	if err != nil {
		return nil, fmt.Errorf("user repository: list: %w", err)
	}
	defer rows.Close()

	var out []Public
	for rows.Next() {
		var p Public
		if err := rows.Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL); err != nil {
			return nil, fmt.Errorf("user repository: list scan: %w", err)
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// UpsertDevice registers or refreshes an FCM token for a user's device.
func (r *Repository) UpsertDevice(ctx context.Context, userID, fcmToken, platform string) error {
	const q = `
		INSERT INTO devices (user_id, fcm_token, platform, last_seen_at)
		VALUES ($1, $2, $3, now())
		ON CONFLICT (user_id, fcm_token)
		DO UPDATE SET last_seen_at = now(), platform = EXCLUDED.platform`

	_, err := r.pool.Exec(ctx, q, userID, fcmToken, platform)
	if err != nil {
		return fmt.Errorf("user repository: upsert device: %w", err)
	}
	return nil
}

// DeviceTokens returns every active FCM token for a user, used to
// push an incoming-call notification to all of their devices.
func (r *Repository) DeviceTokens(ctx context.Context, userID string) ([]string, error) {
	rows, err := r.pool.Query(ctx, `SELECT fcm_token FROM devices WHERE user_id = $1`, userID)
	if err != nil {
		return nil, fmt.Errorf("user repository: device tokens: %w", err)
	}
	defer rows.Close()

	var tokens []string
	for rows.Next() {
		var t string
		if err := rows.Scan(&t); err != nil {
			return nil, err
		}
		tokens = append(tokens, t)
	}
	return tokens, rows.Err()
}
