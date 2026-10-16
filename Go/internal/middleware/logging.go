package middleware

import (
	"bufio"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"time"

	"github.com/google/uuid"
)

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

// Hijack delegates to the underlying ResponseWriter. Without this,
// wrapping the ResponseWriter here breaks the WebSocket upgrade
// entirely: gorilla/websocket's Upgrader.Upgrade requires the
// http.ResponseWriter it receives to implement http.Hijacker so it
// can take over the raw TCP connection to switch protocols, and Go's
// interface satisfaction is structural — embedding http.ResponseWriter
// does NOT automatically pull in Hijack, since the interface itself
// doesn't declare that method. This one missing method is why every
// single /ws request failed with "response does not implement
// http.Hijacker" — which meant the signaling socket never connected
// at all, on any network, which is why every downstream call feature
// (ringtone, accept, mute, speaker, the call ever reaching Active)
// failed identically.
func (r *statusRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	hijacker, ok := r.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, fmt.Errorf("underlying ResponseWriter does not support hijacking")
	}
	return hijacker.Hijack()
}

// Flush delegates too, so any future streaming response keeps
// working through this wrapper.
func (r *statusRecorder) Flush() {
	if flusher, ok := r.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

// Logging logs one structured line per request: method, path,
// status, duration and a request ID that is also echoed back in
// the X-Request-ID response header for client-side correlation.
func Logging(logger *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			requestID := uuid.NewString()
			w.Header().Set("X-Request-ID", requestID)

			rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
			start := time.Now()

			next.ServeHTTP(rec, r)

			logger.Info("request",
				"request_id", requestID,
				"method", r.Method,
				"path", r.URL.Path,
				"status", rec.status,
				"duration_ms", time.Since(start).Milliseconds(),
				"remote_addr", r.RemoteAddr,
			)
		})
	}
}
