# Watch Tile Icon System

Icons for the openHAB Wear OS tile. Supports multiple icon sources, renders at 96x96px with configurable theme color, ring, and tinting — all composited on the watch.

## Icon Resolution

The watch resolves icons using this fallback chain:

1. **`wearTile` metadata `icon` field** — explicit override by the user
2. **Item's `category` field** — set via `<icon>` in the item definition

Both fields support the openHAB icon source prefix syntax:

| Value | Source | Fetch URL |
|-------|--------|-----------|
| `"light"` | openHAB classic (default) | `{serverUrl}/icon/light?format=svg&state={state}` |
| `"oh:light"` | openHAB classic (explicit) | `{serverUrl}/icon/light?format=svg&state={state}` |
| `"iconify:mdi:lightbulb"` | Iconify API | `https://api.iconify.design/mdi/lightbulb.svg` |
| `"material:lightbulb"` | Google Material Symbols | `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/lightbulb/default/48px.svg` |

If no icon is resolved from either field, the app falls back to a generic "none" icon.

## Rendering Pipeline

```
1. Resolve icon reference (metadata → category → fallback)
2. Parse source prefix → determine fetch URL
3. Fetch raw bytes (cached in LRU memory cache, keyed by icon ref)
4. Detect format (SVG or PNG from magic bytes)
5. Composite final 96x96 ARGB_8888 bitmap:
   a. Draw ring (theme accent color, full opacity ON / 0.3 OFF)
   b. Render icon graphic centered (tinted for SVG, alpha for PNG)
6. Provide to ProtoLayout as inline image resource (displayed at 48–64dp, downscaled stays sharp)
7. Label rendered separately as ProtoLayout Text element below image
```

### SVG Icons (preferred)
- Rendered via `androidsvg` library
- Tinted using `PorterDuff.Mode.SRC_IN` color filter:
  - ON state: theme accent color, full opacity
  - OFF state: theme accent color, 0.6 opacity (dimmed, same hue as ring)

### PNG Icons (legacy openHAB classic)
- Decoded via `BitmapFactory`
- Scaled to fit within ring area (36x36)
- ON state: full alpha
- OFF state: 0.4 alpha
- Original colors preserved (no tint — PNGs may be multi-colored)

### Ring
- Drawn as a circle stroke on the composited bitmap
- Color: user's theme accent color
- ON: stroke opacity 1.0, width 2px
- OFF: stroke opacity 0.3, width 2px

## Caching Strategy

- **Raw bytes cache**: LRU in-memory, keyed by `iconRef` (source + name)
  - Not affected by theme or state changes
  - Survives tile refreshes until memory pressure
- **Composited bitmaps**: NOT cached — regenerated each tile refresh
  - Compositing 6 icons takes <10ms
  - Avoids cache invalidation complexity on theme/state changes

## Theme Color

The user can select a theme color in app settings. This affects:
- Ring color
- SVG icon tint color
- Progress indicators and accent elements in Compose screens

Default: Amber (`#FFB300`). Options: amber, red, blue, green, purple.

Stored in DataStore preferences. Read by tile service on each render.

## Icon Sources

### openHAB Classic (`oh:` or no prefix)

- Served from the openHAB server at `/icon/{name}?format=svg&state={state}`
- Server handles dynamic icon selection (e.g., `light-on.svg` vs `light-off.svg`)
- Custom icons placed in `/conf/icons/classic/` override built-in ones
- May return SVG or PNG depending on what's available

**Naming rules for custom icons:**
- Lowercase letters, numbers, hyphens, underscores only
- State variants: `{name}-on.svg`, `{name}-off.svg`, `{name}.svg` (default)
- Number items: `{name}-0.svg`, `{name}-75.svg` (matches equal or next-lowest state)

### Iconify (`iconify:`)

- Fetched from `https://api.iconify.design/{set}/{name}.svg`
- 150,000+ icons from 100+ sets
- Popular sets: `mdi` (Material Design Icons), `lucide`, `tabler`, `fa6-solid`
- Always returns SVG
- Requires internet (watch has WiFi/LTE)
- Browse: https://icon-sets.iconify.design/

**Examples:**
```
iconify:mdi:lightbulb
iconify:mdi:thermometer
iconify:mdi:gate
iconify:mdi:lock
iconify:lucide:timer
iconify:tabler:fan
```

### Google Material Symbols (`material:`)

- Fetched from Google Fonts static CDN
- Outlined style, 48px optical size
- Browse: https://fonts.google.com/icons

**Examples:**
```
material:lightbulb
material:lock
material:thermostat
material:gate
```

## Custom Icons for openHAB Server

### Creating Custom Icons

1. Design a monochrome SVG (single color strokes, transparent background)
2. Keep it simple — recognizable at 48x48px
3. Save to `/home/openhab5/conf/icons/classic/{name}.svg`
4. Optional: create state variants (`{name}-on.svg`, `{name}-off.svg`)
5. openHAB picks up changes immediately

### Prerequisites for Good Watch Rendering

- **Monochrome recommended** — allows uniform tinting by the app
- **SVG preferred** — supports tinting; PNG falls back to alpha-only
- **Simple geometry** — legible at tiny size
- **No embedded fonts** — androidsvg has limited font support

## Demo Page

Open `docs/icons/demo.html` in a browser to preview custom icons with:
- Theme color switching (amber, red, blue, green, purple)
- ON/OFF state comparison
- Simulated watch tile grid layout

## Metadata Examples

```json
// Use Iconify MDI icon
{"value": "tile", "config": {"position": "1", "icon": "iconify:mdi:lightbulb", "label": "Bedroom", "valueDisplay": "color"}}

// Use openHAB classic icon (explicit)
{"value": "tile", "config": {"position": "2", "icon": "oh:light", "label": "Wardrobe", "valueDisplay": "color"}}

// No icon override — uses item's category field
{"value": "tile", "config": {"position": "3", "label": "WDR Delay", "valueDisplay": "value"}}

// Use Material icon
{"value": "tile", "config": {"position": "4", "icon": "material:lock", "label": "Door", "needsConfirmation": "true"}}
```

## .items file syntax

```
Switch BDR_MainLight "Main Light" <light> (BDR_Light) ["Switch"] { wearTile="tile" [position="1", icon="iconify:mdi:lightbulb", label="Bedroom", valueDisplay="color"] }
```

If `icon` is omitted, the item's category (`<light>`) is used as `oh:light`.
