// Package user contains the user domain: models, persistence, and
// HTTP handlers for user profile and contact operations.
package call

import "time"

// User is the full internal representation of an account. A Google
// Sign-In account that hasn't finished onboarding may have a nil
// Username, PasswordHash, FirstName, and LastName — ProfileCompleted
// is the single source of truth for whether it has, not their
// presence.
type User struct {
	ID               string
	Username         *string
	Email            string
	PasswordHash     *string
	DisplayName      *string
	FirstName        *string
	LastName         *string
	AvatarURL        *string
	GoogleID         *string
	ProfileCompleted bool
	CreatedAt        time.Time
	UpdatedAt        time.Time
}

// Public is the subset of a User that is safe to show to other
// users — no email, no password hash.
type Public struct {
	ID          string  `json:"id"`
	Username    *string `json:"username"`
	DisplayName *string `json:"display_name"`
	AvatarURL   *string `json:"avatar_url,omitempty"`
}

// Me is the caller's own profile — includes fields nobody else
// should see, plus ProfileCompleted, which the app uses to decide
// whether to show the "complete your profile" screen.
type Me struct {
	ID               string  `json:"id"`
	Username         *string `json:"username"`
	Email            string  `json:"email"`
	DisplayName      *string `json:"display_name"`
	FirstName        *string `json:"first_name,omitempty"`
	LastName         *string `json:"last_name,omitempty"`
	AvatarURL        *string `json:"avatar_url,omitempty"`
	ProfileCompleted bool    `json:"profile_completed"`
}

func (u *User) ToPublic() Public {
	return Public{ID: u.ID, Username: u.Username, DisplayName: u.DisplayName, AvatarURL: u.AvatarURL}
}

func (u *User) ToMe() Me {
	return Me{
		ID:               u.ID,
		Username:         u.Username,
		Email:            u.Email,
		DisplayName:      u.DisplayName,
		FirstName:        u.FirstName,
		LastName:         u.LastName,
		AvatarURL:        u.AvatarURL,
		ProfileCompleted: u.ProfileCompleted,
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
