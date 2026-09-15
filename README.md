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

CALLIN is an Android application for real-time voice calling over the internet — no telecom minutes required. Two users open the app, one calls the other, the receiver's phone rings, and once accepted, a clear, low-latency two-way voice call begins over Wi-Fi or mobile data.

Voice-only in this version. No video, no SIP/PSTN dependency.

## Core features

- Real-time voice calls over WebRTC (Opus codec)
- Native incoming-call experience via Android Telecom + notifications, even when the app is backgrounded
- Mute, speaker, earpiece, and Bluetooth audio routing
- Resilient call state handling — reconnects gracefully across network changes (Wi-Fi ↔ mobile data)
- Secure, authenticated signaling over WebSocket
- Call history

## Architecture

```
CALLIN Phone A  ⇄  WebSocket signaling  ⇄  Go backend  ⇄  WebSocket signaling  ⇄  CALLIN Phone B
                                              │
                                    PostgreSQL · Firebase FCM

CALLIN Phone A  ⇄  WebRTC audio (direct, or via TURN relay)  ⇄  CALLIN Phone B
```

The backend coordinates signaling and call state — it does not sit in the audio path. Voice flows directly between devices whenever possible.

Full architecture notes: [`docs/architecture.md`](docs/architecture.md)

## Tech stack

| Layer | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose, Coroutines/Flow, WebRTC (Android), Android Telecom APIs |
| Backend | Go, WebSocket, PostgreSQL |
| Real-time media | WebRTC · Opus · ICE/STUN/TURN |
| Push notifications | Firebase Cloud Messaging |
| Deployment | Render |

## Project structure

```
CALLIN/
├── Callin-Android/     Android app (Kotlin + Jetpack Compose)
├── Callin-Go/          Backend (Go)
├── docs/               Architecture & setup documentation
└── README.md
```

## Status

Actively in early development. Backend and Android app are being built in tandem — see `docs/` for current progress notes.

## License

All rights reserved © Sambhav Dwivedi.
