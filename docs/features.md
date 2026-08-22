# Features

## 1. Wear OS Tile

The primary interface — a system-level tile accessible by swiping from the watch face.

![Main tile — 4 navigation buttons](screenshots/main_labeled.png)

### What it does

Displays 1-7 openHAB items as tappable buttons in a concentric layout. Each button shows:
- Item icon (from openHAB icon set, configurable via metadata)
- Item label (truncated to 8 characters)
- Current state — rendered as text or color highlight (configurable via `stateDisplay`)
- Themed ring with radial glow (amber/blue/green/purple/red)

### Interaction Model

Items behave differently based on their type and metadata configuration:

| Item Type | Tile Display | Tap Action |
|-----------|-------------|------------|
| Switch | Label + state (ON/OFF or color) | Send toggle command, wait for SSE state update |
| Dimmer/Number (with min/max) | Label + current value | Opens dedicated rotary control screen |
| Color | Label + state (HSB or color) | Opens color picker (preset chips + bezel brightness) |
| Rollershutter | Label + position % | Opens roller shutter control (UP/STOP/DOWN + bezel) |
| Item with commandOptions | Label + current value | Opens choice picker (scrollable option list). Overridden by explicit `action: "toggle"` — sends ON/OFF directly. |
| Contact (read-only) | Label + value | No action (display only) |
| Command button (`action: "command"`) | Label + state from `valueItem` or primary | Sends fixed `commandValue` to target item |
| Navigation button (`action: "page:..."`) | Page label + icon | Navigate to sub-page (via LoadAction) |
| Double-tap button (`doubleTapItem` set) | Label + doubleTap item state (if `doubleTapStateDisplay: "value"`) | Single tap: primary action; Double tap: secondary action on doubleTapItem |

#### Switch items (toggle)

Tapping a switch item sends the command to the server. The button updates when the SSE event confirms the new state — no optimistic updates, always accurate.

If `needsConfirmation: true` is set in metadata, a confirmation dialog appears before sending.

#### Command items (fixed command)

When `action: "command"` is set, tapping always sends the fixed `commandValue` string to the target item (`commandItem` or primary item). No toggle logic — the same command is sent regardless of state.

Display state can come from a separate `valueItem` (e.g., a Contact sensor), with optional `invertValue` to flip the active/inactive color interpretation. This enables patterns like gate buttons (tap sends pulse, display shows sensor feedback).

#### Double-tap buttons (two actions per button)

When `doubleTapItem` is configured on a slot, the button supports two distinct actions:

- **Single tap** (no second tap within 350ms): executes the primary action (toggle, command, or auto-detect)
- **Double tap** (second tap within 350ms): executes a secondary action on the `doubleTapItem`

The secondary action uses the same auto-detection logic as primary: range items open RotaryControl, Color items open ColorPicker, etc. This can be overridden with `doubleTapAction: "toggle"` or `"command"`.

When `doubleTapStateDisplay: "value"` is set, the button shows the double-tap item's current state (e.g., temperature setpoint) as text on the button face.

Typical use case: AC control — single tap toggles power ON/OFF, double tap opens the temperature setpoint rotary control. The button shows the power state color AND the current setpoint value.

Haptic feedback (long-press vibration) confirms the double-tap was detected.

#### Range/Dimmer items (rotary control)

Tapping a range item navigates to a dedicated control screen:

![Rotary range control — AC temperature setpoint](screenshots/range_control.png)

- Current value displayed large in the center
- Bezel (rotating crown) changes the value up/down
- Min/max/step auto-detected from `stateDescription`
- Value sent via debounced command (500ms after last rotation)
- Back gesture or swipe-dismiss returns to the tile

#### Color items (color picker)

Tapping a Color item opens a dedicated color selection screen:

- **Preset color chips** in a 2×5 grid: Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, Warm White, Cool White
- **Brightness arc** on the screen edge shows current brightness level
- **Bezel rotation** adjusts brightness (0-100%)
- Tap a chip to select hue + saturation (brightness preserved)
- Tap ON/OFF label to toggle the light
- Sends openHAB HSB command format (e.g. `120,100,50`) with 400ms debounce
- Parses current state from item's HSB string on launch (supports `H,S,B`, percentage, ON/OFF)

#### Rollershutter items (shutter control)

