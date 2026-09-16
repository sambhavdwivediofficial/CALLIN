// Package middleware contains cross-cutting HTTP middleware: JWT
// authentication, request logging, and rate limiting.
package middleware

import (
	"context"
	"net/http"
	"strings"
)

type contextKey string

const userIDKey contextKey = "user_id"

// VerifyFunc verifies a raw JWT string and returns the user ID it
// belongs to, or an error if the token is missing, malformed, or
// expired. auth.TokenManager.Verify is adapted to this shape in
// cmd/server/main.go.
type VerifyFunc func(token string) (userID string, err error)

// RequireAuth extracts and verifies a Bearer JWT from the
// Authorization header, and stores the resulting user ID in the
// request context for downstream handlers to read.
func RequireAuth(verify VerifyFunc) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")
			if !strings.HasPrefix(header, "Bearer ") {
				http.Error(w, `{"error":"missing bearer token"}`, http.StatusUnauthorized)
				return
			}

			token := strings.TrimPrefix(header, "Bearer ")
			userID, err := verify(token)
			if err != nil {
				http.Error(w, `{"error":"invalid or expired token"}`, http.StatusUnauthorized)
				return
			}

			ctx := context.WithValue(r.Context(), userIDKey, userID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext retrieves the authenticated user's ID, set by
// RequireAuth, from the request context.
func UserIDFromContext(ctx context.Context) (string, bool) {
	id, ok := ctx.Value(userIDKey).(string)
	return id, ok
}
