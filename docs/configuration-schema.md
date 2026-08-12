# Configuration Schema

Tile and complication configuration is stored server-side in openHAB's JsonDB as UI components under the `wear:tile` namespace (or a user-scoped namespace for multi-user setups).

## Storage

- **REST endpoint:** `GET /rest/ui/components/{namespace}`
- **Default namespace:** `wear:tile`
- **User-scoped namespace:** `wear:tile:{userKey}` (e.g. `wear:tile:joe`)
- **JsonDB location:** `{userdata}/jsondb/uicomponents_wear%3Atile.json` (default) or `uicomponents_wear_tile_{userKey}.json` (user-scoped)
- **Managed by:** Phone companion app (creates/updates/deletes via REST PUT/DELETE)
- **Read by:** Watch app (read-only, fetches on cold load)

The phone companion requires access to a server that exposes the REST API directly (the "Config Server" in [connection settings](connection.md)). The watch can read from any server (including cloud relay).

## Multi-User Configuration

Multiple users can share the same openHAB instance with separate tile/complication layouts by setting a **User Key** in the phone companion app's connection settings.

### How it works

- Each user sets a unique key (e.g. `joe`, `anna`) on their phone
- The key determines the REST namespace: `wear:tile:joe`, `wear:tile:anna`
- Each user's tile pages and complications are stored independently on the server
- The key is synced to the paired watch via Data Layer, so the watch reads from the correct namespace
- If no key is set, the default shared namespace `wear:tile` is used (backward compatible)

### User Key rules

- Optional — leave blank for single-user setups
- Allowed characters: `[a-z0-9_-]` (lowercase alphanumeric, underscore, hyphen)
- Set once per phone/watch pair in Connection settings
- Synced to the watch automatically on "Sync to Watch"

### Example

Two family members sharing one openHAB server:

| User | User Key | Namespace | JsonDB file |
|------|----------|-----------|-------------|
| Joe | `joe` | `wear:tile:joe` | `uicomponents_wear_tile_joe.json` |
| Anna | `anna` | `wear:tile:anna` | `uicomponents_wear_tile_anna.json` |
| (shared) | _(empty)_ | `wear:tile` | `uicomponents_wear%3Atile.json` |

## Document Types

| Component type | UID | Purpose |
|---------------|-----|---------|
| `wear:tile-page` | `main`, `security`, `scenes`, etc. | Defines one tile page with layout + slots |
| `wear:complication-list` | `complications` | Defines all complication configurations |

## Tile Page Schema

Each page is a document at `/rest/ui/components/{namespace}/{uid}`:

```json
{
  "uid": "main",
  "component": "wear:tile-page",
  "config": {
    "label": "Main",
    "layout": 4.0,
    "configVersion": 3.0
  },
  "slots": {
    "default": [
      {
        "component": "wear:tile-slot",
        "config": {
          "position": 1.0,
          "item": "BDR_Light",
          "icon": "light",
          "label": "Bedroom",
          "stateDisplay": "color",
          "action": "toggle",
          "actionCommand": null,
          "actionItem": null,
          "stateItem": null,
          "invertState": false,
          "actionConfirmation": false,
          "aggregateState": false
        }
      }
    ]
  }
}
```

### Page config fields

| Field | Type | Description |
|-------|------|-------------|
| `label` | String | Page display name (shown in the phone editor tabs and navigation button labels) |
| `layout` | Double | Number of button slots (1-7). Determines concentric layout geometry. |
| `configVersion` | Double | Integer counter, incremented on each phone editor save. Used for sync detection. |

### Slot config fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `position` | Double | 1.0 | Slot position (1-7). Determines button placement in the layout. |
| `item` | String? | null | openHAB item name. The primary item for this slot. |
| `icon` | String? | null | Icon reference override. Formats: `"light"` (openHAB), `"iconify:mdi:gate"`, `"material:thermostat"`. Falls back to item category if null. |
| `label` | String? | null | Label override. Falls back to item label if null. |
| `stateDisplay` | String? | null | Display mode: `"value"` (state text), `"color"` (active/inactive highlight), `"none"` (no indicator). Null = `"value"`. |
| `action` | String? | null | Tap action: `"toggle"` (ON/OFF), `"command"` (fixed command), `"page:{name}"` (navigate). Null = auto-detect from item type. |
| `actionCommand` | String? | null | Fixed command string sent on tap. Used when action = `"command"`. |
| `actionItem` | String? | null | Item that receives commands instead of the primary item. |
| `stateItem` | String? | null | Item whose state is displayed instead of the primary item. |
| `invertState` | Boolean | false | Invert active/inactive display interpretation. |
| `actionConfirmation` | Boolean | false | Show confirmation dialog before executing tap action. |
| `aggregateState` | Boolean | false | Navigation buttons only: show as active if any item on the target page is active. |
| `doubleTapItem` | String? | null | Item name for the double-tap secondary action. If set, enables double-tap detection on this button. |
| `doubleTapAction` | String? | null | Double-tap action: `"toggle"` (force toggle), `"command"` (fixed command), `"auto"` or null (auto-detect from item type). |
| `doubleTapCommand` | String? | null | Command to send on double-tap when `doubleTapAction = "command"`. |
| `doubleTapConfirmation` | Boolean | false | Show confirmation dialog before executing double-tap action. |
| `doubleTapStateDisplay` | String? | null | State display mode for the double-tap item value shown on the button: `"value"`, `"color"`, or `"none"`. |