Tapping a Rollershutter item opens a dedicated control screen:

- Three large vertically-stacked buttons: **UP** (green), **STOP** (orange), **DOWN** (red)
- Current position displayed in the center: `OPEN` (0%), `CLOSED` (100%), or percentage
- **Position arc** on screen edge starts from top, fills clockwise as shutter closes
- **Bezel rotation** adjusts position directly (sends percentage command with 500ms debounce)
- UP/DOWN/STOP send their respective commands immediately
- Position refreshes from server 500ms after STOP command

#### Items with command options (choice picker)

Items that have `commandDescription.commandOptions` (or `stateDescription.options` as fallback) open a scrollable list:

- **ScalingLazyColumn** (Wear OS optimized for round screens)
- Header shows item label
- Each option displayed as a card with label and raw command value
- Currently active option highlighted in amber
- Tap sends the command immediately
- Useful for scene selectors, input selectors, mode switches, and any item with predefined values

**Exception:** If the tile slot has `action: "toggle"` explicitly set, the choice picker is bypassed and the item is toggled directly with ON/OFF — even if commandOptions exist (e.g. MQTT switches with `true/false` options). This ensures reliable toggle behavior regardless of the item's commandDescription.

### Concentric Layout

Buttons are sized per layout — larger when fewer items allow it:

| Count | Button size | Arrangement | Notes |
|-------|-------------|-------------|-------|
| 1 | 74dp | Screen center | Single large button |
| 2 | 74dp | Horizontal center line, 4dp gap | Centered pair |
| 3 | 74dp | Staggered: center up, sides down (0.42× shift) | Chevron/roof shape, ~4dp diagonal gap |
| 4 | 74dp | 2×2 square grid, 4dp gap, shifted 4dp up | Clears mic button zone |
| 5 | 64dp | Diamond + center (edge_ratio=1.0) | — |
| 6 | 64dp | 7-item layout minus center button | Honeycomb |
| 7 | 64dp | Full: 2 top + 3 middle + 2 bottom | Full honeycomb |

| 1 | 2 | 3 | 4 |
|---|---|---|---|
| ![1 button](screenshots/layout_1.png) | ![2 buttons](screenshots/layout_2.png) | ![3 buttons](screenshots/layout_3.png) | ![4 buttons](screenshots/layout_4.png) |

| 5 | 6 | 7 |
|---|---|---|
| ![5 buttons](screenshots/layout_5.png) | ![6 buttons](screenshots/layout_6.png) | ![7 buttons](screenshots/layout_7.png) |

The layout 3 stagger places side buttons at `centerY + 0.42*btn` and the center button at `centerY - 0.42*btn`, creating diagonal separation that allows larger buttons without horizontal overlap.

