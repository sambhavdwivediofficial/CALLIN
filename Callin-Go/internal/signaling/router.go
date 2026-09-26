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
		r.pusher.NotifyIncomingCall(context.Background(), payload.CalleeID, activeCall.ID, c.UserID)
	}

	// Echo back to the caller so their UI can move from "dialing"
	// to "ringing" with a confirmed call ID.
	r.hub.SendToUser(c.UserID, out)
}

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

// relay sends the state-transition message to BOTH parties — the
// other side (obviously) and back to the sender too. This is
// CRITICAL: without echoing to the sender, the side that pressed
// Accept never learns their own request succeeded, so their local
// UI never leaves the "Incoming" screen and never reaches Active —
// which is exactly what made calls appear to hang on "Connecting..."
// forever, even between two devices on the same network.
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
	r.hub.SendToUser(c.UserID, out) // <-- the fix: echo to sender
}

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
