# openHAB Configuration

This guide explains how to configure your openHAB items to appear on the watch tile.

## Overview

The watch app reads item configuration from the openHAB server using **item metadata**. Items marked with the `wearTile` metadata namespace will appear on the watch tile and/or in the complication picker, depending on the configuration.

**Important:** Metadata editing is out of scope for the watch app. The watch only *reads* items that have `wearTile` metadata — it never creates, modifies, or deletes metadata. All configuration must be done on the openHAB server side (Main UI, REST API, or `.items` files).

### Tile vs. Complication

The `wearTile` metadata serves two purposes:

| Feature | Where it appears | How to enable |
|---------|-----------------|---------------|
| **Tile** | Wear OS tile (swipe from watch face) | Set `position` in metadata config |
| **Complication** | Watch face data slot | Set value to `"complication"` OR add `complication="true"` in config |

An item can be tile-only, complication-only, or both:

```
# Tile only — a light switch
wearTile = "tile"          [position="main:1", icon="light"]

# Complication only — a temperature sensor shown on the watch face
wearTile = "complication"

# Both — solar power on the tile AND as a watch face complication
wearTile = "tile"          [position="main:3", complication="true", icon="solarplant"]
```

## Adding wearTile Metadata

### Via openHAB Main UI (recommended)

1. Open your openHAB instance (e.g., `http://your-openhab:8080`)
2. Navigate to **Settings → Items**
3. Find the item you want on your watch tile
4. Click the item to edit it
5. Scroll down to **Metadata**
6. Click **Add Metadata** → enter namespace: `wearTile`
7. Set:
   - **Value:** `tile` (or any non-empty string)
   - **Config → position:** `1` (number 1-7 for ordering)
8. Save

Repeat for up to 7 items per page, assigning positions 1 through 7.

### Via REST API

```bash
# Add wearTile metadata with full config
curl -u "user@email.com:password" \
  -X PUT "https://myopenhab.org/rest/items/Bedroom_Light/metadata/wearTile" \
  -H "Content-Type: application/json" \
  -d '{"value": "tile", "config": {"position": "1", "icon": "light", "valueDisplay": "color"}}'

# Add with confirmation required
curl -u "user@email.com:password" \
  -X PUT "https://myopenhab.org/rest/items/FrontGate_Control/metadata/wearTile" \
  -H "Content-Type: application/json" \
  -d '{"value": "tile", "config": {"position": "security:1", "icon": "iconify:mdi:gate", "needsConfirmation": "true", "valueDisplay": "color"}}'
```

### Via .items file (text configuration — simple)

```
Switch Bedroom_Light "Bedroom Light" <light> (gBedroom) ["Lightbulb"] { wearTile="tile" [position="1", icon="light", valueDisplay="color"] }
```

## Metadata Schema

```json
{
  "value": "tile",
  "config": {
    "position": "main:1",
    "icon": "light",
    "label": "Bedroom",
    "action": null,
    "valueDisplay": "color",
    "valueItem": null,
    "invertValue": "false",
    "commandItem": null,
    "commandValue": null,
    "needsConfirmation": "false",
    "aggregateState": "false"
  }
}
```

