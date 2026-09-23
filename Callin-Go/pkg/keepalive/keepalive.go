// Package keepalive periodically pings this service's own /healthz
// endpoint so that Render's free tier doesn't spin the instance
// down from inactivity.
package keepalive

import (
	"log/slog"
	"net/http"
	"time"
)

// Start pings targetURL every interval, forever, in a background
// goroutine. It never blocks main() and never returns an error — a
// missed ping is only logged, never fatal.
func Start(targetURL string, interval time.Duration, logger *slog.Logger) {
	if targetURL == "" {
		logger.Warn("keepalive: no URL configured, self-ping disabled")
		return
	}

	client := &http.Client{Timeout: 10 * time.Second}

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			resp, err := client.Get(targetURL)
			if err != nil {
				logger.Warn("keepalive: ping failed", "error", err)
				continue
			}
			resp.Body.Close()
			logger.Info("keepalive: ping sent", "status", resp.StatusCode)
		}
	}()
}
