# Connection Configuration

## Overview

The wearOH system uses two server connections:

1. **Main Server** (synced to watch) — the openHAB instance the watch connects to for item state, commands, and tile config. This is typically `https://myopenhab.org` (cloud relay) or a direct URL.
2. **Config Server** (phone-only) — an openHAB instance with direct REST API access, used by the phone's tile/complication editor for read/write operations. This can be the same as the main server, or a local instance on the home network.

The watch only knows about the Main Server. The Config Server is used exclusively by the phone companion app for tile editing. However, the phone can optionally share the Config Server's URL and credentials with the watch for direct LAN connectivity (see "Watch uses Config Server" toggle below).

## Connection Flow

```
┌──────────────────────────────────────────────────────────────┐
│  Phone Companion App                                          │
│                                                               │
│  ┌─────────────┐   ┌─────────────────┐   ┌──────────────┐  │
│  │ Main Server │   │  Config Server   │   │  User Key    │  │
│  │ (for watch) │   │ (for tile edit)  │   │ (namespace)  │  │
│  └──────┬──────┘   └────────┬────────┘   └──────┬───────┘  │
│         │                    │                    │           │
│         ▼                    ▼                    │           │
│  Sync to Watch       Tile/Complication            │           │
│  (Data Layer)        Editor REST calls            │           │
│         │                                         │           │
│  ┌──────┴──────────────────────────────────┐     │           │
│  │ "Watch uses Config Server" toggle       │     │           │
│  │ ON  → sends localServerUrl + local auth │     │           │
│  │ OFF → sends empty localServerUrl        │     │           │
│  └─────────────────────────────────────────┘     │           │
└─────────┬────────────────────────────────────────┘           │
          │                                                     │
          ▼                                                     │
┌──────────────────────────────────────────────────────────────┐
│  Watch                                                        │
│                                                               │
│  CredentialStore (DataStore)                                  │
│  ├── server_url        ← Cloud/Main Server URL               │
│  ├── username/password ← Cloud credentials (Basic Auth)      │
│  ├── local_server_url  ← Config Server URL (empty = disabled)│
│  ├── local_username/password/api_token ← Config Server auth  │
│  └── user_key          ← Tile namespace identifier           │
│                                                               │
│  ServerSelector (Happy Eyeballs)                              │
│  ├── Races local vs cloud on first request                   │
│  ├── Caches winner for process lifetime                      │
│  ├── Skips racing if local_server_url is empty               │
│  └── resolveAuthHeader() → correct auth for active server    │
│                                                               │
│  AuthInterceptor                                              │
│  ├── Uses ServerSelector to get active URL                   │
│  ├── Replaces placeholder URL with resolved URL              │
│  └── Adds auth header (local token/creds OR cloud Basic)     │
└──────────────────────────────────────────────────────────────┘
```

## Phone Companion Settings

The phone setup screen has three sections:

### User Key

| Field | Description |
|-------|-------------|
| User Key | Namespace identifier for multi-user setups. Empty = shared `wear:tile` namespace. Allowed: `[a-z0-9_-]`. |
| Watch Device Name | Friendly name for the watch (e.g. "Gabor's Watch"). Used by the Mobile Audio binding as a stable identifier — survives app reinstalls and Android ID changes. The binding uses this as the Thing label and derives the Thing UID from it. |

When User Key is set, tile config is scoped to `wear:tile:{userKey}` — multiple users sharing one openHAB instance get independent tile layouts.

### Main Server (synced to watch)

| Field | Description |
|-------|-------------|
| Server URL | openHAB server the watch connects to. Default: `https://myopenhab.org`. Any URL that exposes `/rest/items` and accepts Basic Auth. |
| Username | openHAB Cloud email or local username. |
| Password | Password for Basic Auth. Stored encrypted (EncryptedSharedPreferences on phone, DataStore on watch). |

Supported server types:
- `https://myopenhab.org` — official cloud relay (works on LTE, no port forwarding)
- `https://openhab.example.com` — self-hosted with reverse proxy
- `http://192.168.x.x:8080` — local network (requires watch on same WiFi or VPN)
- Custom openHAB Cloud instances

### Config Server (phone-only, for tile editor)

| Field | Description |
|-------|-------------|
| Server URL | Direct openHAB REST API URL. Used for reading/writing UI components. |
| Auth Mode | Toggle between Basic Auth (username/password) and API Token (Bearer). |
| Username | Credentials for Basic Auth mode. |
| Password | Password for Basic Auth mode. |
| API Token | openHAB API token for Bearer auth (generated in openHAB Settings > API Security). |

