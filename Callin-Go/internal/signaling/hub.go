package signaling

import (
	"encoding/json"
	"log/slog"
	"sync"
)

type Hub struct {
	mu      sync.RWMutex
	clients map[string]map[*Client]bool
	logger  *slog.Logger
}

func NewHub(logger *slog.Logger) *Hub {
	return &Hub{
		clients: make(map[string]map[*Client]bool),
		logger:  logger,
	}
}

func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.clients[c.UserID] == nil {
		h.clients[c.UserID] = make(map[*Client]bool)
	}
	h.clients[c.UserID][c] = true
	h.logger.Info("client connected", "user_id", c.UserID, "connections", len(h.clients[c.UserID]))
}

func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if conns, ok := h.clients[c.UserID]; ok {
		delete(conns, c)
		if len(conns) == 0 {
			delete(h.clients, c.UserID)
		}
	}
	h.logger.Info("client disconnected", "user_id", c.UserID)
}

func (h *Hub) IsOnline(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[userID]) > 0
}

// SendToUser delivers msg to a user's connections and reports
// whether delivery was attempted on at least one of them.
//
// requireAlive=true (used ONLY for call.invite) restricts delivery
// to connections proven alive recently — this is what decides
// whether a push wake-up is needed for a brand-new incoming call.
//
// requireAlive=false (used for everything else — call.end/reject/
// cancel, webrtc offer/answer/ice, disconnect notifications) attempts
// delivery to EVERY registered connection regardless of pong
// freshness. This used to also require "alive", which was the actual
// cause of "I ended the call but the other phone's call never ended":
// if that device's connection had gone slightly stale (no pong in the
// last ~35s — common right after the app resumes from background or
// a screen lock), the terminal message was silently dropped with no
// retry and no push fallback. Writing to a connection that's
// genuinely dead costs nothing — the channel write either queues
// harmlessly or the connection is already being torn down elsewhere
// — so for anything except the very first ring, attempting delivery
// unconditionally is strictly safer than filtering it out.
func (h *Hub) SendToUser(userID string, msg Message, requireAlive bool) bool {
	h.mu.RLock()
	conns := h.clients[userID]
	h.mu.RUnlock()

	data, err := json.Marshal(msg)
	if err != nil {
		h.logger.Error("marshal signaling message", "error", err)
		return false
	}

	delivered := false
	for c := range conns {
		if requireAlive && !c.IsAlive() {
			continue
		}
		select {
		case c.send <- data:
			delivered = true
		default:
			h.logger.Warn("client send buffer full, dropping connection", "user_id", userID)
			close(c.send)
			h.Unregister(c)
		}
	}

	return delivered
}
