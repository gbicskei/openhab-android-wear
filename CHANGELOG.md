# Changelog

## [1.9.1] — 2026-08-21

### Summary

Architecture refactor: settings sync split into two atomic payloads, then migrated to a persistent DataItem for instant offline access.

### Changed

- **DataItem-based settings sync**: Watch settings are now stored in a shared DataItem at `/openhab/watch-settings` instead of MessageClient messages. Both phone and watch can read settings offline (persisted by Google Play Services). Phone writes settings → watch applies via `onDataChanged`. Watch writes status → phone reads instantly. No more 5-second round-trip timeout on Watch Settings screen open.
- **Atomic settings payloads**: Phone-to-watch sync uses two self-contained mechanisms:
  - `ConnectionPayload` (MessageClient, PATH_CONNECTION) — credentials, URLs, device ID, TTS key. Contains secrets, not persisted in DataItem.
  - `WatchSettingsPayload` (DataItem, `/openhab/watch-settings`) — voice, notifications, theme, debug + watch status (configTimestamp, screenWidthDp, appVersion, hasSpeaker). Backed up to server.
- **Server backup** uses `WatchSettingsPayload` directly (schema version 2, includes theme)
- Old fragmented paths (PATH_CONFIG, PATH_VOICE_SETTINGS, PATH_NOTIFICATION_SETTINGS, PATH_THEME, PATH_SETTINGS, PATH_SETTINGS_REQUEST/RESPONSE) all deprecated, kept for backward compatibility
- Legacy `/openhab/status` DataItem still written for backward compat with older phone versions

### Fixed

- Toggling debug mode no longer wipes local server URL on the watch
- Any settings change in one domain cannot overwrite fields in the other domain
- Watch Settings screen opens instantly (no "Loading..." spinner waiting for watch response)
- Settings (voice, TTS, notifications) now persist correctly after DataItem sync
- Watch initializes from existing DataItem on startup (no default overwrite)
- TTS voice list shows all regions (not just one locale)

### Improved

- Phone voice dropdown shows checkmark + bold text for the currently selected voice
- Watch voice picker auto-scrolls to the currently selected voice on open

---

## [1.9.0] — 2026-08-21

### Summary

Major UX release: M3 Expressive redesign, carousel-based tile editor with drag-and-drop, faster watch connection recovery, and toggle behavior fix.

### New: M3 Expressive Design

- Applied Material 3 Expressive design system to both watch and phone apps
- Surface-Container background on tile buttons (watch)
- Updated color roles, typography, and component patterns

### New: Tile Editor Redesign (Phone)

- **Carousel page selector** replacing the old tab row — horizontally scrollable page chips
- **Drag-and-drop page reordering** — long-press a page chip to drag (main page stays pinned at position 1)
- **Drag-and-drop slot reordering** — long-press a slot on the watch preview to swap positions
- **Position badges** on the outer ring edge of the preview (toward bezel)
- **Complement action** support in tile config sheet
- **wearOH logo** shown on all tile pages in preview (matches watch behavior)
- New pages insert after current selection and auto-focus
- Carousel chips centered when not filling full width
- Back arrow vertically centered on sub-page preview

### Improved: Watch Connection

- **CachingDns in SSE client** — SSE reconnection uses cached IPs when system DNS is blocked (common on Wear OS wake), reducing reconnect time from ~20-30s to ~1-2s
- **Immediate poll on fallback entry** — no more 15s delay before the first poll attempt when SSE is unstable
- **Inline state refresh sets lastSuccessMillis** — first tile render after cold start shows green connection dot immediately (no false red state)
- SSE connect timeout set to 10s for faster failure detection

### Improved: Theme Sync

- Simplified to use DataItem as single source of truth (watch writes, phone reads)
- No longer depends on server tile page config for theme state

### Fixed

- **Toggle override for commandOptions**: When action is explicitly set to "toggle", the tile now sends ON/OFF directly instead of opening the choice picker — even if the item has commandDescription options (e.g. MQTT switches with true/false options)
- Connection indicator stays green during normal SSE reconnect cycles

---

## [1.8.0] — 2026-08-20

### Summary

Feature release introducing **FCM push notifications with audio sink playback**, a **redesigned settings architecture** (watch as source of truth), **app rebranding** to wearOH, and significant **tile editor UX improvements**.

