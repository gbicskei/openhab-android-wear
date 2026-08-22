# Architecture

## Overview

The wearOH app is a **standalone watch application** that communicates directly with any openHAB server — either through the myopenhab.org cloud relay, a self-hosted cloud instance, or a directly-accessible local/VPN server. It does not require the phone to be present or connected for day-to-day operation.

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Galaxy Watch  │◀──WiFi──▶│  openHAB Server  │         │   OR: via cloud │
│   (Wear OS 5+) │  / LTE   │  (direct/local)  │         │   relay proxy   │
└─────────────────┘         └──────────────────┘         └─────────────────┘
        ▲                                                         │
        │ one-time sync (Data Layer API)                          │ FCM push
        │ settings sync (MessageClient)                           ▼
        ▼                                                 ┌─────────────────┐
┌─────────────────┐                                       │  openHAB Cloud  │
│   Phone App     │                                       │  (FCM relay)    │
│  (companion)    │                                       └─────────────────┘
└─────────────────┘                                               │
                                                                  │ FCM
                                                                  ▼
                                                          ┌─────────────────┐
                                                          │   Galaxy Watch  │
                                                          │ (notifications) │
                                                          └─────────────────┘
```

Connection options:
- **Cloud relay** (myopenhab.org) — no port forwarding needed, works on LTE away from home
- **Direct connection** — local IP or hostname (requires watch to be on same network or via VPN)
- **Self-hosted cloud** — custom openHAB Cloud instance

## Key Architecture Decisions

### 1. Standalone Watch App (not phone-dependent)

**Decision:** The watch connects directly to the user's openHAB server over WiFi/LTE.

**Rationale:**
- Modern watches (Galaxy Watch Ultra 2025) have LTE — not using it would waste the hardware
- Phone-proxied connections add 300-700ms latency per request
- Phone connection is unreliable (Bluetooth drops when out of range)
- The openHAB REST API is lightweight enough for direct watch consumption

**Trade-off:** Initial setup requires the phone for credential sync (typing on a tiny screen is painful). After that one-time handshake, the watch operates independently.

### 2. Flexible Server Connection

**Decision:** Support any accessible openHAB server — cloud relay, direct local, or custom cloud.

**Rationale:**
- myopenhab.org cloud relay is convenient (no port forwarding, works on LTE)
- But many users expose their server directly (reverse proxy, VPN, Tailscale, etc.)
- The watch simply needs a base URL + credentials — connection topology is irrelevant

**Supported configurations:**
- `https://myopenhab.org` — official cloud relay (email + password auth)
- `https://openhab.example.com` — self-hosted, reverse-proxied instance
- `http://192.168.1.x:8080` — local network (requires watch on same WiFi)
- Any URL that exposes `/rest/items` and accepts Basic Auth or API tokens

**Auth methods:**
- HTTP Basic (username + password)

### 3. Server-Side Configuration (UI Components)

**Decision:** Tile layout is stored as UI components at `/rest/ui/components/wear:tile`, managed by the phone companion editor. The watch reads these components — it never writes them.

**Rationale:**
- The server is the single source of truth — survives watch resets
- Phone companion provides a visual editor (no JSON knowledge needed)
- Multiple watches can share the same configuration
- Keeps the watch app simple — read-only consumer of config

**Legacy:** The watch still reads `wearTile` item metadata as a fallback for complication item discovery if the `wear:complication-list` document doesn't exist.

### 4. One-Time Credential Sync via Data Layer API

**Decision:** Phone sends server credentials to watch on first setup, then the watch stores them locally. Connection settings auto-sync to watch on save (gated by successful connection test).

**Rationale:**
- Avoids typing URLs and passwords on a 1.5" screen
- The phone app already has the connection configured
- Data Layer API is the standard phone↔watch communication channel
- After sync, no ongoing phone dependency

**Limitation discovered during development:** The Data Layer API requires an active Bluetooth companion connection between phone and watch. During development, Bluetooth is often disabled to stabilize WiFi debugging, which breaks the sync. A `DebugSetupActivity` exists to inject credentials via ADB as a workaround.

### 5. Watch as Source of Truth for Settings

**Decision:** The watch owns all runtime settings (voice, notifications, debug). The phone is a remote editor that reads/writes via MessageClient.

**Rationale:**
- Settings are inherently local to the device that uses them (TTS engine, notification behavior)
- Eliminates sync conflicts — one owner, one copy
- Phone doesn't need to be connected for settings to work
- Server backup (item metadata) provides disaster recovery without being the primary store
- Instant-apply UX: phone changes are pushed and applied immediately (no Save button)

**Implementation:**
- Watch exposes settings via MessageClient request/response (PATH_SETTINGS_REQUEST / PATH_SETTINGS_RESPONSE)
- Phone sends `WatchSettingsPayload` on `PATH_SETTINGS` — watch applies atomically
- Connection secrets sent separately on `PATH_CONNECTION` — watch applies atomically
- Neither payload can overwrite the other's domain (eliminates partial-update bugs)
- Settings backed up to server as item metadata (periodic + on change, schema v2)

### 6. FCM Push Notifications

**Decision:** Receive notifications from openHAB Cloud via Firebase Cloud Messaging, with support for audio-sink playback directly on the watch.

**Rationale:**
- Push-based is battery-friendly (no polling)
- Leverages existing openHAB Cloud infrastructure (same FCM setup as mobile app)
- Audio-sink playback enables TTS announcements from rules (e.g., doorbell, alerts)
- Watch can act as an audio sink without requiring the phone