| Field | Required | Type | Default | Description |
|-------|----------|------|---------|-------------|
| `value` | Yes | String | — | Determines item role: `"tile"` = tile item (requires `position`), `"complication"` = complication-only item. |
| `config.position` | Tile only | String | — | Display order on the tile. Plain number (`"1"` - `"7"`) for main page, or `"page:slot"` format for sub-pages (e.g., `"security:2"`). Lower numbers appear first. Presence of this field makes the item appear on the tile. |
| `config.complication` | No | Boolean | `"false"` | If `"true"`, the item appears in the complication picker on the watch. Can be combined with `position` to appear on both tile and complication. Not needed when `value` is already `"complication"`. |
| `config.icon` | No | String | item's category | Override icon name. Supports classic (`light`), Material (`material:thermostat`), and Iconify (`iconify:mdi:gate`) formats. |
| `config.label` | No | String | item's label | Override display label shown on the tile button. |
| `config.action` | No | String | auto-toggle | What happens on tap. `"page:{name}"` = navigate to sub-page. `"command"` = send a fixed command. `null` = auto-toggle (ON↔OFF) or range control. |
| `config.valueDisplay` | No | Enum | `"value"` | How the item state is rendered. `"value"` = show state as text. `"color"` = color-highlighted circle (accent = active, grey = inactive). |
| `config.valueItem` | No | String | — | Item name to read display state from instead of the primary item. The valueItem does not need its own `wearTile` metadata. |
| `config.invertValue` | No | Boolean | `"false"` | If `"true"`, invert the active/inactive interpretation for display (ON→inactive, OFF→active, OPEN→inactive, CLOSED→active). |
| `config.commandItem` | No | String | — | Item name to send commands to instead of the primary item. Used when the primary item is the display source and the command target is a different item. |
| `config.commandValue` | No | String | — | Fixed command string to send on tap (e.g., `"ON"`). When set, skips toggle logic and always sends this value. Requires `action: "command"`. |
| `config.needsConfirmation` | No | Boolean | `"false"` | If `"true"`, shows a confirmation dialog before sending the command. |
| `config.aggregateState` | No | Boolean | `"false"` | Navigation buttons only. If `"true"`, the nav button shows as active (accent color) when any item on the target sub-page is active. If `"false"` (default), the nav button stays inactive unless it has a `valueItem` or its own item has a non-NULL state. |

### Item Roles

The primary item (the one carrying the `wearTile` metadata) serves as both the display source and command target by default. The `valueItem` and `commandItem` fields allow redirecting either role to a different item:

**Pattern A: Primary = command target, display from elsewhere**
- Metadata on the command item (e.g., a gate trigger switch)
- `valueItem` points to the state sensor (e.g., a Contact reporting gate position)
- Tap → sends to primary, display reads from valueItem

**Pattern B: Primary = display source, command goes elsewhere**
- Metadata on the display item (e.g., a Number showing a calculated value)
- `commandItem` points to the trigger item (e.g., a rule-triggering Switch)
- Tap → sends to commandItem, display reads from primary

`valueItem` and `commandItem` are mutually exclusive — you redirect one role, not both. The primary item always fills the other role.

### Config property details

#### `icon`

Overrides the item's category for icon display. Supports three formats:
- Classic openHAB icons: `"light"`, `"heating"`, `"lock"`, `"gate"`
- Material icons: `"material:thermostat"`, `"material:hallway"`
- Iconify (MDI etc.): `"iconify:mdi:gate"`, `"iconify:mdi:shower"`

The watch fetches classic icons from `/icon/{name}?format=svg&state={state}`. Material and Iconify icons are rendered locally from bundled vector assets.

If omitted, the item's `category` field is used (typically set by the item definition, e.g., `<light>`).

#### `action`

Determines what happens when the button is tapped:

- `null` / omitted (default): Auto-behavior based on item type. Switch/Dimmer/Color → toggle ON↔OFF. Number with min/max → open rotary range control. Contact → no action (display only).
- `"command"`: Send a fixed command. The command value is specified by `commandValue`. Does not toggle — always sends the same string.
- `"page:{name}"`: Navigate to a sub-page. The button becomes a navigation element instead of a toggle.

#### `valueDisplay`

Controls how the item's current state is shown on the tile button:

- `"value"` (default): Shows the state as text below the label (e.g., "ON", "OFF", "22.5°C", "OPEN")
- `"color"`: Shows the entire button with a color highlight — theme accent color for active state, grey for inactive. No state text is displayed.

Active state interpretation (affected by `invertValue`):
- `ON`, `OPEN`, numeric > 0 → active (theme accent color)
- `OFF`, `CLOSED`, `0`, `NULL` → inactive (grey)

#### `valueItem`

Names a different item whose state is displayed on the button. The primary item still receives commands, but display comes from the valueItem.

The valueItem does **not** need its own `wearTile` metadata — the watch fetches it by name as part of loading the tile configuration.

Example: A gate command switch shows the state of a separate contact sensor:

```json
{"value": "tile", "config": {"position": "security:1", "icon": "iconify:mdi:gate", "label": "Front Gate", "valueItem": "FrontGate_State", "commandValue": "ON", "action": "command", "needsConfirmation": "true", "valueDisplay": "color"}}
```

SSE state tracking watches the valueItem instead of the primary item when this field is set.

#### `invertValue`

Flips the active/inactive color interpretation:

