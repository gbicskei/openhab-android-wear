# Version Sync & Update Enforcement

## Overview

The phone companion and watch app share a common `versionName` (e.g., `1.3.0`). When the phone is updated via Play Store but the watch still runs an older version, the phone **blocks sync** to prevent sending incompatible data to the watch. The phone also forces itself to stay current by triggering a Play Core IMMEDIATE in-app update when a newer version is available on Play Store.

Development builds are exempt from all version blocking.

## How It Works

```
┌─────────────────┐     PATH_VERSION_REQUEST     ┌─────────────────┐
│   Phone App     │ ──────────────────────────▶  │   Watch App     │
│   (v1.4.0)      │                              │   (v1.3.0)      │
│                  │  ◀────────────────────────── │                 │
│                  │     PATH_VERSION_RESPONSE    │                 │
│                  │        "1.3.0"               │                 │
│                  │                              │                 │
│  VersionCompat:  │                              │                 │
│  "1.4.0" ≠ "1.3.0"                             │                 │
│  → block sync    │                              │                 │
│  → show banner   │                              │                 │
└─────────────────┘                              └─────────────────┘
```

### Step-by-step flow

1. **Phone app opens** → Play Core checks for phone update → if available, shows IMMEDIATE blocking update flow (user must update before proceeding).
2. **Phone detects watch** (via `watchConnectionState()` polling) → sends `PATH_VERSION_REQUEST` to watch.
3. **Watch receives request** → replies with `BuildConfig.VERSION_NAME` on `PATH_VERSION_RESPONSE`.
4. **Phone receives response** → `WatchVersionHolder` updates → `SetupViewModel` compares versions using `VersionCompat.shouldBlockSync()`.
5. **If versions differ** (and neither is a `.dev` build):
   - `watchVersionMismatch = true` in UI state
   - `canSendToWatch` becomes `false` (sync button disabled)
   - A warning banner appears: "Watch app outdated — sync paused"
6. **When watch gets updated** from Play Store → next version check returns matching version → sync resumes automatically.

## Version Naming

| Build type | versionName | Example |
|-----------|-------------|---------|
| Release (Play Store) | `X.Y.Z` | `1.3.0` |
| Debug (local) | `X.Y.Z.dev` | `1.3.0.dev` |

Both modules read `appVersionName` from `gradle.properties`. The `.dev` suffix is appended automatically for debug builds via `versionNameSuffix = ".dev"` in each module's `build.gradle.kts`.

### Version codes

Phone and watch have **separate `versionCode` values** (required by Play Store for multi-APK delivery):

```properties
# gradle.properties
appVersionName=1.3.0
appVersionCodePhone=54
appVersionCodeWatch=53
```

The `versionCode` is irrelevant for compatibility checking — only `versionName` is compared.

## VersionCompat Logic

Located in `shared/.../sync/VersionCompat.kt`:

```kotlin
object VersionCompat {
    fun shouldBlockSync(phoneVersion: String, watchVersion: String): Boolean {
        // Never block if either side is a dev build
        if (isDevBuild(phoneVersion) || isDevBuild(watchVersion)) return false
        // Block only when production versions differ
        return phoneVersion != watchVersion
    }

    fun isDevBuild(versionName: String): Boolean =
        versionName.endsWith(".dev")
}
```

**Key rule:** If either phone or watch reports a `.dev` version, sync is never blocked. This ensures local development is never interrupted by version mismatches.

## Play Core In-App Update (Phone)

Located in `phone/.../ui/MainActivity.kt`:

- Uses `AppUpdateManager` with `AppUpdateType.IMMEDIATE` (full-screen blocking).
- Checked on every `onResume()`.
- Skipped entirely for `.dev` builds (`VersionCompat.isDevBuild()`).
- Also resumes interrupted updates (`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`).

This ensures the phone app is always at the latest version before it attempts to sync with the watch.

## Message Paths

Defined in `shared/.../sync/SyncConstants.kt`:

| Constant | Path | Direction | Purpose |
|----------|------|-----------|---------|
| `PATH_VERSION_REQUEST` | `/openhab/version-request` | Phone → Watch | Asks watch for its version |
| `PATH_VERSION_RESPONSE` | `/openhab/version-response` | Watch → Phone | Watch replies with versionName |

## Component Responsibilities

| Component | Module | Role |
|-----------|--------|------|
| `VersionCompat` | shared | Pure logic: should sync be blocked? |
| `PhoneDataLayerSender.requestWatchVersion()` | phone | Sends version request to watch |
| `WatchVersionHolder` | phone | Singleton StateFlow bridging listener → ViewModel |
| `PhoneWearListenerService` | phone | Receives version response, updates holder |
| `SetupViewModel` | phone | Observes holder, sets `watchVersionMismatch` state |
| `HomeScreen` / `WatchOutdatedBanner` | phone | Displays warning banner |
| `WearDataLayerListenerService` | watch | Receives version request, replies with version |

## UI Behavior

### Phone Home Screen

When `watchVersionMismatch == true`:

- An error-colored banner appears below the watch status chip:
  - Icon: Warning
  - Title: "Watch app outdated — sync paused"
  - Detail: "Phone: v1.4.0 · Watch: v1.3.0"
  - Help: "Update the watch app from Play Store"
- The "Sync to Watch" button is disabled (grayed out).

### What the user should do

1. Open Play Store on the watch (or navigate to the app's listing on the phone Play Store).
2. Update the watch app.
3. Return to the phone companion — the banner disappears and sync resumes.

## Development Workflow

During development, both phone and watch produce `1.3.0.dev` as their version name. The version check passes through without blocking, regardless of whether the actual code differs. This means:

- You can install mismatched debug builds freely.
- The Play Core in-app update check never fires for debug builds.
- To test the blocking behavior locally, temporarily change one module's `versionNameSuffix` to `""` (simulating a release build).

## Releasing a New Version

1. Bump `appVersionName` in `gradle.properties` (e.g., `1.3.0` → `1.4.0`).
2. Bump `appVersionCodePhone` and `appVersionCodeWatch` (each must increment independently).
3. Build release AABs for both modules.
4. Upload both to Play Store in the same release.
5. Phone users get the update first (faster Play Store delivery to phones).
6. Phone detects watch is behind → blocks sync → user sees banner.
7. Watch update arrives (minutes to hours later) → versions match → sync resumes.
