# Push Notifications & Audio Playback

wearOH receives push notifications from openHAB via FCM (Firebase Cloud Messaging). The watch has its own Firebase project and registers its FCM token directly with the MobileAudio binding on the openHAB server. When a notification is triggered, the binding sends it via FCM directly to the watch — the openHAB Cloud is not involved in the push delivery path.

## Prerequisites

1. **MobileAudio binding** installed on your openHAB server (see [Installation](#installation) below)
2. **Thing configured** — e.g. `mobileaudio:device:MyWatch` (Thing status must be ONLINE)
3. **Watch FCM token registered** — happens automatically when the watch connects to the local server (the `/mobileaudio/register` endpoint is NOT proxied by myopenhab.org — local network required for registration)

## Installation

### MobileAudio Binding

The binding JAR is distributed via Google Drive (not in the git repository — it contains embedded Firebase credentials).

1. Download `org.openhab.binding.mobileaudio-5.3.0-SNAPSHOT.jar` from [Google Drive](https://drive.google.com/drive/u/0/folders/11qmovO3jsNv3oerq3b0_6vnLVZkSaf2j)
2. Copy it to your openHAB server's `addons/` folder:
   ```bash
   scp org.openhab.binding.mobileaudio-5.3.0-SNAPSHOT.jar user@server:/opt/openhab/addons/
   ```
3. The binding loads automatically — no restart needed
4. Add a new Thing of type `mobileaudio:device` via the openHAB UI (Settings → Things → + → MobileAudio Binding)
5. The watch registers its FCM token with the binding when it first connects to the local server

## How It Works

```
openHAB Rule
    → MobileAudio binding sends FCM data message directly to watch
    → FcmMessageListenerService receives it
    → NotificationHandler processes by tag
```

## Notification Types

The `tag` field in the FCM payload determines how the watch handles the message:

| Tag | Behavior | Visual Notification |
|-----|----------|-------------------|
| `audio-tts` | Watch speaks text using configured TTS engine | Yes |
| `audio-sink` | Watch downloads and plays pre-rendered audio URL | No (URL not meaningful) |
| *(any other)* | Shows notification; optionally reads aloud based on priority | Yes |

## Priority & Styling

Notifications are styled by priority:

| Priority | Color | Read Aloud | Vibrate |
|----------|-------|------------|---------|
| `high` | Red | Yes (if meets threshold) | Yes |
| `normal` | Orange | Yes (if meets threshold) | Default |
| `low` | Blue | No (below threshold) | No |

Read-aloud behavior is controlled by the "Min Read Aloud Priority" setting (configurable from the phone companion app).

## openHAB Rule Examples

All examples use JS Scripting (ECMAScript 2024+) with the Rule Builder pattern. Rules have no triggers — execute them manually from the openHAB UI (Settings → Rules → Run Now) or via REST API.

### Local TTS (watch speaks text)

The binding sends the text to the watch via FCM with `tag=audio-tts`. The watch uses its configured TTS engine (local Android TTS or Google Cloud TTS, depending on settings).

```javascript
const { rules, actions } = require('openhab');

rules.JSRule({
  name: 'Test Watch Speak (local TTS)',
  description: 'Sends text to watch for local TTS playback',
  tags: ['Test'],
  triggers: [],
  execute: () => {
    const watch = actions.thingActions('mobileaudio', 'mobileaudio:device:MyWatch');
    watch.speak('This is a local TTS test from the watch');
  }
});
```

### Server TTS (openHAB renders audio, watch streams it)

The server synthesizes audio using a configured TTS service (e.g. Google Cloud TTS via the `googletts` voice), hosts the resulting audio file, and sends the URL to the watch via FCM with `tag=audio-sink`. The watch downloads (with authentication) and plays it.

```javascript
rules.JSRule({
  name: 'Test Watch Say (server TTS)',
  description: 'Server generates audio, watch downloads and plays',
  tags: ['Test'],
  triggers: [],
  execute: () => {
    actions.Voice.say(
      'This is a server TTS test sent to the watch',
      'googletts:enUSWavenetA',
      'mobileaudio:device:MyWatch'
    );
  }
});
```

### High-Priority Notification (vibrate + read aloud)

Sends a regular notification with `priority=high`. The watch shows it in the notification tray (red accent), vibrates, and reads it aloud if the priority meets the configured threshold.

```javascript
rules.JSRule({
  name: 'Test Watch Notify High',
  description: 'Send high-priority notification to watch (vibrate + read aloud)',
  tags: ['Test'],
  triggers: [],
  execute: () => {
    const watch = actions.thingActions('mobileaudio', 'mobileaudio:device:MyWatch');
    watch.notify('Alert: front door opened', 'high');
  }
});
```

### Low-Priority Notification (silent)

Sends a notification with `priority=low`. Appears in the notification tray (blue accent) but does not vibrate or read aloud.

```javascript
rules.JSRule({
  name: 'Test Watch Notify Low',
  description: 'Send low-priority notification to watch (silent, no read aloud)',
  tags: ['Test'],
  triggers: [],
  execute: () => {
    const watch = actions.thingActions('mobileaudio', 'mobileaudio:device:MyWatch');
    watch.notify('Temperature is 22 degrees', 'low');
  }
});
```

## Running Test Rules

### From openHAB UI

Settings → Rules → find rule → "Run Now" button

### From Karaf console

```bash
sshpass -p habopen ssh -o StrictHostKeyChecking=no -o PubkeyAuthentication=no -p 8101 openhab@<server> \
  'openhab:script "getActions(\"mobileaudio\", \"mobileaudio:device:MyWatch\").speak(\"Hello from Karaf\")"'
```

### From REST API

```bash
curl -u 'user:pass' -X POST 'http://<server>:8080/rest/rules/<rule-uid>/runnow' \
  -H 'Content-Type: application/json' -d '{}'
```

## Troubleshooting

### No notification appears

Check `dumpsys notification` for `importance=NONE`. See known-issues #23 for the fix (uninstall/reinstall).

### Audio doesn't play (audio-sink)

The watch downloads audio from the server URL with authentication. If the server is behind a reverse proxy with fail2ban, unauthenticated requests trigger bans. The watch uses `ServerSelector.resolveAuthHeader()` for proper auth — ensure credentials are synced.

### TTS silent on first trigger

Cold start issue — AudioFlinger needs time to initialize. The foreground service keeps the process alive. If only the chime plays, the 5-second timeout ensures TTS proceeds.

### Thing shows OFFLINE

The FCM token registration (`/mobileaudio/register`) only works on the local network. Ensure the watch can reach the openHAB server directly (not through the cloud proxy).

## Advanced: Using Your Own Firebase Project (Optional)

By default, wearOH uses a shared Firebase project for FCM delivery. If you prefer to use your own Firebase project (e.g. quota control), follow these steps:

### 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click **Add project** and follow the wizard
3. Enable **Firebase Cloud Messaging API** in [Google Cloud Console → APIs & Services](https://console.cloud.google.com/apis/library)

### 2. Create a Service Account Key

1. In Firebase Console → **Project Settings** → **Service accounts** tab
2. Click **Generate new private key** → confirm → download the JSON file
3. Go to [Google Cloud Console → IAM](https://console.cloud.google.com/iam-admin/iam)
4. Find your service account (e.g. `firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com`)
5. Click edit (pencil icon) → **Add Another Role** → select **Firebase Cloud Messaging Admin**
6. Save

### 3. Configure the Binding

Place the service account JSON on your openHAB server:

```bash
scp your-service-account.json server:/etc/openhab/firebase-service-account.json
```

Then configure the binding to use it (via openHAB UI → Settings → Things → MobileAudio Device → Configuration):

- **Service Account Path**: `/etc/openhab/firebase-service-account.json`

Or via `.config` file:

```
serviceAccountPath="/etc/openhab/firebase-service-account.json"
```

### 4. Register a Wear OS App in Firebase

1. In Firebase Console → **Project Settings** → **General** tab
2. Click **Add app** → select **Android**
3. Package name: `org.openhab.habdroid.wear`
4. Download `google-services.json`
5. Place it in the watch app source at `watch/` and rebuild

### 5. IAM Propagation

After granting the Firebase Cloud Messaging Admin role, it can take up to 7 minutes for the permission to propagate. If you see HTTP 403 errors with `cloudmessaging.messages.create` permission denied, wait and retry.