| State | Normal (`"false"`) | Inverted (`"true"`) |
|-------|---|---|
| ON / OPEN | active (accent color) | inactive (grey) |
| OFF / CLOSED | inactive (grey) | active (accent color) |

Useful for sensors where the "good" state is the opposite of what the default logic assumes. Example: A gate where `CLOSED` means "secured" and should appear as the calm/inactive color.

#### `commandItem`

Names a different item that receives the command on tap. The primary item provides the display state. Used when the display item is a sensor or calculated value and the command trigger is a separate item.

Example: A temperature display item with a command that triggers a rule via a separate switch:

```json
{"value": "tile", "config": {"position": "3", "icon": "material:thermostat", "label": "Home Temp", "commandItem": "Climate_Trigger", "commandValue": "ON", "action": "command", "valueDisplay": "value"}}
```

#### `commandValue`

A fixed string command sent on tap. When set, the watch does not toggle — it always sends this exact value. Must be used with `action: "command"`.

Examples:
- `"ON"` — pulse trigger for gates (gate resets itself)
- `"REFRESH"` — trigger a recalculation
- `"play"` — send a player command

#### `needsConfirmation`

When set to `"true"`, tapping the item on the tile does not immediately act. Instead, a confirmation screen appears on the watch asking "Are you sure?" with Yes/No options. Only on "Yes" is the command sent or navigation performed.

Recommended for:
- Gate controls
- Smart locks
- Scene activations with side effects (e.g., "Away" scene turns off all lights)
- Any action that is difficult or impossible to reverse

### Example configurations

```json
// Bedroom light — simple toggle with color display
{"value": "tile", "config": {"position": "1", "icon": "light", "label": "Bedroom", "valueDisplay": "color"}}

// AC setpoint — range control with value display
{"value": "tile", "config": {"position": "5", "icon": "material:thermostat", "label": "AC Temp", "valueDisplay": "value"}}

// Gate — command with state from separate sensor
{"value": "tile", "config": {"position": "security:1", "icon": "iconify:mdi:gate", "label": "Front Gate", "valueItem": "FrontGate_State", "action": "command", "commandValue": "ON", "invertValue": "true", "needsConfirmation": "true", "valueDisplay": "color"}}

// Scene activation — fixed command, no toggle
{"value": "tile", "config": {"position": "security:3", "icon": "iconify:mdi:exit-run", "label": "Away", "action": "command", "commandValue": "ON", "valueDisplay": "color"}}

// Temperature with rule trigger — display from primary, command elsewhere
{"value": "tile", "config": {"position": "3", "icon": "material:thermostat", "label": "Home Temp", "commandItem": "Climate_Trigger", "action": "command", "commandValue": "ON", "valueDisplay": "value"}}

// Navigation button to sub-page
{"value": "tile", "config": {"position": "main:6", "icon": "iconify:mdi:shield-home", "label": "Security", "action": "page:security"}}
```

### Via .items file (text configuration)

```
// Simple toggle with color display
Switch Bedroom_Light "Bedroom Light" <light> (gBedroom) ["Lightbulb"] { wearTile="tile" [position="1", icon="light", label="Bedroom", valueDisplay="color"] }

// Gate with confirmation
Switch FrontGate_Control "Front Gate" <gate> (gOutdoor) ["Control"] { wearTile="tile" [position="security:1", icon="iconify:mdi:gate", label="Front Gate", needsConfirmation="true", valueDisplay="color"] }

// Gate with valueItem and fixed command
Switch FrontGate_Control "Front Gate" <gate> { wearTile="tile" [position="security:1", icon="iconify:mdi:gate", label="Front Gate", valueItem="FrontGate_State", action="command", commandValue="ON", invertValue="true", needsConfirmation="true", valueDisplay="color"] }

// Navigation button (attached to a Group item)
Group gSecurity "Security" { wearTile="tile" [position="main:6", icon="iconify:mdi:shield-home", label="Security", action="page:security"] }
```

## Configuring Items for Complications

Watch face complications show a single item's state directly on the watch face (no app needed). Items are made available in the complication picker via the `wearTile` metadata.

### Marking an item as a complication source

**Option A — Complication only** (no tile button):
```
Number BDR_Temperature "Bedroom Temp" <temperature> { wearTile="complication" }
```

**Option B — Both tile and complication**:
```
Number Solar_Power "Solar" <solarplant> { wearTile="tile" [position="main:3", complication="true"] }
```

