// Package call owns call history persistence and the in-memory
// call state machine used while a call is actively ringing or
// connected.
package call

import "time"

// Status is one state in the call lifecycle.
type Status string

const (
	StatusRinging    Status = "ringing"
	StatusConnecting Status = "connecting"
	StatusConnected  Status = "connected"
	StatusEnded      Status = "ended"
	StatusMissed     Status = "missed"
	StatusDeclined   Status = "declined"
	StatusCancelled  Status = "cancelled"
)

// Call is a row of call history, persisted once a call reaches a
// terminal state.
type Call struct {
	ID          string     `json:"id"`
	CallerID    string     `json:"caller_id"`
	CalleeID    string     `json:"callee_id"`
	Status      Status     `json:"status"`
	EndReason   *string    `json:"end_reason,omitempty"`
	StartedAt   time.Time  `json:"started_at"`
	ConnectedAt *time.Time `json:"connected_at,omitempty"`
	EndedAt     *time.Time `json:"ended_at,omitempty"`
}
