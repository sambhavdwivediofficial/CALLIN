// Package signaling implements the real-time WebSocket layer that
// carries call control (invite/accept/reject/cancel/end) and WebRTC
// negotiation (offer/answer/ICE candidates) between two CALLIN
// clients, brokered through the backend.
package signaling

import "encoding/json"

// MessageType identifies what kind of signaling message this is.
// Keeping this as a versioned, closed set of string constants (not
// free-form strings) is what lets the router validate every
// incoming message before acting on it.
type MessageType string

const (
	TypeCallInvite MessageType = "call.invite"
	TypeCallAccept MessageType = "call.accept"
	TypeCallReject MessageType = "call.reject"
	TypeCallCancel MessageType = "call.cancel"
	TypeCallEnd    MessageType = "call.end"

	TypeWebRTCOffer     MessageType = "webrtc.offer"
	TypeWebRTCAnswer    MessageType = "webrtc.answer"
	TypeWebRTCCandidate MessageType = "webrtc.ice_candidate"

	TypePing  MessageType = "ping"
	TypePong  MessageType = "pong"
	TypeError MessageType = "error"
)

// Message is the single envelope every signaling frame is wrapped
// in, both client-to-server and server-to-client. Payload is kept
// as raw JSON so each message type can define its own shape without
// this envelope needing to change.
type Message struct {
	Type      MessageType     `json:"type"`
	CallID    string          `json:"call_id,omitempty"`
	From      string          `json:"from,omitempty"`
	To        string          `json:"to,omitempty"`
	Payload   json.RawMessage `json:"payload,omitempty"`
	Timestamp int64           `json:"timestamp"`
}

// CallInvitePayload is carried in a call.invite message.
type CallInvitePayload struct {
	CalleeID string `json:"callee_id"`
}

// SDPPayload carries a WebRTC session description (offer or answer).
type SDPPayload struct {
	SDP  string `json:"sdp"`
	Type string `json:"type"` // "offer" | "answer"
}

// ICECandidatePayload carries one ICE candidate discovered during
// connectivity establishment.
type ICECandidatePayload struct {
	Candidate     string `json:"candidate"`
	SDPMid        string `json:"sdp_mid"`
	SDPMLineIndex int    `json:"sdp_mline_index"`
}

// ErrorPayload is sent back to a client when its message could not
// be processed.
type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
