package auth

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strings"

	"golang.org/x/crypto/bcrypt"

	"callin-go/internal/user"
)

var (
	ErrInvalidCredentials = errors.New("auth: invalid credentials")
	ErrUsernameTaken      = errors.New("auth: username or email already in use")
	ErrWeakPassword       = errors.New("auth: password must be at least 8 characters")
	ErrInvalidUsername    = errors.New("auth: username must be 3-32 characters, letters, numbers, underscores only")
)

var usernameRE = regexp.MustCompile(`^[a-zA-Z0-9_]{3,32}$`)

// Service implements registration, login and token refresh. It sits
// between the HTTP handler and the user repository.
type Service struct {
	users  *user.Repository
	tokens *TokenManager
}

func NewService(users *user.Repository, tokens *TokenManager) *Service {
	return &Service{users: users, tokens: tokens}
}

// AuthResult is returned from every successful auth operation.
type AuthResult struct {
	User         user.Public `json:"user"`
	AccessToken  string      `json:"access_token"`
	RefreshToken string      `json:"refresh_token"`
}

func (s *Service) Register(ctx context.Context, username, email, password, displayName string) (*AuthResult, error) {
	username = strings.TrimSpace(strings.ToLower(username))
	email = strings.TrimSpace(strings.ToLower(email))

	if !usernameRE.MatchString(username) {
		return nil, ErrInvalidUsername
	}
	if len(password) < 8 {
		return nil, ErrWeakPassword
	}
	if displayName == "" {
		displayName = username
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("hashing password: %w", err)
	}

	u := &user.User{
		Username:     username,
		Email:        email,
		PasswordHash: string(hash),
		DisplayName:  displayName,
	}

	if err := s.users.Create(ctx, u); err != nil {
		if isUniqueViolation(err) {
			return nil, ErrUsernameTaken
		}
		return nil, fmt.Errorf("creating user: %w", err)
	}

	return s.issueTokens(u)
}

func (s *Service) Login(ctx context.Context, identifier, password string) (*AuthResult, error) {
	identifier = strings.TrimSpace(strings.ToLower(identifier))

	u, err := s.users.GetByUsernameOrEmail(ctx, identifier)
	if err != nil {
		if errors.Is(err, user.ErrNotFound) {
			return nil, ErrInvalidCredentials
		}
		return nil, fmt.Errorf("looking up user: %w", err)
	}

	if err := bcrypt.CompareHashAndPassword([]byte(u.PasswordHash), []byte(password)); err != nil {
		return nil, ErrInvalidCredentials
	}

	return s.issueTokens(u)
}

func (s *Service) Refresh(ctx context.Context, refreshToken string) (*AuthResult, error) {
	claims, err := s.tokens.Verify(refreshToken)
	if err != nil {
		return nil, ErrInvalidCredentials
	}

	u, err := s.users.GetByID(ctx, claims.UserID)
	if err != nil {
		return nil, ErrInvalidCredentials
	}

	return s.issueTokens(u)
}

func (s *Service) issueTokens(u *user.User) (*AuthResult, error) {
	access, err := s.tokens.GenerateAccessToken(u.ID)
	if err != nil {
		return nil, fmt.Errorf("generating access token: %w", err)
	}
	refresh, err := s.tokens.GenerateRefreshToken(u.ID)
	if err != nil {
		return nil, fmt.Errorf("generating refresh token: %w", err)
	}

	return &AuthResult{
		User:         u.ToPublic(),
		AccessToken:  access,
		RefreshToken: refresh,
	}, nil
}

func isUniqueViolation(err error) bool {
	// pgx wraps postgres errors; checking the message keeps this
	// package decoupled from a specific pg driver error type.
	return strings.Contains(err.Error(), "duplicate key value violates unique constraint")
}
