# Development Guide

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK with API 35
- A Wear OS device or emulator (API 34+)
- `google-services.json` from Firebase Console (for FCM)

## Project Structure

```
openhab-android-wear/
├── build.gradle.kts              # Root plugins declaration
├── settings.gradle.kts           # Module include + repository config
├── gradle.properties             # JVM args, AndroidX flags
├── gradle/
│   ├── libs.versions.toml        # Version catalog (all dependencies)
│   └── wrapper/                  # Gradle wrapper
├── app/
│   ├── build.gradle.kts          # App module config (minSdk 34, deps)
│   ├── proguard-rules.pro        # R8 rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                   # Strings, colors, drawables
│       └── java/org/openhab/habdroid/wear/
│           ├── OpenHabWearApp.kt              # @HiltAndroidApp
│           ├── di/
│           │   └── AppModule.kt               # Hilt provides: DataStore, OkHttp, Retrofit, API
│           ├── data/
│           │   ├── api/
│           │   │   ├── OpenHabApiService.kt   # Retrofit interface
│           │   │   ├── AuthInterceptor.kt     # URL rewriting + Basic Auth
│           │   │   └── TileStateEventSource.kt # SSE client for real-time state updates
│           │   ├── icon/
│           │   │   ├── IconCompositor.kt      # Renders icons with ring, glow, tint
│           │   │   └── IconResolver.kt        # Resolves icon source + fetches bytes
│           │   ├── model/
│           │   │   ├── Item.kt                # @Serializable item model
│           │   │   ├── ServerCredentials.kt   # Connection config
│           │   │   └── TileItem.kt            # Positioned item for tile
│           │   └── repository/
│           │       ├── CredentialStore.kt     # DataStore persistence
│           │       ├── ItemCache.kt           # In-memory tile item + state cache
│           │       ├── OpenHabRepository.kt   # Business logic layer
│           │       ├── ThemeStore.kt          # Theme color preference
│           │       └── TilePreferenceStore.kt # Tile-specific preferences
│           ├── tile/
│           │   ├── OpenHabTileService.kt      # System tile provider
│           │   ├── TileActionReceiver.kt      # Handles tile taps
│           │   └── PageNavigationActivity.kt  # Page navigation with optional confirmation
│           ├── notification/
│           │   ├── FcmListenerService.kt      # FCM message receiver
│           │   └── FcmRegistrationManager.kt  # Token registration worker
│           ├── sync/
│           │   └── WearDataLayerListenerService.kt  # Phone → watch config sync
│           └── ui/
│               ├── MainActivity.kt            # Launcher menu
│               ├── MainViewModel.kt
│               ├── GridPreviewActivity.kt     # Debug: layout grid preview
│               ├── setup/
│               │   ├── SetupActivity.kt       # Onboarding flow
│               │   ├── SetupViewModel.kt
│               │   └── DebugSetupActivity.kt  # Debug: inject credentials via ADB
│               ├── control/
│               │   ├── RotaryControlActivity.kt   # Bezel-driven range control
│               │   └── RotaryControlViewModel.kt
│               ├── tile/
│               │   ├── TileConfigActivity.kt  # View configured items
│               │   ├── TileConfigViewModel.kt
│               │   ├── ItemSelectorScreen.kt  # Item selection Compose screen
│               │   └── ThemePickerActivity.kt # Theme color selector (bezel rotation)
│               └── voice/
│                   ├── VoiceCommandActivity.kt  # Speech recognition
│                   └── VoiceCommandViewModel.kt
└── docs/                          # This documentation
```

