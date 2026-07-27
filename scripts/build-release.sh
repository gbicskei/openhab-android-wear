#!/usr/bin/env bash
#
# Build a release for Play Store upload.
#
# What it does:
#   1. Increments versionCode in app/build.gradle.kts
#   2. Builds release AAB (Play Store) and APK (direct install)
#   3. Commits the version bump
#   4. Tags the commit (e.g., v0.9.0-3)
#
# Usage:
#   ./scripts/build-release.sh              # full release flow
#   ./scripts/build-release.sh --no-bump    # rebuild without bumping (no commit/tag)
#

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_FILE="$PROJECT_DIR/app/build.gradle.kts"
AAB_OUTPUT="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"
APK_OUTPUT="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"

cd "$PROJECT_DIR"

# Parse args
BUMP=true
if [[ "${1:-}" == "--no-bump" ]]; then
    BUMP=false
fi

# Check for uncommitted changes (other than build.gradle.kts)
if [ "$BUMP" = true ]; then
    DIRTY=$(git status --porcelain -- ':!app/build.gradle.kts' | head -1)
    if [ -n "$DIRTY" ]; then
        echo "ERROR: You have uncommitted changes. Commit or stash them first."
        echo ""
        git status --short
        exit 1
    fi
fi

# Read current versions
CURRENT_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$BUILD_FILE")
CURRENT_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD_FILE")

if [ -z "$CURRENT_CODE" ]; then
    echo "ERROR: Could not read versionCode from $BUILD_FILE"
    exit 1
fi

if [ "$BUMP" = true ]; then
    NEW_CODE=$((CURRENT_CODE + 1))
    echo "Bumping versionCode: $CURRENT_CODE → $NEW_CODE"
    sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_FILE"
else
    NEW_CODE=$CURRENT_CODE
    echo "Skipping version bump (--no-bump)"
fi

echo "Building release: $CURRENT_NAME ($NEW_CODE)"
echo ""

# Build AAB (for Play Store)
./gradlew :app:bundleRelease

# Also build APK (for direct install / testing)
./gradlew :app:assembleRelease

# Verify outputs exist
if [ ! -f "$AAB_OUTPUT" ]; then
    echo "ERROR: AAB not found at $AAB_OUTPUT"
    exit 1
fi

# Commit and tag (only when bumping)
if [ "$BUMP" = true ]; then
    TAG="v${CURRENT_NAME}-${NEW_CODE}"

    echo ""
    echo "Committing version bump..."
    git add "$BUILD_FILE"
    git commit -m "release: ${CURRENT_NAME} (${NEW_CODE})"

    echo "Tagging: $TAG"
    git tag -a "$TAG" -m "Release ${CURRENT_NAME} build ${NEW_CODE}"
fi

echo ""
echo "===== BUILD COMPLETE ====="
echo "Version:  $CURRENT_NAME ($NEW_CODE)"
echo "Tag:      v${CURRENT_NAME}-${NEW_CODE}"
echo "AAB:      $AAB_OUTPUT"
echo "APK:      $APK_OUTPUT"
echo ""
echo "Next steps:"
echo "  1. Upload AAB to Google Play Console → Wear OS → Internal testing"
echo "  2. Push: git push && git push --tags"
