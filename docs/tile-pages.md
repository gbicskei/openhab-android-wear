# Tile Pages Design

## Overview

The openHAB tile supports multiple pages — a main page with up to 7 item slots, and named sub-pages accessible via navigation buttons. This allows organizing items by function (e.g., a "Security" page for gates and locks) without cluttering the main tile.

## Layout

### Main Page

```
┌──────────────────────────┐
│      openHAB             │  ← title
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
│      Security            │  ← page name
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
- **Back button** is auto-rendered at the bottom of every sub-page (not configurable, not an item slot).
- **Page switching** uses ProtoLayout's `LoadAction` — the tile re-renders instantly with the new page content. No Activity is launched (unless confirmation is required).
- **Navigation button state**: shows active (accent color) if any item on the target sub-page is active. Priority: `valueItem` state > own item state > aggregate from sub-page items.

### Naming Convention

Navigation Groups use the `WT_` prefix (Wear Tile):
- `WT_Security` — navigates to security page
- `WT_Light` — navigates to light page
- `WT_Scenes` — navigates to scenes page
- `WT_Control` — navigates to control page

These are purpose-built Group items with no channel bindings — they exist solely to carry `wearTile` metadata for navigation.

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

## Metadata Configuration

### Regular Item (on main page)

```json
{"value": "tile", "config": {"position": "main:1", "icon": "light", "label": "Bedroom", "valueDisplay": "color"}}
```

### Navigation Button (links to sub-page)

A navigation button is defined as a special metadata entry. It does NOT correspond to a real openHAB item — it's purely a UI navigation element. However, to keep it within the openHAB metadata system, it's attached to a Group item (or a virtual String item created for this purpose).

```json
{"value": "tile", "config": {"position": "main:6", "icon": "iconify:mdi:shield-home", "label": "Security", "action": "page:security"}}
```

The `action: "page:{pageName}"` field distinguishes navigation buttons from regular items.

### Item on Sub-Page

```json
{"value": "tile", "config": {"position": "security:1", "icon": "material:lock", "label": "Door Lock", "needsConfirmation": "true", "valueDisplay": "color"}}
```

### Protected Sub-Page (with confirmation)

If the navigation button has `needsConfirmation: "true"`, the confirmation dialog appears before navigating:

```json
{"value": "tile", "config": {"position": "main:5", "icon": "iconify:mdi:shield-lock", "label": "Security", "action": "page:security", "needsConfirmation": "true"}}
```

## Examples

### .items file

```
// Navigation item for security page (attached to a Group)
Group    TileNav_Security    "Security Page"    { wearTile="tile" [position="main:6", icon="iconify:mdi:shield-home", label="Security", action="page:security", needsConfirmation="true"] }

// Items on the security page — gate uses valueItem for sensor state
Switch   FrontGate_Control   "Front Gate"   <gate>    { wearTile="tile" [position="security:1", icon="iconify:mdi:gate", label="Gate", valueItem="FrontGate_State", action="command", commandValue="ON", invertValue="true", needsConfirmation="true", valueDisplay="color"] }
Switch   SmartLock           "Lock"                   { wearTile="tile" [position="security:2", icon="material:lock", label="Lock", needsConfirmation="true", valueDisplay="color"] }
Switch   Garage_Control      "Garage"       <garage>  { wearTile="tile" [position="security:3", icon="iconify:mdi:garage", label="Garage", valueItem="Garage_State", action="command", commandValue="ON", invertValue="true", needsConfirmation="true", valueDisplay="color"] }
```

### Full tile configuration (example)

```
Main page:
  1: Kitchen_Light          (toggle, light icon, "Kitchen")
  2: Living_Light           (toggle, light icon, "Living Room")
  3: AC_Power               (toggle, mdi:air-conditioner icon, "AC")
  4: Heating_Power          (toggle, heating icon, "Heating")
  5: AC_Setpoint            (range, material:thermostat icon, "Temp")
  6: TileNav_Security       (nav button → page:security, mdi:shield-home icon, "Security")

Security page:
  1: FrontGate_Control      (command+confirm, valueItem=FrontGate_State, mdi:gate icon, "Gate")
  2: SmartLock              (toggle+confirm, material:lock icon, "Lock")
  3: Garage_Control         (command+confirm, valueItem=Garage_State, mdi:garage icon, "Garage")
  [back button auto-rendered at bottom]
```

**Note:** Navigation buttons require an item to carry the metadata. This is a known limitation
of the metadata-based approach — the planned page-based UI configuration approach will eliminate
this constraint.

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

The tile editor needs to:
- Show which page is being edited (tab/selector at top)
- Allow adding navigation buttons as a special item type
- Allow creating new pages (just a name)
- Show the page structure visually

### Resource Version

Page changes don't require new resources (icons are already loaded for all pages). The same resource version serves all pages — only the layout timeline entry changes.

## Constraints

- Maximum 7 item slots per page (excluding the bottom back/mic button)
- Maximum depth: 2 levels (main + sub-pages)
- Number of sub-pages: unlimited (but practically 3-4 is reasonable for a watch)
- Back button is always present on sub-pages, always at the bottom
- Mic button is only on the main page
- Navigation buttons count toward the 7-slot limit on their source page
