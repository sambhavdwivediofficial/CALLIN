# Android App Setup Guide

Everything needed to clone, configure, build, and run `Callin-Android` — including the
configuration points you must fill in yourself before the app will connect to a backend or
complete Google Sign-In.

## Table of Contents

1. [`Prerequisites`](#1-prerequisites)
2. [`Opening the Project`](#2-opening-the-project)
3. [`Required Configuration Before First Run`](#3-required-configuration-before-first-run)
4. [`Key Dependencies`](#4-key-dependencies)
5. [`Running on a Physical Device`](#5-running-on-a-physical-device)
6. [`Runtime Permissions`](#6-runtime-permissions)
7. [`Building a Release Build`](#7-building-a-release-build)
8. [`Module Map`](#8-module-map)
9. [`Troubleshooting`](#9-troubleshooting)

---

## 1. Prerequisites

| Requirement | Version |
|---|---|
| Android Studio | A release compatible with AGP `9.2.1` / Gradle `9.4.1` (see `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`). |
| JDK | 21 — the Gradle daemon toolchain is pinned to 21 (`gradle/gradle-daemon-jvm.properties`). Note this is independent of the app module's own `sourceCompatibility`/`targetCompatibility`, which targets Java 11 bytecode. |
| Android SDK | `compileSdk 37`, `targetSdk 37`, `minSdk 26`. |
| A physical Android device | **Strongly recommended** over an emulator. CALLIN's entire value proposition is real-time voice + camera (QR scanning) + background/lock-screen behavior, none of which an emulator reproduces faithfully — emulator microphone passthrough and Doze/battery-optimization behavior do not match a real device. |

## 2. Opening the Project

```bash
git clone https://github.com/sambhavdwivediofficial/CALLIN.git
cd CALLIN/Callin-Android
```

Open the `Callin-Android` directory (not the repository root) in Android Studio and let it
sync. The first sync will fail or warn about two missing files — both are intentionally
excluded from version control (`.gitignore`) and are covered next.

## 3. Required Configuration Before First Run

### 3.1 Backend base URL

`core/network/ApiConfig.kt` hard-codes the backend's base URL:

```kotlin
object ApiConfig {
    const val BASE_URL = "https://your-backend-host/"
}
```

- Pointing at a **deployed HTTPS backend** (see [`docs/backend-setup.md`](/backend-setup.md)) works out of the box.
- Pointing at a **local backend over plain HTTP** requires two changes, since the manifest sets
  `android:usesCleartextTraffic="false"`:
  1. Run `adb reverse tcp:8080 tcp:8080` for each connected device/emulator, so `localhost:8080`
     on the device reaches your machine's backend.
  2. Add a debug-only network security configuration permitting cleartext traffic to
     `localhost` (or use an HTTPS tunnel such as `ngrok`/`cloudflared` instead, which needs no
     manifest change at all). Do **not** re-enable cleartext traffic app-wide for a release
     build.

### 3.2 Google Sign-In web client ID

`ui/auth/LoginScreen.kt` defines:

```kotlin
private const val WEB_CLIENT_ID = "<your-oauth-web-client-id>.apps.googleusercontent.com"
```

This **must** be a **Web application** OAuth client ID (Credential Manager signs the token for
a web audience even though the request originates on Android), and it **must** exactly match
the backend's `GOOGLE_WEB_CLIENT_ID` (see [`docs/backend-setup.md`](/backend-setup.md), [`§6`](/backend-setup.md#6-google-sign-in)). A mismatch surfaces as
a sign-in that appears to succeed on-device but is rejected by the backend with `401`.

### 3.3 Firebase (`google-services.json`)

Push-notification wake-up for incoming calls requires a Firebase project:

1. Create/select a Firebase project and add an Android app with package name
   `com.sambhavdwivedi.callin`.
2. Download `google-services.json` and place it at `Callin-Android/app/google-services.json`.
   This file is `.gitignore`d — every contributor fetches their own from the Firebase console.
3. Without it, the Google Services Gradle plugin fails the build with a clear "File
   google-services.json is missing" error — there is no silent degraded mode on the client side
   (unlike the backend, which disables push gracefully if unconfigured).

### 3.4 Release signing (`keystore.properties`)

The release build type reads a signing config from a root-level `keystore.properties`
(`.gitignore`d, never committed):

```properties
storeFile=/absolute/path/to/your.jks
storePassword=••••••••
keyAlias=your-alias
keyPassword=••••••••
```

Debug builds need none of this — only `./gradlew assembleRelease` /
`./gradlew bundleRelease` consult it.

## 4. Key Dependencies

From `gradle/libs.versions.toml` — the ones worth knowing about before touching related code:

| Dependency | Version | Used for |
|---|---|---|
| Jetpack Compose BOM | `2026.02.01` | Entire UI layer. |
| Retrofit + OkHttp + kotlinx-serialization converter | `2.11.0` / `4.12.0` / — | REST client, with a logging interceptor and a token-refresh `AuthInterceptor`. |
| DataStore (Preferences) | `1.1.1` | `TokenStore`, `RecentCallsStore`, `RingtoneStore`, `NotificationHistoryStore` — every piece of on-device persistence. |
| Navigation Compose | `2.8.0` | `CallinNavHost`. |
| Credential Manager + Google ID | `1.3.0` / `1.1.1` | Google Sign-In. |
| CameraX + ML Kit Barcode Scanning | `1.4.1` / `17.3.0` | Live QR scanning for the "add contact" flow. |
| ZXing core | `3.5.3` | Generating (not scanning) the user's own QR code. |
| `stream-webrtc-android` | `1.1.1` | The WebRTC `PeerConnectionFactory`/`PeerConnection` bindings. |
| Firebase BOM + Messaging | `33.5.1` | Incoming-call push wake-up. |
| Coil Compose | `2.7.0` | Avatar/profile-photo image loading. |

## 5. Running on a Physical Device

1. Enable Developer Options → USB debugging on the device.
2. Connect via USB, approve the debugging prompt.
3. If testing against a local backend, run `adb reverse tcp:8080 tcp:8080` **after every USB
   reconnect** (the reverse mapping does not persist across disconnects).
4. Run the `app` configuration from Android Studio, or:
   ```bash
   ./gradlew installDebug
   ```

## 6. Runtime Permissions

CALLIN requests every permission it needs exactly once, in sequence, the first time the user
reaches Home (`core/permissions/RequestAppPermissionsOnce`), tracked in `TokenStore` so it never
repeats — even across a cold restart:

1. `RECORD_AUDIO` — required for any call.
2. `POST_NOTIFICATIONS` (API 33+) — required to show the incoming-call notification at all on
   modern Android.
3. `CAMERA` — required only for the QR-scan "add contact" flow.
4. **Ignore battery optimizations** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — not a
   runtime permission in the Android API sense, but requested through the same flow. This is
   what stops aggressive manufacturer battery managers (common on Xiaomi/Vivo/OnePlus/Oppo)
   from force-killing the process in the background, which would otherwise silently drop
   incoming calls even while FCM delivery itself is working correctly.

Declining any of these degrades the corresponding feature gracefully rather than crashing —
but declining `RECORD_AUDIO` or the battery-optimization exemption will materially affect call
reliability, and should be communicated to testers as such.

## 7. Building a Release Build

```bash
./gradlew bundleRelease   # Android App Bundle, for Play Store / internal distribution
./gradlew assembleRelease # Signed APK
```

Both require `keystore.properties` at the repository root (see §3.4) and a valid
`google-services.json` (see §3.3). The release build type currently ships with
`isMinifyEnabled = false` — ProGuard/R8 rules exist in `app/proguard-rules.pro` for when
minification is turned on, but they are not yet exercised by CI or manual QA; treat enabling
minification as its own testing pass, not a one-line flag flip.

## 8. Module Map

| Package | Contents |
|---|---|
| `core/di` | `AppContainer` — the hand-rolled dependency graph (see [`docs/architecture.md`](/architecture.md) [`§4.1`](/architecture.md#41-backend-architecture)). |
| `core/network` | Retrofit/OkHttp setup, `AuthInterceptor` (transparent token refresh on `401`), `SignalingClient` (the WebSocket connection), `TokenStore`. |
| `core/signaling` | Wire-format data classes mirroring the backend's `internal/signaling` message envelope. |
| `core/webrtc` | `WebRtcClient`, `IceServers`. |
| `core/call` | `CallNotifier`, `CallForegroundService`, `CallActionReceiver` — everything that keeps a call alive and visible outside the app's own UI. |
| `core/storage` | On-device persistence: recent calls, notification history, ringtone selection. |
| `core/ringtone` | Bundled ringtone asset listing (`assets/ringtones/*.mp3`). |
| `core/permissions` | The first-run permission sequence. |
| `core/push` | `CallinFirebaseMessagingService`. |
| `data/remote` | Retrofit API interfaces and DTOs. |
| `data/repository` | `AuthRepository`, `UserRepository`, `ConnectionRepository`, `CallRepository` — the app's state-owning layer. |
| `ui/*` | Compose screens, grouped by feature: `auth`, `home`, `call`, `qr`, `notifications`, `legal`, `navigation`, `theme`, `components`. |

## 9. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Build fails: "File google-services.json is missing" | Place a real Firebase config at `app/google-services.json` (§3.3). |
| Network calls fail with a cleartext/`CLEARTEXT_NOT_PERMITTED` error | You're pointing `ApiConfig.BASE_URL` at a plain-HTTP host while `usesCleartextTraffic="false"` — use HTTPS or add a debug-only network security exception (§3.1). |
| Google Sign-In succeeds on-device, backend rejects it with 401 | `WEB_CLIENT_ID` in `LoginScreen.kt` doesn't match the backend's `GOOGLE_WEB_CLIENT_ID`. |
| Incoming calls don't ring when the app is backgrounded | Check that the battery-optimization exemption was granted (§6) and that `google-services.json` matches a project with the same `FCM_PROJECT_ID`/`FCM_SERVICE_ACCOUNT_FILE` configured on the backend. |
| `adb reverse` mapping "stops working" | It does not survive a USB disconnect/reconnect or a device reboot — re-run it each session. |
| Release build fails signing | Confirm `keystore.properties` exists at the repository root and every path/credential in it is correct ([`§3.4`](#34-release-signing-keystoreproperties)). |
