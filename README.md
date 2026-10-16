<p align="center">
  <img src="docs/logo.png" alt="CALLIN" width="120">
</p>

<h1 align="center">CALLIN</h1>

<p align="center">
  A modern, internet-based voice calling app for Android — built to feel like a real phone call.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84?logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/Backend-Go-00ADD8?logo=go&logoColor=white">
  <img src="https://img.shields.io/badge/Media-WebRTC-333333?logo=webrtc&logoColor=white">
  <img src="https://img.shields.io/badge/DB-PostgreSQL-4169E1?logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Status-In%20Development-orange">
</p>

---

## What is CALLIN?

CALLIN is an Android application for real-time voice calling over the internet — no telecom
minutes required. Two users open the app, one calls the other, the receiver's phone rings, and
once accepted, a clear, low-latency two-way voice call begins over Wi-Fi or mobile data.

Voice-only in this version. No video, no SIP/PSTN dependency.

## Core features

- Real-time voice calls over WebRTC (Opus codec), with direct peer-to-peer audio whenever
  possible and automatic TURN-relay fallback when NAT traversal fails
- Native incoming-call experience — full-screen ringing over the lock screen, with
  Accept/Decline actions — even when the app is fully backgrounded or has been swiped from Recents
- Mute, speaker, earpiece, and Bluetooth audio routing
- Resilient call state handling — reconnects gracefully across network changes (Wi-Fi ↔ mobile
  data) and recovers automatically from a cold process kill via push wake-up
- Secure, authenticated signaling over WebSocket, with Google Sign-In and short-lived JWT sessions
- Camera-only QR contact exchange — no screenshots, no gallery picker
- Call history, kept entirely on-device for privacy

## Architecture

```
CALLIN Phone A  ⇄  WebSocket signaling  ⇄  Go backend  ⇄  WebSocket signaling  ⇄  CALLIN Phone B
                                              │
                                    PostgreSQL · Firebase FCM

CALLIN Phone A  ⇄  WebRTC audio (direct, or via TURN relay)  ⇄  CALLIN Phone B
```

The backend coordinates signaling and call state — it does not sit in the audio path. Voice
flows directly between devices whenever possible, and the server is never able to hear,
record, or store a call.

Full architecture notes: [`docs/architecture.md`](docs/architecture.md)

## Tech stack

| Layer | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose, Coroutines/Flow, WebRTC (Android), Android Telecom-adjacent background delivery |
| Backend | Go, WebSocket, PostgreSQL |
| Real-time media | WebRTC · Opus · ICE/STUN/TURN |
| Push notifications | Firebase Cloud Messaging |

## Documentation

| Guide | Covers |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | How the Android client, Go backend, and WebRTC media path fit together, end to end. |
| [`docs/backend-setup.md`](docs/backend-setup.md) | Running, configuring, and deploying the `Callin-Go` backend — environment variables, database migrations, Google Sign-In, Firebase, Supabase, and the full API reference. |
| [`docs/android-setup.md`](docs/android-setup.md) | Cloning, configuring, building, and running `Callin-Android` — including every value you must fill in yourself before the app will connect to a backend. |
| [`docs/signaling-protocol.md`](docs/signaling-protocol.md) | The authoritative WebSocket signaling protocol spec — message envelope, every message type, the call state machine, and the push-notification fallback. |
| [`docs/testing-guide.md`](docs/testing-guide.md) | What's automated today, how to run it, and the manual QA checklist the project currently depends on. |

## Status

Actively in early development. Backend and Android app are being built in tandem — see
`docs/` for current progress notes and open design decisions.

## License

© All rights reserved. See the [`LICENSE`](LICENSE) file for details.