## Build

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing config)
./gradlew :app:assembleRelease

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test
```

## Firebase Setup

The app requires a `google-services.json` file for FCM:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a project (or use existing)
3. Add an Android app with package name: `org.openhab.habdroid.wear`
4. Download `google-services.json`
5. Place it in `app/google-services.json`

**Note:** The file is gitignored. Each developer needs their own, or use a shared Firebase project.

To build without FCM temporarily, comment out the `google-services` plugin in `app/build.gradle.kts` and the Firebase dependencies.

## Deploying to Watch

### Device Info (Current Dev Watch)

- **Device:** Samsung Galaxy Watch Ultra 2025
- **Serial:** `RFAXA2EE8ZZ` (shows as `adb-RFAXA2EE8ZZ-mRPCxM._adb-tls-connect._tcp`)
- **Wear OS:** 6 (One UI 8 Watch)
- **Chip:** Exynos W1000
- **Connectivity:** WiFi + LTE (disable LTE for stable debugging)

### Connect via Wireless Debugging

On the watch:
1. Settings → Developer options → enable **ADB debugging**
2. Enable **Wireless debugging**
3. Tap **Pair new device** — note the IP:port and pairing code

On your machine:
```bash
# Pair (one-time)
adb pair 192.168.1.x:PORT
# Enter the pairing code when prompted

# Connect
adb connect 192.168.1.x:5555
```

### Connection Stability Issues

The watch WiFi debugging connection **drops frequently**. Known causes and mitigations:

| Problem | Solution |
|---------|----------|
| Screen turns off → connection drops | Keep watch on charger (stays awake) |
| LTE active → watch prefers cellular | Disable LTE in Settings → Connections → Mobile networks |
| Bluetooth active → WiFi instability | Disable Bluetooth during WiFi debugging sessions |
| Router AP isolation | Check router settings, ensure devices can see each other |
| Multiple ADB entries after reconnect | Normal — use whichever serial works, ignore duplicates |

**Important trade-off:** Bluetooth must be ON for Data Layer API sync (phone→watch credential transfer), but turning it OFF stabilizes WiFi debugging. Choose one at a time.

### Deploy

```bash
# Verify watch is connected
adb devices

# Install watch app to watch
adb -s adb-RFAXA2EE8ZZ-mRPCxM._adb-tls-connect._tcp install -r app/build/outputs/apk/debug/app-debug.apk

# If multiple watch serial entries appear after reconnect, try each one:
adb -s "adb-RFAXA2EE8ZZ-mRPCxM (2)._adb-tls-connect._tcp" install -r ...
```

### Debug APK Size

The debug APK is ~77MB due to unminified dependencies (Compose, Hilt, Retrofit, Firebase, Tiles libraries). This makes WiFi deploys take 15-40 seconds. A release build with R8 shrinking reduces this to ~10-15MB.

### Inject Credentials via ADB (bypasses manual entry)

Since the watch manual setup UI doesn't have RemoteInput yet, use this debug shortcut:

```bash
adb -s <watch-serial> shell am start \
  -n org.openhab.habdroid.wear/.ui.setup.DebugSetupActivity \
  --es url "https://myopenhab.org" \
  --es user "your@email.com" \
  --es pass "yourpassword"
```

This writes credentials directly to DataStore. The `DebugSetupActivity` is exported and available in debug builds.

### Launch App via ADB

```bash
# Main menu
adb -s <watch-serial> shell am start -n org.openhab.habdroid.wear/.ui.MainActivity

# Setup screen
adb -s <watch-serial> shell am start -n org.openhab.habdroid.wear/.ui.setup.SetupActivity

# Voice command
adb -s <watch-serial> shell am start -n org.openhab.habdroid.wear/.ui.voice.VoiceCommandActivity
```

## Emulator (alternative)

```bash
# Create a Wear OS AVD via Android Studio:
# Tools → Device Manager → Create Device → Wear OS → API 34+

# Or via command line:
sdkmanager "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n WearOS5 -k "system-images;android-34;google_apis;x86_64" -d "wearos_large_round"
emulator -avd WearOS5
```

**Limitations:** Emulator doesn't have real speech recognition, LTE, or Bluetooth. Use a real device for testing voice commands and Data Layer sync.

## openHAB Server Access

The home openHAB server is the backend that the watch communicates with via the myopenhab.org cloud relay.

### Connection

```bash
# SSH into the server
ssh nas
```

### Server Details

| Property | Value |
|----------|-------|
| Version | openHAB 5.1.4 |
| Deployment | Docker container (`openhab5`) |
| HTTP port | 9999 |
| HTTPS port | 9444 |
| Config path | `/home/openhab5/conf/` |
| Userdata path | `/home/openhab5/userdata/` |
| Logs | `/home/openhab5/userdata/logs/` |
| Timezone | Europe/Budapest |
| Cloud relay | myopenhab.org (default, remote mode) |

### Useful Commands

```bash
# View live openHAB logs
ssh nas tail -f /home/openhab5/userdata/logs/openhab.log