**Auth mode priority:** If an API token is set, it's sent as `Authorization: Bearer {token}`. Otherwise falls back to `Authorization: Basic {base64(user:pass)}`. If neither is configured, no auth header is sent (requests will likely fail with 401).

**Why two auth modes exist:**
- openHAB 5+ disables Basic Auth for the REST API by default. Users must either enable it in Settings > API Security (`allowBasicAuth=true`) or use an API token.
- API tokens are the recommended approach — they don't expose the main account password and can be revoked independently.
- The phone companion uses the Config Server auth for all REST write operations (PUT/DELETE on `/rest/ui/components/`).

**When to use each:**

| Scenario | Auth mode | Notes |
|----------|-----------|-------|
| openHAB 5+ (default security settings) | API Token | Generate at `{server}/settings/api-security` |
| openHAB 5+ with `allowBasicAuth=true` | Basic Auth | Simpler but less secure |
| openHAB 4.x or older | Basic Auth | API tokens not available |
| myopenhab.org as Config Server | Basic Auth | Cloud relay uses email/password (but has limited REST write support) |

The Config Server is needed because:
- The cloud relay (`myopenhab.org`) may not expose all REST endpoints for writes
- Writing UI components requires direct server access
- The tile editor needs PUT/DELETE on `/rest/ui/components/wear:tile`

If not configured, the phone uses the Main Server credentials for editor operations (read-only mode — edits will fail without a properly authenticated Config Server).

## Credential Sync (Phone to Watch)

### Protocol

Credentials and settings are sent from phone to watch via two mechanisms:

**1. Connection credentials** — via MessageClient (one-time, requires active connection):

| Path | Payload | Purpose |
|------|---------|---------|
| `/openhab/connection` | JSON (`ConnectionPayload`) | Sync server URLs + credentials + device identity |
| `/openhab/reload` | empty | Signal watch to clear cache and refresh tile |

**2. Watch settings** — via DataItem (persistent, offline-capable, bidirectional):

| DataItem Path | Content | Purpose |
|---------------|---------|---------|
| `/openhab/watch-settings` | `WatchSettingsPayload` (DataMap) | All non-credential settings + watch status |

The DataItem is shared bidirectionally:
- **Phone writes** settings fields (voice, notifications, theme, debug) → watch applies via `onDataChanged`
- **Watch writes** status fields (configTimestamp, screenWidthDp, appVersion, hasSpeaker) → phone reads instantly

The phone can read settings offline without the watch being connected or awake.

**Deprecated paths** (kept for backward compatibility with watch app < 1.10.0):

| Path | Payload | Replaced by |
|------|---------|-------------|
| `/openhab/config` | `SyncConfigPayload` | `PATH_CONNECTION` |
| `/openhab/voice-settings` | `SyncVoiceSettingsPayload` | DataItem |
| `/openhab/notification-settings` | `SyncNotificationSettingsPayload` | DataItem |
| `/openhab/theme` | theme name string | DataItem |
| `/openhab/settings` | `WatchSettingsPayload` JSON | DataItem |
| `/openhab/settings-request` | empty | DataItem (no round-trip needed) |
| `/openhab/settings-response` | `WatchSettingsSnapshot` JSON | DataItem (no round-trip needed) |
| `/openhab/status` | DataMap | Merged into `/openhab/watch-settings` |

### ConnectionPayload format (PATH_CONNECTION)

```json
{
  "serverUrl": "https://myopenhab.org",
  "username": "user@email.com",
  "password": "secret",
  "userKey": "joe",
  "localServerUrl": "http://192.168.1.100:8080",
  "localUsername": "admin",
  "localPassword": "localpass",
  "localApiToken": "oh.mytoken.abc123",
  "resolvedIps": ["1.2.3.4"],
  "deviceName": "Joe's Watch",
  "bindingInstalled": true,
  "googleTtsApiKey": "AIza...",
  "triggerReload": false
}
```

| Field | Description |
|-------|-------------|
| `serverUrl` | Main server URL for the watch (cloud) |
| `username` | Basic Auth username (cloud) |
| `password` | Basic Auth password (cloud) |
| `userKey` | Tile namespace (empty = shared) |
| `localServerUrl` | Config Server URL for Happy Eyeballs racing (empty = cloud-only, controlled by "Watch uses Config Server" toggle) |
| `localUsername` | Config Server username for Basic Auth |
| `localPassword` | Config Server password for Basic Auth |
| `localApiToken` | Config Server API token for Bearer auth (takes priority over Basic Auth) |
| `resolvedIps` | Pre-resolved DNS addresses (seeded by phone) |
| `deviceName` | Friendly watch name for audio sink binding registration |
| `bindingInstalled` | Whether the Mobile Audio binding is installed on the server (controls notification UI visibility and FCM registration) |
| `googleTtsApiKey` | Google Cloud TTS API key (optional) |
| `triggerReload` | When true, watch clears tile cache and reloads from server |

