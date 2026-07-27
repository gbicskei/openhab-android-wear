# Features

## 1. Wear OS Tile

The primary interface — a system-level tile accessible by swiping from the watch face.

### What it does

Displays 1-7 openHAB items as tappable buttons in a concentric layout. Each button shows:
- Item icon (from openHAB icon set, configurable via metadata)
- Item label (truncated to 8 characters)
- Current state — rendered as text or color highlight (configurable via `valueDisplay`)
- Themed ring with radial glow (amber/blue/green/purple/red)

### Interaction Model

Items behave differently based on their type and metadata configuration:

| Item Type | Tile Display | Tap Action |
|-----------|-------------|------------|
| Switch | Label + state (ON/OFF or color) | Send toggle command, wait for SSE state update |
| Dimmer/Number (with min/max) | Label + current value | Opens dedicated rotary control screen |
| Contact (read-only) | Label + value | No action (display only) |
| Command button (`action: "command"`) | Label + state from `valueItem` or primary | Sends fixed `commandValue` to target item |
| Navigation button (`action: "page:..."`) | Page label + icon | Navigate to sub-page (via LoadAction) |

#### Switch items (toggle)

Tapping a switch item sends the command to the server. The button updates when the SSE event confirms the new state — no optimistic updates, always accurate.

If `needsConfirmation: true` is set in metadata, a confirmation dialog appears before sending.

#### Command items (fixed command)

When `action: "command"` is set, tapping always sends the fixed `commandValue` string to the target item (`commandItem` or primary item). No toggle logic — the same command is sent regardless of state.

Display state can come from a separate `valueItem` (e.g., a Contact sensor), with optional `invertValue` to flip the active/inactive color interpretation. This enables patterns like gate buttons (tap sends pulse, display shows sensor feedback).

#### Range/Dimmer items (rotary control)

Tapping a range item navigates to a dedicated control screen:
- Current value displayed large in the center
- Bezel (rotating crown) changes the value up/down
- Min/max/step auto-detected from `stateDescription`
- Value sent via debounced command (500ms after last rotation)
- Back gesture or swipe-dismiss returns to the tile

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

The layout 3 stagger places side buttons at `centerY + 0.42*btn` and the center button at `centerY - 0.42*btn`, creating diagonal separation that allows larger buttons without horizontal overlap.

Fixed zones (overlays, don't affect button positions):
- Top: "openHAB" title (main page) or page name (sub-pages) — dims during loading
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

- **Forward**: Tap navigation button → `LoadAction` (instant, no Activity launch)
- **Back**: Tap back button at bottom → `LoadAction` returns to main
- **With confirmation**: Launches `PageNavigationActivity` for dialog
- **Nav button state**: Active (accent color) if any item on its target sub-page is active. Priority: explicit `valueItem` > own item state > aggregate from sub-page items.
- Max depth: 2 levels (main + sub-pages)
- Sub-page items use cached states (no re-fetch)

### Themes

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

- **Item configuration** (metadata, positions, labels, icons): cached in memory after first fetch. Persists until "Reload Items" in the app.
- **Item states**: fetched fresh on each tile enter (swipe to tile). Updated via SSE while visible.
- **Icon bytes**: LRU memory cache (30 entries). Survives tile refreshes.
- **Composited bitmaps**: regenerated each render (theme/state dependent).

### Configuration

Items are configured via openHAB item metadata (namespace: `wearTile`) in the openHAB Main UI. The watch reads this configuration — it never modifies server-side metadata.

See [openHAB Configuration](openhab-configuration.md) for setup instructions.

### Implementation

| Component | File | Role |
|-----------|------|------|
| `OpenHabTileService` | `tile/OpenHabTileService.kt` | Fetches items, computes positions, builds tile layout |
| `TileActionReceiver` | `tile/TileActionReceiver.kt` | Handles tap → sends command → requests refresh |
| `PageNavigationActivity` | `tile/PageNavigationActivity.kt` | Handles confirmed page navigation |
| `ThemePickerActivity` | `ui/tile/ThemePickerActivity.kt` | Theme color selector (bezel rotation) |
| `ItemCache` | `data/repository/ItemCache.kt` | In-memory cache for tile items + states |
| `TileStateEventSource` | `data/api/TileStateEventSource.kt` | SSE client for real-time state updates |
| `IconCompositor` | `data/icon/IconCompositor.kt` | Renders icons with ring, glow, tint, label |

---

## 2. Voice Commands

Triggered from the mic button on the tile's main page.

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

## 3. Push Notifications

Receive openHAB Cloud notifications directly on the watch.

### How it works

1. An openHAB rule calls `sendNotification()` or `sendBroadcastNotification()`
2. openHAB Cloud pushes via FCM to registered devices
3. `FcmListenerService` receives and displays a notification

### Supported features

- Title + message text
- Notification grouping (by tag)
- Replace/cancel notifications (by reference-id)
- Notification channel with vibration

---

## 4. App Menu (Launcher)

Opening the app from the watch launcher shows:

1. **Server Settings** — configure server URL, username, password (RemoteInput)
2. **Reload Items** — clears item cache, fetches fresh config from server, shows toast with result

### Setup

- First launch auto-redirects to setup if not configured
- Server URL pre-filled with `https://myopenhab.org`
- Text input via Wear OS RemoteInput (keyboard/voice/handwriting)
- Verifies connectivity after save ("Save & Connect")
- Existing credentials loaded when re-opening settings

---

## 5. Theme Picker (Tile Long-Press)

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
| Server is source of truth for item config | No sync conflicts, openHAB Main UI is the single editor |
| Watch stores only theme + credentials locally | Minimal local state, no divergence from server |
| Concentric layout from screen size | Responsive to all Wear OS screen sizes |
| Box overlay positioning (absolute) | ProtoLayout doesn't support relative/grid positioning |
| Dim/lit state machine | Clear online indicator, prevents commands before state is known |
| SSE for real-time updates | Battery-efficient push-based, only while tile visible |
| LoadAction for page navigation | Instant re-render without Activity launch overhead |
| Item cache with state refresh on enter | Fast page renders, fresh states on each visit |
