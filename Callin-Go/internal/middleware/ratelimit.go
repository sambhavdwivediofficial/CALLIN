package middleware

import (
	"net"
	"net/http"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// visitor tracks the rate limiter for one client IP and when it was
// last seen, so stale entries can be garbage collected.
type visitor struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// IPRateLimiter is a simple per-IP token-bucket rate limiter. It is
// intentionally in-memory and single-instance — appropriate for a
// single backend replica. If CALLIN is ever scaled to multiple
// backend instances, this should move to a shared store (Redis)
// instead; that is a deliberate future decision, not an oversight.
type IPRateLimiter struct {
	mu       sync.Mutex
	visitors map[string]*visitor
	rps      rate.Limit
	burst    int
}

func NewIPRateLimiter(requestsPerSecond float64, burst int) *IPRateLimiter {
	l := &IPRateLimiter{
		visitors: make(map[string]*visitor),
		rps:      rate.Limit(requestsPerSecond),
		burst:    burst,
	}
	go l.cleanupLoop()
	return l
}

func (l *IPRateLimiter) getLimiter(ip string) *rate.Limiter {
	l.mu.Lock()
	defer l.mu.Unlock()

	v, exists := l.visitors[ip]
	if !exists {
		limiter := rate.NewLimiter(l.rps, l.burst)
		l.visitors[ip] = &visitor{limiter: limiter, lastSeen: time.Now()}
		return limiter
	}
	v.lastSeen = time.Now()
	return v.limiter
}

func (l *IPRateLimiter) cleanupLoop() {
	for {
		time.Sleep(time.Minute)
		l.mu.Lock()
		for ip, v := range l.visitors {
			if time.Since(v.lastSeen) > 10*time.Minute {
				delete(l.visitors, ip)
			}
		}
		l.mu.Unlock()
	}
}

// RateLimit rejects requests from an IP once it exceeds the
// configured requests-per-second budget, with HTTP 429.
func (l *IPRateLimiter) RateLimit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			host = r.RemoteAddr
		}

		if !l.getLimiter(host).Allow() {
			http.Error(w, `{"error":"too many requests"}`, http.StatusTooManyRequests)
			return
		}

		next.ServeHTTP(w, r)
	})
}
