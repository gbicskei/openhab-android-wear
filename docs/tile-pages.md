# Tile Pages Design

## Overview

The openHAB tile supports multiple pages — a main page with up to 7 item slots, and named sub-pages accessible via navigation buttons. This allows organizing items by function (e.g., a "Security" page for gates and locks) without cluttering the main tile.

## Layout

### Main Page

```
┌──────────────────────────┐
│      [logo] ●            │  ← wearOH logo + connection dot
│                          │
│   [item]  [item]  [item] │
│   [item]  [item]  [item] │  ← up to 7 slots (adaptive grid)
│     [item]  [page→]      │
│                          │
│         [mic]            │  ← voice command button (bottom center)
└──────────────────────────┘
```

### Sub-Page

```
┌──────────────────────────┐
│      [logo] ●            │  ← wearOH logo + connection dot
│                          │
│   [gate]  [lock]  [door] │
│   [door1]   [door2]     │  ← up to 7 items on this page
│                          │
│         [← back]         │  ← back button (bottom center, replaces mic)
└──────────────────────────┘
```

## Navigation Model

- **Two levels only**: main page + sub-pages. No nesting deeper.
- **Navigation buttons** occupy one of the 7 item slots on the source page.
- **Self-navigation not allowed**: the tile editor excludes the current page from navigation target options (prevents circular navigation).
- **Back button** is auto-rendered at the bottom of every sub-page (not configurable, not an item slot).
- **Page switching** uses ProtoLayout's `LoadAction` — the tile re-renders instantly with the new page content. No Activity is launched (unless confirmation is required).
- **Navigation button state**: shows active (accent color) based on priority: `valueItem` state > own item state > aggregate from sub-page items (only if `aggregateState: "true"`). By default (`aggregateState: "false"`), nav buttons without a `valueItem` or own state remain inactive.

### Naming Convention

Navigation slots use the `WT_` prefix (Wear Tile) when they reference a backing Group item for aggregate state:
- `WT_Security` — navigates to security page
- `WT_Light` — navigates to light page
- `WT_Scenes` — navigates to scenes page
- `WT_Control` — navigates to control page

These are optional Group items with no channel bindings — they exist solely so aggregate state can be computed from their members. Navigation slots without aggregate state don't need a backing item.

### State Flow

```
User taps page button → loadAction(state: {page: "security"})
  → system calls onTileRequest()
  → service reads currentState.page
  → renders the "security" page items
  → includes back button at bottom

User taps back → loadAction(state: {page: "main"})
  → renders main page again
```

## Position Format

Positions use `{page}:{slot}` format:

| Position | Meaning |
|----------|---------|
| `1` or `main:1` | Main page, slot 1 |
| `main:6` | Main page, slot 6 |
| `security:1` | Security sub-page, slot 1 |
| `climate:3` | Climate sub-page, slot 3 |

For backward compatibility, a plain number (e.g., `"1"`, `"3"`) is treated as `main:{number}`.

### Slot Numbering

Slots are placed on a concentric 3×3 grid based on item count:

```
1 item:                    2 items:              3 items:
      [1]                  [1]   [2]            [1] [2] [3]

4 items:                   5 items:              6 items:
[1]       [2]             [1]       [3]         [1]       [2]
                                [2]             [3]       [4]
[3]       [4]             [4]       [5]         [5]       [6]

7 items:
[1]       [2]
[3]  [4]  [5]
[6]       [7]
```

Grid positions (row,col) per layout:
- 1: 1,1
- 2: 1,0 / 1,2
- 3: 1,0 / 1,1 / 1,2
- 4: 0,0 / 0,2 / 2,0 / 2,2
- 5: 0,0 / 1,1 / 0,2 / 2,0 / 2,2
- 6: 0,0 / 0,2 / 1,0 / 1,2 / 2,0 / 2,2
- 7: 0,0 / 0,2 / 1,0 / 1,1 / 1,2 / 2,0 / 2,2

## Configuration

Tile pages are stored as UI components in openHAB's JsonDB under the `wear:tile` namespace (or user-scoped `wear:tile:{userKey}`). Configuration is managed exclusively through the phone companion app's tile editor, which reads and writes via the REST API at `/rest/ui/components/{namespace}`.

See [configuration-schema.md](configuration-schema.md) for the full schema reference.

### Regular Item Slot

```json
{
  "component": "wear:tile-slot",
  "config": {
    "position": 1.0,
    "item": "BDR_Light",
    "icon": "light",
    "label": "Bedroom",
    "stateDisplay": "color",
    "action": "toggle"
  }
}
```

### Navigation Button (links to sub-page)

A navigation button occupies a slot on the source page and navigates to a named sub-page on tap. It does not require a backing openHAB item.

