package user

import (
	"encoding/json"
	"errors"
	"net/http"
	"regexp"
	"strings"

	"callin-go/internal/middleware"
)

var usernameRE = regexp.MustCompile(`^[a-zA-Z0-9_]{3,32}$`)

// Handler exposes user-related HTTP endpoints: profile and contacts.
type Handler struct {
	repo *Repository
}

func NewHandler(repo *Repository) *Handler {
	return &Handler{repo: repo}
}

// Me returns the authenticated user's own profile.
// GET /api/v1/users/me
func (h *Handler) Me(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	u, err := h.repo.GetByID(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}

	writeJSON(w, http.StatusOK, u.ToMe())
}

// List returns every other user, for the contacts screen.
// GET /api/v1/users
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	users, err := h.repo.List(r.Context(), userID, 200)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list users")
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"users": users})
}

type registerDeviceRequest struct {
	FCMToken string `json:"fcm_token"`
	Platform string `json:"platform"`
}

// RegisterDevice stores or refreshes the caller's FCM push token.
// POST /api/v1/users/me/device
func (h *Handler) RegisterDevice(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	var req registerDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.FCMToken == "" {
		writeError(w, http.StatusBadRequest, "fcm_token is required")
		return
	}
	if req.Platform == "" {
		req.Platform = "android"
	}

	if err := h.repo.UpsertDevice(r.Context(), userID, req.FCMToken, req.Platform); err != nil {
		writeError(w, http.StatusInternalServerError, "could not register device")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

type completeProfileRequest struct {
	Username  string `json:"username"`
	FirstName string `json:"first_name"`
	LastName  string `json:"last_name"`
}

// CompleteProfile sets a first-time Google Sign-In user's username,
// first name and last name. It can only be called once per account
// — once profile_completed is true, this always fails, even for the
// same values.
// POST /api/v1/users/me/complete-profile
func (h *Handler) CompleteProfile(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	var req completeProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	username := strings.ToLower(strings.TrimSpace(req.Username))
	firstName := strings.TrimSpace(req.FirstName)
	lastName := strings.TrimSpace(req.LastName)

	if !usernameRE.MatchString(username) {
		writeError(w, http.StatusBadRequest, "username must be 3-32 characters: letters, numbers, underscores only")
		return
	}
	if firstName == "" || lastName == "" {
		writeError(w, http.StatusBadRequest, "first_name and last_name are required")
		return
	}

	updated, err := h.repo.CompleteProfile(r.Context(), userID, username, firstName, lastName)
	if err != nil {
		switch {
		case errors.Is(err, ErrUsernameTaken):
			writeError(w, http.StatusConflict, "username already in use")
		case errors.Is(err, ErrProfileAlreadySet):
			writeError(w, http.StatusConflict, "profile has already been completed")
		default:
			writeError(w, http.StatusInternalServerError, "could not complete profile")
		}
		return
	}

	writeJSON(w, http.StatusOK, updated.ToMe())
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
