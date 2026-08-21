# Design System

This document describes the Material 3 Expressive design system applied to the wearOH project (watch + phone companion apps). It serves as a reference for developers and AI agents working on the UI.

## Design Guidelines References

### Watch (Wear OS)
- **Get started**: https://developer.android.com/design/ui/wear/guides/get-started
- **UX principles**: https://developer.android.com/design/ui/wear/guides/get-started/design-for-wearables/principles
- **M3 Expressive design language**: https://developer.android.com/design/ui/wear/guides/get-started/apply
- **Color system & roles**: https://developer.android.com/design/ui/wear/guides/styles/color/roles-tokens
- **Color theming (HCT)**: https://developer.android.com/design/ui/wear/guides/styles/color/system
- **Typography**: https://developer.android.com/design/ui/wear/guides/styles/typography
- **Adaptive design & screen sizes**: https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design
- **App layouts (scrolling)**: https://developer.android.com/design/ui/wear/guides/foundations/common-layouts/apps-scrolling
- **Navigation (swipe-to-close)**: https://developer.android.com/design/ui/wear/guides/behaviors-and-patterns/navigation
- **Buttons & components**: https://developer.android.com/design/ui/wear/guides/components/buttons
- **Tile best practices**: https://developer.android.com/design/ui/wear/guides/surfaces/tiles/bestpractices
- **Tile overview & principles**: https://developer.android.com/design/ui/wear/guides/surfaces/tiles
- **Wear Compose Material3 API**: https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/package-summary

### Phone (Android Mobile)
- **Mobile design overview**: https://developer.android.com/design/ui/mobile
- **Themes**: https://developer.android.com/design/ui/mobile/guides/styles/themes
- **Color**: https://developer.android.com/design/ui/mobile/guides/styles/color
- **Layout basics**: https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics
- **Navigation patterns**: https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns
- **Settings patterns**: https://developer.android.com/design/ui/mobile/guides/patterns/settings
- **Material components**: https://developer.android.com/design/ui/mobile/guides/components/material-overview
- **Material 3 in Compose**: https://developer.android.com/develop/ui/compose/designsystems/material3

---

## Color System

### Tonal Palette (M3 Dark Theme)

Colors follow the HCT tonal system. For dark-on-black (Wear OS and phone dark mode):

| Role | Tone | Purpose |
|------|------|---------|
| Primary | 80 | Main accent on buttons, active states |
| Primary-Dim | 60 | Less prominent accent elements |
| Primary-Container | 30 | Card/modal backgrounds for selected states |
| On-Primary | 20 | Text/icons on primary fills |
| On-Primary-Container | 90 | Text/icons on containers |

### Theme Presets (shared between watch and phone)

| Name | Primary (tone 80) | Hex |
|------|-------------------|-----|
| Amber | Warm orange | `#FFB950` |
| Blue | Soft blue | `#A8C8FF` |
| Green | Soft green | `#8AD88E` |
| Purple | Lavender | `#D4BBFF` |
| Red | Salmon pink | `#FFB4AB` |

### Fixed Roles (not affected by theme selection)

| Role | Color | Usage |
|------|-------|-------|
| Secondary | `#A8C8FF` (Blue tone 80) | Supporting actions |
| Tertiary | `#8AD88E` (Green tone 80) | Status, success indicators |
| Error | `#FFB4AB` (Red tone 80) | Delete, dismiss, alerts |
| Error-Dim | `#CC5449` (Red tone 60) | High-priority errors |
| Surface-Container | `#2C2C2C` | Card/button backgrounds |
| Surface-Container-Low | `#1A1A1A` | Low-emphasis backgrounds |
| Surface-Container-High | `#3D3D3D` | Elevated components |
| On-Surface | `#E6E1DC` | Primary text/icons |
| On-Surface-Variant | `#C4BFB8` | Secondary text |
| Background | `#000000` | Always pure black (OLED) |

### Theme Source of Truth

- **Watch** `ThemeStore` (DataStore) is the authoritative source
- **Phone** caches the theme locally for display when watch is disconnected
- On connection, phone adopts watch's reported theme
- Theme changes from phone are sent to watch via Data Layer (`PATH_THEME`)

---

## Watch App (Wear OS)

### Architecture

- `AppScaffold` → provides TimeText (curved clock at top)
- `ScreenScaffold` → provides ScrollIndicator + contentPadding per screen
- `TransformingLazyColumn` → shape-morphing list items via `SurfaceTransformation`
- `WearOHTheme` → wraps all screens, reads `ThemeStore.cachedTheme` for instant first-frame color

### Key Patterns

