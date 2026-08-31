#!/usr/bin/env bash
#
# Upload release AABs to Google Play Console via the Publishing API.
#
# Google Play uses separate tracks for phone and Wear OS artifacts.
# This script uploads each AAB to its respective track in a single edit.
#
# Prerequisites:
#   - Google Play service account JSON key (see Setup below)
#   - Python 3.8+ with pip
#   - AABs built by build-release.sh
#
# Setup:
#   1. Google Cloud Console → create service account → download JSON key
#   2. Google Play Console → Users & permissions → invite service account email
#   3. Grant "Publish apps to testing tracks" permission
#   4. Place JSON key at project root as play-service-account.json
#      (or set PLAY_SERVICE_ACCOUNT_JSON env var)
#
# Usage:
#   ./scripts/upload-to-play.sh                    # internal testing (default)
#   ./scripts/upload-to-play.sh --track=closed     # closed testing
#   ./scripts/upload-to-play.sh --dry-run          # validate without publishing
#
# Track shortcuts (mapped to actual Play Console track names):
#   internal    → phone: internal,    watch: wear:internal
#   closed      → phone: alpha,       watch: wear:openHAB Wear - Closed Testing
#   open        → phone: beta,        watch: wear:beta
#   production  → phone: production,  watch: wear:production
#

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_PROPS="$PROJECT_DIR/gradle.properties"
WATCH_AAB="$PROJECT_DIR/watch/build/outputs/bundle/release/watch-release.aab"
PHONE_AAB="$PROJECT_DIR/phone/build/outputs/bundle/release/phone-release.aab"
PACKAGE_NAME="org.openhab.habdroid.wear"

# Service account JSON key — override with PLAY_SERVICE_ACCOUNT_JSON env var
KEY_FILE="${PLAY_SERVICE_ACCOUNT_JSON:-$PROJECT_DIR/play-service-account.json}"

# Defaults
TRACK="internal"
STATUS="completed"
DRY_RUN=false

# Parse args
for arg in "$@"; do
    case "$arg" in
        --track=*)    TRACK="${arg#*=}" ;;
        --status=*)   STATUS="${arg#*=}" ;;
        --dry-run)    DRY_RUN=true ;;
        --key=*)      KEY_FILE="${arg#*=}" ;;
        --help|-h)
            head -35 "$0" | tail -33
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            exit 1
            ;;
    esac
done

# Map track shortcut to actual Play Console track names
case "$TRACK" in
    internal)
        PHONE_TRACK="internal"
        WATCH_TRACK="wear:internal"
        ;;
    closed|alpha)
        PHONE_TRACK="alpha"
        WATCH_TRACK="wear:openHAB Wear - Closed Testing"
        ;;
    open|beta)
        PHONE_TRACK="beta"
        WATCH_TRACK="wear:beta"
        ;;
    production)
        PHONE_TRACK="production"
        WATCH_TRACK="wear:production"
        ;;
    *)
        echo "Unknown track: $TRACK"
        echo "Valid: internal, closed, open, production"
        exit 1
        ;;
esac

# ─── Validate ───

if [ ! -f "$KEY_FILE" ]; then
    echo "ERROR: Service account JSON key not found at: $KEY_FILE"
    echo ""
    echo "Set PLAY_SERVICE_ACCOUNT_JSON or use --key=/path/to/key.json"
    echo "See script header for setup instructions."
    exit 1
fi

if [ ! -f "$WATCH_AAB" ]; then
    echo "ERROR: Watch AAB not found: $WATCH_AAB"
    echo "Run ./scripts/build-release.sh first."
    exit 1
fi

if [ ! -f "$PHONE_AAB" ]; then
    echo "ERROR: Phone AAB not found: $PHONE_AAB"
    echo "Run ./scripts/build-release.sh first."
    exit 1
fi

# Read version info and release notes
VERSION_NAME=$(grep -oP 'appVersionName=\K.+' "$GRADLE_PROPS")
WATCH_CODE=$(grep -oP 'appVersionCodeWatch=\K\d+' "$GRADLE_PROPS")
PHONE_CODE=$(grep -oP 'appVersionCodePhone=\K\d+' "$GRADLE_PROPS")

