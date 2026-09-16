package signaling

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"callin-go/internal/call"
	"callin-go/internal/push"
)

// Router validates every incoming signaling message and routes it
// to the right effect: forwarding to the other party if they are
// online, updating call state, persisting terminal calls, and
// falling back to a push notification when the target is offline.
type Router struct {
	hub    *Hub
	calls  *call.Registry
	pool   *pgxpool.Pool
	pusher *push.FCMClient
	logger *slog.Logger
}

func NewRouter(hub *Hub, calls *call.Registry, pool *pgxpool.Pool, pusher *push.FCMClient, logger *slog.Logger) *Router {
	return &Router{hub: hub, calls: calls, pool: pool, pusher: pusher, logger: logger}
}

// Handle validates a message's type and dispatches it. Unknown
// types and malformed payloads get an error frame back, never a
// silent drop or a panic.
func (r *Router) Handle(c *Client, msg Message) {
	switch msg.Type {
	case TypeCallInvite:
		r.handleInvite(c, msg)
	case TypeCallAccept:
		r.handleTransition(c, msg, call.StatusConnecting)
	case TypeCallReject:
		r.handleTerminal(c, msg, call.StatusDeclined)
	case TypeCallCancel:
		r.handleTerminal(c, msg, call.StatusCancelled)
	case TypeCallEnd:
		r.handleTerminal(c, msg, call.StatusEnded)
	case TypeWebRTCOffer, TypeWebRTCAnswer, TypeWebRTCCandidate:
		r.forward(c, msg)
	case TypePing:
		r.handlePing(c)
	default:
		c.sendError("unknown_type", "unrecognized message type")
	}
}

func (r *Router) handleInvite(c *Client, msg Message) {
	var payload CallInvitePayload
	if err := json.Unmarshal(msg.Payload, &payload); err != nil || payload.CalleeID == "" {
		c.sendError("bad_request", "call.invite requires a callee_id")
		return
	}

	activeCall, err := r.calls.Start(c.UserID, payload.CalleeID)
	if err != nil {
		c.sendError("busy", "one of you is already on a call")
		return
	}

	out := Message{
		Type:      TypeCallInvite,
		CallID:    activeCall.ID,
		From:      c.UserID,
		To:        payload.CalleeID,
		Payload:   msg.Payload,
		Timestamp: time.Now().UnixMilli(),
	}

	if delivered := r.hub.SendToUser(payload.CalleeID, out); !delivered {
		// Callee has no open socket right now — wake their device
		// with a high-priority push instead.
		r.pusher.NotifyIncomingCall(context.Background(), payload.CalleeID, activeCall.ID, c.UserID)
	}

	// Echo back to the caller so their UI can move from "dialing"
	// to "ringing" with a confirmed call ID.
	r.hub.SendToUser(c.UserID, out)
}

// handleTransition moves a non-terminal call forward (e.g.
// accepted -> connecting) and relays the message to the other party.
func (r *Router) handleTransition(c *Client, msg Message, to call.Status) {
	if msg.CallID == "" {
		c.sendError("bad_request", "call_id is required")
		return
	}

	activeCall, err := r.calls.Transition(msg.CallID, to)
	if err != nil {
		c.sendError("invalid_state", "that call transition is not allowed right now")
		return
	}

	r.relay(c, activeCall, msg)
}

// handleTerminal moves a call into a terminal state, relays the
// message, and persists the finished call to history.
func (r *Router) handleTerminal(c *Client, msg Message, to call.Status) {
	if msg.CallID == "" {
		c.sendError("bad_request", "call_id is required")
		return
	}

	activeCall, err := r.calls.Transition(msg.CallID, to)
	if err != nil {
		c.sendError("invalid_state", "that call transition is not allowed right now")
		return
	}

	r.relay(c, activeCall, msg)

	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := call.Persist(ctx, r.pool, activeCall, to, nil); err != nil {
			r.logger.Error("persisting call history", "call_id", activeCall.ID, "error", err)
		}
	}()
}

func (r *Router) relay(c *Client, activeCall *call.ActiveCall, msg Message) {
	other := activeCall.CalleeID
	if c.UserID == activeCall.CalleeID {
		other = activeCall.CallerID
	}

	out := Message{
		Type:      msg.Type,
		CallID:    activeCall.ID,
		From:      c.UserID,
		To:        other,
		Payload:   msg.Payload,
		Timestamp: time.Now().UnixMilli(),
	}
	r.hub.SendToUser(other, out)
}

// forward relays WebRTC negotiation messages (offer/answer/ICE)
// straight through to the other party without inspecting the SDP —
// the backend is a signaling relay, not a media endpoint.
func (r *Router) forward(c *Client, msg Message) {
	if msg.To == "" {
		c.sendError("bad_request", "to is required for webrtc messages")
		return
	}
	msg.From = c.UserID
	msg.Timestamp = time.Now().UnixMilli()
	r.hub.SendToUser(msg.To, msg)
}

func (r *Router) handlePing(c *Client) {
	data, _ := json.Marshal(Message{Type: TypePong, Timestamp: time.Now().UnixMilli()})
	select {
	case c.send <- data:
	default:
	}
}
