#!/usr/bin/env bash
# Deploy watch APK to Galaxy Watch over WiFi debugging.
# Retries automatically when the watch connection drops.

clear

set -euo pipefail

APK="watch/build/outputs/apk/debug/watch-debug.apk"
WATCH_SERIAL_PREFIX="adb-RFAXA2EE8ZZ"
MAX_ATTEMPTS=20
RETRY_DELAY=3

cd "$(dirname "$0")/.."

echo "Building watch APK..."
./gradlew :watch:assembleDebug -q || { echo "Build failed!"; exit 1; }

echo "APK: $APK ($(du -h "$APK" | cut -f1))"
echo "Waiting for watch to connect..."
echo ""

for attempt in $(seq 1 $MAX_ATTEMPTS); do
    # Find the watch serial
    WATCH_SERIAL=$(adb devices 2>/dev/null | grep "$WATCH_SERIAL_PREFIX" | cut -f1)

    if [[ -z "$WATCH_SERIAL" ]]; then
        printf "\r[%d/%d] Watch not connected, waiting %ds..." "$attempt" "$MAX_ATTEMPTS" "$RETRY_DELAY"
        sleep "$RETRY_DELAY"
        continue
    fi

    echo ""
    echo "[${attempt}/${MAX_ATTEMPTS}] Watch found: $WATCH_SERIAL"
    echo "Installing..."

    if adb -s "$WATCH_SERIAL" install -r "$APK" 2>&1 | tee /dev/stderr | grep -q "Success"; then
        echo ""
        echo "Install successful!"
        echo "Launching voice command activity..."
        adb -s "$WATCH_SERIAL" shell am start -n "org.openhab.habdroid.wear.debug/org.openhab.habdroid.wear.ui.voice.VoiceCommandActivity" 2>/dev/null || true
        echo "Done."
        exit 0
    else
        echo "Install failed or connection dropped. Retrying in ${RETRY_DELAY}s..."
        sleep "$RETRY_DELAY"
    fi
done

echo ""
echo "Failed after $MAX_ATTEMPTS attempts. Tips:"
echo "  - Keep the watch screen on during install (tap it)"
echo "  - Move watch closer to WiFi router"
echo "  - Try: adb tcpip 5555 on the watch via USB cradle first"
exit 1