**Implementation:**
- `FcmRegistrationWorker` registers token with openHAB Cloud
- `FcmMessageListenerService` receives push messages
- `NotificationHandler` routes by FCM tag: `audio-tts` (watch TTS), `audio-sink` (URL stream), or standard notification
- `AudioUrlPlayer` streams pre-rendered audio from server URL
- `SpeakDisplayActivity` shows message text during playback

### 7. Modern Tech Stack (independent of mobile app)

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
| Phone Sync | Wear Data Layer API | Standard phone↔watch messaging + settings sync |
| Push | Firebase Cloud Messaging | Push notifications from openHAB Cloud |
| Audio | MediaPlayer | Streaming audio-sink URLs for TTS/announcements |
| DNS | CachingDns | Reliable DNS on watch (reduces timeout issues) |
| Background | WorkManager | Reliable background task scheduling |
| Build | Kotlin DSL + Version Catalog | Type-safe, centralized dependency management |

## Data Flow

### Tile Rendering
```
TileService.onTileRequest()
  → OpenHabRepository.getTileItems()
    → OpenHabApiService.getTileComponents(namespace)
      → OkHttp → AuthInterceptor (adds base URL + auth header)
        → {serverUrl}/rest/ui/components/{namespace}
  → Parse wear:tile-page documents
  → Resolve items for each slot (fetch state)
  → Sort by position
  → Render 1-7 item buttons in grid layout per page
```

### Tile Action (tap to toggle)
```
User taps item button on tile
  → TileActionReceiver (transparent Activity)
    → OpenHabRepository.sendCommand(itemName, "ON"/"OFF")
      → POST {serverUrl}/rest/items/{name}
    → Request tile refresh
```

### Complication Tap (control activity routing)
```
User taps complication on watch face
  → ComplicationTapActivity
    → Fetch item from REST API (type, state, label, commandDescription)
    → Route by type:
      Switch/Group       → ToggleControlActivity
      Dimmer/Range       → RotaryControlActivity
      Color              → ColorPickerActivity
      Rollershutter      → RollerShutterActivity
      Has commandOptions → ChoicePickerActivity
      Other              → ComplicationDetailActivity
    → Control activity:
      → Displays item with shared ControlScreenComponents styling
      → Subscribes to SSE for real-time updates
      → On user action: sendCommand → ComplicationRefresher.requestUpdate()
```

### Theme Sync (DataItem — bidirectional)
```
Phone: WatchSettingsViewModel → setTheme(themeName)
  → buildSettingsPayload(theme = themeName)
  → dataLayerSender.sendSettings(payload)          [PATH_SETTINGS → watch]
  → credentialStore.saveSelectedTheme(themeName)   [local cache]

Watch: handleSettingsMessage()
  → themeStore.setTheme(TileTheme.fromName(theme))
  → watchStatusWriter.writeTheme(theme)            [DataItem → phone reads]
  → requestUpdate(OpenHabTileService)

Phone reads theme back:
  → PhoneWearListenerService.onDataChanged()
  → ThemeHolder.update(themeName)                  [confirms watch applied it]
```

### Voice Command
```
VoiceCommandActivity
  → ACTION_RECOGNIZE_SPEECH (system speech UI)
  → Recognized text returned
  → OpenHabRepository.sendVoiceCommand(text)
    → POST {serverUrl}/rest/voice/interpreters
      (with Accept-Language header)
  → openHAB server interprets and executes
```

### Push Notification (Audio Sink)
```
openHAB Cloud → FCM → FcmMessageListenerService
  → NotificationHandler.handle(message)
    → tag = "audio-sink"?
      → AudioUrlPlayer.play(audioUrl)
      → SpeakDisplayActivity shows message text
    → tag = "audio-tts"?
      → TTS engine speaks message text
    → else
      → Post standard notification to watch shade
```

### Settings Sync (Phone → Watch) — Two Atomic Payloads
```
Connection (Setup screen saves):
  → SetupViewModel.buildConnectionPayload()
  → dataLayerSender.sendConnection(payload)        [PATH_CONNECTION]
  → Watch: saves credentials, local URL, device name, binding, TTS key
  → Watch: resets ServerSelector, restarts SSE
  → Never backed up to server (contains secrets)

Settings (Watch Settings screen changes):
  → WatchSettingsViewModel.buildSettingsPayload()
  → dataLayerSender.sendSettings(payload)          [PATH_SETTINGS]
  → Watch: applies voice + notifications + theme + debug atomically
  → Watch: refreshes tile
  → Backed up to server as item metadata (debounced)
```

Each payload is self-contained — connection changes cannot touch voice/notification/theme settings and vice versa.

### Version Handshake (Phone ↔ Watch)
```
Phone detects watch connected
  → Sends PATH_VERSION_REQUEST
  → Watch responds with BuildConfig.VERSION_NAME
  → Phone compares with own version
  → If mismatch (both prod builds): blocks sync, shows warning banner
  → If either is .dev: no blocking (development mode)
```

See [Version Sync](version-sync.md) for the full specification.

## Minimum Device Requirements

| Requirement | Value | Reason |
|-------------|-------|--------|
| Wear OS version | 5+ (API 34) | Google Play minimum for new apps (Aug 2025) |
| Connectivity | WiFi or LTE | Direct server connection (cloud relay, local, or VPN) |
| Microphone | Required | Voice command input |

## Security Considerations

- Credentials stored in DataStore Preferences on the watch (app-sandboxed, wiped on uninstall)
- Credentials stored in EncryptedSharedPreferences on the phone (AES-256-GCM, hardware-backed keystore)
- All communication over HTTPS (TLS)
- Basic Auth credentials sent only to the configured server URL
- No credentials stored on the openHAB server (config is layout only, not secrets)
- Data Layer sync encrypted by the Wear OS platform (Bluetooth link encryption)

See [Connection](connection.md) for full credential storage and sync details.