| Pattern | Implementation |
|---------|---------------|
| Scrolling screens | `ScreenScaffold { contentPadding -> TransformingLazyColumn(contentPadding = ...) }` |
| Bottom padding | `contentPadding.calculateBottomPadding() + 48.dp` (ensures last item fully visible) |
| Non-scrolling controls | No ScreenScaffold (Rotary, RollerShutter, ColorPicker, Toggle) |
| Edge button | `EdgeButton` for confirm actions at screen bottom (TileConfig) |
| TimeText | Provided automatically by AppScaffold, scrolls away on scroll |
| Theme loading | `ThemeStore.cachedTheme` warmed in `Application.onCreate()` — no flash |
| Auto-close controls | ChoicePicker and ToggleControl `finish()` after command sent |

### Tile (ProtoLayout)

| Guideline | Implementation |
|-----------|---------------|
| Black background | Always `#000000` |
| Button containers | Circular `Surface-Container` (`#2C2C2C`) background behind each icon |
| Tap targets | All buttons ≥ 64dp (min requirement: 48dp) |
| Title hiding | Logo + dot hidden on screens < 225dp with 5+ item layouts |
| No horizontal swipe | Page navigation via tap buttons (system owns horizontal swipe) |
| Single use-case | Grid of item controls + mic/back |

### Screens NOT using ScreenScaffold

These are full-screen, bezel-driven UIs with edge-to-edge arc indicators:
- `RotaryControlActivity` — dimmer/range arc
- `RollerShutterActivity` — position arc + UP/STOP/DOWN
- `ColorPickerActivity` — brightness arc + color chips
- `ToggleControlActivity` — power toggle (auto-closes)

---

## Phone App (Android Mobile)

### Architecture

- Single Activity + Compose Navigation (`NavHost`)
- Hub pattern (HomeScreen with cards → sub-screens)
- No bottom NavigationBar (configurator app, not daily-use)

### Theme

| Aspect | Detail |
|--------|--------|
| Framework | `androidx.compose.material3` via Compose BOM |
| Dynamic color | Disabled — accent color from user selection takes priority |
| Light/Dark | Both supported, follows system preference |
| Accent source | `PhoneCredentialStore.selectedThemeState` (reactive `StateFlow`) |
| Shapes | 8dp (small), 12dp (medium), 16dp (large), 28dp (extraLarge) |
| Typography | Custom weights: Bold headlines, SemiBold titles, Medium labels |

### Key Patterns

| Pattern | Implementation |
|---------|---------------|
| Edge-to-edge | `enableEdgeToEdge()` + transparent system bars in XML theme |
| WindowInsets | HomeScreen uses `WindowInsets.systemBars.asPaddingValues()` |
| Cards | `surfaceColorAtElevation(2.dp)` for tonal depth |
| Predictive back | `android:enableOnBackInvokedCallback="true"` in manifest |
| Settings sub-screens | Watch Settings uses internal `currentSection` state navigation |
| Accessibility | Content descriptions on all meaningful icons, 48dp touch targets via M3 defaults |

### Watch Settings Sub-menus

```
Watch Settings
├── Voice         — commands, read aloud, TTS, test
├── Notifications — enable, read aloud, chime, priority
├── Theme         — 5 color cards (sends to watch on selection)
└── Misc          — debug mode, server backup
```

---

## Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| No NavigationBar on phone | Hub pattern suits infrequent-use configurator apps better |
| No ScreenScaffold on control screens | Edge-to-edge arc indicators would conflict with scaffold padding |
| Dynamic color disabled on phone | Ensures phone accent matches watch theme selection consistently |
| Theme cached in Application.onCreate | Prevents amber flash before DataStore emits real value |
| Tile buttons have container background | M3 requires visible interactive containers on black backgrounds |
| ChoicePicker/Toggle auto-close | Reduces taps — user sees result on tile immediately |
| Watch is theme source of truth | Watch is always-on device; phone is transient configurator |
| Tile designer theme is preview-only | Avoids accidental theme persistence from preview interactions |

---

## File Locations

### Watch Theme
- `watch/src/main/java/.../ui/theme/Color.kt` — Color definitions
- `watch/src/main/java/.../ui/theme/Theme.kt` — `WearOHTheme` composable + `buildColorScheme()`
- `watch/src/main/java/.../ui/theme/Type.kt` — Typography (system Roboto Flex)
- `watch/src/main/java/.../data/repository/ThemeStore.kt` — `TileTheme` enum + persistence + cached theme

### Phone Theme
- `phone/src/main/java/.../ui/theme/Theme.kt` — `OpenHabWearPhoneTheme`, `PhoneAccent` enum, Typography, Shapes
- `phone/src/main/java/.../data/PhoneCredentialStore.kt` — `selectedThemeState` StateFlow + persistence

### Shared
- `shared/src/main/java/.../sync/SyncConstants.kt` — `PATH_THEME` and other Data Layer paths

### Tile
- `watch/src/main/java/.../tile/OpenHabTileService.kt` — ProtoLayout tile with container backgrounds + adaptive title hiding
