# openHAB Wear OS App

A standalone Wear OS companion app for [openHAB](https://www.openhab.org/) smart home. Control your home directly from your wrist — toggle items, issue voice commands, and receive notifications — all without needing your phone nearby.

## Key Features

- **Concentric Tile Layout** — 1-7 items arranged in a responsive honeycomb pattern derived from screen size
- **Themes** — 5 color themes (amber, blue, green, purple, red) with radial glow, selectable via bezel rotation
- **Voice Commands** — Speak natural language commands processed by openHAB's interpreter
- **Push Notifications** — Receive openHAB Cloud notifications directly on the watch via FCM
- **Standalone Operation** — Connects directly to myopenhab.org over WiFi/LTE. No phone dependency
- **Real-time Updates** — SSE-based state changes while tile is visible
- **Multi-page Navigation** — Sub-pages for organizing items (e.g., Security, Climate)

## Target Platform

- **Min SDK:** 34 (Wear OS 5)
- **Target SDK:** 35 (Wear OS 6)
- **Tested on:** Samsung Galaxy Watch Ultra 2025

## Quick Start

1. Configure items in openHAB with `wearTile` metadata ([guide](docs/openhab-configuration.md))
2. Build and deploy: `./gradlew :app:installDebug` or `scripts/deploy-watch.sh`
3. Open the app on the watch → configure server connection
4. Swipe to your new tile
5. Long-press tile → pencil to change theme color

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System design, connectivity model, tech stack decisions |
| [Features](docs/features.md) | Detailed feature descriptions and UX flows |
| [openHAB Configuration](docs/openhab-configuration.md) | How to configure items for the watch tile |
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
| Push | Firebase Cloud Messaging |
| Sync | Wear Data Layer API (one-time credential sync) |
| Build | Kotlin DSL, Gradle Version Catalog |

## Project Structure

```
openhab-android-wear/
├── app/                             # Wear OS watch app (standalone)
│   └── src/main/java/org/openhab/habdroid/wear/
│       ├── OpenHabWearApp.kt           # Application entry point
│       ├── di/                          # Hilt dependency injection
│       ├── data/
│       │   ├── api/                     # Retrofit API service + auth
│       │   ├── model/                   # Item, ServerCredentials, TileItem
│       │   └── repository/             # CredentialStore, OpenHabRepository
│       ├── tile/                        # Wear OS Tile service
│       ├── notification/                # FCM push handling
│       ├── sync/                        # Data Layer credential sync (receiver)
│       └── ui/
│           ├── MainActivity.kt          # Launcher menu
│           ├── setup/                   # Onboarding / server config
│           ├── tile/                    # Tile configuration viewer
│           └── voice/                   # Voice command input
└── docs/                            # Project documentation
```

## License

TBD — to be aligned with openHAB project licensing (Eclipse Public License 2.0).
