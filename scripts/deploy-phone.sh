#!/usr/bin/env bash
# Deploy phone companion APK to the connected Galaxy phone.
# Detects the phone from connected adb devices by model (non-Wear OS device).
# Uses transport_id (-t) for addressing since serials can vary.

clear

set -euo pipefail

APK="phone/build/outputs/apk/debug/phone-debug.apk"
MAX_ATTEMPTS=5
RETRY_DELAY=2

# Known phone identifiers (model or device name patterns)
# SM_S931B = Galaxy S24, pa1q = same device codename
PHONE_MODELS="SM_S9[0-9]+|SM_G9[0-9]+|SM_A[0-9]+|pa1q"

# Known watch identifiers (to exclude from phone detection)
WATCH_MODELS="SM_L705F|SM_R9[0-9]+|SM_L[0-9]+|projectx2ul"

cd "$(dirname "$0")/.."

echo "Building phone APK..."
./gradlew :phone:assembleDebug -q || { echo "Build failed!"; exit 1; }

echo "Deploying phone companion app..."
echo "APK: $APK ($(du -h "$APK" | cut -f1))"
echo ""

find_phone_transport_id() {
    # Find the transport_id for the phone device.
    local tid=""

    # First: find by known phone model patterns
    tid=$(adb devices -l 2>/dev/null \
        | grep -E "$PHONE_MODELS" \
        | head -1 \
        | grep -oP 'transport_id:\K\d+')

    if [[ -n "$tid" ]]; then
        echo "$tid"
        return
    fi

    # Fallback: pick first connected device that isn't a watch
    tid=$(adb devices -l 2>/dev/null \
        | grep -v "^List" \
        | grep -v "^$" \
        | grep -v -E "$WATCH_MODELS" \
        | grep "device " \
        | head -1 \
        | grep -oP 'transport_id:\K\d+')

    echo "$tid"
}

for attempt in $(seq 1 $MAX_ATTEMPTS); do
    TRANSPORT_ID=$(find_phone_transport_id)

    if [[ -z "$TRANSPORT_ID" ]]; then
        printf "\r[%d/%d] Phone not found among connected devices, waiting %ds..." "$attempt" "$MAX_ATTEMPTS" "$RETRY_DELAY"
        sleep "$RETRY_DELAY"
        continue
    fi

    echo "[${attempt}/${MAX_ATTEMPTS}] Phone found (transport_id=$TRANSPORT_ID)"
    echo "Installing..."

    if adb -t "$TRANSPORT_ID" install -r "$APK" 2>&1 | tee /dev/stderr | grep -q "Success"; then
        echo ""
        echo "Install successful!"
        echo "Launching app..."
        adb -t "$TRANSPORT_ID" shell am start -n "org.openhab.habdroid.wear/org.openhab.habdroid.wear.phone.ui.MainActivity" 2>/dev/null || true
        echo "Done."
        exit 0
    else
        echo "Install failed. Retrying in ${RETRY_DELAY}s..."
        sleep "$RETRY_DELAY"
    fi
done

echo ""
echo "Failed after $MAX_ATTEMPTS attempts. Connected devices:"
adb devices -l
echo ""
echo "Tips:"
echo "  - Check USB connection"
echo "  - Verify USB debugging is enabled"
exit 1
