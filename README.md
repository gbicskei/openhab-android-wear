# openHAB Wear OS App

A standalone Wear OS companion app for [openHAB](https://www.openhab.org/) smart home, with a phone companion for easy configuration. Control your home directly from your wrist — toggle items, adjust colors, control shutters, and issue voice commands — all without needing your phone nearby.

## Key Features

### Watch App
- **Concentric Tile Layout** — 1-7 items arranged in a responsive honeycomb pattern
- **Dedicated Control Screens** — Color picker (HSB + bezel brightness), roller shutter (UP/STOP/DOWN + bezel position), choice picker (commandOptions list), rotary control (range/dimmer)
- **Watch Face Complications** — Display item values on the watch face (4 types: SHORT_TEXT, LONG_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE)
- **Themes** — 5 color themes (amber, blue, green, purple, red) with radial glow
- **Voice Commands** — Natural language commands processed by openHAB's interpreter
- **Standalone Operation** — Connects directly to any openHAB server over WiFi/LTE (cloud relay, local, or VPN)
- **Real-time Updates** — SSE with automatic reconnection + polling fallback
- **Multi-page Navigation** — Sub-pages for organizing items (Security, Climate, etc.)
- **Disk Caching** — Warm start from cached config on process restart
- **stateDisplay Modes** — Color (active/inactive), Value (text), None (icon only)

### Phone Companion App
- **Tile Design Editor** — Visual editor with layout selector, watch preview, icon picker (MDI/Material/openHAB), position swap
- **Complication Editor** — Per-type configuration with pattern validation and metadata import
- **Theme Sync** — Select theme on phone, push to watch
- **Config Sync Detection** — Warns when watch is out of sync with server config
- **Connection Setup** — Main server (cloud) + Config server (local) with encrypted storage

## Target Platform

- **Min SDK:** 34 (Wear OS 5)
- **Target SDK:** 35 (Wear OS 6)
- **Tested on:** Samsung Galaxy Watch Ultra 2025

## Quick Start

### Watch (standalone)
1. Build and deploy: `./gradlew :watch:assembleRelease`
2. Open the app on the watch → configure server connection
3. Swipe to your new tile

### Phone Companion (recommended)
1. Install the phone app: `./gradlew :phone:assembleDebug`
2. Configure Main Server (e.g. myopenhab.org or your own URL) + Config Server (local openHAB URL)
3. Use Tile Design to visually configure your watch layout
4. Tap "Sync to Watch" to push credentials + trigger reload
5. Long-press tile on watch → pencil to change theme (or set via phone)

### Legacy: Manual item metadata
Items with `wearTile` metadata are supported as a fallback for complication discovery only. Tile configuration is done via the phone companion editor.

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System design, connectivity model, tech stack decisions |
| [Features](docs/features.md) | Detailed feature descriptions and UX flows |
| [Configuration Schema](docs/configuration-schema.md) | Server-side config format: wear:tile namespace, JSON schema, sync |
| [Complications](docs/complications.md) | Watch face complications setup and usage |
| [Icons](docs/icons/ICONS.md) | Custom icon system — design spec, theming, creating new icons |
| [Tile Pages](docs/tile-pages.md) | Multi-page tile navigation design (sub-pages, back button) |
| [Development](docs/development.md) | Build, deploy, debug, project structure |
| [Distribution](docs/distribution.md) | Signing, Play Store, testing tracks |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose for Wear OS (Material 3) |
| Tiles | Wear Tiles API + Material 3 |
| DI | Hilt |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Storage | DataStore Preferences |
| Images | Coil (SVG support) |
| Build | Kotlin DSL, Gradle Version Catalog |

## Project Structure

```
openhab-android-wear/
├── shared/                          # Shared models + sync constants
│   └── src/main/java/org/openhab/habdroid/wear/shared/
│       ├── model/ServerCredentials.kt
│       └── sync/SyncConstants.kt, SyncConfigPayload.kt
├── phone/                           # Phone companion app
│   └── src/main/java/org/openhab/habdroid/wear/phone/
│       ├── PhoneCompanionApp.kt        # @HiltAndroidApp
│       ├── data/                        # CredentialStore, ConnectionTester, LocalServerConfig
│       ├── di/                          # Hilt DI module
│       ├── sync/                        # PhoneDataLayerSender, WatchStatusReader, ListenerService
│       └── ui/
│           ├── home/                    # Home screen (nav cards, sync, status)
│           ├── setup/                   # Connection settings
│           ├── tiledesign/              # Tile editor + components + data layer
│           ├── complications/           # Complication editor
│           └── navigation/              # Nav routes + host
├── watch/                           # Wear OS watch app (standalone)
│   └── src/main/java/org/openhab/habdroid/wear/
│       ├── OpenHabWearApp.kt           # @HiltAndroidApp
│       ├── di/                          # Hilt DI
│       ├── data/
│       │   ├── api/                     # Retrofit API, Auth, SSE client
│       │   ├── icon/                    # Icon resolution + compositing
│       │   ├── model/                   # Item, TileItem, WearTileComponent, WearComplicationConfig
│       │   └── repository/             # Repository, ItemCache, TileConfigDiskCache, stores
│       ├── tile/                        # Tile service + action handling
│       ├── complication/                # Watch face complication service
│       ├── sync/                        # Data Layer listener + WatchStatusWriter
│       └── ui/
│           ├── control/                 # RotaryControl, ColorPicker, RollerShutter, ChoicePicker
│           ├── tile/                    # Tile config + theme picker
│           ├── voice/                   # Voice command input
│           └── setup/                   # Debug setup
└── docs/                            # Documentation
```

## License

TBD — to be aligned with openHAB project licensing (Eclipse Public License 2.0).
