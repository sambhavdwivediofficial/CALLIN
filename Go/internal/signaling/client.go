package signaling

import (
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait  = 10 * time.Second
	pongWait   = 35 * time.Second   // was 60s — shortened so a genuinely dead connection is noticed and falls back to push much sooner
	pingPeriod = (pongWait * 8) / 10 // comfortably under pongWait so pings always land before the deadline
	maxMessageSize = 32 * 1024
	sendBufferSize = 32

	// aliveWindow: how recently a client must have proven itself
	// alive (via a pong) for call-routing to treat it as reachable.
	aliveWindow = pongWait
)

// Client wraps one WebSocket connection for one authenticated user.
type Client struct {
	UserID string
	conn   *websocket.Conn
	hub    *Hub
	router *Router
	logger *slog.Logger
	send   chan []byte

	mu       sync.RWMutex
	lastPong time.Time
}

func NewClient(userID string, conn *websocket.Conn, hub *Hub, router *Router, logger *slog.Logger) *Client {
	return &Client{
		UserID:   userID,
		conn:     conn,
		hub:      hub,
		router:   router,
		logger:   logger,
		send:     make(chan []byte, sendBufferSize),
		lastPong: time.Now(),
	}
}

// IsAlive reports whether this connection has proven itself alive
// within the last aliveWindow (via a pong, or by having just
// connected). Hub.SendToUser uses this to decide whether a call can
// actually be delivered here, or whether the push fallback should
// fire instead — without this, a "zombie" connection (dead socket
// not yet reaped by the OS, e.g. a phone that lost network in deep
// sleep) looks "online" to the hub for up to a minute, during which
// calls to that user silently vanish: the hub thinks it delivered
// the message, so no push is ever attempted.
func (c *Client) IsAlive() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return time.Since(c.lastPong) < aliveWindow
}

func (c *Client) touchPong() {
	c.mu.Lock()
	c.lastPong = time.Now()
	c.mu.Unlock()
}

func (c *Client) Run() {
	c.hub.Register(c)
	go c.writePump()
	c.readPump()
}

func (c *Client) readPump() {
	defer func() {
		c.hub.Unregister(c)
		if !c.hub.IsOnline(c.UserID) {
			c.router.HandleDisconnect(c.UserID)
		}
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	_ = c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.touchPong()
		return c.conn.SetReadDeadline(time.Now().Add(pongWait))
	})

	for {
		_, data, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				c.logger.Warn("unexpected websocket close", "user_id", c.UserID, "error", err)
			}
			return
		}

		var msg Message
		if err := json.Unmarshal(data, &msg); err != nil {
			c.sendError("bad_request", "message was not valid JSON")
			continue
		}

		msg.From = c.UserID
		msg.Timestamp = time.Now().UnixMilli()

		c.router.Handle(c, msg)
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case data, ok := <-c.send:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				_ = c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, data); err != nil {
				return
			}

		case <-ticker.C:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) sendError(code, message string) {
	payload, _ := json.Marshal(ErrorPayload{Code: code, Message: message})
	data, _ := json.Marshal(Message{
		Type:      TypeError,
		Payload:   payload,
		Timestamp: time.Now().UnixMilli(),
	})
	select {
	case c.send <- data:
	default:
	}
}