### WatchSettingsPayload format (PATH_SETTINGS)

```json
{
  "voiceCommandsEnabled": true,
  "readAloudEnabled": false,
  "useServerTts": false,
  "serverTtsVoice": "",
  "speechRate": 1.0,
  "pitch": 1.0,
  "notificationsEnabled": true,
  "notificationReadAloudEnabled": false,
  "chimeEnabled": true,
  "chimeSound": "default",
  "minReadAloudPriority": "normal",
  "theme": "BLUE",
  "debugMode": false
}
```

This payload contains no secrets and is backed up to the openHAB server as item metadata.

### Sync process

**Connection sync** (Setup screen → "Send to Watch"):

1. User taps "Send to Watch" on the phone (or credentials auto-sync on save)
2. Phone builds `ConnectionPayload` from UI state + credential store
3. Phone pre-resolves server hostname DNS (seeds watch cache)
4. Phone sends payload via MessageClient to `/openhab/connection`
5. Watch saves credentials, local URL, device name, binding status, TTS API key
6. Watch resets `ServerSelector` (forces re-race on next request)
7. Watch seeds DNS cache with phone-resolved IPs
8. Watch schedules FCM registration (if `bindingInstalled=true`)
9. Watch restarts SSE connection (picks up new server URL immediately)
10. If `triggerReload=true`: watch clears item cache and re-fetches tile config

**Settings sync** (Watch Settings screen — instant on every change):

1. User changes any setting (voice toggle, theme, debug, etc.)
2. Phone builds full `WatchSettingsPayload` from current UI state
3. Phone writes DataItem at `/openhab/watch-settings` (merges with watch-owned status fields)
4. Watch receives `onDataChanged`, applies all settings fields atomically
5. Watch refreshes tile to reflect theme changes
6. Phone schedules debounced server backup write (if backup enabled)

Note: settings sync does NOT require the watch to be actively connected — the DataItem is persisted locally by Google Play Services and syncs automatically when devices reconnect.

### Requirements

- Watch and phone must be paired via Galaxy Wearable (or equivalent)
- Bluetooth connection must be active (Data Layer uses BT or cloud relay)
- Both apps must share the same signing key (or be linked via Play Console)
- `openhab_watch_app` capability must be declared in the watch app's `wear.xml`

## Watch-Side Setup

The watch supports two setup paths:

1. **Phone sync (primary)** — credentials synced from the phone companion via Data Layer. This is the standard flow for paired watches.
2. **Manual entry** — the watch's own Setup screen (`SetupViewModel`) allows entering server URL, username, and password directly. After saving, it resets `ServerSelector` and verifies connectivity before confirming success.

The watch app's launcher menu provides:
- **Setup** — opens manual credential entry (or sends a Data Layer message to open the phone companion)
- **Reload Items** — clears item cache and re-fetches tile config from the server

If no credentials are configured, the tile renders in a dimmed/empty state until credentials are provided via either path.

## Watch-Side Auth (AuthInterceptor + ServerSelector)

All watch HTTP requests go through a **two-layer** connection system:

### ServerSelector (Happy Eyeballs)

On the first API request of the process, `ServerSelector` races the local (LAN) and cloud server URLs in parallel. Whichever responds first is cached as the active URL for the remainder of the process lifetime.

- **Local preferred:** If the local server responds within 5s, it wins immediately (lower latency, no cloud hop).
- **Cloud fallback:** If only the cloud server responds, it's used for all subsequent requests.
- **Neither:** Defaults to cloud (lets the real request fail naturally with a meaningful error).
- **Reset:** The cached winner is cleared whenever credentials change (phone sync or manual setup), forcing a re-race on the next request.

The local server URL is only present on the watch when the phone user enables **"Watch uses Config Server"** in setup. When disabled, the phone sends an empty `localServerUrl` to the watch — `ServerSelector` sees no local URL and uses cloud directly without racing.

### AuthInterceptor

All Retrofit API calls pass through `AuthInterceptor`, which:

1. Resolves the active server URL via `ServerSelector` (triggers the race on the first call)
2. Replaces the placeholder URL (`https://placeholder.openhab.org/`) with the resolved server
3. Adds the appropriate `Authorization` header based on which server won:
   - **Local active:** API token (Bearer) > Basic Auth with local credentials > no auth
   - **Cloud active:** Basic Auth with cloud credentials

