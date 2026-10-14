# Backend Setup Guide

Everything needed to run, configure, and deploy the `Callin-Go` backend: environment
variables, database migrations, third-party service setup (Google Sign-In, Firebase Cloud
Messaging, Supabase Storage), and the full REST + WebSocket API surface.

## Table of Contents

1. [`Prerequisites`](#1-prerequisites)
2. [`Project Layout`](#2-project-layout)
3. [`Environment Variables`](#3-environment-variables)
4. [`Database Setup`](#4-database-setup)
5. [`Running Locally`](#5-running-locally)
6. [`Google Sign-In`](#6-google-sign-in)
7. [`Firebase Cloud Messaging`](#7-firebase-cloud-messaging)
8. [`Supabase Storage (Avatars)`](#8-supabase-storage-avatars)
9. [`CORS & WebSocket Origin Policy`](#9-cors--websocket-origin-policy)
10. [`Deploying to Render`](#10-deploying-to-render)
11. [`API Reference`](#11-api-reference)
12. [`Troubleshooting`](#12-troubleshooting)

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Go | 1.26+ | Matches `go.mod`. |
| PostgreSQL | 14+ | Needs the `pgcrypto` extension (created automatically by the first migration). |
| A Google Cloud OAuth 2.0 client (type **Web application**) | — | Required only if Google Sign-In is enabled. |
| A Firebase project | — | Required only if incoming-call push wake-up is enabled. |
| A Supabase project | — | Required only if avatar uploads are enabled. |

None of the three external services are hard requirements to boot the server — each is
feature-flagged by the presence of its configuration (see [§3](#3-environment-variables)) and
logs a warning and disables itself cleanly if left unset. `DATABASE_URL` and `JWT_SECRET` are
the only two values the process refuses to start without.

## 2. Project Layout

```
Callin-Go/
├── cmd/server/main.go       Composition root — wiring, routes, graceful shutdown
├── internal/
│   ├── auth/                Registration, login, JWT, Google ID-token verification
│   ├── call/                In-memory call state machine (no DB dependency)
│   ├── config/               Environment loading & validation
│   ├── connection/           "Add contact" request graph
│   ├── db/                   PostgreSQL connection pool
│   ├── middleware/            Auth, logging, rate limiting
│   ├── push/                  Firebase Cloud Messaging client
│   ├── signaling/             WebSocket hub / client / router
│   ├── storage/               Supabase Storage client (avatars)
│   └── user/                  Profile, device tokens, contact directory
├── pkg/logger/                 Structured logging (slog)
├── pkg/keepalive/               Self-ping to prevent free-tier idling
└── scripts/migrate.sh            Applies every pending SQL migration
```

## 3. Environment Variables

Create a `.env` file at `Callin-Go/.env` for local development — it is loaded automatically
(and silently ignored if absent, which is the expected shape in production where the
platform injects real environment variables instead).

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `ENVIRONMENT` | No | `development` | `development` → human-readable text logs at debug level with source locations. Anything else → structured JSON logs at info level. |
| `PORT` | No | `8080` | HTTP listen port. |
| `DATABASE_URL` | **Yes** | — | PostgreSQL connection string, e.g. `postgres://user:pass@host:5432/callin?sslmode=disable`. |
| `JWT_SECRET` | **Yes** | — | HMAC signing key for access/refresh tokens. **Minimum 16 characters**; the process refuses to boot below that. Use a long, random value in production. |
| `JWT_ACCESS_TTL_MINUTES` | No | `15` | Access token lifetime. |
| `JWT_REFRESH_TTL_HOURS` | No | `720` (30 days) | Refresh token lifetime, fixed from the moment of issuance. |
| `GOOGLE_WEB_CLIENT_ID` | No | — | OAuth **Web** client ID used to verify Google ID tokens. Leave unset to disable Google Sign-In entirely (classic login still works). Must exactly match the web client ID configured in the Android app's sign-in flow. |
| `FCM_PROJECT_ID` | No | — | Firebase project ID for the HTTP v1 Cloud Messaging endpoint. |
| `FCM_SERVICE_ACCOUNT_FILE` | No | — | Path to a Firebase service-account JSON key file. Leave unset to disable incoming-call push wake-up (in-app ringing while the socket is connected still works). |
| `SUPABASE_URL` | No | — | e.g. `https://xxxx.supabase.co`. |
| `SUPABASE_SERVICE_ROLE_KEY` | No | — | Service-role key — server-side only, never ship this to a client. |
| `SUPABASE_AVATARS_BUCKET` | No | `avatars` | Storage bucket name. Must be **public** so uploaded URLs resolve without extra signing. |
| `ALLOWED_ORIGINS` | No | `*` | Comma-separated list. Currently consulted only by the WebSocket upgrader's origin check (see [§9](#9-cors--websocket-origin-policy)) — it does **not** add CORS headers to the REST API. |
| `RATE_LIMIT_RPS` | No | `5` | Sustained requests per second, per client IP. |
| `RATE_LIMIT_BURST` | No | `10` | Token-bucket burst allowance on top of the sustained rate. |
| `RENDER_EXTERNAL_URL` / `SELF_URL` | No | — | Used only by the keepalive self-pinger. `RENDER_EXTERNAL_URL` is set automatically by Render; `SELF_URL` is the manual override for any other host. |

## 4. Database Setup

```bash
# 1. Create the database
createdb callin

# 2. Point DATABASE_URL at it, then apply every migration in order
export DATABASE_URL="postgres://user:pass@localhost:5432/callin?sslmode=disable"
./scripts/migrate.sh
```

Migrations are applied in filename order and are individually idempotent
(`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`):

| Migration | Introduces |
|---|---|
| `0001_init.sql` | `users`, `devices`, `calls` (the `calls` table is schema-present but unused by the current backend — see [`docs/architecture.md`](architecture.md), [`§6`](architecture.md#6-data-model)).
| `0002_google_auth.sql` | Google Sign-In support: relaxes `users` so a Google-only account can exist without a password/username, adds `google_id`, `first_name`, `last_name`, `profile_completed`. |
| `0003_connections.sql` | The `connections` request graph and a reserved, currently-unwritten `call_stats` anonymous counter table. |

`scripts/migrate.sh` requires `psql` on `PATH` and `DATABASE_URL` in the environment; it exits
non-zero on the first failing statement (`ON_ERROR_STOP=1`).

## 5. Running Locally

```bash
cd Callin-Go
go mod download
go run ./cmd/server
```

A healthy boot logs `starting CALLIN backend` followed by `connected to database` and
`listening`. Verify with:

```bash
curl http://localhost:8080/healthz
# {"status":"ok"}
```

If you intend to connect an Android app running on a **physical device over USB** to this local server, see the [`adb reverse`](android-setup.md) instructions in [`docs/android-setup.md`](android-setup.md) — the backend itself needs no special configuration for that case.

## 6. Google Sign-In

1. In Google Cloud Console → **APIs & Services → Credentials**, create an OAuth 2.0 Client ID
   of type **Web application** (not Android). Android's Credential Manager signs the ID token
   for this web client regardless of the fact that the request originates from a mobile app —
   this is a platform requirement, not a CALLIN-specific choice.
2. Set `GOOGLE_WEB_CLIENT_ID` on the backend to that client ID.
3. Use the **exact same value** as the web client ID the Android app is configured with. A
   mismatch here fails verification silently from the client's point of view (the backend
   returns `401 invalid credentials`).
4. Leaving `GOOGLE_WEB_CLIENT_ID` unset disables the feature cleanly: `POST /auth/google`
   returns `503` with a clear message instead of crashing the process.

## 7. Firebase Cloud Messaging

1. In the Firebase console, create (or reuse) a project and generate a **service account** key
   under **Project Settings → Service Accounts → Generate new private key**.
2. Save the downloaded JSON somewhere the server process can read, and set:
   - `FCM_PROJECT_ID` — the Firebase project ID
   - `FCM_SERVICE_ACCOUNT_FILE` — absolute path to the key file
3. **Never commit this file.** It is excluded by `.gitignore` (`*.pem`, `*.key`, and any path
   you keep it under outside the repo is safest).
4. With no key configured, the server logs a warning and simply never attempts a push — a call
   to an offline device will ring only if that device later reconnects within the invite's
   validity window, so FCM is strongly recommended for any real deployment.

## 8. Supabase Storage (Avatars)

1. Create a Supabase project and a **public** storage bucket (default name `avatars`, override
   with `SUPABASE_AVATARS_BUCKET` if you use a different one).
2. Set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` (Project Settings → API → `service_role`
   secret — not the `anon` key).
3. Uploads go to `avatars/<user_id>.<ext>` and overwrite any previous photo for that user
   (`x-upsert: true`), so there is never more than one stored photo per account.
4. With no Supabase configuration, `POST /users/me/avatar` returns `503` rather than failing
   unpredictably.

## 9. CORS & WebSocket Origin Policy

`ALLOWED_ORIGINS` is read once at boot and consulted **only** by the `/ws` upgrader's
`CheckOrigin` function. The default `*` accepts any origin, which is appropriate for a native
mobile client (there is no browser `Origin` header to police) but should be narrowed to your
own deployed web origin(s) if you ever expose a browser-based CALLIN client. The REST API
itself does not currently set CORS response headers — a browser-based client calling it
cross-origin would need that added separately.

## 10. Deploying to Render

CALLIN's production backend targets Render's free/starter web service tier:

1. Point the service at the `Callin-Go` directory with build command `go build -o app ./cmd/server`
   and start command `./app`.
2. Set every environment variable from [§3](#3-environment-variables) in the Render dashboard.
   Render supplies `RENDER_EXTERNAL_URL` automatically — `pkg/keepalive` uses it to self-ping
   `/healthz` every 10 minutes, which is what keeps a free-tier instance from spinning down
   between calls (and from paying the ~30–60s cold-start penalty on the next one).
3. Provision a managed PostgreSQL instance (Render's own, or any external Postgres) and set
   `DATABASE_URL` to it.
4. Run `scripts/migrate.sh` against the production `DATABASE_URL` once before first deploy, and
   again after pulling any new migration file.

## 11. API Reference

All authenticated routes require `Authorization: Bearer <access_token>`.

### Public

| Method & Path | Description |
|---|---|
| `GET /healthz` | Liveness check — `{"status":"ok"}`. |
| `POST /api/v1/auth/register` | Classic username/password account creation. |
| `POST /api/v1/auth/login` | Classic login by username or email. |
| `POST /api/v1/auth/refresh` | Exchange a refresh token for a new access token. |
| `POST /api/v1/auth/google` | Verify a Google ID token; finds or creates the matching account. |

### Authenticated — Users

| Method & Path | Description |
|---|---|
| `GET /api/v1/users/me` | The caller's own profile, including `profile_completed`. |
| `GET /api/v1/users` | Every other profile-complete user (contact directory). |
| `GET /api/v1/users/check-username?username=` | Live availability check during profile setup. |
| `POST /api/v1/users/me/device` | Register/refresh this install's FCM token. |
| `POST /api/v1/users/me/complete-profile` | One-time: sets username, first name, last name. Fails on a second call for the same account. |
| `POST /api/v1/users/me/avatar` | Multipart image upload (`avatar` field), ≤5 MB. |

### Authenticated — Connections

| Method & Path | Description |
|---|---|
| `POST /api/v1/connections/request` | Send (or auto-accept, if the other side already requested you) a connection by username. |
| `GET /api/v1/connections/pending` | Requests waiting on you to respond. |
| `POST /api/v1/connections/{id}/respond` | `{"accept": true\|false}`. |
| `GET /api/v1/connections` | Your accepted contacts. |

### Real-Time

| Method & Path | Description |
|---|---|
| `GET /ws?token=<access_token>` | Upgrades to a WebSocket. See [`docs/signaling-protocol.md`](signaling-protocol.md) for the full message protocol. |

> **Note:** an earlier revision additionally exposed `GET /api/v1/calls` backed by a persisted
> `calls` table. That endpoint and its handler have since been removed in favor of fully
> on-device call history (see [`docs/architecture.md`](architecture.md), [`§6`](architecture.md#6-data-model)) — do not re-add it without also

> re-introducing a server-side write path, which would be a deliberate privacy-model change.

## 12. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Process exits immediately with `config: DATABASE_URL is required` | `.env` missing or `DATABASE_URL` unset in the shell. |
| Process exits with `JWT_SECRET is required and must be at least 16 characters` | Secret too short or unset. |
| Every `/ws` upgrade fails | Confirm the logging middleware still forwards `Hijack()`/`Flush()` — see [`docs/architecture.md`](architecture.md) [`§3.3`](architecture.md#33-backend-architecture). This is the single most likely regression if that middleware is ever touched. |
| `POST /auth/google` always returns `401` | `GOOGLE_WEB_CLIENT_ID` doesn't match the Android app's web client ID, or the Google account's email isn't verified. |
| Incoming calls never wake a backgrounded device | `FCM_SERVICE_ACCOUNT_FILE`/`FCM_PROJECT_ID` unset, or the device's FCM token is stale — check the server logs for "pruned dead device token". |
| Avatar upload returns `503` | `SUPABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY` unset. |
| Free-tier instance feels slow on the first call after a break | Confirm `RENDER_EXTERNAL_URL` (or `SELF_URL`) is set so the keepalive pinger is actually reaching the deployed host. |
