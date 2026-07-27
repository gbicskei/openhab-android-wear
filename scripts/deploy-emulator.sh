#!/usr/bin/env bash
#
# Deploy the debug APK to a running Wear OS emulator.
# Automatically finds the emulator, installs, injects credentials, and refreshes the tile.
#

set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE="org.openhab.habdroid.wear"

cd "$PROJECT_DIR"

# Build
echo "Building..."
./gradlew :app:assembleDebug -q

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi

# Find emulator device
EMU_SERIAL=$(adb devices | grep "emulator-" | grep -v "offline" | head -1 | cut -f1)

if [ -z "$EMU_SERIAL" ]; then
    echo "ERROR: No emulator found. Start one first."
    echo ""
    echo "Connected devices:"
    adb devices -l
    exit 1
fi

echo "Deploying to emulator: $EMU_SERIAL"
adb -s "$EMU_SERIAL" install -r "$APK"

# Check if credentials are already configured
HAS_CREDS=$(adb -s "$EMU_SERIAL" shell "run-as $PACKAGE cat shared_prefs/openhab_wear_prefs.preferences_pb 2>/dev/null | grep -c myopenhab || echo 0")

if [ "$HAS_CREDS" = "0" ]; then
    echo ""
    echo "No credentials found. Injecting via DebugSetupActivity..."
    echo "Enter server URL [https://myopenhab.org]:"
    read -r URL
    URL="${URL:-https://myopenhab.org}"

    echo "Enter username (email):"
    read -r USER

    echo "Enter password:"
    read -rs PASS
    echo ""

    adb -s "$EMU_SERIAL" shell am start \
        -n "$PACKAGE/.ui.setup.DebugSetupActivity" \
        --es url "$URL" \
        --es user "$USER" \
        --es pass "$PASS"

    echo "Credentials injected. Waiting 2s..."
    sleep 2
fi

# Force tile refresh
echo "Forcing tile refresh..."
adb -s "$EMU_SERIAL" shell am broadcast -a com.google.android.clockwork.home.action.FORCE_UPDATE_TILES 2>/dev/null || true

echo "Done."
echo ""
echo "Add the tile: swipe left on watch face → '+' → find openHAB tile"
