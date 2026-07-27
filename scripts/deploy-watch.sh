#!/usr/bin/env bash
#
# Deploy the debug APK to a connected Wear OS device.
# Automatically finds the watch (filters out phones) and installs.
#

set -euo pipefail

clear

APK="app/build/outputs/apk/debug/app-debug.apk"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$PROJECT_DIR"

# Build first
echo "Building..."
./gradlew :app:assembleDebug -q

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi

# Find watch device (look for adb-tls-connect which indicates wireless Wear OS device)
WATCH_SERIAL=$(adb devices | grep "_adb-tls-connect" | grep -v "offline" | head -1 | cut -f1)

if [ -z "$WATCH_SERIAL" ]; then
    # Fallback: show all devices and let user pick
    echo "No wireless watch device found. Connected devices:"
    adb devices -l
    echo ""
    echo "Enter device serial to deploy to:"
    read -r WATCH_SERIAL
fi

if [ -z "$WATCH_SERIAL" ]; then
    echo "ERROR: No device selected"
    exit 1
fi

echo "Deploying to: $WATCH_SERIAL"
adb -s "$WATCH_SERIAL" install -r "$APK"

echo ""
echo "Forcing tile refresh..."
adb -s "$WATCH_SERIAL" shell am broadcast -a com.google.android.clockwork.home.action.FORCE_UPDATE_TILES 2>/dev/null || true

echo "Done."
