package signaling

import (
	"encoding/json"
	"log/slog"
	"sync"
)

// Hub tracks every currently connected client and routes messages
// to them by user ID. A user may have multiple devices connected;
// messages are sent to all of that user's reachable connections.
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

// IsOnline reports whether a user has at least one open connection,
// regardless of freshness — used only for "should this call be
// force-ended" on full disconnect, not for delivery decisions.
func (h *Hub) IsOnline(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[userID]) > 0
}

// SendToUser delivers a message to every connection a user has open
// AND recently proven alive (see Client.IsAlive). It returns false
// if no such connection exists, so the caller can fall back to a
// push notification promptly instead of waiting out a stale
// connection's full timeout.
func (h *Hub) SendToUser(userID string, msg Message) bool {
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
		if !c.IsAlive() {
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