# Read release notes (supports backslash line continuations in gradle.properties)
RELEASE_NOTES=$(python3 -c "
import re
with open('$GRADLE_PROPS') as f:
    text = f.read()
# Join backslash-continuation lines
text = re.sub(r'\\\\\n\s*', '', text)
for line in text.splitlines():
    if line.startswith('releaseNotes='):
        print(line.split('=', 1)[1])
        break
else:
    print('v$VERSION_NAME')
")

if [ ${#RELEASE_NOTES} -gt 500 ]; then
    echo "WARNING: Release notes exceed 500 chars (${#RELEASE_NOTES}), truncating"
    RELEASE_NOTES="${RELEASE_NOTES:0:497}..."
fi

echo "═══════════════════════════════════════"
echo "  Google Play Upload"
echo "═══════════════════════════════════════"
echo "  Package:     $PACKAGE_NAME"
echo "  Version:     $VERSION_NAME"
echo "  Phone:       versionCode=$PHONE_CODE → track=$PHONE_TRACK"
echo "  Watch:       versionCode=$WATCH_CODE → track=$WATCH_TRACK"
echo "  Status:      $STATUS"
echo "  Dry run:     $DRY_RUN"
echo "═══════════════════════════════════════"
echo ""
echo "Release notes (${#RELEASE_NOTES} chars):"
echo "$RELEASE_NOTES"
echo ""

# ─── Ensure Python deps ───

ensure_python_deps() {
    python3 -c "import google.oauth2.service_account; from googleapiclient.discovery import build" 2>/dev/null && return 0
    echo "Installing Google API Python dependencies..."
    pip3 install --quiet --user google-api-python-client google-auth-httplib2 google-auth-oauthlib
}

ensure_python_deps

# ─── Upload via Python ───

python3 - "$KEY_FILE" "$PACKAGE_NAME" "$PHONE_TRACK" "$WATCH_TRACK" "$STATUS" "$DRY_RUN" "$WATCH_AAB" "$PHONE_AAB" "$VERSION_NAME" "$WATCH_CODE" "$PHONE_CODE" "$RELEASE_NOTES" <<'PYEOF'
import sys
import os

key_file      = sys.argv[1]
package_name  = sys.argv[2]
phone_track   = sys.argv[3]
watch_track   = sys.argv[4]
status        = sys.argv[5]
dry_run       = sys.argv[6] == "true"
watch_aab     = sys.argv[7]
phone_aab     = sys.argv[8]
version_name  = sys.argv[9]
watch_code    = sys.argv[10]
phone_code    = sys.argv[11]
release_notes = sys.argv[12]

from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

SCOPES = ['https://www.googleapis.com/auth/androidpublisher']

credentials = Credentials.from_service_account_file(key_file, scopes=SCOPES)
service = build('androidpublisher', 'v3', credentials=credentials, cache_discovery=False)
edits = service.edits()

release_notes_text = release_notes

# Create a single edit for both uploads
print("Creating edit...")
edit = edits.insert(packageName=package_name, body={}).execute()
edit_id = edit['id']
print(f"  Edit ID: {edit_id}")

try:
    # ── Upload phone AAB ──
    print(f"\nUploading phone AAB ({os.path.getsize(phone_aab) / 1024 / 1024:.1f} MB)...")
    phone_upload = edits.bundles().upload(
        packageName=package_name,
        editId=edit_id,
        media_body=MediaFileUpload(phone_aab, mimetype='application/octet-stream'),
        media_mime_type='application/octet-stream'
    ).execute()
    print(f"  Uploaded: versionCode={phone_upload['versionCode']}")

    # Assign phone to its track
    print(f"  Assigning to track '{phone_track}'...")
    edits.tracks().update(
        packageName=package_name,
        editId=edit_id,
        track=phone_track,
        body={
            'track': phone_track,
            'releases': [{
                'name': f"v{version_name}",
                'versionCodes': [phone_upload['versionCode']],
                'status': status,
                'releaseNotes': [{'language': 'en-US', 'text': release_notes_text}]
            }]
        }
    ).execute()
    print(f"  Phone → {phone_track} ✓")

    # ── Upload watch AAB ──
    print(f"\nUploading watch AAB ({os.path.getsize(watch_aab) / 1024 / 1024:.1f} MB)...")
    watch_upload = edits.bundles().upload(
        packageName=package_name,
        editId=edit_id,
        media_body=MediaFileUpload(watch_aab, mimetype='application/octet-stream'),
        media_mime_type='application/octet-stream'
    ).execute()
    print(f"  Uploaded: versionCode={watch_upload['versionCode']}")

    # Assign watch to its track
    print(f"  Assigning to track '{watch_track}'...")
    edits.tracks().update(
        packageName=package_name,
        editId=edit_id,
        track=watch_track,
        body={
            'track': watch_track,
            'releases': [{
                'name': f"v{version_name}",
                'versionCodes': [watch_upload['versionCode']],
                'status': status,
                'releaseNotes': [{'language': 'en-US', 'text': release_notes_text}]
            }]
        }
    ).execute()
    print(f"  Watch → {watch_track} ✓")

    # ── Commit or validate ──
    if dry_run:
        print("\n[DRY RUN] Validating edit...")
        edits.validate(packageName=package_name, editId=edit_id).execute()
        print("[DRY RUN] Validation passed. Discarding edit.")
        edits.delete(packageName=package_name, editId=edit_id).execute()
    else:
        print("\nCommitting edit...")
        edits.commit(packageName=package_name, editId=edit_id).execute()
        print("  Committed successfully.")

except Exception as e:
    print(f"\nERROR: {e}")
    print("Deleting edit to clean up...")
    try:
        edits.delete(packageName=package_name, editId=edit_id).execute()
    except:
        pass
    sys.exit(1)

print("\n══════════════════════════════════")
if dry_run:
    print("  DRY RUN COMPLETE — nothing published")
else:
    print("  UPLOAD COMPLETE")
    print(f"  Phone → {phone_track}")
    print(f"  Watch → {watch_track}")
print(f"  {version_name} (watch={watch_code}, phone={phone_code})")
print("══════════════════════════════════")
PYEOF

echo ""
echo "Done."
