// Package config loads and validates all runtime configuration for
// the CALLIN backend from environment variables.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Config holds every piece of configuration the server needs to boot.
type Config struct {
	Environment string
	Port        string

	DatabaseURL string

	JWTSecret     string
	JWTAccessTTL  time.Duration
	JWTRefreshTTL time.Duration

	FCMProjectID          string
	FCMServiceAccountFile string

	AllowedOrigins []string

	RateLimitRPS   float64
	RateLimitBurst int
}

// Load reads a .env file if present (harmless in production, where
// real environment variables are injected by the platform instead),
// then builds and validates a Config.
func Load() (*Config, error) {
	_ = godotenv.Load()

	cfg := &Config{
		Environment: getEnv("ENVIRONMENT", "development"),
		Port:        getEnv("PORT", "8080"),
		DatabaseURL: os.Getenv("DATABASE_URL"),
		JWTSecret:   os.Getenv("JWT_SECRET"),

		FCMProjectID:          os.Getenv("FCM_PROJECT_ID"),
		FCMServiceAccountFile: os.Getenv("FCM_SERVICE_ACCOUNT_FILE"),

		AllowedOrigins: splitAndTrim(getEnv("ALLOWED_ORIGINS", "*")),
	}

	accessMinutes, err := getEnvInt("JWT_ACCESS_TTL_MINUTES", 15)
	if err != nil {
		return nil, err
	}
	cfg.JWTAccessTTL = time.Duration(accessMinutes) * time.Minute

	refreshHours, err := getEnvInt("JWT_REFRESH_TTL_HOURS", 720)
	if err != nil {
		return nil, err
	}
	cfg.JWTRefreshTTL = time.Duration(refreshHours) * time.Hour

	rps, err := getEnvFloat("RATE_LIMIT_RPS", 5)
	if err != nil {
		return nil, err
	}
	cfg.RateLimitRPS = rps

	burst, err := getEnvInt("RATE_LIMIT_BURST", 10)
	if err != nil {
		return nil, err
	}
	cfg.RateLimitBurst = burst

	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

func (c *Config) validate() error {
	if c.DatabaseURL == "" {
		return fmt.Errorf("DATABASE_URL is required")
	}
	if c.JWTSecret == "" || len(c.JWTSecret) < 16 {
		return fmt.Errorf("JWT_SECRET is required and must be at least 16 characters")
	}
	return nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) (int, error) {
	v := os.Getenv(key)
	if v == "" {
		return fallback, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("invalid value for %s: %w", key, err)
	}
	return n, nil
}

func getEnvFloat(key string, fallback float64) (float64, error) {
	v := os.Getenv(key)
	if v == "" {
		return fallback, nil
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid value for %s: %w", key, err)
	}
	return f, nil
}

func splitAndTrim(v string) []string {
	parts := strings.Split(v, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
