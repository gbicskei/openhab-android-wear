# MobileAudio Binding

openHAB binding that acts as an audio sink for wearOH. Sends TTS audio and notifications directly to the watch via FCM.

**Recommended:** Keep the Firebase service account out of the bundle by placing it under `userdata/` (see [Firebase Service Account](#firebase-service-account) below). A JAR built with an embedded credential must NOT be committed to this repository — distribute those via Google Drive only.

## Compatibility

- openHAB 5.x (tested with 5.2.1+)
- Java 21

## Installation

Copy the JAR to your openHAB `addons/` directory:

```bash
cp org.openhab.binding.mobileaudio-5.3.0-SNAPSHOT.jar /opt/openhab/addons/
```

The binding loads automatically. No restart required.

## Firebase Service Account

The binding needs a Firebase service account JSON to authenticate FCM sends. It is resolved in this order (first match wins):

1. **Configured path** — the binding-level `serviceAccountPath` setting (Settings → Add-on Settings → Mobile Audio Binding, or `conf/services/binding.mobileaudio.cfg`), if set.
2. **Userdata (recommended)** — `<userdata>/mobileaudio/firebase-service-account.json`. This keeps the credential out of the bundle and out of git.
3. **Embedded fallback** — a resource bundled in the JAR, used only if neither of the above is present.

To use the recommended location, place the JSON on the server and lock down permissions so only the openHAB user can read it:

```bash
mkdir -p /var/lib/openhab/mobileaudio
cp firebase-service-account.json /var/lib/openhab/mobileaudio/
chown openhab:openhab /var/lib/openhab/mobileaudio/firebase-service-account.json
chmod 600 /var/lib/openhab/mobileaudio/firebase-service-account.json
```

Adjust the userdata path (`/var/lib/openhab` above) and owner to match your install. Changing the `serviceAccountPath` setting re-initializes the sender without a restart; adding or replacing the userdata file takes effect on the next binding reload.

## Thing Configuration

Add via UI: Settings → Things → + → MobileAudio Binding → MobileAudio Device

The Thing ID becomes the audio sink ID (e.g. `mobileaudio:device:MyWatch`).

## Actions

Available as Thing Actions in rules:

| Action | Description |
|--------|-------------|
| `speak(text)` | Send text to watch for local TTS playback (tag: `audio-tts`) |
| `notify(message, priority)` | Send notification to watch. Priority: `high`, `normal`, `low` |

The binding also registers as an openHAB audio sink, so `actions.Voice.say()` works with it (tag: `audio-sink`).

## FCM Registration

The watch registers its FCM token at:

```
GET {serverUrl}/mobileaudio/register?regId={fcmToken}&deviceId={androidId}&deviceModel={model}&deviceName={name}
```

This endpoint is served by the binding and is NOT proxied through myopenhab.org. The watch must reach the openHAB server on the local network for registration to succeed.

## Source

The binding source is in the `openhab-addons` repository on branch `feature/mobileaudio-binding-5.3`.