### Tap action routing (watch-side)

Based on slot config + item type, the watch determines the tap behavior:

| Condition | Action |
|-----------|--------|
| action = `"page:{name}"` | Navigate to sub-page |
| item.type = `"Contact"` | No action (display only) |
| item has stateDescription.minimum + maximum | Open RotaryControlActivity |
| item.type = `"Color"` | Open ColorPickerActivity |
| item.type = `"Rollershutter"` | Open RollerShutterActivity |
| item has commandDescription.commandOptions | Open ChoicePickerActivity |
| action = `"command"` | Send actionCommand to target item |
| default | Toggle ON/OFF |

### Double-tap action routing

When `doubleTapItem` is configured, the button uses a `QuickActionActivity` to detect single vs double tap (350ms window):

| Tap | Action |
|-----|--------|
| Single tap | Executes primary action (same as above table) |
| Double tap | Executes secondary action on `doubleTapItem` using auto-detection (same routing logic as primary: range → rotary, color → picker, etc.) |

If `doubleTapAction` is set to `"toggle"` or `"command"`, the routing is overridden accordingly.

### Double-tap example

A button that toggles the AC power on single tap and opens the temperature setpoint control on double-tap, with the current setpoint value displayed on the button:

```json
{
  "component": "wear:tile-slot",
  "config": {
    "position": 1.0,
    "item": "GA_BDR_DaikinPower",
    "icon": "iconify:mdi:air-conditioner",
    "label": "BDR",
    "action": "toggle",
    "stateDisplay": "color",
    "doubleTapItem": "AC_BDR_Daikin_Setpoint",
    "doubleTapStateDisplay": "value"
  }
}
```

## Complication List Schema

Single document at `/rest/ui/components/{namespace}/complications`:

```json
{
  "uid": "complications",
  "component": "wear:complication-list",
  "config": {},
  "slots": {
    "default": [
      {
        "component": "wear:complication-slot",
        "config": {
          "item": "DailyEnergy",
          "label": "Energy",
          "icon": "iconify:mdi:lightning-bolt",
          "shortText": {
            "title": "Energy",
            "text": "%.0f kW"
          },
          "longText": {
            "title": "Daily Energy",
            "text": "%.1f kWh today"
          },
          "rangedValue": {
            "title": "Energy",
            "text": "%.0f",
            "min": 0,
            "max": 50
          },
          "monochromaticImage": {
            "monochromaticImage": "iconify:mdi:lightning-bolt"
          }
        }
      }
    ]
  }
}
```

### Complication slot fields

| Field | Type | Description |
|-------|------|-------------|
| `item` | String | openHAB item name whose state is shown on the watch face. |
| `label` | String | Display label for the complication picker. |
| `icon` | String | Icon reference for the complication picker and MONOCHROMATIC_IMAGE type. |
| `shortText` | Object | Config for SHORT_TEXT complication type (max 7 chars for text + title). |
| `longText` | Object | Config for LONG_TEXT complication type. |
| `rangedValue` | Object | Config for RANGED_VALUE complication type (progress arc). |
| `monochromaticImage` | Object | Config for MONOCHROMATIC_IMAGE complication type (icon-only). |

### Per-type config objects

**shortText / longText:**
| Field | Type | Description |
|-------|------|-------------|
| `title` | String | Static title label (e.g. "Energy") |
| `text` | String | Format pattern for the value. Uses Java String.format syntax (e.g. `"%.0f kW"`). |

**rangedValue:**
| Field | Type | Description |
|-------|------|-------------|
| `title` | String | Static title label |
| `text` | String | Format pattern for the value |
| `min` | Double? | Override minimum for the arc range. Falls back to item stateDescription.minimum. |
| `max` | Double? | Override maximum for the arc range. Falls back to item stateDescription.maximum. |

**monochromaticImage:**
| Field | Type | Description |
|-------|------|-------------|
| `monochromaticImage` | String | Default icon reference |
| `activeIcon` | String | Icon shown when item is active (optional) |
| `inactiveIcon` | String | Icon shown when item is inactive (optional) |

## Config Sync

The `configVersion` field in the `main` page config is the sync mechanism:

1. Phone editor increments `configVersion` on every save
2. Watch reads it after cold load, writes to DataClient at path `/openhab/status` (key: `configTimestamp`)
3. Phone reads DataClient + fetches server version, compares integers
4. Mismatch → "out of sync" indicator on phone home screen

## DataClient Status (watch → phone)

```
Path: /openhab/status
Keys:
  configTimestamp: "3"   (configVersion from main page, as string)
  theme: "AMBER"         (current watch theme name)
```

## Legacy: wearTile Metadata

The watch still supports `wearTile` item metadata as a fallback for complication item discovery when the `wear:complication-list` document doesn't exist. Items with `value = "complication"` or `config.complication = "true"` appear in the complication picker.

This path is not used for tile configuration.
