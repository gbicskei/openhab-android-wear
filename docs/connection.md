# Connection Configuration

## Overview

The openHAB Wear OS system uses two server connections:

1. **Main Server** (synced to watch) — the openHAB instance the watch connects to for item state, commands, and tile config. This is typically `https://myopenhab.org` (cloud relay) or a direct URL.
2. **Config Server** (phone-only) — an openHAB instance with direct REST API access, used by the phone's tile/complication editor for read/write operations. This can be the same as the main server, or a local instance on the home network.

The watch only knows about the Main Server. The Config Server is used exclusively by the phone companion app.

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
└─────────┬────────────────────────────────────────┘           │
          │                                                     │
          ▼                                                     │
┌──────────────────────────────────────────────────────────────┐
│  Watch                                                        │
│                                                               │
│  CredentialStore (DataStore)                                  │
│  ├── server_url    ← Main Server URL                         │
│  ├── username      ← Main Server credentials                 │
│  ├── password      ← Main Server credentials                 │
│  └── user_key      ← Tile namespace identifier               │
│                                                               │
│  AuthInterceptor                                              │
│  ├── Replaces placeholder URL with server_url                │
│  └── Adds Basic Auth header from username/password           │
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

Credentials are sent from phone to watch via the Wear Data Layer MessageClient:

| Path | Payload | Purpose |
|------|---------|---------|
| `/openhab/config` | JSON (`SyncConfigPayload`) | Sync server URL + credentials + user key |
| `/openhab/reload` | empty | Signal watch to clear cache and refresh tile |
| `/openhab/theme` | theme name (UTF-8 string) | Sync theme color to watch |

### SyncConfigPayload format

```json
{
  "serverUrl": "https://myopenhab.org",
  "username": "user@email.com",
  "password": "secret",
  "userKey": "joe",
  "deviceName": "Joe's Watch",
  "googleTtsApiKey": "",
  "debugMode": false,
  "bindingInstalled": true,
  "resolvedIps": ["1.2.3.4"],
  "localServerUrl": "http://192.168.1.100:8080"
}
```

| Field | Description |
|-------|-------------|
| `serverUrl` | Main server URL for the watch |
| `username` | Basic Auth username |
| `password` | Basic Auth password |
| `userKey` | Tile namespace (empty = shared) |
| `deviceName` | Friendly watch name for audio sink binding registration |
| `googleTtsApiKey` | Google Cloud TTS API key (optional) |
| `debugMode` | Enable verbose logging on the watch |
| `bindingInstalled` | Whether the Mobile Audio binding is installed on the server (controls notification UI visibility and FCM registration) |
| `resolvedIps` | Pre-resolved DNS addresses (seeded by phone) |
| `localServerUrl` | Direct/LAN server URL for Happy Eyeballs racing (empty = cloud-only) |

### Sync process

1. User taps "Sync to Watch" on the phone
2. Phone saves credentials locally (if unsaved changes exist)
3. Phone sends `SyncConfigPayload` via MessageClient to `/openhab/config`
4. Watch `WearDataLayerListenerService` receives the message
5. Watch deserializes payload and saves to `CredentialStore` (DataStore)
6. Watch saves `deviceName`, `bindingInstalled`, `localServerUrl`, debug mode
7. Watch schedules FCM registration (if `bindingInstalled=true`)
8. Phone sends `/openhab/reload` — watch clears item cache and refreshes tile

### Requirements

- Watch and phone must be paired via Galaxy Wearable (or equivalent)
- Bluetooth connection must be active (Data Layer uses BT or cloud relay)
- Both apps must share the same signing key (or be linked via Play Console)
- `openhab_watch_app` capability must be declared in the watch app's `wear.xml`

## Watch-Side Setup

The watch does not support manual credential entry. Setup is handled exclusively by the phone companion app via Data Layer sync.

The watch app's launcher menu provides:
- **Setup on Phone** — sends a Data Layer message (`/openhab/open-app`) to open the phone companion
- **Reload Items** — clears item cache and re-fetches tile config from the server

If no credentials are configured, the tile renders in a dimmed/empty state until the phone syncs credentials.

## Watch-Side Auth (AuthInterceptor)

All watch HTTP requests go through `AuthInterceptor`, which:

1. Replaces the placeholder URL (`https://placeholder.openhab.org/`) with the configured server URL
2. Adds an `Authorization: Basic` header if credentials are configured

Retrofit defines all endpoints against the placeholder host. The interceptor transparently rewrites every request to the real server.

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
GET {localServerUrl}/mobileaudio/register?regId={token}&deviceId={androidId}&deviceModel={model}&deviceName={name}
```

The worker prefers the local server URL (the servlet is not proxied by myopenhab.org). The `deviceName` parameter enables stable Thing matching across app reinstalls.
