// Package call owns the in-memory call state machine used while a
// call is actively ringing or connected. Nothing in this package is
// persisted to a database — CALLIN does not keep server-side call
// history; call history lives entirely on-device (see
// RecentCallsStore in the Android app).
package call

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
