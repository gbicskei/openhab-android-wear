# Distribution

## Overview

Wear OS apps cannot be sideloaded by regular users (no file manager, no browser install). Distribution options:

| Method | Audience | Effort |
|--------|----------|--------|
| ADB install | Developers with wireless debugging | None |
| Google Play Internal Testing | Up to 100 testers by email | Low (one-time setup) |
| Google Play Closed Testing | Larger groups, opt-in link | Medium |
| Google Play Production | Public | High (full review) |

## Signing

### Create a release keystore (one-time)

Use the provided helper script:

```bash
chmod +x scripts/generate-keystore.sh
./scripts/generate-keystore.sh
```

Or manually:

```bash
keytool -genkey -v \
  -keystore openhab-wear-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias openhab-wear \
  -dname "CN=Gabor Bicskei,OU=openHAB,O=openHAB,L=Budapest,ST=Budapest,C=HU"
```

Store the keystore file securely. **Never commit it to version control.**

### Configure signing

The build reads credentials from `keystore.properties` in the project root (gitignored). Copy the template and fill in your passwords:

```bash
cp keystore.properties.template keystore.properties
```

Contents of `keystore.properties`:

```properties
storeFile=../openhab-wear-release.jks
storePassword=YOUR_PASSWORD
keyAlias=openhab-wear
keyPassword=YOUR_PASSWORD
```

The `app/build.gradle.kts` automatically picks this up — no manual Gradle edits needed.

### Build signed APK/AAB

```bash
# APK (for direct install via ADB)
./gradlew :app:assembleRelease

# AAB (for Play Store upload)
./gradlew :app:bundleRelease
```

Output locations:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

## Google Play Console Setup

### One-time: Create the app listing

1. Go to [Google Play Console](https://play.google.com/console)
2. **Create app**:
   - App name: `openHAB Wear`
   - Default language: English (United States)
   - App or game: App
   - Free or paid: Free
3. **Advanced settings → Form factors**:
   - Activate **Wear OS** (separate release track)
   - Android XR: select "Use same track as mobile" (no-op since there's no mobile release)
   - The default store listing still requires phone screenshots — upload placeholder images (watch screenshots on dark 1080x1920 background)
4. **Dashboard → Set up your app** — complete the mandatory declarations:
   - App access: All functionality available without special access
   - Ads: No ads
   - Content rating: Complete the questionnaire (utility/smart home → likely "Everyone")
   - Target audience: 18+ (smart home control)
   - News app: No
   - Data safety: Declare network permissions, device identifiers (FCM token), server connection data
   - Government apps: No
5. **Store listing**:
   - Short description: "Control your openHAB smart home directly from your Wear OS watch."
   - Full description: Feature overview
   - App icon: 512x512 PNG
   - Feature graphic: 1024x500 PNG
   - Phone screenshots: Required even for Wear OS-only apps (use placeholders)
   - Wear OS screenshots: Round watch screenshots (capture via `adb exec-out screencap -p > screenshot.png`)
6. **App category**: Tools / Smart Home

### Wear OS form factor checklist

Play Console requires these steps to activate Wear OS distribution:

1. Upload Wear OS screenshots to all store listings
2. Upload a Wear OS AAB to a testing track (Wear OS → Internal testing)
3. Accept the Wear OS distribution review policy

### Upload to Internal Testing (Wear OS track)

1. Navigate to **Wear OS → Testing → Internal testing**
2. Click **Create new release**
3. **App signing**: Choose "Let Google manage and protect your app signing key" (recommended — uses Play App Signing)
   - Upload your upload key (the keystore you generated)
   - Google wraps it with their own signing key for distribution
4. Upload `app-release.aab`
5. Release name: `0.1.0 (2)` (auto-filled from versionName/versionCode)
6. Release notes: Describe what's in this build
7. **Review and roll out to Internal testing**

Note: The Wear OS track is separate from the phone/tablet track. Always upload to the Wear OS-specific internal testing section.

### Add testers

1. Go to **Wear OS → Internal testing → Testers**
2. Create an email list (e.g., "Family testers")
3. Add email addresses (Google accounts with a paired Wear OS watch)
4. Copy the **opt-in link** and share it with testers
5. Testers accept the link → app appears in Play Store on their watch

### Update process

1. Bump `versionCode` in `app/build.gradle.kts`
2. Build new AAB
3. Upload to the same internal testing track
4. Testers get the update automatically (within hours)

## Play Store Requirements (for future production release)

| Requirement | Status |
|-------------|--------|
| Target API 34+ | Done (targeting 35) |
| Wear OS standalone metadata | Done (`android.hardware.type.watch` required=true) |
| `uses-feature` watch required | Done |
| Privacy policy URL | Needed |
| App icon (512x512 PNG) | Needed |
| Feature graphic (1024x500 PNG) | Needed |
| Phone screenshots (placeholder) | Needed (mandatory even for Wear OS-only) |
| Wear OS screenshots (round) | Needed |
| Content rating questionnaire | Needed |
| Data safety form | Needed |
| Wear OS distribution policy accepted | Needed |

## Version Management

Follow semantic versioning:

```
versionName = "0.1.0"   # Major.Minor.Patch
versionCode = 2          # Incrementing integer for Play Store
```

- Increment `versionCode` for every Play Store upload
- Increment `versionName` for user-visible changes
- Pre-1.0: feature development
- 1.0.0: first public release

## CI/CD (future)

For automated builds and deployment, a GitHub Actions workflow could:

1. Build on push to `main`
2. Run tests
3. Sign the AAB
4. Upload to internal testing track via [Google Play Developer API](https://developers.google.com/android-publisher)

This can be set up later when the project stabilizes.
