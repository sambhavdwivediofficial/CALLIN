package signaling

import (
	"encoding/json"
	"log/slog"
	"sync"
)

// Hub tracks every currently connected client and routes messages
// to them by user ID. A user may have multiple devices connected;
// messages are sent to all of that user's active connections.
type Hub struct {
	mu      sync.RWMutex
	clients map[string]map[*Client]bool // userID -> set of connections
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

// IsOnline reports whether a user currently has at least one open
// WebSocket connection.
func (h *Hub) IsOnline(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[userID]) > 0
}

// SendToUser delivers a message to every connection a user
// currently has open. It returns false if the user has no open
// connections, so the caller can fall back to a push notification.
func (h *Hub) SendToUser(userID string, msg Message) bool {
	h.mu.RLock()
	conns := h.clients[userID]
	h.mu.RUnlock()

	if len(conns) == 0 {
		return false
	}

	data, err := json.Marshal(msg)
	if err != nil {
		h.logger.Error("marshal signaling message", "error", err)
		return false
	}

	for c := range conns {
		select {
		case c.send <- data:
		default:
			// The client's send buffer is full — it is not keeping
			// up. Drop the connection rather than let one slow
			// client back-pressure the whole hub.
			h.logger.Warn("client send buffer full, dropping connection", "user_id", userID)
			close(c.send)
			h.Unregister(c)
		}
	}

	return true
}
