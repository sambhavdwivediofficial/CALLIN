// Package push sends incoming-call wake-up notifications to
// offline or unreachable devices via Firebase Cloud Messaging's
// HTTP v1 API.
package push

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"

	"callin-go/internal/user"
)

const fcmMessagingScope = "https://www.googleapis.com/auth/firebase.messaging"

type FCMClient struct {
	projectID   string
	users       *user.Repository
	httpClient  *http.Client
	tokenSource oauth2.TokenSource
	logger      *slog.Logger
}

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

type CallerInfo struct {
	Username    string
	DisplayName string
	AvatarURL   string
}

// NotifyIncomingCall pushes a wake-up notification to every device
// the callee has registered. A token FCM reports as permanently
// dead (NotRegistered/UNREGISTERED) is pruned from the database
// immediately — standard FCM housekeeping, and what stops a stale
// token from one old install from silently swallowing every future
// call attempt to that user while a valid token sits unused right
// next to it.
func (f *FCMClient) NotifyIncomingCall(ctx context.Context, calleeID, callID string, caller CallerInfo) {
	if f.tokenSource == nil {
		return
	}

	tokens, err := f.users.DeviceTokens(ctx, calleeID)
	if err != nil {
		f.logger.Error("fetching device tokens", "user_id", calleeID, "error", err)
		return
	}

	if len(tokens) == 0 {
		f.logger.Warn("no registered device tokens for user — push cannot be sent", "user_id", calleeID)
		return
	}

	for _, token := range tokens {
		unregistered, sendErr := f.sendCallPush(ctx, token, callID, caller)
		if sendErr != nil {
			f.logger.Error("sending FCM push", "user_id", calleeID, "error", sendErr)
		}
		if unregistered {
			if delErr := f.users.DeleteDeviceToken(ctx, calleeID, token); delErr != nil {
				f.logger.Error("pruning dead device token", "user_id", calleeID, "error", delErr)
			} else {
				f.logger.Info("pruned dead device token", "user_id", calleeID, "token_prefix", tokenPrefix(token))
			}
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

// fcmErrorResponse mirrors FCM's HTTP v1 error body — enough to
// reliably tell "this token is permanently dead" apart from every
// other failure (wrong project, auth failure, transient server
// error), which must NOT have their token deleted.
type fcmErrorResponse struct {
	Error struct {
		Status  string `json:"status"`
		Message string `json:"message"`
	} `json:"error"`
}

// sendCallPush returns (unregistered, err): unregistered is true
// only when FCM has explicitly said this exact token will never
// work again.
func (f *FCMClient) sendCallPush(ctx context.Context, deviceToken, callID string, caller CallerInfo) (bool, error) {
	token, err := f.tokenSource.Token()
	if err != nil {
		return false, fmt.Errorf("getting oauth token: %w", err)
	}

	var body fcmMessage
	body.Message.Token = deviceToken
	body.Message.Data = map[string]string{
		"type":                "incoming_call",
		"call_id":             callID,
		"caller_id":           caller.Username,
		"caller_username":     caller.Username,
		"caller_display_name": caller.DisplayName,
		"caller_avatar_url":   caller.AvatarURL,
	}
	body.Message.Android.Priority = "high"

	payload, err := json.Marshal(body)
	if err != nil {
		return false, fmt.Errorf("encoding FCM payload: %w", err)
	}

	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", f.projectID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return false, fmt.Errorf("building FCM request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token.AccessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := f.httpClient.Do(req)
	if err != nil {
		return false, fmt.Errorf("calling FCM: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)

		var parsed fcmErrorResponse
		_ = json.Unmarshal(respBody, &parsed)
		unregistered := parsed.Error.Status == "NOT_FOUND" ||
			strings.EqualFold(parsed.Error.Message, "NotRegistered") ||
			strings.Contains(string(respBody), "UNREGISTERED")

		return unregistered, fmt.Errorf("FCM returned status %d, project=%s, token_prefix=%s: %s",
			resp.StatusCode, f.projectID, tokenPrefix(deviceToken), string(respBody))
	}
	return false, nil
}

func tokenPrefix(token string) string {
	if len(token) > 12 {
		return token[:12] + "..."
	}
	return token
}
