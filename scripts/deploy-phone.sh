#!/usr/bin/env bash
# Deploy phone companion APK to the connected Galaxy phone via USB.

clear

set -euo pipefail

APK="phone/build/outputs/apk/debug/phone-debug.apk"
PHONE_SERIAL="RFCY71BTAEB"
MAX_ATTEMPTS=5
RETRY_DELAY=2

cd "$(dirname "$0")/.."

echo "Building phone APK..."
./gradlew :phone:assembleDebug -q || { echo "Build failed!"; exit 1; }

echo "Deploying phone companion app..."
echo "APK: $APK ($(du -h "$APK" | cut -f1))"
echo ""

for attempt in $(seq 1 $MAX_ATTEMPTS); do
    if ! adb devices 2>/dev/null | grep -q "$PHONE_SERIAL"; then
        printf "\r[%d/%d] Phone not connected, waiting %ds..." "$attempt" "$MAX_ATTEMPTS" "$RETRY_DELAY"
        sleep "$RETRY_DELAY"
        continue
    fi

    echo "[${attempt}/${MAX_ATTEMPTS}] Phone found: $PHONE_SERIAL"
    echo "Installing..."

    if adb -s "$PHONE_SERIAL" install -r "$APK" 2>&1 | tee /dev/stderr | grep -q "Success"; then
        echo ""
        echo "Install successful!"
        echo "Launching app..."
        adb -s "$PHONE_SERIAL" shell am start -n "org.openhab.habdroid.wear/org.openhab.habdroid.wear.phone.ui.MainActivity" 2>/dev/null || true
        echo "Done."
        exit 0
    else
        echo "Install failed. Retrying in ${RETRY_DELAY}s..."
        sleep "$RETRY_DELAY"
    fi
done

echo ""
echo "Failed after $MAX_ATTEMPTS attempts."
echo "  - Check USB connection"
echo "  - Verify USB debugging is enabled"
exit 1
