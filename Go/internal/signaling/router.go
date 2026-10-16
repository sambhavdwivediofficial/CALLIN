package signaling

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"time"

	"callin-go/internal/call"
	"callin-go/internal/push"
	"callin-go/internal/user"
)

type Router struct {
	hub    *Hub
	calls  *call.Registry
	pusher *push.FCMClient
	users  *user.Repository
	logger *slog.Logger
}

func NewRouter(hub *Hub, calls *call.Registry, pusher *push.FCMClient, users *user.Repository, logger *slog.Logger) *Router {
	return &Router{hub: hub, calls: calls, pusher: pusher, users: users, logger: logger}
}

func (r *Router) Handle(c *Client, msg Message) {
	if msg.Type != TypePing && msg.Type != TypeWebRTCCandidate {
		// Type + call id only. No user ids on purpose: logs must not
		// become a record of who called whom.
		r.logger.Info("signal", "type", string(msg.Type), "call_id", msg.CallID)
	}

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
		r.logger.Warn("invite rejected", "reason", "busy")
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

	delivered := r.hub.SendToUser(payload.CalleeID, out, true)
	r.logger.Info("invite routed", "call_id", activeCall.ID, "via_websocket", delivered)

	if !delivered {
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

	r.hub.SendToUser(c.UserID, out, false)
}

func (r *Router) handleTransition(c *Client, msg Message, to call.Status) {
	if msg.CallID == "" {
		c.sendError("bad_request", "call_id is required")
		return
	}
	activeCall, err := r.calls.Transition(msg.CallID, to)
	if err != nil {
		r.logger.Warn("transition rejected", "type", string(msg.Type), "call_id", msg.CallID, "error", err)
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
		if errors.Is(err, call.ErrCallNotFound) {
			// Already ended by the other side or by a disconnect.
			// Nothing left to relay, and not an error.
			return
		}
		r.logger.Warn("terminal rejected", "type", string(msg.Type), "call_id", msg.CallID, "error", err)
		c.sendError("invalid_state", "that call transition is not allowed right now")
		return
	}
	r.relay(c, activeCall, msg)
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
	r.hub.SendToUser(other, out, false)
	r.hub.SendToUser(c.UserID, out, false)
}

func (r *Router) forward(c *Client, msg Message) {
	if msg.To == "" {
		c.sendError("bad_request", "to is required for webrtc messages")
		return
	}
	msg.From = c.UserID
	msg.Timestamp = time.Now().UnixMilli()
	r.hub.SendToUser(msg.To, msg, false)
}

func (r *Router) handlePing(c *Client) {
	data, _ := json.Marshal(Message{Type: TypePong, Timestamp: time.Now().UnixMilli()})
	select {
	case c.send <- data:
	default:
	}
}

// HandleDisconnect force-ends any call a fully-disconnected user was
// in and tells the other side. Nothing is persisted anywhere.
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
		r.hub.SendToUser(other, out, false)
	}
}
