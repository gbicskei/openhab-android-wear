# Changelog

## [Unreleased] — Since v0.9.0-6

### Summary

Major release introducing the **Phone Companion App**, server-side tile configuration,
a complete complication editor, and dedicated control activities for Color, Rollershutter,
and multi-value items. The project has been restructured from a single `app/` module into
a multi-module architecture: `watch/`, `phone/`, and `shared/`.

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
- **Main Server** — the openHAB cloud URL (remote access via myopenhab.org)
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
