// Package auth handles account registration, login, and issuing
// and verifying the JSON Web Tokens used to authenticate every
// other request in the system, including the WebSocket handshake.
package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

var ErrInvalidToken = errors.New("auth: invalid or expired token")

// Claims is the payload embedded in every CALLIN access/refresh token.
type Claims struct {
	UserID string `json:"uid"`
	jwt.RegisteredClaims
}

// TokenManager issues and verifies HMAC-signed JWTs.
type TokenManager struct {
	secret     []byte
	accessTTL  time.Duration
	refreshTTL time.Duration
}

func NewTokenManager(secret string, accessTTL, refreshTTL time.Duration) *TokenManager {
	return &TokenManager{secret: []byte(secret), accessTTL: accessTTL, refreshTTL: refreshTTL}
}

func (m *TokenManager) GenerateAccessToken(userID string) (string, error) {
	return m.generate(userID, m.accessTTL)
}

func (m *TokenManager) GenerateRefreshToken(userID string) (string, error) {
	return m.generate(userID, m.refreshTTL)
}

func (m *TokenManager) generate(userID string, ttl time.Duration) (string, error) {
	claims := Claims{
		UserID: userID,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			Issuer:    "callin-backend",
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(m.secret)
}

// Verify parses and validates a token, returning the embedded claims.
func (m *TokenManager) Verify(tokenString string) (*Claims, error) {
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokenString, claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, ErrInvalidToken
		}
		return m.secret, nil
	})
	if err != nil || !token.Valid {
		return nil, ErrInvalidToken
	}
	return claims, nil
}
