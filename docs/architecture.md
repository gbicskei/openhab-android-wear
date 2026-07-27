# Architecture

## Overview

The openHAB Wear OS app is a **standalone watch application** that communicates directly with an openHAB server (typically via the myopenhab.org cloud relay). It does not require the phone to be present or connected for day-to-day operation.

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Galaxy Watch  │◀──WiFi──▶│  myopenhab.org   │◀──────▶│  Home openHAB   │
│   (Wear OS 5+) │  / LTE   │  (cloud relay)   │         │  Server (5.x)   │
└─────────────────┘         └──────────────────┘         └─────────────────┘
        ▲
        │ one-time sync (Data Layer API)
        ▼
┌─────────────────┐
│   Phone App     │
│  (openHAB)      │
└─────────────────┘
```

## Key Architecture Decisions

### 1. Standalone Watch App (not phone-dependent)

**Decision:** The watch connects directly to myopenhab.org over WiFi/LTE.

**Rationale:**
- Modern watches (Galaxy Watch Ultra 2025) have LTE — not using it would waste the hardware
- Phone-proxied connections add 300-700ms latency per request
- Phone connection is unreliable (Bluetooth drops when out of range)
- The openHAB REST API is lightweight enough for direct watch consumption

**Trade-off:** Initial setup requires the phone for credential sync (typing on a tiny screen is painful). After that one-time handshake, the watch operates independently.

### 2. Cloud Relay (myopenhab.org) as Primary Connection

**Decision:** Use the openHAB Cloud service as the connection endpoint.

**Rationale:**
- No port forwarding or VPN required on the home network
- Same approach the existing mobile app uses
- Works anywhere the watch has internet (home WiFi, LTE on the go)
- The cloud connector handles authentication and proxying transparently

**Connection details:**
- Base URL: `https://myopenhab.org/rest/`
- Auth: HTTP Basic (email + password)
- Note: `connect.myopenhab.org` does NOT accept Basic Auth (redirects to login page)

### 3. Server-Side Configuration (wearTile metadata)

**Decision:** Tile item selection is stored as openHAB item metadata, not on the watch. The watch is read-only with respect to metadata — it never writes, modifies, or deletes `wearTile` metadata.

**Rationale:**
- The server is the single source of truth — survives watch resets
- Configurable from the openHAB Main UI on desktop (big screen, search/filter)
- No modification to the existing mobile app required
- Watch simply queries "which items have wearTile metadata?" and displays them
- Multiple watches can share the same configuration
- Keeps the watch app simple — no metadata CRUD, no conflict resolution

### 4. One-Time Credential Sync via Data Layer API

**Decision:** Phone sends server credentials to watch on first setup, then the watch stores them locally.

**Rationale:**
- Avoids typing URLs and passwords on a 1.5" screen
- The phone app already has the connection configured
- Data Layer API is the standard phone↔watch communication channel
- After sync, no ongoing phone dependency

**Fallback:** Manual entry on the watch is available for users without the phone app.

**Limitation discovered during development:** The Data Layer API requires an active Bluetooth companion connection between phone and watch. During development, Bluetooth is often disabled to stabilize WiFi debugging, which breaks the sync. A `DebugSetupActivity` exists to inject credentials via ADB as a workaround.

**Additional constraint:** The Data Layer API also requires both apps to be signed with the same key (or linked via Play Console) and the phone must be paired via the Galaxy Wearable app. For sideloaded debug builds, the applicationId mismatch between phone (`org.openhab.habdroid.wear.phone`) and watch (`org.openhab.habdroid.wear`) may prevent node discovery. This needs verification once Bluetooth is re-enabled.

### 5. Modern Tech Stack (independent of mobile app)

**Decision:** Use current Android/Wear OS best practices rather than matching the mobile app's stack.

**Rationale:**
- The mobile app uses XML layouts, no DI, raw OkHttp — functional but dated
- A new project has no legacy constraints
- Compose for Wear OS is required for modern tiles anyway
- Hilt, Retrofit, DataStore are the current Android recommendations
- The wear module is self-contained — no shared code with the mobile app

## Tech Stack Detail

| Layer | Choice | Why |
|-------|--------|-----|
| UI Framework | Compose for Wear OS + Material 3 | Google-recommended, round-screen-aware, required for Tiles |
| Tiles | Wear Tiles API 1.5 + Material 3 | Latest tile rendering with system-level integration |
| DI | Hilt | First-class WorkManager/Compose integration |
| HTTP Client | Retrofit + OkHttp | Type-safe API, interceptors for auth/URL rewriting |
| Serialization | kotlinx.serialization | Kotlin-native, no reflection, smaller APK than Gson |
| Storage | DataStore Preferences | Coroutine-native replacement for SharedPreferences |
| Images | Coil | Compose integration, SVG decoder for openHAB icons |
| Push | Firebase Cloud Messaging | Same channel the openHAB Cloud connector uses |
| Phone Sync | Wear Data Layer API | Standard phone↔watch messaging |
| Background | WorkManager | Reliable background task scheduling |
| Build | Kotlin DSL + Version Catalog | Type-safe, centralized dependency management |

## Data Flow

### Tile Rendering
```
TileService.onTileRequest()
  → OpenHabRepository.getTileItems()
    → OpenHabApiService.getItems(metadata="wearTile")
      → OkHttp → AuthInterceptor (adds URL + Basic Auth)
        → myopenhab.org/rest/items?metadata=wearTile
  → Filter items with wearTile metadata
  → Sort by position
  → Render 1-6 item buttons in grid layout
```

### Tile Action (tap to toggle)
```
User taps item button on tile
  → TileActionReceiver (transparent Activity)
    → OpenHabRepository.sendCommand(itemName, "ON"/"OFF")
      → POST myopenhab.org/rest/items/{name}
    → Request tile refresh
```

### Voice Command
```
VoiceCommandActivity
  → ACTION_RECOGNIZE_SPEECH (system speech UI)
  → Recognized text returned
  → OpenHabRepository.sendVoiceCommand(text)
    → POST myopenhab.org/rest/voice/interpreters
      (with Accept-Language header)
  → openHAB server interprets and executes
```

### Push Notification
```
openHAB rule triggers sendNotification()
  → openHAB Cloud → FCM → FcmListenerService
    → Parse message type (notification / hideNotification)
    → Display Android notification on watch
```

## Minimum Device Requirements

| Requirement | Value | Reason |
|-------------|-------|--------|
| Wear OS version | 5+ (API 34) | Google Play minimum for new apps (Aug 2025) |
| Connectivity | WiFi or LTE | Direct cloud connection required |
| Microphone | Required | Voice command input |
| Speaker | Optional | TTS notifications (text fallback available) |

## Security Considerations

- Credentials stored in DataStore (encrypted at rest by Android keystore on Wear OS 5+)
- All communication over HTTPS
- Basic Auth credentials sent only to the configured server URL
- No credentials stored on the openHAB server (metadata is config only, not secrets)
- Data Layer sync is encrypted by the Wear OS platform
