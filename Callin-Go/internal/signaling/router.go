package signaling

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"callin-go/internal/call"
	"callin-go/internal/push"
	"callin-go/internal/user"
)

type Router struct {
	hub    *Hub
	calls  *call.Registry
	pool   *pgxpool.Pool
	pusher *push.FCMClient
	users  *user.Repository
	logger *slog.Logger
}

func NewRouter(hub *Hub, calls *call.Registry, pool *pgxpool.Pool, pusher *push.FCMClient, users *user.Repository, logger *slog.Logger) *Router {
	return &Router{hub: hub, calls: calls, pool: pool, pusher: pusher, users: users, logger: logger}
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
		// Offline — wake them with a push carrying the caller's real
		// identity, looked up here server-side (always available,
		// never dependent on the callee's local cache being warm).
		callerInfo := push.CallerInfo{}
		if u, err := r.users.GetByID(context.Background(), c.UserID); err == nil {
			if u.Username != nil {
				callerInfo.Username = *u.Username
			}
			if u.DisplayName != nil {
				callerInfo.DisplayName = *u.DisplayName
			}
			if u.AvatarURL != nil {
				callerInfo.AvatarURL = *u.AvatarURL
			}
		}
		r.pusher.NotifyIncomingCall(context.Background(), payload.CalleeID, activeCall.ID, callerInfo)
	}

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
	r.hub.SendToUser(c.UserID, out)
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

// HandleDisconnect is called once a user has zero remaining open
// WebSocket connections (every device they were signed in on has
// disconnected). If they were ringing, connecting, or active on a
// call, that call is force-ended: the other party gets a call.end
// so their UI doesn't hang waiting forever, and the call is
// persisted to history. This is the fix for calls getting
// permanently stuck "busy" after an app kill or crash.
func (r *Router) HandleDisconnect(userID string) {
	ended := r.calls.EndAllForUser(userID)
	for _, activeCall := range ended {
		other := activeCall.CalleeID
		if userID == activeCall.CalleeID {
			other = activeCall.CallerID
		}

		out := Message{
			Type:      TypeCallEnd,
			CallID:    activeCall.ID,
			From:      userID,
			To:        other,
			Timestamp: time.Now().UnixMilli(),
		}
		r.hub.SendToUser(other, out)

		endReason := "disconnected"
		go func(ac *call.ActiveCall, reason string) {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			if err := call.Persist(ctx, r.pool, ac, call.StatusEnded, &reason); err != nil {
				r.logger.Error("persisting disconnected call", "call_id", ac.ID, "error", err)
			}
		}(activeCall, endReason)
	}
}