### New: Push Notifications & Audio Sink

- FCM push notifications forwarded from openHAB Cloud to the watch
- Two notification modes based on FCM tag:
  - `audio-tts` — watch speaks the message using configured TTS engine
  - `audio-sink` — downloads and plays pre-rendered audio from server (authenticated via ServerSelector)
- `SpeakDisplayActivity` shows message text + logo during audio playback
- Configurable notification chime and read-aloud behavior (phone UI, synced to watch)
- POST_NOTIFICATIONS and VIBRATE permissions requested at runtime (Wear OS API 33+)
- `CachingDns` for reliable DNS resolution on watch (reduces timeout issues)
- `AudioPlaybackService` foreground service keeps process alive during playback (prevents lmkd kill and AudioHardening mute)
- `TtsManager.speak()` is now a suspend function — blocks until utterance finishes, ensuring foreground state and ringer mode are maintained for full duration
- `AudioUrlPlayer` downloads audio via OkHttp with auth (same pattern as FcmRegistrationWorker) to avoid unauthenticated 401s that trigger fail2ban bans
- `ServerTtsPlayer.playFile()` runs on Main dispatcher for proper MediaPlayer Looper callback delivery
- Chime playback has a 5-second timeout to prevent blocking TTS on cold start when audio system is initializing

### New: Settings Architecture Redesign

- **Watch is source of truth** for all settings (voice, notifications, debug)
- Phone acts as a remote editor — reads settings from watch, pushes changes back
- New `WatchSettingsScreen` on phone with instant-apply UX (no Save button)
- Settings are backed up to server as item metadata for disaster recovery
- Phone reads/writes settings via MessageClient request/response pattern
- Theme moved into tile page definition (no separate sync path)
- Connection Settings auto-syncs credentials to watch on save (gated by test)
- Watch `SettingsActivity` expanded: two-level nav (Voice, Notifications, Debug)
- Google TTS voice picker on watch (loads voices from API) with test button
- Debug mode persisted on watch (survives restart)
- Debug log: captures all levels when debug is on, debounced publish to phone
- Uncaught exception handler on watch sends crashes to debug log
- Server connection status indicator (green/red dot) on watch

### New: App Rebranding (wearOH)

