// Package push sends incoming-call wake-up notifications to
// offline or backgrounded devices via Firebase Cloud Messaging's
// HTTP v1 API.
package push

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"

	"callin-go/internal/user"
)

const fcmMessagingScope = "https://www.googleapis.com/auth/firebase.messaging"

// FCMClient sends high-priority "incoming call" data messages
// through Firebase Cloud Messaging's HTTP v1 API. It looks up the
// recipient's registered device tokens itself, so the rest of the
// backend only ever needs to pass a user ID.
type FCMClient struct {
	projectID   string
	users       *user.Repository
	httpClient  *http.Client
	tokenSource oauth2.TokenSource
	logger      *slog.Logger
}

// NewFCMClient loads a Firebase service account JSON key file and
// prepares an OAuth2 token source scoped to FCM. If
// serviceAccountFile is empty, push notifications are disabled and
// calls simply won't wake backgrounded devices — everything else
// (signaling, in-app ringing) keeps working normally.
func NewFCMClient(ctx context.Context, projectID, serviceAccountFile string, users *user.Repository, logger *slog.Logger) (*FCMClient, error) {
	if serviceAccountFile == "" {
		logger.Warn("FCM_SERVICE_ACCOUNT_FILE not set — incoming-call push notifications are disabled")
		return &FCMClient{projectID: projectID, users: users, httpClient: http.DefaultClient, logger: logger}, nil
	}

	keyData, err := os.ReadFile(serviceAccountFile)
	if err != nil {
		return nil, fmt.Errorf("reading FCM service account file: %w", err)
	}

	creds, err := google.CredentialsFromJSON(ctx, keyData, fcmMessagingScope)
	if err != nil {
		return nil, fmt.Errorf("parsing FCM service account credentials: %w", err)
	}

	return &FCMClient{
		projectID:   projectID,
		users:       users,
		httpClient:  &http.Client{Timeout: 10 * time.Second},
		tokenSource: creds.TokenSource,
		logger:      logger,
	}, nil
}

// NotifyIncomingCall pushes a wake-up notification to every device
// the callee has registered. Failures are logged, not returned — a
// failed push should never break call signaling for clients that
// are still connected over the WebSocket.
func (f *FCMClient) NotifyIncomingCall(ctx context.Context, calleeID, callID, callerID string) {
	if f.tokenSource == nil {
		return // push disabled, see NewFCMClient
	}

	tokens, err := f.users.DeviceTokens(ctx, calleeID)
	if err != nil {
		f.logger.Error("fetching device tokens", "user_id", calleeID, "error", err)
		return
	}

	for _, token := range tokens {
		if err := f.sendCallPush(ctx, token, callID, callerID); err != nil {
			f.logger.Error("sending FCM push", "user_id", calleeID, "error", err)
		}
	}
}

type fcmMessage struct {
	Message struct {
		Token   string            `json:"token"`
		Data    map[string]string `json:"data"`
		Android struct {
			Priority string `json:"priority"`
		} `json:"android"`
	} `json:"message"`
}

func (f *FCMClient) sendCallPush(ctx context.Context, deviceToken, callID, callerID string) error {
	token, err := f.tokenSource.Token()
	if err != nil {
		return fmt.Errorf("getting oauth token: %w", err)
	}

	var body fcmMessage
	body.Message.Token = deviceToken
	body.Message.Data = map[string]string{
		"type":      "incoming_call",
		"call_id":   callID,
		"caller_id": callerID,
	}
	body.Message.Android.Priority = "high"

	payload, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("encoding FCM payload: %w", err)
	}

	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", f.projectID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("building FCM request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token.AccessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := f.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("calling FCM: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		return fmt.Errorf("FCM returned status %d", resp.StatusCode)
	}
	return nil
}
