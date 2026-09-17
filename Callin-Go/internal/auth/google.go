package auth

import (
	"context"
	"errors"
	"fmt"

	"github.com/coreos/go-oidc/v3/oidc"
)

var ErrInvalidGoogleToken = errors.New("auth: invalid Google ID token")

// GoogleClaims is the subset of a verified Google ID token we use.
type GoogleClaims struct {
	Subject       string `json:"sub"`
	Email         string `json:"email"`
	EmailVerified bool   `json:"email_verified"`
	GivenName     string `json:"given_name"`
	FamilyName    string `json:"family_name"`
	Picture       string `json:"picture"`
}

// GoogleVerifier checks Google-issued ID tokens against Google's own
// published signing keys and our expected audience. Credential
// Manager on Android signs the token for the Web Client ID (not an
// Android-specific one), so that's what we configure here — the same
// value used as GOOGLE_WEB_CLIENT_ID everywhere in this system.
type GoogleVerifier struct {
	verifier *oidc.IDTokenVerifier
}

func NewGoogleVerifier(ctx context.Context, webClientID string) (*GoogleVerifier, error) {
	provider, err := oidc.NewProvider(ctx, "https://accounts.google.com")
	if err != nil {
		return nil, fmt.Errorf("connecting to Google OIDC discovery: %w", err)
	}
	return &GoogleVerifier{
		verifier: provider.Verifier(&oidc.Config{ClientID: webClientID}),
	}, nil
}

// Verify checks the token's signature, issuer, audience and
// expiry, and returns the claims embedded in it. It also rejects
// tokens for unverified email addresses — we never want to create
// an account CALLIN can't actually reach.
func (g *GoogleVerifier) Verify(ctx context.Context, rawIDToken string) (*GoogleClaims, error) {
	idToken, err := g.verifier.Verify(ctx, rawIDToken)
	if err != nil {
		return nil, ErrInvalidGoogleToken
	}

	var claims GoogleClaims
	if err := idToken.Claims(&claims); err != nil {
		return nil, ErrInvalidGoogleToken
	}
	if !claims.EmailVerified {
		return nil, ErrInvalidGoogleToken
	}
	return &claims, nil
}