### Good complication candidates

- Temperature sensors (`Number:Temperature`)
- Energy/power values (`Number` with unit)
- Humidity sensors
- Battery levels
- Door/window contacts (`Contact`)
- Any read-only numeric value with a `stateDescription.pattern` for formatting

### What the complication shows

The watch formats the item state using `stateDescription.pattern` from the API:

| Item state | Pattern | Complication displays |
|-----------|---------|---------------------|
| `22.456` | `"%.1f °C"` | `22.5 °C` |
| `450` | `"%d W"` | `450 W` |
| `ON` | (none) | `ON` |
| `CLOSED` | (none) | `CLOSED` |

For `RANGED_VALUE` complications (progress arc), `stateDescription.minimum` and `maximum` define the arc range.

### How it works on the watch

1. User long-presses watch face → Edit → taps a complication slot
2. Selects "openHAB Item" from the picker list
3. A config screen shows all items marked `complication` in their `wearTile` metadata
4. User picks one → that item's state appears on the watch face
5. Updates every 15 minutes via WorkManager (push-style, battery-friendly)

## Which Items Work Best

### For Tiles

The tile supports several interaction modes depending on item type and configuration:

### Default behavior (action = null)

| Item Type | Tap Behavior | Display |
|-----------|-------------|---------|
| Switch | Toggles ON/OFF | State as text or color |
| Group (with Switch members) | Sends ON/OFF to group | State as text or color |
| Dimmer | Toggles ON/OFF | State as text or color |
| Color | Toggles ON/OFF | State as text or color |
| Number (with min/max stateDescription) | Opens rotary range control | Current value |
| Number:Temperature | Opens rotary range control | Current value |
| Contact | No action (display only) | OPEN/CLOSED as text or color |

### With action = "command"

Any item type can become a fixed-command button. Combined with `valueItem` and `commandItem`, this enables:

- **Gate buttons** — tap sends "ON" pulse to trigger switch, display shows Contact sensor state
- **Scene buttons** — tap always sends "ON" to activate, display shows scene state
- **Rule triggers** — tap sends a command to a trigger item, display shows a calculated value from another item
- **Rollershutter controls** — separate buttons with `commandValue: "UP"`, `"STOP"`, `"DOWN"`

### Good tile candidates

- Lights (quick toggle, clear visual state)
- Power switches (outlets, HVAC, audio)
- Gates and locks (with `needsConfirmation` and `valueItem` for sensor feedback)
- Scenes (fixed "ON" command)
- Temperature setpoints (rotary control)
- Any sensor with a paired trigger (Pattern B: display item + commandItem)

### Less suited for tiles

- Items requiring complex multi-step interaction (e.g., RGB color picker)
- Items with very long state strings (state text truncated to 6 characters on display; labels truncated to 8 characters)

## Verifying Configuration

Check which items are configured:

```bash
curl -s -u "user@email.com:password" \
  "https://myopenhab.org/rest/items?metadata=wearTile&fields=name,label,state,metadata" \
  | python3 -m json.tool
```

The watch app also shows configured items in the **Tile Items** screen (accessible from the main menu).

## Removing Items from Tile

Delete the metadata to remove an item from the watch tile:

```bash
curl -u "user@email.com:password" \
  -X DELETE "https://myopenhab.org/rest/items/Bedroom_Light/metadata/wearTile"
```

Or remove it via the Main UI metadata editor.

## Notes

- Changes take effect on the next tile refresh (up to 30 seconds, or swipe away and back)
- Item configuration is cached in memory after the first fetch; the cache is cleared on "Reload Items" or app restart. Item states are fetched fresh each time the tile becomes visible.
- Position values can be decimals (e.g., `1.5`) — they're sorted numerically
- If two items share the same position, order is undefined
- Maximum 7 item slots per page (excluding the back/mic button at the bottom)
- Icon sources support three formats: classic (`light`), Material (`material:thermostat`), Iconify (`iconify:mdi:gate`)
- Active state detection for `valueDisplay: "color"`: ON, OPEN, numeric > 0 = active (accent color); OFF, CLOSED, 0, NULL = inactive (grey)
- `valueItem` items are fetched individually by name — they don't need `wearTile` metadata
- SSE state tracking watches the effective display item (`valueItem` when set, primary item otherwise)
