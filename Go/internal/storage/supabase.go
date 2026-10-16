// Package storage uploads user-generated files (currently just
// avatars) to Supabase Storage using the project's service role key.
package storage

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
)

// SupabaseStorage is a thin client around Supabase's Storage REST
// API — no SDK needed for something this small.
type SupabaseStorage struct {
	projectURL string // e.g. https://xxxx.supabase.co
	serviceKey string
	bucket     string
	httpClient *http.Client
}

func NewSupabaseStorage(projectURL, serviceKey, bucket string) *SupabaseStorage {
	return &SupabaseStorage{
		projectURL: projectURL,
		serviceKey: serviceKey,
		bucket:     bucket,
		httpClient: http.DefaultClient,
	}
}

// UploadAvatar uploads image bytes to <bucket>/avatars/<userID>.<ext>,
// overwriting any previous avatar for that user (upsert), and
// returns its public URL. The bucket must be public.
func (s *SupabaseStorage) UploadAvatar(ctx context.Context, userID, ext, contentType string, data []byte) (string, error) {
	objectPath := fmt.Sprintf("avatars/%s.%s", userID, ext)
	uploadURL := fmt.Sprintf("%s/storage/v1/object/%s/%s", s.projectURL, s.bucket, objectPath)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, uploadURL, bytes.NewReader(data))
	if err != nil {
		return "", fmt.Errorf("building upload request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+s.serviceKey)
	req.Header.Set("Content-Type", contentType)
	req.Header.Set("x-upsert", "true") // overwrite if this user already has an avatar

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("uploading to supabase storage: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("supabase storage returned %d: %s", resp.StatusCode, string(body))
	}

	publicURL := fmt.Sprintf("%s/storage/v1/object/public/%s/%s", s.projectURL, s.bucket, objectPath)
	return publicURL, nil
}
