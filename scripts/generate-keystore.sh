#!/bin/bash
# Generate a release keystore for Play Store signing.
# Run this once, then keep the .jks file safe and backed up.
#
# Usage: ./scripts/generate-keystore.sh
#
# You will be prompted for passwords if not set via environment variables.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
KEYSTORE_FILE="$PROJECT_DIR/openhab-wear-release.jks"
KEY_ALIAS="openhab-wear"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "ERROR: Keystore already exists at $KEYSTORE_FILE"
    echo "Delete it first if you want to regenerate."
    exit 1
fi

STORE_PASS="${KEYSTORE_PASSWORD:-}"
KEY_PASS="${KEY_PASSWORD:-}"

if [ -z "$STORE_PASS" ]; then
    read -s -p "Keystore password (min 6 chars): " STORE_PASS
    echo
fi

if [ -z "$KEY_PASS" ]; then
    read -s -p "Key password (min 6 chars): " KEY_PASS
    echo
fi

echo "Generating keystore at: $KEYSTORE_FILE"

keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA -keysize 2048 \
    -validity 10000 \
    -alias "$KEY_ALIAS" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=Gabor Bicskei,OU=openHAB,O=openHAB,L=Budapest,ST=Budapest,C=HU"

echo ""
echo "Keystore generated successfully!"
echo ""
echo "Now create keystore.properties in the project root:"
echo ""
echo "  storeFile=../openhab-wear-release.jks"
echo "  storePassword=$STORE_PASS"
echo "  keyAlias=$KEY_ALIAS"
echo "  keyPassword=$KEY_PASS"
echo ""
echo "IMPORTANT: Back up openhab-wear-release.jks securely."
echo "           If you lose it, you cannot update the app on Play Store."
