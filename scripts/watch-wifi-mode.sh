#!/usr/bin/env bash
# Set the Wi-Fi automatic mode on the connected Galaxy Watch.
#
# Usage:
#   ./scripts/watch-wifi-mode.sh off     # Wi-Fi always off (0)
#   ./scripts/watch-wifi-mode.sh auto    # Wi-Fi auto — on when needed, off when BT proxy active (1, default)
#   ./scripts/watch-wifi-mode.sh on      # Wi-Fi always on (2)
#   ./scripts/watch-wifi-mode.sh         # No argument — shows current value

set -euo pipefail

# Known watch identifiers (model or device name patterns)
WATCH_MODELS="SM_L705F|SM_R9[0-9]+|SM_L[0-9]+|projectx2ul"

find_watch_transport_id() {
    adb devices -l 2>/dev/null \
        | grep -E "$WATCH_MODELS" \
        | grep -v "offline\|unauthorized" \
        | tail -1 \
        | grep -oP 'transport_id:\K\d+'
}

TRANSPORT_ID=$(find_watch_transport_id)

if [[ -z "$TRANSPORT_ID" ]]; then
    echo "Error: Watch not found or not connected (offline/unauthorized)."
    echo ""
    echo "Connected devices:"
    adb devices -l
    echo ""
    echo "Tips:"
    echo "  - Enable Wireless debugging on the watch"
    echo "  - Tap the watch screen to wake it"
    echo "  - If 'offline', try: adb reconnect"
    exit 1
fi

echo "Watch found (transport_id=$TRANSPORT_ID)"

# No argument — read current value
if [[ $# -eq 0 ]]; then
    CURRENT=$(timeout 5 adb -t "$TRANSPORT_ID" shell settings get global wifi_automatic 2>/dev/null || echo "timeout")
    case "$CURRENT" in
        0) echo "Wi-Fi mode: OFF (always off)" ;;
        1) echo "Wi-Fi mode: AUTO (on when needed)" ;;
        2) echo "Wi-Fi mode: ON (always on)" ;;
        timeout) echo "Error: adb command timed out. Watch may be asleep." ; exit 1 ;;
        *) echo "Wi-Fi mode: $CURRENT (unknown)" ;;
    esac
    exit 0
fi

# Map argument to value
case "${1,,}" in
    off|0)
        VALUE=0
        LABEL="OFF (always off)"
        ;;
    auto|1)
        VALUE=1
        LABEL="AUTO (on when needed, default)"
        ;;
    on|always|2)
        VALUE=2
        LABEL="ON (always on)"
        ;;
    *)
        echo "Usage: $0 [off|auto|on]"
        echo ""
        echo "  off   (0) - Wi-Fi always disabled"
        echo "  auto  (1) - Wi-Fi on when needed, off when BT proxy active (default)"
        echo "  on    (2) - Wi-Fi always enabled"
        echo ""
        echo "  No argument: show current value"
        exit 1
        ;;
esac

timeout 5 adb -t "$TRANSPORT_ID" shell settings put global wifi_automatic "$VALUE" || { echo "Error: adb command timed out."; exit 1; }

# Also control Samsung's Wi-Fi mediator which can override the standard setting
if [[ "$VALUE" -eq 2 ]]; then
    timeout 5 adb -t "$TRANSPORT_ID" shell settings put global cw_disable_wifimediator 1 2>/dev/null
elif [[ "$VALUE" -eq 1 ]]; then
    timeout 5 adb -t "$TRANSPORT_ID" shell settings put global cw_disable_wifimediator 0 2>/dev/null
fi

echo "Wi-Fi mode set to: $LABEL"
