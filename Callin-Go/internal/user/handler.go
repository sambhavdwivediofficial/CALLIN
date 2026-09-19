package user

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"regexp"
	"strings"

	"callin-go/internal/middleware"
	"callin-go/internal/storage"
)

// usernameRE: lowercase letters, digits, underscore, hyphen only —
// 3 to 18 characters.
var usernameRE = regexp.MustCompile(`^[a-z0-9_-]{3,18}$`)

const maxAvatarSize = 5 << 20 // 5MB

// Handler exposes user-related HTTP endpoints: profile, contacts,
// and avatar uploads.
type Handler struct {
	repo    *Repository
	storage *storage.SupabaseStorage // nil when Supabase Storage isn't configured
}

func NewHandler(repo *Repository, storage *storage.SupabaseStorage) *Handler {
	return &Handler{repo: repo, storage: storage}
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
		writeError(w, http.StatusBadRequest, "username must be 3-18 characters: lowercase letters, numbers, underscore, hyphen only")
		return
	}
	if len(firstName) < 2 || len(firstName) > 10 {
		writeError(w, http.StatusBadRequest, "first_name must be 2-10 characters")
		return
	}
	if len(lastName) < 2 || len(lastName) > 10 {
		writeError(w, http.StatusBadRequest, "last_name must be 2-10 characters")
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

// CheckUsername reports whether a username is free to take. Used
// for the live "Available" / "Already taken" indicator while the
// user is typing during profile setup.
// GET /api/v1/users/check-username?username=xxx
func (h *Handler) CheckUsername(w http.ResponseWriter, r *http.Request) {
	if _, ok := middleware.UserIDFromContext(r.Context()); !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	username := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("username")))
	if !usernameRE.MatchString(username) {
		writeJSON(w, http.StatusOK, map[string]bool{"available": false})
		return
	}

	exists, err := h.repo.UsernameExists(r.Context(), username)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not check username")
		return
	}

	writeJSON(w, http.StatusOK, map[string]bool{"available": !exists})
}

// UploadAvatar accepts a single image file (form field "avatar"),
// uploads it to Supabase Storage, and saves the resulting URL on
// the caller's profile.
// POST /api/v1/users/me/avatar
func (h *Handler) UploadAvatar(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "not authenticated")
		return
	}

	if h.storage == nil {
		writeError(w, http.StatusServiceUnavailable, "avatar uploads are not configured on this server yet")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxAvatarSize)
	if err := r.ParseMultipartForm(maxAvatarSize); err != nil {
		writeError(w, http.StatusBadRequest, "file too large (max 5MB) or invalid upload")
		return
	}

	file, header, err := r.FormFile("avatar")
	if err != nil {
		writeError(w, http.StatusBadRequest, "avatar file is required")
		return
	}
	defer file.Close()

	contentType := header.Header.Get("Content-Type")
	if !strings.HasPrefix(contentType, "image/") {
		writeError(w, http.StatusBadRequest, "file must be an image")
		return
	}

	data, err := io.ReadAll(file)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not read uploaded file")
		return
	}

	url, err := h.storage.UploadAvatar(r.Context(), userID, extensionFor(contentType), contentType, data)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not upload avatar")
		return
	}

	if err := h.repo.UpdateAvatarURL(r.Context(), userID, url); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save avatar")
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"avatar_url": url})
}

func extensionFor(contentType string) string {
	switch contentType {
	case "image/png":
		return "png"
	case "image/webp":
		return "webp"
	case "image/gif":
		return "gif"
	default:
		return "jpg"
	}
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
