// Package connection owns the "add contact" flow: connection
// requests between two accounts, and the accepted connections that
// result. This is the only place a relationship between two users
// is ever recorded — no call history, no message history, nothing
// else lives here.
package connection

import "time"

type Status string

const (
	StatusPending  Status = "pending"
	StatusAccepted Status = "accepted"
	StatusRejected Status = "rejected"
)

// Request is a pending connection request, from the point of view
// of the person who received it.
type Request struct {
	ID              string    `json:"id"`
	FromUserID      string    `json:"from_user_id"`
	FromUsername    string    `json:"from_username"`
	FromDisplayName *string   `json:"from_display_name"`
	FromAvatarURL   *string   `json:"from_avatar_url,omitempty"`
	CreatedAt       time.Time `json:"created_at"`
}

// Contact is an accepted connection, from the caller's own point of
// view — the "other side" of the relationship.
type Contact struct {
	UserID      string    `json:"user_id"`
	Username    string    `json:"username"`
	DisplayName *string   `json:"display_name"`
	AvatarURL   *string   `json:"avatar_url,omitempty"`
	ConnectedAt time.Time `json:"connected_at"`
}
