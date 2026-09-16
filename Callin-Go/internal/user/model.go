// Package user contains the user domain: models, persistence, and
// HTTP handlers for user profile and contact operations.
package user

import "time"

// User is the full internal representation of an account, including
// the password hash. Never serialize this directly to JSON.
type User struct {
	ID           string    `json:"id"`
	Username     string    `json:"username"`
	Email        string    `json:"email"`
	PasswordHash string    `json:"-"`
	DisplayName  string    `json:"display_name"`
	AvatarURL    *string   `json:"avatar_url,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

// Public is the subset of a User that is safe to expose over the
// API — no password hash, no email for other users' profiles.
type Public struct {
	ID          string  `json:"id"`
	Username    string  `json:"username"`
	DisplayName string  `json:"display_name"`
	AvatarURL   *string `json:"avatar_url,omitempty"`
}

func (u *User) ToPublic() Public {
	return Public{
		ID:          u.ID,
		Username:    u.Username,
		DisplayName: u.DisplayName,
		AvatarURL:   u.AvatarURL,
	}
}

// Device represents one installation of the app registered to
// receive FCM push notifications for a user.
type Device struct {
	ID         string    `json:"id"`
	UserID     string    `json:"user_id"`
	FCMToken   string    `json:"fcm_token"`
	Platform   string    `json:"platform"`
	LastSeenAt time.Time `json:"last_seen_at"`
	CreatedAt  time.Time `json:"created_at"`
}
