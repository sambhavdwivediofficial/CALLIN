package call

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/jackc/pgx/v5/pgxpool"

	"callin-go/internal/middleware"
)

// Handler exposes read-only REST endpoints for call history. Live
// call actions (invite, accept, decline, end) happen over the
// WebSocket signaling channel, not REST — see internal/signaling.
type Handler struct {
	pool *pgxpool.Pool
}

func NewHandler(pool *pgxpool.Pool) *Handler {
	return &Handler{pool: pool}
}

// History returns the caller's recent calls, most recent first.
// GET /api/v1/calls?limit=50
func (h *Handler) History(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	limit := 50
	if v := r.URL.Query().Get("limit"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 && n <= 200 {
			limit = n
		}
	}

	const q = `
		SELECT id, caller_id, callee_id, status, end_reason, started_at, connected_at, ended_at
		FROM calls
		WHERE caller_id = $1 OR callee_id = $1
		ORDER BY started_at DESC
		LIMIT $2`

	rows, err := h.pool.Query(r.Context(), q, userID, limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load call history")
		return
	}
	defer rows.Close()

	calls := make([]Call, 0, limit)
	for rows.Next() {
		var c Call
		if err := rows.Scan(&c.ID, &c.CallerID, &c.CalleeID, &c.Status, &c.EndReason,
			&c.StartedAt, &c.ConnectedAt, &c.EndedAt); err != nil {
			writeError(w, http.StatusInternalServerError, "could not read call history")
			return
		}
		calls = append(calls, c)
	}

	writeJSON(w, http.StatusOK, map[string]any{"calls": calls})
}

// Persist writes a terminal call to history. Called by the
// signaling router once a call ends, is declined, missed, or
// cancelled.
func Persist(ctx context.Context, pool *pgxpool.Pool, c *ActiveCall, status Status, endReason *string) error {
	const q = `
		INSERT INTO calls (id, caller_id, callee_id, status, end_reason, started_at, ended_at)
		VALUES ($1, $2, $3, $4, $5, $6, now())`

	_, err := pool.Exec(ctx, q, c.ID, c.CallerID, c.CalleeID, status, endReason, c.StartedAt)
	return err
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
