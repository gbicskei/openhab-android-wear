#!/usr/bin/env bash
# Deploy watch APK to Galaxy Watch.
# Detects the watch from connected adb devices by model (Wear OS device).
# Uses transport_id (-t) for addressing since mDNS serials can contain spaces.

clear

set -euo pipefail

APK="watch/build/outputs/apk/debug/watch-debug.apk"
MAX_ATTEMPTS=20
RETRY_DELAY=3

# Known watch identifiers (model or device name patterns)
# SM_L705F = Galaxy Watch Ultra, projectx2ul = same device codename
WATCH_MODELS="SM_L705F|SM_R9[0-9]+|SM_L[0-9]+|projectx2ul"

cd "$(dirname "$0")/.."

echo "Building watch APK..."
./gradlew :watch:assembleDebug -q || { echo "Build failed!"; exit 1; }

echo "APK: $APK ($(du -h "$APK" | cut -f1))"
echo "Waiting for watch to connect..."
echo ""

find_watch_transport_id() {
    # Find the transport_id for the watch device.
    # Using transport_id avoids issues with mDNS serials that contain spaces/parens.
    adb devices -l 2>/dev/null \
        | grep -E "$WATCH_MODELS" \
        | head -1 \
        | grep -oP 'transport_id:\K\d+'
}

for attempt in $(seq 1 $MAX_ATTEMPTS); do
    TRANSPORT_ID=$(find_watch_transport_id)

    if [[ -z "$TRANSPORT_ID" ]]; then
        printf "\r[%d/%d] Watch not found among connected devices, waiting %ds..." "$attempt" "$MAX_ATTEMPTS" "$RETRY_DELAY"
        sleep "$RETRY_DELAY"
        continue
    fi

    echo "[${attempt}/${MAX_ATTEMPTS}] Watch found (transport_id=$TRANSPORT_ID)"
    echo "Installing..."

    if adb -t "$TRANSPORT_ID" install -r "$APK" 2>&1 | tee /dev/stderr | grep -q "Success"; then
        echo ""
        echo "Install successful!"
        echo "Launching voice command activity..."
        adb -t "$TRANSPORT_ID" shell am start -n "org.openhab.habdroid.wear.debug/org.openhab.habdroid.wear.ui.voice.VoiceCommandActivity" 2>/dev/null || true
        echo "Done."
        exit 0
    else
        echo "Install failed or connection dropped. Retrying in ${RETRY_DELAY}s..."
        sleep "$RETRY_DELAY"
    fi
done

echo ""
echo "Failed after $MAX_ATTEMPTS attempts. Connected devices:"
adb devices -l
echo ""
echo "Tips:"
echo "  - Keep the watch screen on during install (tap it)"
echo "  - Move watch closer to WiFi router"
echo "  - Try: adb tcpip 5555 on the watch via USB cradle first"
exit 1