Fixed zones (overlays, don't affect button positions):
- Top: wearOH logo + connection status dot (all pages) — dims during loading
- Bottom: Mic button (main page, drawn SVG icon) or Back button (sub-pages, ← character)

### Tile State Machine

| State | Visual | Clickable | Trigger |
|-------|--------|-----------|---------|
| Dimmed | All buttons grey, rings faded, title grey | No | Swipe to tile (initial render) |
| Live | Colored rings, themed icons, title white | Yes | Fresh states received from server |

Flow:
1. Swipe to tile → render dimmed from cached config
2. Fetch fresh states from server in background
3. States arrive → re-render lit → SSE connection starts
4. SSE event received → fetch fresh states from server → re-render with updated state
5. Swipe away → SSE stops
6. Page navigation (main ↔ sub-pages) → instant from cache, stays lit

### Page Navigation

![Security sub-page with gate controls and back button](screenshots/main_page.png)

- **Forward**: Tap navigation button → `LoadAction` (instant, no Activity launch)
- **Back**: Tap back button at bottom → `LoadAction` returns to main
- **With confirmation**: Launches `PageNavigationActivity` for dialog
- **Nav button state**: Active (accent color) if any item on its target sub-page is active. Priority: explicit `valueItem` > own item state > aggregate from sub-page items.
- Max depth: 2 levels (main + sub-pages)
- Sub-page items use cached states (no re-fetch)

### Themes

![Theme picker with 7-button preview and dot indicators](screenshots/theme_picker.png)

5 color themes available (configurable via long-press → pencil on tile):
- **Amber** — warm golden glow (default)
- **Blue** — cool tech glow
- **Green** — nature/secure glow
- **Purple** — elegant accent
- **Red** — intense warm glow

Theme controls: ring color, icon tint, radial glow, state text color.
Theme selection stored locally in DataStore.
Theme picker uses bezel/crown rotation with live preview.

### Caching

- **Tile config**: persisted to disk as JSON (`TileConfigDiskCache`) with a `configVersion` tag. On process restart, renders immediately from disk cache (warm start). Cache stays valid as long as `configVersion` matches the server — no re-fetch needed.
- **Item metadata**: fetched in parallel (max 3 concurrent) for only the ~14 referenced items. Never fetches all 748 items. Cached to disk with configVersion — skips network entirely when version unchanged.
- **Item states**: fetched fresh on each tile enter via parallel `getItem()` calls for ~12 items (~0.8–1s). Updated via SSE while tile is visible.
- **Group item state**: when a Group's own state is NULL/UNDEF, derives activity from its members.
- **Icon bytes**: LRU memory cache (30 entries). Survives tile refreshes.
- **Composited bitmaps**: LRU memory cache (128 entries). Cache hits are 0ms.
- **SSE reconnection**: coroutine-based loop with 30s ALIVE timeout, 5s reconnect delay, 3-strike polling fallback (15s interval).

#### Performance (via myopenhab.org cloud relay)

| Operation | Duration | Notes |
|-----------|----------|-------|
| Cold load (disk cache valid) | ~0ms | configVersion matches, loaded from disk |
| Cold load (config changed) | ~1.7s | Parallel fetch of ~14 items (throttled 3 concurrent) |
| State refresh (tile enter) | ~0.8–1s | Parallel fetch of ~12 items |
| Icon composite (cache hit) | 0ms | LRU hit |
| Icon composite (cache miss) | 8–25ms | SVG/PNG render + compress |
| Send command | ~230ms | Single API call |

### Configuration

Tile configuration is managed via the **Phone Companion App**, which provides a visual tile editor. The editor writes `wear:tile-page` UI components to the server via REST API at `/rest/ui/components/wear:tile` (or a user-scoped namespace). No item metadata knowledge required.

Each page document supports per-slot configuration including stateDisplay, action, actionItem, stateItem, and more. See [configuration-schema.md](configuration-schema.md) for the full schema and [tile-pages.md](tile-pages.md) for the page/navigation model.

### State Display Modes

Each tile button's state indicator can be configured with one of three modes:

| Mode | API Value | Visual | Use Case |
|------|-----------|--------|----------|
| **Color** | `"color"` | Icon ring colored (amber=active, grey=inactive), no text | Binary on/off indicators |
| **Value** | `"value"` | Neutral icon ring + state text below (ON/OFF, 22.5°C, 50%) | Showing current values |
| **None** | `"none"` | Neutral icon ring, no text, no active/inactive indication | Command-only buttons (gates, scenes) |

### Implementation

| Component | File | Role |
|-----------|------|------|
| `OpenHabTileService` | `tile/OpenHabTileService.kt` | Fetches items, computes positions, builds tile layout |
| `TileActionReceiver` | `tile/TileActionReceiver.kt` | Handles tap → sends command → requests refresh |
| `QuickActionActivity` | `tile/QuickActionActivity.kt` | Double-tap detection (350ms window), routes primary/secondary actions |
| `PageNavigationActivity` | `tile/PageNavigationActivity.kt` | Handles confirmed page navigation |
| `ThemePickerActivity` | `ui/tile/ThemePickerActivity.kt` | Theme color selector (bezel rotation) |
| `ItemCache` | `data/repository/ItemCache.kt` | In-memory cache for tile items + states |
| `TileConfigDiskCache` | `data/repository/TileConfigDiskCache.kt` | Disk persistence with configVersion tracking |
| `TileStateEventSource` | `data/api/TileStateEventSource.kt` | SSE client for real-time state updates |
| `IconCompositor` | `data/icon/IconCompositor.kt` | Renders icons with ring, glow, tint, label |

---

## 2. Voice Commands

Triggered from the mic button on the tile's main page.

![Voice command — Google speech recognizer](screenshots/voice_command.png)

### How it works

1. Tap mic button → `VoiceCommandActivity` launches
2. System speech recognizer launches immediately
3. Recognized text is sent to `POST /rest/voice/interpreters` with device locale
4. openHAB's interpreter processes the command
5. Watch shows success/error feedback, then finishes

### Server-side requirements

The voice endpoint requires a Human Language Interpreter (HLI) configured in openHAB:

1. **Set locale** in `conf/services/runtime.cfg`:
   ```
   org.openhab.i18n:language=en
   org.openhab.i18n:region=US
   ```

2. **Set default HLI** in `conf/services/runtime.cfg`:
   ```
   org.openhab.voice:defaultHLI=system
   ```

Available interpreters:
- `system` — Built-in, matches item labels directly. Supports "turn on [label]". Limited pattern set.
- `rulehli` — Rule-based, uses semantic model (requires Location tags on room groups). More capable but needs proper semantic tagging.

### Notes

- No configuration needed on the watch — uses server credentials from setup
- Language comes from the watch's system locale
- Requires network connectivity (speech recognition is cloud-based)
- No separate menu entry — accessed only from tile mic button
- The `system` interpreter matches against item labels — items need descriptive English labels

---

## 3. App Menu (Launcher)

Opening the app from the watch launcher shows a logo header with server connection indicator (green/red dot) and:

1. **Setup on Phone** — sends a message to open the phone companion app for connection setup
2. **Reload Items** — clears item cache, fetches fresh config from server, shows toast with result
3. **Settings** — opens watch settings (Voice, Notifications, Debug)
4. **About** — app version info + configVersion

### Setup

- First launch shows "Setup on Phone" button — tapping it opens the phone companion for configuration
- No manual credential entry on the watch (phone companion is required for initial setup)
- "Reload Items" clears cache and re-fetches tile config from the server

See [Connection](connection.md) for the full setup flow including phone-to-watch sync.

---

## 4. Theme Picker (Tile Long-Press)

Accessed via long-press on tile → system pencil icon.

- Shows concentric button layout as live preview
- Rotate bezel/crown to cycle themes (amber → blue → green → purple → red)
- Dot indicators at top edge show position
- All buttons update color in real-time
- Theme saved automatically on rotation
- Swipe back to confirm and exit

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Server is source of truth for tile config | No sync conflicts, phone app is the visual editor |
| Watch is source of truth for settings | Settings are local to the device; phone is a remote editor, server is backup |
| Concentric layout from screen size | Responsive to all Wear OS screen sizes |
| Box overlay positioning (absolute) | ProtoLayout doesn't support relative/grid positioning |
| Dim/lit state machine | Clear online indicator, prevents commands before state is known |
| SSE for real-time updates | Battery-efficient push-based, only while tile visible |
| LoadAction for page navigation | Instant re-render without Activity launch overhead |
| Disk cache with hot path refresh | Fast warm start, then 1 API call to refresh states |
| Dedicated control activities per item type | Best UX for each interaction pattern (bezel, buttons, list) |
| Phone companion edits UI components (not metadata) | Richer config (action, stateItem, etc.) beyond what metadata supports |
| Config version counter for sync detection | Simple integer compare, no timestamp drift issues |
| FCM for push notifications | Battery-friendly, leverages existing openHAB Cloud infrastructure |
| MessageClient for settings sync | Instant request/response, no polling or persistent connection needed |
| Theme in tile page definition | Single source of truth on server, no separate sync path |

---

## 5. Phone Companion App

A full Android phone application for managing the watch tile configuration.

### Connection Setup

Two server connections:
- **Main Server** — the openHAB server URL used by the watch (cloud relay or direct)
- **Config Server** — local network URL for REST API access to edit tile configuration

Authentication modes for Config Server:
- **Basic Auth** — username/password (requires `allowBasicAuth=true` in openHAB API Security settings)
- **API Token** — Bearer token generated in openHAB Settings > API Security (recommended for openHAB 5+)

Both connections are tested before saving. Credentials are stored in EncryptedSharedPreferences. Saving auto-syncs credentials to the watch (gated by successful test).

See [Connection](connection.md) for the full credential model, sync protocol, and security details.

### Tile Design Editor

Visual editor for the watch tile layout:
- **Carousel page selector** — horizontally scrollable page chips replacing tab row
- **Drag-and-drop page reordering** — long-press a page chip to drag (main page pinned at position 1)
- **Long-press context menu** on any page chip — Rename or Duplicate the page
- **Page creation** — "Add Page" asks for a display label; internal uid is auto-generated. New pages insert after current selection and auto-focus.
- **Page duplication** — "Save As..." dialog pre-filled with "{Label} (copy)", copies all slots to the new page
- **Layout selector** to set the button count (1-7)
- **Circular watch preview** rendering the actual tile layout with wearOH logo on all pages
- **Position badges** on the outer ring edge toward the bezel (shows slot numbers)
- **Drag-and-drop slot reordering** — long-press a slot on the watch preview to drag-swap positions
- **Slot configuration** — tap any slot to open the config sheet:
  - Item selection (searchable picker)
  - Icon override (searchable bottom sheet: MDI, Material Symbols, openHAB icons)
  - Label override
  - State display mode (Color / Value / None)
  - Action type (Toggle / Command / Navigate)
  - Action item (searchable item picker — item that receives commands)
  - State item (searchable item picker — item whose state is displayed)
  - Complement action (secondary action for toggle items)
  - Fixed command, invert state, confirmation, aggregate
- **Navigation target filtering** — current page is excluded from the target page list
- Carousel chips centered when not filling full width

### Complication Editor

Configure watch face complications:
- List view of all configured complications with add/edit/remove
- 4 supported types: SHORT_TEXT, LONG_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE
- Per-type expandable config sections with live pattern validation
- Character limits enforced (7 chars for short text fields)
- Import existing complication config from item metadata

### Theme Management

- Theme color stored in the tile page definition on the server
- Managed via the tile design editor (no separate sync path)
- Watch reads theme from tile config on cold load
- 5 themes: Amber (default), Blue, Green, Purple, Red

### Config Sync Detection

- Watch writes its current `configVersion` to DataClient after each cold load
- Phone reads this + fetches the server's version, compares
- "Watch config out of sync" banner on home screen when they differ
- Clears on sync, re-checks on app resume

### Watch Settings (Remote Editor)

The phone provides a remote settings editor for watch-owned settings. The watch is the source of truth — the phone reads current values via MessageClient request/response and pushes changes back instantly (no Save button).

Sections:
- **Voice** — TTS engine, speech rate, pitch, WaveNet voice, test button
- **Notifications** — enable/disable, read aloud, chime, priority filter
- **Debug** — enable/disable debug mode, view debug log (24h retention, tail-f UX)

Settings are backed up to the server as item metadata for disaster recovery.

### Debug Log

- Watch captures all log levels when debug mode is on
- Debounced publish to phone via MessageClient
- Phone persists log entries (24h retention, paged loading)
- Tail-f UX: auto-scrolls to latest entries
- Uncaught exception handler on watch sends crashes to debug log

---

## 6. Push Notifications

FCM push notifications forwarded from openHAB Cloud to the watch.

### Notification Modes

| FCM Tag | Behavior |
|---------|----------|
| `audio-tts` | Watch speaks the message using configured TTS engine |
| `audio-sink` | Streams pre-rendered audio from a server URL (AudioUrlPlayer) |
| _(default)_ | Standard notification posted to watch notification shade |

### Audio Sink Playback

- `AudioUrlPlayer` streams audio from server-prepared URLs
- `SpeakDisplayActivity` shows message text + wearOH logo during playback
- Playback uses device system volume (no app-level volume override)

### Implementation

| Component | File | Role |
|-----------|------|------|
| `FcmMessageListenerService` | `notification/FcmMessageListenerService.kt` | Receives FCM messages from openHAB Cloud |
| `FcmRegistrationWorker` | `notification/FcmRegistrationWorker.kt` | Registers/refreshes FCM token with openHAB Cloud |
| `NotificationHandler` | `notification/NotificationHandler.kt` | Routes by tag: TTS, audio-sink, or standard notification |
| `SpeakDisplayActivity` | `notification/SpeakDisplayActivity.kt` | Shows message text + logo during audio playback |
| `AudioUrlPlayer` | `util/AudioUrlPlayer.kt` | MediaPlayer wrapper for streaming audio URLs |
| `NotificationPreferenceStore` | `data/repository/NotificationPreferenceStore.kt` | Stores notification preferences (chime, read-aloud, priority) |
