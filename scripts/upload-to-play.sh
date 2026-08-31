#!/usr/bin/env bash
#
# Upload release AABs to Google Play Console via the Publishing API.
#
# Prerequisites:
#   - Google Play service account JSON key (see Setup below)
#   - Python 3.8+ with pip
#   - AABs built by build-release.sh
#
# Setup:
#   1. Google Play Console → Setup → API access → Create/link a service account
#   2. Grant the service account "Release manager" (or at minimum "Release to testing tracks")
#   3. Download the JSON key file
#   4. Place it at the path below (or set PLAY_SERVICE_ACCOUNT_JSON env var)
#
# Usage:
#   ./scripts/upload-to-play.sh                          # upload to internal testing (default)
#   ./scripts/upload-to-play.sh --track=alpha            # upload to closed testing
#   ./scripts/upload-to-play.sh --track=internal --status=completed
#   ./scripts/upload-to-play.sh --dry-run                # validate without uploading
#
# Tracks:
#   internal  — Internal testing (default, no review)
#   alpha     — Closed testing
#   beta      — Open testing
#   production
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

# Read version info
VERSION_NAME=$(grep -oP 'appVersionName=\K.+' "$GRADLE_PROPS")
WATCH_CODE=$(grep -oP 'appVersionCodeWatch=\K\d+' "$GRADLE_PROPS")
PHONE_CODE=$(grep -oP 'appVersionCodePhone=\K\d+' "$GRADLE_PROPS")

echo "═══════════════════════════════════════"
echo "  Google Play Upload"
echo "═══════════════════════════════════════"
echo "  Package:  $PACKAGE_NAME"
echo "  Version:  $VERSION_NAME"
echo "  Watch:    versionCode=$WATCH_CODE"
echo "  Phone:    versionCode=$PHONE_CODE"
echo "  Track:    $TRACK"
echo "  Status:   $STATUS"
echo "  Dry run:  $DRY_RUN"
echo "═══════════════════════════════════════"
echo ""

# ─── Ensure Python deps ───

ensure_python_deps() {
    python3 -c "import google.oauth2.service_account; from googleapiclient.discovery import build" 2>/dev/null && return 0
    echo "Installing Google API Python dependencies..."
    pip3 install --quiet --user google-api-python-client google-auth-httplib2 google-auth-oauthlib
}

ensure_python_deps

# ─── Upload via Python ───

python3 - "$KEY_FILE" "$PACKAGE_NAME" "$TRACK" "$STATUS" "$DRY_RUN" "$WATCH_AAB" "$PHONE_AAB" "$VERSION_NAME" "$WATCH_CODE" "$PHONE_CODE" <<'PYEOF'
import sys
import os

key_file     = sys.argv[1]
package_name = sys.argv[2]
track_name   = sys.argv[3]
status       = sys.argv[4]
dry_run      = sys.argv[5] == "true"
watch_aab    = sys.argv[6]
phone_aab    = sys.argv[7]
version_name = sys.argv[8]
watch_code   = sys.argv[9]
phone_code   = sys.argv[10]

from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

SCOPES = ['https://www.googleapis.com/auth/androidpublisher']

credentials = Credentials.from_service_account_file(key_file, scopes=SCOPES)
service = build('androidpublisher', 'v3', credentials=credentials, cache_discovery=False)
edits = service.edits()

# Create a new edit
print("Creating edit...")
edit = edits.insert(packageName=package_name, body={}).execute()
edit_id = edit['id']
print(f"  Edit ID: {edit_id}")

try:
    # Upload watch AAB
    print(f"\nUploading watch AAB ({os.path.getsize(watch_aab) / 1024 / 1024:.1f} MB)...")
    watch_upload = edits.bundles().upload(
        packageName=package_name,
        editId=edit_id,
        media_body=MediaFileUpload(watch_aab, mimetype='application/octet-stream'),
        media_mime_type='application/octet-stream'
    ).execute()
    print(f"  Uploaded: versionCode={watch_upload['versionCode']}")

    # Upload phone AAB
    print(f"\nUploading phone AAB ({os.path.getsize(phone_aab) / 1024 / 1024:.1f} MB)...")
    phone_upload = edits.bundles().upload(
        packageName=package_name,
        editId=edit_id,
        media_body=MediaFileUpload(phone_aab, mimetype='application/octet-stream'),
        media_mime_type='application/octet-stream'
    ).execute()
    print(f"  Uploaded: versionCode={phone_upload['versionCode']}")

    # Assign both to the track
    release_notes = f"v{version_name} (watch={watch_code}, phone={phone_code})"
    track_body = {
        'track': track_name,
        'releases': [{
            'name': f"v{version_name}",
            'versionCodes': [
                watch_upload['versionCode'],
                phone_upload['versionCode']
            ],
            'status': status,
            'releaseNotes': [{
                'language': 'en-US',
                'text': release_notes
            }]
        }]
    }

    print(f"\nAssigning to track '{track_name}' with status '{status}'...")
    edits.tracks().update(
        packageName=package_name,
        editId=edit_id,
        track=track_name,
        body=track_body
    ).execute()
    print(f"  Release: {release_notes}")

    if dry_run:
        # Validate but don't commit
        print("\n[DRY RUN] Validating edit...")
        edits.validate(packageName=package_name, editId=edit_id).execute()
        print("[DRY RUN] Validation passed. Discarding edit.")
        edits.delete(packageName=package_name, editId=edit_id).execute()
    else:
        # Commit the edit — makes it live
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
    print(f"  UPLOADED to '{track_name}' track")
print(f"  {version_name} (watch={watch_code}, phone={phone_code})")
print("══════════════════════════════════")
PYEOF

echo ""
echo "Done."