- Custom watch face icon (square case, hour markers, OH-style chevron hands in #f1350d)
- Gray and black SVG variants in `assets/`
- PNG exports at standard mipmap sizes
- Shared `AppLogoHeader` composable with connection indicator
- Logo header applied to all watch activities (Main, Settings, About, Voice, control screens)
- Splash screen theme for watch MainActivity
- Tile shows logo + connection status dot (page titles removed)
- Phone launcher foreground updated to match new icon
- Old `ic_openhab_*` drawables and SVGs removed

### New: BSL 1.1 License

- Licensed under Business Source License 1.1
- Change License: Eclipse Public License 2.0
- Change Date: upon transfer to openHAB Foundation, or 4 years from first public distribution
- Additional Use Grant: any purpose except distributing a competing smart home wearable app

### Improved: Tile Editor UX

- **Add Page dialog** — now asks for a display label (not an internal ID); uid is auto-generated with suffix for duplicates
- **Page duplication** — long-press a page tab → context menu with Rename and Duplicate options; "Save As..." dialog lets user set the name before saving
- **Navigation target filtering** — current page excluded from the navigation target list (both in item picker and config sheet)
- **Action/State item picker** — replaced plain text fields with searchable item picker (same pattern as Double Tap Item); includes clear button to reset

### Fixed

- Tile page reset: respect `tileVisible` flag (don't reset to main when tile is still visible)
- Tile nav: fixed premature page reset on fresh display
- Refresh all states after tile command (ensures button state updates immediately)
- Debug logging added to `refreshStates` for troubleshooting

### Project Structure

- Added shared `assets/` source directory to both modules

---

## [1.7.4] — 2026-08-19

### Summary

SSE real-time event streaming fix — events now flow reliably on both WiFi and LTE.

### Fixed

- SSE client no longer inherits AuthInterceptor (was blocking the event reader thread)
- Nginx reverse proxy `Connection: close` header no longer kills SSE streams (server-side config)
- SSE reconnect no longer blocked by state refresh (fires in background)
- Skip redundant server race when local == cloud URL (eliminates 5-10s probe delay)

### Improved

- SSE drop reason tracing (onFailure vs onClosed with exception class)
- Debug log buffer increased from 500 to 1500 entries

---

## [1.7.3] — 2026-08-19

### Summary

Bugfix release consolidating server connection handling and fixing branding.

### Fixed

- All network paths (SSE, FCM registration, complication config) now consistently use ServerSelector for URL resolution and auth
- `observeItemState()` no longer hardcodes cloud URL — uses local server when available
- `FcmRegistrationWorker` now uses correct auth for local server (API token/local creds instead of cloud creds)
- `SetupViewModel` resets ServerSelector after saving credentials (prevents stale cached server)
- Guard added for unconfigured watch in `observeItemState()` (prevents crash on empty URL)

### Improved

- Auth decision logging in ServerSelector (Bearer/Basic/none for local/cloud)
- Suppressed DataLayer poll spam in debug log (only logs on state change now)
- Connection documentation updated with ServerSelector architecture

### Changed

- Renamed all "openHAB Wear" references to "wearOH" (branding consistency)

---

## [1.1.0] — 2026-08-06

### Summary

Feature release introducing **double-tap actions**, **live tile preview**, **voice settings sync**, and a major **performance overhaul** that eliminates the 748-item bulk fetch. The watch now fetches only referenced items in parallel with configVersion-gated disk caching.

---

### New: Double-Tap Actions on Tile Buttons

- Configure a secondary action on any tile button via `doubleTapItem`
- Single tap (350ms window): executes primary action (toggle, command, navigate)
- Double tap: executes secondary action on a different item (auto-detected: rotary, color picker, etc.)
- `doubleTapStateDisplay: "value"` shows the secondary item's state on the button face
- Haptic feedback confirms double-tap detection
- Supports `doubleTapAction` override: `"toggle"`, `"command"`, or auto-detect
- `doubleTapConfirmation` for confirmation dialog before executing

### New: Live Tile Preview (Phone Editor)

- Phone tile design editor shows a WYSIWYG circular watch preview
- Real-time state updates via SSE connection to the local server
- Icons render with correct theme coloring, active/inactive state, and labels
- Preview updates immediately on slot configuration changes

### New: Voice Settings Sync

- Configure TTS engine, volume, speech rate, pitch, and WaveNet voice on the phone
- Settings sync to watch via Data Layer on "Sync to Watch"
- Google Cloud TTS (WaveNet) support with server-side synthesis
- Test voice button for previewing on the phone speaker
- Mic button visibility controlled by voice enabled/disabled setting

### Improved: Performance

- **Parallel item fetch**: watch fetches only ~14 referenced items (was: all 748 from server)
- **Semaphore throttle**: max 3 concurrent requests to avoid cloud relay overload
- **configVersion-gated disk cache**: skips network entirely when tile config hasn't changed
- **Parallel state refresh**: ~12 items fetched in parallel on tile enter (~0.8s vs 1.5–2.1s)
- Cold load (cached): 3.7s → ~0ms (disk hit)
- Cold load (fresh): 3.7s → ~1.7s
- State refresh: 1.5–2.1s → ~0.8–1s

### Improved: Icon Rendering

- SVG namespace prefix handling (fixes icons like `door` that use `ns0:` prefixes)
- Fallback "?" placeholder when icon fetch or SVG parse fails (button stays visible)
- Empty response protection (prevents caching broken 0-byte icons)
- LRU composited bitmap cache (128 entries, 0ms cache hits)

### Improved: Tile Configuration

- Page rename via long-press on tab in phone editor
- Auto-create main page when opening tile editor for the first time
- Watch respects server-defined layout count for button positioning
- `doubleTap*` fields added to disk cache (fixes missing setpoint values after restart)
- API token (Bearer) authentication for phone editor writes

### Fixed

- Tile toggle state/icon one-click lag
- Sync indicator always re-checks on phone resume
- Unique resource IDs for navigation buttons (prevents tile render conflicts)

---

### New: Phone Companion App

A full Android phone application for configuring and managing the watch tile remotely.

#### Home Screen
- Navigation cards for Connection, Tile Design, and Complications screens
- "Sync to Watch" button sends credentials + reload signal to the watch
- Out-of-sync indicator when watch config version differs from server
- Watch status chip (reads configVersion + theme from DataClient)
- Re-checks sync status on every RESUMED lifecycle event

#### Connection Screen
- **Main Server** — the openHAB server URL (cloud relay like myopenhab.org, or direct URL)
- **Config Server** — local network URL for direct REST API access to edit tile config
- Connection testing with "Test" button before saving
- Encrypted credential storage (EncryptedSharedPreferences)
- Handles reinstall crash (KeyStoreException → recreates keystore)
- Password managers supported (both fields share ContentType)

#### Tile Design Editor
- **Page tabs** — switch between tile pages (main, security, scenes, etc.)
- **Layout selector** — choose 1-7 button layout per page
- **Watch preview** — circular rendering of the actual tile layout
- **Slot configuration sheet** — tap any slot to configure:
  - Item picker (searchable, shows type + label)
  - Icon picker (searchable bottom sheet with 3 tabs: MDI via Iconify API, Material Symbols via Iconify API, openHAB static list)
  - Label override
  - State display mode: Color / Value / None
  - Action type: Toggle / Command / Navigate
  - Action command (for fixed command buttons)
  - Action item (separate command target)
  - State item (separate display source)
  - Invert state toggle
  - Confirmation required toggle
  - Aggregate state toggle (for nav buttons)
- **Position swap** — numbered circles in config sheet, tap to swap positions
- **Theme selector** — choose theme color, sends to watch via Data Layer
- Reads current watch theme from DataClient on editor open
- Config sheet stays open after position swap (closes only on Save)
- Tile Design + Complications cards disabled until config server is tested/saved

#### Complication Editor
- Accessible from home screen "Complications" card
- List view with add/edit/remove per complication slot
- **4 complication types**: SHORT_TEXT, LONG_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE
- Per-type expandable configuration sections
- Character limit enforcement (7 chars for SHORT_TEXT/RANGED_VALUE title/text)
- Pattern format helper with live validation + expandable reference card with examples
- Type filtering based on item type (RANGED_VALUE only for numeric items, MONOCHROMATIC_IMAGE not for String)
- Import from old wearTile metadata (download button in top bar)
- Stores as `wear:complication-list` document in `wear:tile` namespace

#### Data Layer Sync
- Phone sends credentials, reload, and theme messages to watch
- Listens for watch messages (open-app, open-tile-editor)
- Reads watch status from DataClient (`/openhab/status`)

---

### New: Server-Side Tile Configuration

The tile layout is now stored server-side as UI components at `/rest/ui/components/wear:tile`.

#### Config Structure
- Each page is a `wear:tile-page` component with a `configVersion` integer
- Slots define item, icon, label, stateDisplay, action, actionItem, stateItem, etc.
- Complications stored as `wear:complication-list` component with per-type config blocks
- `configVersion` increments on every phone editor save

#### Config Sync Detection
- Watch reads `configVersion` after cold load, writes to DataClient at `/openhab/status`
- Phone reads from DataClient + fetches server version, compares integers
- "Watch config out of sync" warning on phone home screen
- Warning clears immediately on sync, re-checks after 3 seconds

---

### New: Watch Control Activities

Dedicated full-screen control screens launched from tile buttons or complications.

#### ColorPickerActivity (Color items)
- Preset color chips arranged in a 2×5 grid (Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, Warm, Cool)
- Brightness arc on screen edge shows current level
- **Bezel rotation** adjusts brightness (0-100%)
- Tap a color chip to select hue + saturation
- Tap ON/OFF label to toggle the light
- Sends HSB command (e.g. `120,100,50`) with 400ms debounce
- Parses current state from openHAB HSB format on launch

#### RollerShutterActivity (Rollershutter items)
- Three large buttons stacked vertically: UP (green) / STOP (orange) / DOWN (red)
- Current position displayed in the center (0%=OPEN, 100%=CLOSED)
- Position arc on screen edge (starts from top, fills clockwise as shutter closes)
- **Bezel rotation** adjusts position directly (sends percentage command with 500ms debounce)
- UP/DOWN/STOP send respective commands immediately
- Position refreshes from server 500ms after STOP

#### ChoicePickerActivity (items with commandOptions)
- ScalingLazyColumn (Wear OS optimized scrollable list)
- ListHeader shows item label
- TitleCards for each option with label + raw command value
- Active option highlighted in amber
- Tap sends the command immediately
- Sources: `commandDescription.commandOptions` (preferred) or `stateDescription.options` (fallback)

#### Tile Routing Logic
Priority order for tap actions:
1. Page navigation → LoadAction (instant) or PageNavigationActivity (with confirmation)
2. Contact → no action (display only)
3. Range (min/max) → RotaryControlActivity (existing)
4. Color → ColorPickerActivity
5. Rollershutter → RollerShutterActivity
6. Items with commandOptions → ChoicePickerActivity
7. Everything else → TileActionReceiver (toggle / fixed command)

---

### New: stateDisplay "none" Option

- Hides the state indicator completely on tile buttons
- Useful for command-only buttons (gate open, scene trigger, etc.)
- Icon renders in neutral state (no active/inactive coloring)
- No state text shown underneath the icon
- Phone editor shows 3-option segmented row: Color / Value / None
- Server config: `"stateDisplay": "none"`

---

### New: Watch State Fetching Refactor

#### Batch Refresh
- **Cold load** = 2 API calls: `getTileComponents` (config) + batch `getItems` (states)
- **Hot path** = 1 batch call (state refresh only, no config re-parse)

#### Disk Cache
- `TileConfigDiskCache` persists tile config as JSON to filesystem
- Warm start from disk on process restart (no network needed for initial render)
- Then hot path refreshes states in background

#### Group Item State Resolution
- `Item.isActive` checks members when `state == "NULL"` for Group items without aggregation function
- SSE events for Group members update the parent Group's display state

#### SSE Reconnection
- Coroutine-based event loop with 30s ALIVE timeout
- 5s reconnect delay on connection drop
- 3-strike polling fallback (15s interval) if SSE repeatedly fails
- Direct cache updates from SSE events including Group members

---

### New: Theme Sync (Phone → Watch)

- Phone sends theme on "Sync to Watch" button press (not immediately on selection)
- Watch receives via `/openhab/theme` message path → saves to DataStore → refreshes tile → writes to DataClient
- Watch manifest `pathPrefix` set to `/openhab` to receive all message paths
- Phone reads watch theme from DataClient on tile editor open, sets selector to match

---

### New: Watch Complication Service Enhancements

- Reads from `wear:complication-list` document (raw JSON endpoint for flexible parsing)
- Per-type config blocks for formatting: pattern, title, min/max per complication type
- Falls back to old metadata approach if document doesn't exist (backward compatible)
- Added **MONOCHROMATIC_IMAGE** complication type support (manifest + service)

---

### Improved: Watch UI

- **About screen** now shows `configVersion` for debugging sync issues
- **Main screen** updated with reload + about navigation
- **Tile preview** replaced with actual PNG screenshot (instead of generated XML drawable)

---

### Project Structure Changes

#### Multi-Module Architecture
```
openhab-android-wear/
├── shared/          # Shared models + constants (ServerCredentials, SyncConstants)
├── phone/           # Phone companion app (Compose Material 3)
├── watch/           # Watch app (Compose for Wear OS + ProtoLayout tiles)
├── build.gradle.kts # Root build
└── settings.gradle.kts
```

The previous single `app/` module has been renamed to `watch/`. A new `phone/` module contains the companion app. The `shared/` module holds common data models and sync constants.

---

### Technical Details

#### Phone App Tech Stack
| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Navigation | Navigation Compose |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Storage | EncryptedSharedPreferences, DataStore |
| Phone↔Watch | Wear Data Layer API (messages + DataClient) |

#### New Dependencies
- `androidx.wear:wear-data-layer` (phone + watch)
- `androidx.security:security-crypto` (phone — encrypted prefs)
- `androidx.navigation:navigation-compose` (phone)
- Material 3 Compose (phone)

#### Build Commands (updated)
```bash
# Build both modules
./gradlew :phone:assembleDebug :watch:assembleRelease

# Deploy phone app
adb -s <phone-serial> install -r phone/build/outputs/apk/debug/phone-debug.apk

# Deploy watch app
adb -t <transport_id> install -r watch/build/outputs/apk/release/watch-release.apk
```
