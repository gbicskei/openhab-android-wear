# Development Guide

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK with API 35
- A Wear OS device or emulator (API 34+)

## Project Structure

See [README.md](../README.md#project-structure) for the current multi-module layout (`shared/`, `phone/`, `watch/`).

## Build

```bash
# Build watch (release)
./gradlew :watch:assembleRelease

# Build phone (debug)
./gradlew :phone:assembleDebug

# Build both
./gradlew :phone:assembleDebug :watch:assembleRelease

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test

# Run specific watch tests
./gradlew :watch:test --tests "org.openhab.habdroid.wear.data.model.ValueDisplayTest"
```

## Deploy

```bash
# Verify devices connected
adb devices -l

# Install watch app
adb -t <watch_transport_id> install -r watch/build/outputs/apk/release/watch-release.apk

# Install phone app
adb -s <phone_serial> install -r phone/build/outputs/apk/debug/phone-debug.apk
```

### Debug APK Size

Debug APK is ~77MB (unminified Compose, Hilt, Retrofit, Tiles libraries). Release with R8 reduces to ~5MB.

### Inject Credentials via ADB

```bash
adb -s <watch-serial> shell am start \
  -n org.openhab.habdroid.wear/.ui.setup.DebugSetupActivity \
  --es url "https://your-openhab-server.example.com" \
  --es user "your-username" \
  --es pass "your-password"
```

Writes credentials directly to DataStore. Available in debug builds.

### Launch Activities via ADB

```bash
# Main menu
adb -s <watch-serial> shell am start -n org.openhab.habdroid.wear/.ui.MainActivity

# Voice command
adb -s <watch-serial> shell am start -n org.openhab.habdroid.wear/.ui.voice.VoiceCommandActivity
```

## Watch Debugging (Wireless ADB)

### Connect

On the watch: Settings → Developer options → enable ADB debugging → enable Wireless debugging → Pair new device.

```bash
adb pair 192.168.1.x:PORT    # one-time pairing
adb connect 192.168.1.x:5555
```

### Known Issues

| Problem | Mitigation |
|---------|-----------|
| Screen off → connection drops | Keep watch on charger |
| LTE active → prefers cellular | Disable LTE during debugging |
| Bluetooth → WiFi instability | Disable BT during WiFi debug (breaks Data Layer sync) |
| Router AP isolation | Ensure devices on same subnet |

## Key Patterns

### Adding a new API endpoint

1. Add method to `OpenHabApiService.kt` (Retrofit interface)
2. Add repository method in `OpenHabRepository.kt`
3. Call from ViewModel

### Adding a new control screen

1. Create `ui/control/YourActivity.kt` (@AndroidEntryPoint, ComponentActivity + setContent)
2. Create `ui/control/YourViewModel.kt` (@HiltViewModel, SavedStateHandle for `item_name` extra)
3. Register in `AndroidManifest.xml`
4. Add routing case in `OpenHabTileService.buildItemClickable()`

### Modifying the tile layout

Edit `OpenHabTileService.buildItemGrid()` and `buildItemButton()`. The tile uses ProtoLayout (not Compose). Refer to [Wear Tiles API docs](https://developer.android.com/training/articles/wear-tiles).

## Debugging

### Logcat filter

```bash
adb -s <watch-serial> logcat -s "TileNav" "TilePos" "TileActionReceiver" "VoiceCommand" "WearDataLayer" "AuthInterceptor" "ComplicationService"
```

### Test API connectivity

```bash
# Same API call the watch makes
curl -s -u "user:pass" "https://your-server/rest/items?metadata=wearTile&fields=name,label,state,metadata"

# Voice command
curl -s -u "user:pass" \
  -X POST "https://your-server/rest/voice/interpreters" \
  -H "Content-Type: text/plain" \
  -H "Accept-Language: en" \
  -d "turn off bedroom light"
```

### Force tile refresh

```bash
adb -s <watch-serial> shell am broadcast -a com.google.android.clockwork.home.action.FORCE_UPDATE_TILES
```
