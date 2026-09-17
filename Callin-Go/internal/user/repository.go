package user

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrNotFound          = errors.New("user: not found")
	ErrUsernameTaken     = errors.New("user: username already in use")
	ErrProfileAlreadySet = errors.New("user: profile already completed")
)

// selectColumns is the full column list, in scan order, used by
// every query that returns a complete User row.
const selectColumns = `id, username, email, password_hash, display_name, first_name, last_name, avatar_url, google_id, profile_completed, created_at, updated_at`

// Repository is the PostgreSQL-backed persistence layer for users
// and their registered devices.
type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// Create registers a classic username+password account. Not used by
// the app's UI (Google Sign-In only) but kept available for testing
// and any future non-Google auth path.
func (r *Repository) Create(ctx context.Context, u *User) error {
	const q = `
		INSERT INTO users (username, email, password_hash, display_name, profile_completed)
		VALUES ($1, $2, $3, $4, true)
		RETURNING id, created_at, updated_at`

	return r.pool.QueryRow(ctx, q, u.Username, u.Email, u.PasswordHash, u.DisplayName).
		Scan(&u.ID, &u.CreatedAt, &u.UpdatedAt)
}

func (r *Repository) GetByID(ctx context.Context, id string) (*User, error) {
	return r.scanOne(ctx, "SELECT "+selectColumns+" FROM users WHERE id = $1", id)
}

func (r *Repository) GetByUsernameOrEmail(ctx context.Context, identifier string) (*User, error) {
	return r.scanOne(ctx, "SELECT "+selectColumns+" FROM users WHERE username = $1 OR email = $1", identifier)
}

func (r *Repository) getByGoogleID(ctx context.Context, googleID string) (*User, error) {
	return r.scanOne(ctx, "SELECT "+selectColumns+" FROM users WHERE google_id = $1", googleID)
}

func (r *Repository) getByEmail(ctx context.Context, email string) (*User, error) {
	return r.scanOne(ctx, "SELECT "+selectColumns+" FROM users WHERE email = $1", email)
}

func (r *Repository) scanOne(ctx context.Context, q string, args ...any) (*User, error) {
	var u User
	err := r.pool.QueryRow(ctx, q, args...).Scan(
		&u.ID, &u.Username, &u.Email, &u.PasswordHash, &u.DisplayName, &u.FirstName, &u.LastName,
		&u.AvatarURL, &u.GoogleID, &u.ProfileCompleted, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("user repository: %w", err)
	}
	return &u, nil
}

// FindOrCreateByGoogle looks up a user by their Google account. If
// no account is linked to this Google ID yet but one already exists
// with the same email (e.g. from classic register), it links the
// Google ID to that account instead of creating a duplicate. If
// truly new, it creates a bare account with profile_completed=false.
func (r *Repository) FindOrCreateByGoogle(ctx context.Context, googleID, email string) (*User, error) {
	if existing, err := r.getByGoogleID(ctx, googleID); err == nil {
		return existing, nil
	} else if !errors.Is(err, ErrNotFound) {
		return nil, err
	}

	if existing, err := r.getByEmail(ctx, email); err == nil {
		if _, execErr := r.pool.Exec(ctx, `UPDATE users SET google_id = $1, updated_at = now() WHERE id = $2`, googleID, existing.ID); execErr != nil {
			return nil, fmt.Errorf("user repository: link google id: %w", execErr)
		}
		existing.GoogleID = &googleID
		return existing, nil
	} else if !errors.Is(err, ErrNotFound) {
		return nil, err
	}

	const insertQ = `
		INSERT INTO users (email, google_id, profile_completed)
		VALUES ($1, $2, false)
		RETURNING ` + selectColumns

	var created User
	err := r.pool.QueryRow(ctx, insertQ, email, googleID).Scan(
		&created.ID, &created.Username, &created.Email, &created.PasswordHash, &created.DisplayName,
		&created.FirstName, &created.LastName, &created.AvatarURL, &created.GoogleID,
		&created.ProfileCompleted, &created.CreatedAt, &created.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("user repository: create google user: %w", err)
	}
	return &created, nil
}

// CompleteProfile sets a first-time user's username, first name and
// last name, and marks the profile complete. It refuses to run a
// second time — once set, username and name are locked at this
// layer, not just in the UI.
func (r *Repository) CompleteProfile(ctx context.Context, userID, username, firstName, lastName string) (*User, error) {
	existing, err := r.GetByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if existing.ProfileCompleted {
		return nil, ErrProfileAlreadySet
	}

	displayName := firstName + " " + lastName
	const q = `
		UPDATE users
		SET username = $1, first_name = $2, last_name = $3, display_name = $4,
		    profile_completed = true, updated_at = now()
		WHERE id = $5
		RETURNING ` + selectColumns

	var updated User
	err = r.pool.QueryRow(ctx, q, username, firstName, lastName, displayName, userID).Scan(
		&updated.ID, &updated.Username, &updated.Email, &updated.PasswordHash, &updated.DisplayName,
		&updated.FirstName, &updated.LastName, &updated.AvatarURL, &updated.GoogleID,
		&updated.ProfileCompleted, &updated.CreatedAt, &updated.UpdatedAt,
	)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrUsernameTaken
		}
		return nil, fmt.Errorf("user repository: complete profile: %w", err)
	}
	return &updated, nil
}

// List returns every other user who has finished onboarding, for
// the contacts/search screen.
func (r *Repository) List(ctx context.Context, excludeUserID string, limit int) ([]Public, error) {
	const q = `
		SELECT id, username, display_name, avatar_url
		FROM users
		WHERE id != $1 AND profile_completed = true
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

func isUniqueViolation(err error) bool {
	return strings.Contains(err.Error(), "duplicate key value violates unique constraint")
}
