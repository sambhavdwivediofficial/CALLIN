// Package logger provides a single structured logger for the whole
// CALLIN backend, built on the standard library's slog package.
package logger

import (
	"log/slog"
	"os"
)

// New builds a structured logger. In development it logs at debug
// level as human-readable text; in production it logs at info
// level as JSON so it can be shipped to a log aggregator.
func New(environment string) *slog.Logger {
	level := slog.LevelInfo
	if environment == "development" {
		level = slog.LevelDebug
	}

	opts := &slog.HandlerOptions{
		Level:     level,
		AddSource: environment == "development",
	}

	var handler slog.Handler
	if environment == "development" {
		handler = slog.NewTextHandler(os.Stdout, opts)
	} else {
		handler = slog.NewJSONHandler(os.Stdout, opts)
	}

	logger := slog.New(handler)
	slog.SetDefault(logger)
	return logger
}
