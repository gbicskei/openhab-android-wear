# Watch Face Complications

Display openHAB item values directly on your watch face — temperature, switch state, energy readings, door status — without opening the app.

![Watch face with openHAB complications — Garage State and Daikin temperature](screenshots/complication.png)

## How It Works

The app registers a complication data source that the watch face system can pull data from. You select "openHAB Item" from the watch face complication picker, choose which item to display, and the value appears on the watch face.

Supported complication types:
- **SHORT_TEXT** — compact value with label (e.g., "22.5 °C" with title "Temp")
- **LONG_TEXT** — full label and value (e.g., "Daikin Indoor: 28.5 °C")
- **RANGED_VALUE** — arc/progress indicator for numeric values (dimmers, temperatures)

## Configuring Items for Complications

Items are made available for complications via the `wearTile` metadata in openHAB.

### Complication-only item

```
Number:Temperature  BDR_Daikin_IndoorTemp  "Bedroom Temp [%.1f %unit%]"  {channel="...", wearTile="complication"}
```

### Item on both tile and complication

```
Number:Power  Solar_Power  "Solar [%d W]"  {channel="...", wearTile="tile" [position="main:3", complication="true", icon="solarplant"]}
```

### Detection logic

| Scenario | Metadata | Result |
|----------|----------|--------|
| Tile only | `wearTile="tile" [position="main:1"]` | Shows on tile, not in complication picker |
| Complication only | `wearTile="complication"` | Shows in complication picker, not on tile |
| Both | `wearTile="tile" [position="main:3", complication="true"]` | Shows on both |

## Adding a Complication to Your Watch Face

1. Long-press the watch face → **Customize** or **Edit**
2. Tap a complication slot
3. Scroll to find **openHAB Item**
4. Select it — the app shows a list of complication-eligible items
5. Pick the item you want to display
6. The value appears on the watch face

### Samsung Galaxy Watch Note

Samsung's built-in watch faces (Analog Utility, Premium Analogue, etc.) may not show third-party complications. Use a watch face that accepts all data sources:
- Ultra Info Board (default on Galaxy Watch Ultra)
- Watch faces from Google Play
- Watch faces created with Samsung Watch Face Studio

## Tap Behavior

Tapping a configured complication opens a detail screen showing:
- The openHAB icon at the top
- The item label
- The current value in large text (freshly fetched from the server)

## Update Frequency

- Complications refresh every **5 minutes** (system-managed via `UPDATE_PERIOD_SECONDS`)
- A background WorkManager job triggers additional updates every 15 minutes
- Tapping the complication always fetches a fresh value
- The system may reduce update frequency in ambient mode or when the watch is off-wrist

## Supported Item Types

| Item Type | Display | Example |
|-----------|---------|---------|
| Number:Temperature | Formatted with unit | "28.5 °C" |
| Number:Power | Formatted with unit | "450 W" |
| Number:Energy | Formatted with unit | "3.2 kWh" |
| Dimmer | Percentage | "75%" |
| Switch | State text | "ON" / "OFF" |
| Contact | State text | "OPEN" / "CLOSED" |
| String | Raw value (truncated) | "Sunny" |

## Architecture

```
Watch Face (renders complication)
    ↑ ComplicationData (text, value, icon)
    │
OpenHabComplicationService
    │ reads item name from DataStore (per slot)
    │ fetches state from REST API
    ↓
OpenHabRepository → openHAB REST API
```

### Key components

| File | Purpose |
|------|---------|
| `OpenHabComplicationService.kt` | Data source service — responds to system requests |
| `ComplicationConfigActivity.kt` | Item picker shown when adding the complication |
| `ComplicationDetailActivity.kt` | Full-screen value display on tap |
| `ComplicationPreferenceStore.kt` | Stores slot → item mapping in DataStore |
| `ComplicationUpdateWorker.kt` | Periodic WorkManager refresh |

## Value Formatting

The complication uses the openHAB REST API's `transformedState` field when available. This contains the server-formatted value using the item's label pattern (e.g., `[%.1f %unit%]` → `"28.5 °C"`).

Fallback formatting (when `transformedState` is unavailable):
- Numeric values are formatted to 1 decimal place
- Unit symbols are appended based on item type (°C, W, kWh, hPa, etc.)
- Non-numeric states (ON/OFF/OPEN/CLOSED) are shown as-is

## Multiple Complications

You can add multiple openHAB complications to the same watch face, each showing a different item. Each slot stores its own item preference independently.
