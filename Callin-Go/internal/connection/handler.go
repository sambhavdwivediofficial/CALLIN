package connection

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"callin-go/internal/middleware"
)

// Handler exposes the connection-request flow over HTTP: send a
// request (what a QR scan resolves to), list requests waiting on
// you, respond to one, and list your accepted connections.
type Handler struct {
	repo *Repository
}

func NewHandler(repo *Repository) *Handler {
	return &Handler{repo: repo}
}

type requestRequest struct {
	Username string `json:"username"`
}

// SendRequest sends (or auto-accepts, if they already requested you)
// a connection request to a user identified by username.
// POST /api/v1/connections/request
func (h *Handler) SendRequest(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	var req requestRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	username := strings.ToLower(strings.TrimSpace(req.Username))
	if username == "" {
		writeError(w, http.StatusBadRequest, "username is required")
		return
	}

	targetID, err := h.repo.FindUserIDByUsername(r.Context(), username)
	if err != nil {
		if errors.Is(err, ErrUserNotFound) {
			writeError(w, http.StatusNotFound, "no CALLIN user with that username")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not look up user")
		return
	}

	if already, err := h.repo.AreConnected(r.Context(), userID, targetID); err == nil && already {
		writeJSON(w, http.StatusOK, map[string]string{"status": string(StatusAccepted)})
		return
	}

	status, err := h.repo.CreateRequest(r.Context(), userID, targetID)
	if err != nil {
		if errors.Is(err, ErrCannotSelf) {
			writeError(w, http.StatusBadRequest, "you can't connect with yourself")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not send request")
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": string(status)})
}

// Pending returns requests waiting on the caller to respond.
// GET /api/v1/connections/pending
func (h *Handler) Pending(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}
	pending, err := h.repo.PendingForUser(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load requests")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"requests": pending})
}

type respondRequest struct {
	Accept bool `json:"accept"`
}

// Respond accepts or rejects a pending request.
// POST /api/v1/connections/{id}/respond
func (h *Handler) Respond(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}
	requestID := r.PathValue("id")

	var req respondRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if err := h.repo.Respond(r.Context(), requestID, userID, req.Accept); err != nil {
		if errors.Is(err, ErrNotFound) {
			writeError(w, http.StatusNotFound, "request not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not respond to request")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// List returns the caller's accepted connections.
// GET /api/v1/connections
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}
	contacts, err := h.repo.ListAccepted(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load connections")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"connections": contacts})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
