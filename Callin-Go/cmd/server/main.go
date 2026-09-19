// Command server boots the CALLIN backend: HTTP API, WebSocket
// signaling, and all the wiring for auth, users, calls, and push.
package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gorilla/websocket"

	"callin-go/internal/auth"
	"callin-go/internal/call"
	"callin-go/internal/config"
	"callin-go/internal/db"
	"callin-go/internal/middleware"
	"callin-go/internal/push"
	"callin-go/internal/signaling"
	"callin-go/internal/storage"
	"callin-go/internal/user"
	"callin-go/pkg/logger"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		panic("config: " + err.Error())
	}

	log := logger.New(cfg.Environment)
	log.Info("starting CALLIN backend", "environment", cfg.Environment, "port", cfg.Port)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Error("connecting to database", "error", err)
		os.Exit(1)
	}
	defer pool.Close()
	log.Info("connected to database")

	userRepo := user.NewRepository(pool)
	tokenManager := auth.NewTokenManager(cfg.JWTSecret, cfg.JWTAccessTTL, cfg.JWTRefreshTTL)

	var googleVerifier *auth.GoogleVerifier
	if cfg.GoogleWebClientID == "" {
		log.Warn("GOOGLE_WEB_CLIENT_ID not set — Google Sign-In is disabled")
	} else {
		googleVerifier, err = auth.NewGoogleVerifier(ctx, cfg.GoogleWebClientID)
		if err != nil {
			log.Error("initializing Google verifier", "error", err)
			os.Exit(1)
		}
	}
	authService := auth.NewService(userRepo, tokenManager, googleVerifier)

	fcmClient, err := push.NewFCMClient(ctx, cfg.FCMProjectID, cfg.FCMServiceAccountFile, userRepo, log)
	if err != nil {
		log.Error("initializing FCM client", "error", err)
		os.Exit(1)
	}

	var avatarStorage *storage.SupabaseStorage
	if cfg.SupabaseURL == "" || cfg.SupabaseServiceKey == "" {
		log.Warn("SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY not set — avatar uploads are disabled")
	} else {
		avatarStorage = storage.NewSupabaseStorage(cfg.SupabaseURL, cfg.SupabaseServiceKey, cfg.SupabaseBucket)
	}

	hub := signaling.NewHub(log)
	callRegistry := call.NewRegistry()
	router := signaling.NewRouter(hub, callRegistry, pool, fcmClient, log)

	userHandler := user.NewHandler(userRepo, avatarStorage)
	authHandler := auth.NewHandler(authService)
	callHandler := call.NewHandler(pool)

	verify := func(token string) (string, error) {
		claims, err := tokenManager.Verify(token)
		if err != nil {
			return "", err
		}
		return claims.UserID, nil
	}
	requireAuth := middleware.RequireAuth(verify)

	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", healthCheck)

	mux.HandleFunc("POST /api/v1/auth/register", authHandler.Register)
	mux.HandleFunc("POST /api/v1/auth/login", authHandler.Login)
	mux.HandleFunc("POST /api/v1/auth/refresh", authHandler.Refresh)
	mux.HandleFunc("POST /api/v1/auth/google", authHandler.GoogleSignIn)

	mux.Handle("GET /api/v1/users/me", requireAuth(http.HandlerFunc(userHandler.Me)))
	mux.Handle("GET /api/v1/users", requireAuth(http.HandlerFunc(userHandler.List)))
	mux.Handle("GET /api/v1/users/check-username", requireAuth(http.HandlerFunc(userHandler.CheckUsername)))
	mux.Handle("POST /api/v1/users/me/device", requireAuth(http.HandlerFunc(userHandler.RegisterDevice)))
	mux.Handle("POST /api/v1/users/me/complete-profile", requireAuth(http.HandlerFunc(userHandler.CompleteProfile)))
	mux.Handle("POST /api/v1/users/me/avatar", requireAuth(http.HandlerFunc(userHandler.UploadAvatar)))

	mux.Handle("GET /api/v1/calls", requireAuth(http.HandlerFunc(callHandler.History)))

	upgrader := websocket.Upgrader{
		ReadBufferSize:  4096,
		WriteBufferSize: 4096,
		CheckOrigin:     allowedOriginChecker(cfg.AllowedOrigins),
	}

	mux.HandleFunc("GET /ws", func(w http.ResponseWriter, r *http.Request) {
		// WebSocket clients can't always set custom headers during
		// the upgrade handshake, so the access token is also
		// accepted as a query parameter here.
		token := r.URL.Query().Get("token")
		if token == "" {
			http.Error(w, "missing token", http.StatusUnauthorized)
			return
		}
		claims, err := tokenManager.Verify(token)
		if err != nil {
			http.Error(w, "invalid or expired token", http.StatusUnauthorized)
			return
		}

		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Error("websocket upgrade failed", "error", err)
			return
		}

		client := signaling.NewClient(claims.UserID, conn, hub, router, log)
		go client.Run()
	})

	rateLimiter := middleware.NewIPRateLimiter(cfg.RateLimitRPS, cfg.RateLimitBurst)
	handler := middleware.Logging(log)(rateLimiter.RateLimit(mux))

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      handler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Info("listening", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Error("server error", "error", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	log.Info("shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Error("graceful shutdown failed", "error", err)
	}
	log.Info("shutdown complete")
}

func healthCheck(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}

func allowedOriginChecker(allowed []string) func(r *http.Request) bool {
	return func(r *http.Request) bool {
		if len(allowed) == 1 && allowed[0] == "*" {
			return true
		}
		origin := r.Header.Get("Origin")
		for _, o := range allowed {
			if o == origin {
				return true
			}
		}
		return false
	}
}
