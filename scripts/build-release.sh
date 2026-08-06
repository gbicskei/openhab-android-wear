#!/usr/bin/env bash
#
# Build a release for Play Store upload (both watch and phone AABs).
#
# What it does:
#   1. Reads current versionCodes from both modules
#   2. Determines next two unique codes (watch gets max+1, phone gets max+2)
#   3. Builds release AABs for both modules
#   4. Commits the version bump
#   5. Tags the commit (e.g., v1.0.12-36)
#
# Usage:
#   ./scripts/build-release.sh              # full release flow
#   ./scripts/build-release.sh --no-bump    # rebuild without bumping (no commit/tag)
#

clear

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WATCH_BUILD="$PROJECT_DIR/watch/build.gradle.kts"
PHONE_BUILD="$PROJECT_DIR/phone/build.gradle.kts"
WATCH_AAB="$PROJECT_DIR/watch/build/outputs/bundle/release/watch-release.aab"
PHONE_AAB="$PROJECT_DIR/phone/build/outputs/bundle/release/phone-release.aab"

cd "$PROJECT_DIR"

# Parse args
BUMP=true
if [[ "${1:-}" == "--no-bump" ]]; then
    BUMP=false
fi

# Check for uncommitted changes
if [ "$BUMP" = true ]; then
    DIRTY=$(git status --porcelain -- ':!watch/build.gradle.kts' ':!phone/build.gradle.kts' | head -1)
    if [ -n "$DIRTY" ]; then
        echo "ERROR: You have uncommitted changes. Commit or stash them first."
        echo ""
        git status --short
        exit 1
    fi
fi

# Read current versions
WATCH_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$WATCH_BUILD")
PHONE_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$PHONE_BUILD")
VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$WATCH_BUILD")

if [ -z "$WATCH_CODE" ] || [ -z "$PHONE_CODE" ]; then
    echo "ERROR: Could not read versionCode from build files"
    echo "  watch: ${WATCH_CODE:-<not found>}"
    echo "  phone: ${PHONE_CODE:-<not found>}"
    exit 1
fi

echo "Current versions:"
echo "  watch: $VERSION_NAME ($WATCH_CODE)"
echo "  phone: $VERSION_NAME ($PHONE_CODE)"
echo ""

if [ "$BUMP" = true ]; then
    # Both version codes must be globally unique across watch and phone.
    # Take the max of both, then assign sequential codes.
    MAX_CODE=$((WATCH_CODE > PHONE_CODE ? WATCH_CODE : PHONE_CODE))
    NEW_WATCH_CODE=$((MAX_CODE + 1))
    NEW_PHONE_CODE=$((MAX_CODE + 2))

    echo "Bumping versionCodes:"
    echo "  watch: $WATCH_CODE → $NEW_WATCH_CODE"
    echo "  phone: $PHONE_CODE → $NEW_PHONE_CODE"
    echo ""

    sed -i "s/versionCode = $WATCH_CODE/versionCode = $NEW_WATCH_CODE/" "$WATCH_BUILD"
    sed -i "s/versionCode = $PHONE_CODE/versionCode = $NEW_PHONE_CODE/" "$PHONE_BUILD"
else
    NEW_WATCH_CODE=$WATCH_CODE
    NEW_PHONE_CODE=$PHONE_CODE
    echo "Skipping version bump (--no-bump)"
    echo ""
fi

echo "Building release: $VERSION_NAME (watch=$NEW_WATCH_CODE, phone=$NEW_PHONE_CODE)"
echo ""

# Build both AABs
./gradlew :watch:bundleRelease :phone:bundleRelease

# Verify outputs
FAILED=false
if [ ! -f "$WATCH_AAB" ]; then
    echo "ERROR: Watch AAB not found at $WATCH_AAB"
    FAILED=true
fi
if [ ! -f "$PHONE_AAB" ]; then
    echo "ERROR: Phone AAB not found at $PHONE_AAB"
    FAILED=true
fi
if [ "$FAILED" = true ]; then
    exit 1
fi

# Commit and tag (only when bumping)
if [ "$BUMP" = true ]; then
    TAG="v${VERSION_NAME}-${NEW_WATCH_CODE}"

    echo ""
    echo "Committing version bump..."
    git add "$WATCH_BUILD" "$PHONE_BUILD"
    git commit -m "release: ${VERSION_NAME} (watch=${NEW_WATCH_CODE}, phone=${NEW_PHONE_CODE})"

    echo "Tagging: $TAG"
    git tag -a "$TAG" -m "Release ${VERSION_NAME} (watch=${NEW_WATCH_CODE}, phone=${NEW_PHONE_CODE})"
fi

echo ""
echo "===== BUILD COMPLETE ====="
echo "Version:  $VERSION_NAME"
echo "  watch:  versionCode=$NEW_WATCH_CODE"
echo "  phone:  versionCode=$NEW_PHONE_CODE"
echo "Tag:      v${VERSION_NAME}-${NEW_WATCH_CODE}"
echo ""
echo "Outputs:"
echo "  Watch AAB: $WATCH_AAB"
echo "  Phone AAB: $PHONE_AAB"
echo ""
echo "Next steps:"
echo "  1. Upload Watch AAB to Play Console → Wear OS track"
echo "  2. Upload Phone AAB to Play Console → Phone track"
echo "  3. Push: git push && git push --tags"
