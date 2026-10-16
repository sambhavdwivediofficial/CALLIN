package auth

import (
	"encoding/json"
	"errors"
	"net/http"
)

// Handler exposes registration, login, token refresh, and Google
// Sign-In over HTTP.
type Handler struct {
	service *Service
}

func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

type registerRequest struct {
	Username    string `json:"username"`
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
}

// Register creates a new classic (username+password) account.
// POST /api/v1/auth/register
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	result, err := h.service.Register(r.Context(), req.Username, req.Email, req.Password, req.DisplayName)
	if err != nil {
		handleAuthError(w, err)
		return
	}

	writeJSON(w, http.StatusCreated, result)
}

type loginRequest struct {
	Identifier string `json:"identifier"` // username or email
	Password   string `json:"password"`
}

// Login authenticates an existing classic account.
// POST /api/v1/auth/login
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	result, err := h.service.Login(r.Context(), req.Identifier, req.Password)
	if err != nil {
		handleAuthError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, result)
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

// Refresh exchanges a valid refresh token for a new token pair.
// POST /api/v1/auth/refresh
func (h *Handler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	result, err := h.service.Refresh(r.Context(), req.RefreshToken)
	if err != nil {
		handleAuthError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, result)
}

type googleSignInRequest struct {
	IDToken string `json:"id_token"`
}

// GoogleSignIn verifies a Google ID token from the Android app and
// logs the user in, creating a new (profile-incomplete) account the
// first time this Google account is seen.
// POST /api/v1/auth/google
func (h *Handler) GoogleSignIn(w http.ResponseWriter, r *http.Request) {
	var req googleSignInRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.IDToken == "" {
		writeError(w, http.StatusBadRequest, "id_token is required")
		return
	}

	result, err := h.service.GoogleSignIn(r.Context(), req.IDToken)
	if err != nil {
		handleAuthError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, result)
}

func handleAuthError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrInvalidCredentials), errors.Is(err, ErrInvalidGoogleToken):
		writeError(w, http.StatusUnauthorized, "invalid credentials")
	case errors.Is(err, ErrUsernameTaken):
		writeError(w, http.StatusConflict, "username or email already in use")
	case errors.Is(err, ErrWeakPassword), errors.Is(err, ErrInvalidUsername), errors.Is(err, ErrNoPassword):
		writeError(w, http.StatusBadRequest, err.Error())
	case errors.Is(err, ErrGoogleNotConfigured):
		writeError(w, http.StatusServiceUnavailable, err.Error())
	default:
		writeError(w, http.StatusInternalServerError, "something went wrong")
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