```json
{
  "component": "wear:tile-slot",
  "config": {
    "position": 6.0,
    "icon": "iconify:mdi:shield-home",
    "label": "Security",
    "action": "page:security",
    "stateDisplay": "none"
  }
}
```

To make the nav button light up when any item on the target page is active, add `aggregateState`:

```json
{
  "component": "wear:tile-slot",
  "config": {
    "position": 6.0,
    "icon": "iconify:mdi:shield-home",
    "label": "Security",
    "action": "page:security",
    "aggregateState": true
  }
}
```

The `action: "page:{pageName}"` field distinguishes navigation buttons from regular items.

### Item on Sub-Page (with confirmation)

```json
{
  "component": "wear:tile-slot",
  "config": {
    "position": 1.0,
    "item": "FrontGate_Control",
    "icon": "iconify:mdi:gate",
    "label": "Gate",
    "stateItem": "FrontGate_State",
    "action": "command",
    "actionCommand": "ON",
    "invertState": true,
    "actionConfirmation": true,
    "stateDisplay": "color"
  }
}
```

### State Display Modes

| Value | Behavior |
|-------|----------|
| `"value"` | Show state text below the icon (default) |
| `"color"` | Color-highlighted circle (accent = active, grey = inactive) |
| `"none"` | No state indicator — icon only, useful for command-only buttons |

## Examples

### Full tile configuration (example)

```
Main page (layout: 6):
  1: Kitchen_Light          (toggle, stateDisplay=color, light icon, "Kitchen")
  2: Living_Light           (toggle, stateDisplay=color, light icon, "Living Room")
  3: AC_Power               (toggle, stateDisplay=color, mdi:air-conditioner icon, "AC")
  4: Heating_Power          (toggle, stateDisplay=color, heating icon, "Heating")
  5: AC_Setpoint            (range, stateDisplay=value, material:thermostat icon, "Temp")
  6: WT_Security            (nav → page:security, stateDisplay=none, mdi:shield-home icon, "Security")

Security page (layout: 3):
  1: FrontGate_Control      (command ON+confirm, stateItem=FrontGate_State, invertState, mdi:gate icon, "Gate")
  2: SmartLock              (toggle+confirm, material:lock icon, "Lock")
  3: Garage_Control         (command ON+confirm, stateItem=Garage_State, invertState, mdi:garage icon, "Garage")
  [back button auto-rendered at bottom]
```

## Implementation Notes

### TileService Changes

- `onTileRequest` reads `requestParams.currentState.lastClickableId` to detect page navigation clicks
- Uses state map to track current page (default: "main")
- `buildTileLayout()` accepts a page name and filters items accordingly
- Navigation buttons render with `loadAction()` instead of `launchAction()`
- Back button uses `loadAction()` to set page back to "main"

### Data Model Changes

- `TileItem` has `action: String?` field (null for regular items, "page:{name}" for nav buttons, "command" for fixed commands)
- `TileItem.page: String` parsed from position (e.g., "main", "security")
- `TileItem.slot: Int` parsed from position (e.g., 1-7)
- `TileItem.valueItemName: String?` for separate display state source
- `TileItem.commandItemName: String?` for separate command target
- `TileItem.commandValue: String?` for fixed command string
- `TileItem.invertValue: Boolean` for flipping active/inactive display
- Repository groups items by page and resolves valueItems after initial fetch

### Position Parsing

```kotlin
fun parsePosition(raw: String): Pair<String, Int> {
    val parts = raw.split(":")
    return when (parts.size) {
        1 -> "main" to (parts[0].toDoubleOrNull()?.toInt() ?: 1)
        2 -> parts[0] to (parts[1].toDoubleOrNull()?.toInt() ?: 1)
        else -> "main" to 1
    }
}
```

### Tile Editor Impact

The tile editor provides:
- **Page tabs** at top showing all pages (main first, then sub-pages)
- **Long-press context menu** on any tab: Rename or Duplicate
- **Add Page** — user enters a display label; uid is auto-generated (`label.lowercase().replace(" ", "_")`) with `_2`, `_3` suffix for duplicates
- **Duplicate Page** — "Save As..." dialog pre-filled with "{Label} (copy)"; copies all slots to a new page
- **Navigation button creation** — in the item picker's "Navigate" tab; current page is excluded from the target list
- **Navigation target dropdown** in config sheet — also excludes the current page
- **Page deletion** — close button on selected non-main tab; confirmation dialog

### Resource Version

Page changes don't require new resources (icons are already loaded for all pages). The same resource version serves all pages — only the layout timeline entry changes.

## Constraints

- Maximum 7 item slots per page (excluding the bottom back/mic button)
- Maximum depth: 2 levels (main + sub-pages)
- Number of sub-pages: unlimited (but practically 3-4 is reasonable for a watch)
- Back button is always present on sub-pages, always at the bottom
- Mic button is only on the main page
- Navigation buttons count toward the 7-slot limit on their source page
