-- CALLIN initial schema: users, devices, calls.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(32)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  TEXT         NOT NULL,
    display_name   VARCHAR(64)  NOT NULL,
    avatar_url     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS devices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fcm_token     TEXT NOT NULL,
    platform      VARCHAR(16) NOT NULL DEFAULT 'android',
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices (user_id);

CREATE TABLE IF NOT EXISTS calls (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    callee_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        VARCHAR(16) NOT NULL DEFAULT 'ringing',
    end_reason    VARCHAR(32),
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    connected_at  TIMESTAMPTZ,
    ended_at      TIMESTAMPTZ,
    CONSTRAINT chk_calls_status CHECK (
        status IN ('ringing', 'connecting', 'connected', 'ended', 'missed', 'declined', 'cancelled')
    )
);

CREATE INDEX IF NOT EXISTS idx_calls_caller_id ON calls (caller_id);
CREATE INDEX IF NOT EXISTS idx_calls_callee_id ON calls (callee_id);
CREATE INDEX IF NOT EXISTS idx_calls_started_at ON calls (started_at DESC);