# Check container status
ssh nas docker ps --filter name=openhab5

# Restart openHAB
ssh nas docker restart openhab5

# Test REST API locally on the server
ssh nas curl -s http://localhost:9999/rest/items?metadata=wearTile

# Test specific item state
ssh nas curl -s http://localhost:9999/rest/items/BDR_Light
```

### Configuration Repository

The server configuration is version-controlled at `/home/gbicskei/Projects/openHAB/remote5/conf/`. This is a mount of the live server's `/home/openhab5/conf/` directory. Key directories:

```
remote5/conf/
├── automation/js/    # JavaScript automation rules (14 rule files)
├── items/            # Item definitions (.items files)
├── things/           # Thing/binding definitions
├── services/         # Add-on and service configuration
├── persistence/      # Persistence strategy config
└── transform/        # Transformation maps
```

### Installed Bindings

Domintell (home bus), Shelly, Hue, Daikin, Chromecast, Nuki, Samsung TV, Yamaha Receiver, MQTT, ipcamera, OpenWeatherMap, GPS Tracker, Harmony Hub, iCalendar.

### Items Relevant to Watch Tile

These items are configured with `wearTile` metadata for display on the watch:

| Position | Item | Label | Type |
|----------|------|-------|------|
| 1 | `BDR_Light` | Bedroom Light | Group |
| 2 | `LVR_CouchLight` | Couch Light | Group:Switch |
| 3 | `SAC_BalconyLight` | Balcony Light | Switch |
| 4 | `AC_Boiler_Control` | Boiler Control | Switch |
| 5 | `DEC_DecorationEnable` | Decoration | Switch |
| 6 | `AudioPower` | Audio Power | Switch |

## Key Patterns

### Adding a new API endpoint

1. Add the method to `OpenHabApiService.kt` (Retrofit interface)
2. Add a repository method in `OpenHabRepository.kt`
3. Call from ViewModel

### Adding a new screen

1. Create `ui/yourfeature/YourActivity.kt` (ComponentActivity + Compose)
2. Create `ui/yourfeature/YourViewModel.kt` (@HiltViewModel)
3. Register in `AndroidManifest.xml`
4. Add navigation from `MainActivity`

### Modifying the tile layout

Edit `OpenHabTileService.buildItemGrid()` and `buildItemButton()`. The tile uses ProtoLayout (not Compose) — it's a different layout system. Refer to [Wear Tiles documentation](https://developer.android.com/training/articles/wear-tiles).

## Debugging

### Logcat filter

```bash
# Watch logcat (filter to app tags)
adb -s <watch-serial> logcat -s "OpenHabTileService" "TileActionReceiver" "VoiceCommandActivity" "FcmListenerService" "WearDataLayerListener" "AuthInterceptor" "DebugSetup"
```

### Test API connectivity from watch

The app logs all HTTP requests in debug builds (OkHttp logging interceptor at BODY level). Check logcat for request/response details.

### Verify items via command line

```bash
# Test the same API call the watch makes
curl -s -u "your@email.com:password" "https://myopenhab.org/rest/items?metadata=wearTile&fields=name,label,state,metadata"

# Test voice command
curl -s -u "your@email.com:password" \
  -X POST "https://myopenhab.org/rest/voice/interpreters" \
  -H "Content-Type: text/plain" \
  -H "Accept-Language: en" \
  -d "turn off bedroom light"
```

### Force tile refresh

```bash
adb -s <watch-serial> shell am broadcast -a com.google.android.clockwork.home.action.FORCE_UPDATE_TILES
```

### Test FCM notification

Use the Firebase Console → Cloud Messaging → Send test message, targeting the watch's FCM token (logged at startup).

### Check installed package on watch

```bash
adb -s <watch-serial> shell pm list packages | grep openhab
# Expected: package:org.openhab.habdroid.wear
```
