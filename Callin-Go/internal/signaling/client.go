package signaling

import (
	"encoding/json"
	"log/slog"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 32 * 1024 // 32KB — signaling messages are small; this stops abuse.
	sendBufferSize = 32
)

// Client wraps one WebSocket connection for one authenticated user.
// A user may have several Clients open at once (multiple devices).
type Client struct {
	UserID string
	conn   *websocket.Conn
	hub    *Hub
	router *Router
	logger *slog.Logger
	send   chan []byte
}

func NewClient(userID string, conn *websocket.Conn, hub *Hub, router *Router, logger *slog.Logger) *Client {
	return &Client{
		UserID: userID,
		conn:   conn,
		hub:    hub,
		router: router,
		logger: logger,
		send:   make(chan []byte, sendBufferSize),
	}
}

// Run registers the client, then blocks running its read and write
// pumps until the connection closes.
func (c *Client) Run() {
	c.hub.Register(c)

	go c.writePump()
	c.readPump() // blocks until the connection closes
}

func (c *Client) readPump() {
	defer func() {
		c.hub.Unregister(c)
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	_ = c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
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
