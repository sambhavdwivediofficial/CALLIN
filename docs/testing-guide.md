# Testing Guide

CALLIN's core value — a real-time voice call that survives backgrounding, locked screens, and
flaky mobile networks — is inherently hard to capture in automated tests. This guide covers
what *is* automated today, how to run it, and a deliberate, repeatable manual QA process for
everything that currently isn't — which, honestly, is most of the risk surface. Treat the
manual checklist in §4 as load-bearing, not optional.

## Table of Contents

1. [`Current State of Automated Coverage`](#1-current-state-of-automated-coverage)
2. [`Android Unit Tests`](#2-android-unit-tests)
3. [`Android Instrumented Tests`](#3-android-instrumented-tests)
4. [`Manual Two-Device Call Checklist`](#4-manual-two-device-call-checklist)
5. [`Backend API Smoke Testing`](#5-backend-api-smoke-testing)
6. [`Signaling Protocol Manual Testing`](#6-signaling-protocol-manual-testing)
7. [`Push Notification Testing`](#7-push-notification-testing)
8. [`Rate Limiting Verification`](#8-rate-limiting-verification)
9. [`Database Migration Testing`](#9-database-migration-testing)
10. [`Recommended Next Automated Tests`](#10-recommended-next-automated-tests)

---

## 1. Current State of Automated Coverage

Be honest with yourself about this before relying on CI alone:

- **Android:** `app/src/test` and `app/src/androidTest` contain only the default Android
  Studio project scaffold (`addition_isCorrect`, `useAppContext`) — placeholders, not real
  coverage.
- **Backend:** there are currently no `*_test.go` files in `Callin-Go`. Nothing is unit-tested.

This guide is written around that reality: §2–§3 tell you how to run what exists, §4–§9 are the
actual day-to-day QA process the project depends on right now, and §10 is a prioritized list of
what to automate first.

## 2. Android Unit Tests

```bash
cd Callin-Android
./gradlew test
```

Runs on the JVM, no device/emulator required. Currently exercises nothing beyond the scaffold
test. When you add real unit tests, the highest-value, easiest-to-isolate targets are listed in
§10.

## 3. Android Instrumented Tests

```bash
cd Callin-Android
./gradlew connectedAndroidTest
```

Requires a connected device or running emulator. Note the limitations before trusting results
from an emulator specifically:

- Emulator microphone passthrough does not reliably reproduce real audio capture/routing
  behavior (`AudioManager` mode switching, Bluetooth routing).
- Emulators do not enforce the manufacturer-specific battery-optimization behavior that
  `core/permissions/RequestAppPermissionsOnce` exists to work around — a test passing on an
  emulator says nothing about real-world backgrounded reliability on, say, a Xiaomi device.
- CameraX + ML Kit barcode scanning needs a real or virtual camera feed with an actual QR code
  in frame — not meaningfully testable headlessly.

Use instrumented tests for Compose UI assertions and navigation-graph correctness; use the
manual checklist below for anything involving real audio, real background/lock-screen state,
or real push delivery.

## 4. Manual Two-Device Call Checklist

Requires **two physical devices**, two CALLIN accounts already connected as contacts (via QR
scan — see [`docs/architecture.md`](docs/architecture.md) [`§4.7`](docs/architecture.md#47-android-client-architecture), and ideally two different networks (e.g. one on Wi-Fi,)
one on mobile data) to exercise the TURN-relay path realistically.

### 4.1 Basic call

- [ ] Device A calls Device B; B's screen rings within ~1–2 seconds on a healthy connection.
- [ ] B accepts; both devices transition to the Active screen with a running timer that matches to the second on both devices (confirms both clients are reading the same server-assigned `startedAtMillis` — see [`docs/signaling-protocol.md`](docs/signaling-protocol.md) [`§6`](docs/signaling-protocol.md#6-full-call-sequence)).
- [ ] Audio is audible in both directions, with no more than brief initial jitter.
- [ ] A ends the call; B's screen reflects "Call ended" within ~1 second, not stuck on Active.

### 4.2 Call control edge cases

- [ ] B declines an incoming call → A sees "Declined" promptly.
- [ ] A cancels before B responds → B's ringing screen dismisses on its own.
- [ ] A calls B while B is already on a call with a third device/account → A is told the line is
      busy, not left ringing forever.
- [ ] Neither device answers for 60 seconds → the call auto-resolves to "missed" on the callee
      and the caller's own ringing screen also clears.

### 4.3 Background & lock screen

- [ ] Call B while B's app is fully backgrounded (not just minimized — press Home, open two
      other unrelated apps) → B still gets a full-screen ringing UI over the lock screen.
- [ ] Repeat with B's screen off and device locked.
- [ ] Repeat with B's app **swiped away from Recents** entirely — the call should still ring
      (confirms `CallForegroundService`'s `stopWithTask="false"` is actually taking effect, and
      that B was re-woken via FCM if the process had genuinely died).
- [ ] While A and B are mid-call, background both apps for 60+ seconds, then foreground them
      again — the call should still be connected with accurate elapsed time, not dropped.

### 4.4 Network resilience

- [ ] Start a call on Wi-Fi, then disable Wi-Fi on one device mid-call (forcing a fall to mobile
      data) — the call should recover, not silently go to dead air. If it doesn't recover within
      ~8 seconds, confirm the app surfaces "Connection failed" rather than staying stuck on a
      silent "Active" state (see [`docs/architecture.md`](docs/architecture.md) [`§4.4`](docs/architecture.md#44-android-client-architecture), item 4).
- [ ] Put one device briefly into airplane mode mid-call, then take it back out — same
      expectation: either recovers, or clearly reports failure.
- [ ] Kill the backend process (local dev only) mid-call — both clients should detect the drop,
      attempt to reconnect, and the call should end cleanly rather than hang indefinitely.

### 4.5 In-call controls

- [ ] Mute toggles correctly and audibly on the remote end.
- [ ] Speaker toggle actually routes audio out of the loudspeaker vs. earpiece.
- [ ] If a Bluetooth headset is available, verify audio routes to it and mute/speaker toggles
      behave sensibly in that configuration too.

### 4.6 Contacts & QR flow

- [ ] Scanning a contact's live QR code sends a request; if they'd already scanned yours first,
      the connection completes instantly on both sides (mutual-request auto-accept).
- [ ] A screenshot of a QR code does **not** scan — the flow is camera-only by design
      (`docs/architecture.md` [`§4.7`](docs/architecture.md#47-android-client-architecture)); confirm this hasn't regressed.

## 5. Backend API Smoke Testing

Replace `$HOST` with your running backend (e.g. `http://localhost:8080`).

```bash
# Liveness
curl $HOST/healthz

# Classic register
curl -X POST $HOST/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"tester1","email":"tester1@example.com","password":"correcthorsebattery","display_name":"Tester One"}'

# Classic login
curl -X POST $HOST/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"tester1","password":"correcthorsebattery"}'

# Authenticated request (swap in the access_token from the login response)
curl $HOST/api/v1/users/me -H 'Authorization: Bearer <access_token>'

# Refresh
curl -X POST $HOST/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"<refresh_token>"}'
```

For connection-request flows, register a second account and exercise
`POST /connections/request` → `GET /connections/pending` → `POST /connections/{id}/respond`
end to end — this is the REST path that backs the QR "add contact" feature, so it's worth
confirming independently of the Android client when debugging a contact-sync issue.

## 6. Signaling Protocol Manual Testing

A WebSocket CLI such as [`wscat`](https://github.com/websockets/wscat) is the fastest way to
poke the signaling layer directly, without needing two phones:

```bash
wscat -c "wss://$HOST/ws?token=$ACCESS_TOKEN"

# Once connected, send a ping:
> {"type":"ping"}
< {"type":"pong","timestamp":...}

# Invite a known user ID (you'll need a second connected client/session to see it arrive):
> {"type":"call.invite","payload":{"callee_id":"<target-user-id>"}}
```

This is especially useful for reproducing a specific message-ordering bug without the overhead
of two physical devices, and for confirming new message types or payload changes before wiring
them into the Android client. See [`docs/signaling-protocol.md`](docs/signaling-protocol.md) for every message type and the
exact payload shapes.

## 7. Push Notification Testing

1. Confirm a device's FCM token actually registered: check the backend logs for the device
   registration, or query the `devices` table directly for the account under test.
2. Force the "unreachable via WebSocket" path by closing the app's WebSocket connection (e.g.
   enable airplane mode just long enough for the socket to drop, or force-stop the app) and
   then call that account from another device — the push path only fires when no recently-alive
   connection exists (`docs/signaling-protocol.md` §7).
3. To test dead-token pruning specifically: register a token, then manually mark it invalid on
   the Firebase side or simply let an uninstalled app's old token go stale, and confirm the
   backend logs "pruned dead device token" on the next call attempt rather than repeatedly
   failing silently.

## 8. Rate Limiting Verification

```bash
# Default is 5 req/s sustained, burst 10 — this should start returning 429 quickly:
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code}\n" $HOST/healthz; done
```

Expect a mix of `200`s (within burst) followed by `429 {"error":"too many requests"}` once the
bucket is exhausted. Adjust `RATE_LIMIT_RPS`/`RATE_LIMIT_BURST` and re-run to confirm the
configuration actually takes effect — this is cheap to verify and easy to silently break.

## 9. Database Migration Testing

Before merging a new migration file:

```bash
createdb callin_migration_test
DATABASE_URL="postgres://user:pass@localhost:5432/callin_migration_test?sslmode=disable" \
  ./scripts/migrate.sh
```

Confirm it applies cleanly against a **fresh** database (not just one that already has every
prior migration's side effects baked in by hand), and that re-running `migrate.sh` a second
time is a no-op (every statement in the existing migrations uses `IF NOT EXISTS` /
`ON CONFLICT DO NOTHING` for exactly this reason — new migrations should follow the same
pattern).

## 10. Recommended Next Automated Tests

In priority order — each of these is isolated enough to unit-test without a running database
or device:

1. **`internal/call` state machine** (`CanTransition`, `Registry.Start/Transition/EndAllForUser`)
   — pure functions and in-memory state, zero external dependencies, and this is exactly the
   code whose edge cases (`ringing → ended`, busy detection) have already caused real bugs once.
2. **`internal/signaling.Router`** against a mock `Hub` — verify `requireAlive` is set correctly
   per message type (§7 of the protocol doc) and that malformed payloads produce the right
   `error` code without panicking.
3. **JWT issuance/verification** (`internal/auth.TokenManager`) — expiry boundaries, tampered
   signatures, wrong signing method rejection.
4. **Android `SignalingMessage` (de)serialization** round-trips against literal JSON fixtures
   copied from [`docs/signaling-protocol.md`](docs/signaling-protocol.md) — this is the cheapest way to catch a client/server
   payload-shape drift before it reaches a physical device.
5. **`CallRepository`'s state transitions** given a scripted sequence of incoming
   `SignalingMessage`s — this is the single highest-risk piece of client logic and currently has
   zero coverage of any kind.