### Connection paths

| Path | Goes through ServerSelector? | Notes |
|------|------------------------------|-------|
| Retrofit API calls (items, commands, tile config, complications) | Yes (via AuthInterceptor) | Standard path |
| TileStateEventSource (SSE for live updates) | Yes (explicit reset + resolveUrl on each reconnect) | Adapts when entering/leaving home WiFi |
| OpenHabRepository.observeItemState (SSE for single item) | Yes (resolveUrl + resolveAuthHeader) | Used for complication preview |
| FcmRegistrationWorker | Yes (reset + resolveUrl + resolveAuthHeader) | Works via local or cloud (endpoint is proxied by myopenhab.org) |
| IconResolver (openHAB icons) | Yes (via AuthInterceptor on main OkHttpClient) | — |
| IconResolver (Iconify/Material icons) | No (plainClient, external URLs) | No auth needed |
| ServerTtsPlayer (Google TTS) | No (external Google API) | Uses Google API key |

### ServerSelector.resolveAuthHeader()

A shared utility on `ServerSelector` that returns the correct auth header for the currently active server:

```kotlin
suspend fun resolveAuthHeader(): String?
// Local active → Bearer {apiToken} > Basic(localUser, localPass) > null
// Cloud active → Basic(cloudUser, cloudPass) > null
```

Used by connection paths that build their own OkHttpClient (SSE, FCM registration) to avoid duplicating auth-resolution logic.

## Debug Credential Injection (ADB)

For development without Bluetooth pairing:

```bash
adb -s <watch-serial> shell am start \
  -n org.openhab.habdroid.wear/.ui.setup.DebugSetupActivity \
  --es url "https://myopenhab.org" \
  --es user "user@email.com" \
  --es pass "secret"
```

Writes directly to DataStore. Available in debug builds only.

## Security

### Phone storage
- EncryptedSharedPreferences (AES-256-GCM values, AES-256-SIV keys)
- Master key in Android Keystore (hardware-backed where available)
- Wiped on app uninstall

### Watch storage
- DataStore Preferences (plaintext on watch filesystem)
- Stores cloud credentials, local server URL + auth (API token, username, password)
- Protected by Wear OS app sandboxing
- Wiped on app uninstall or watch factory reset

### In transit
- Data Layer messages are encrypted by Google Play Services (BT link encryption)
- HTTP credentials sent as Basic Auth over HTTPS (TLS)
- No credentials are logged or exposed in debug builds

## Config Sync Detection

The phone tracks whether the watch's tile config is up to date:

1. Phone reads watch's `configTimestamp` and `theme` from DataClient (`/openhab/status`)
2. Phone fetches the server's `configVersion` from the `main` page document
3. If they differ, the phone shows an "out of sync" indicator
4. User syncs to watch, which reloads config and writes the new version to DataClient

This ensures the watch always reflects the latest tile editor changes.

## Mobile Audio Binding Detection

The phone detects whether the Mobile Audio binding is installed on the openHAB server:

1. On Watch Settings screen open, phone calls `GET {configServerUrl}/rest/thing-types/mobileaudio:device`
2. HTTP 200 → binding installed; HTTP 404 → not installed
3. Result is persisted in `PhoneCredentialStore` and included in `SyncConfigPayload`
4. Watch stores `bindingInstalled` in its `CredentialStore`

**Effect on UI:**
- Phone Watch Settings: notification controls hidden when binding not installed (info message shown instead)
- Watch Settings: Notifications button disabled when binding not installed

**Effect on behavior:**
- Watch `FcmRegistrationWorker` skips registration entirely when `bindingInstalled=false` (no network call, no 404 errors)

**FCM Registration (when binding is installed):**

The watch registers its FCM token with the binding's servlet:
```
GET {serverUrl}/mobileaudio/register?regId={token}&deviceId={androidId}&deviceModel={model}&deviceName={name}
```

The worker uses `ServerSelector` to resolve the best reachable server and applies the correct auth for whichever server won the race. The `/mobileaudio/register` endpoint is reachable both directly on the local network and through the myopenhab.org cloud proxy (the cloud vhost forwards any path to the local instance after REST auth), so registration succeeds whether the watch is home or remote. The `deviceName` parameter enables stable Thing matching across app reinstalls. The worker is also triggered when the app or tile is opened, re-registering the current token (skipping the network call when it is unchanged) so a token the binding rejected as `UNREGISTERED` self-heals.
