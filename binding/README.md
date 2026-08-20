# MobileAudio Binding

openHAB binding that acts as an audio sink for wearOH. Sends TTS audio and notifications directly to the watch via FCM.

**Important:** The binding JAR must NOT be committed to this repository — it contains embedded Firebase service account credentials. Distribute via Google Drive only.

## Compatibility

- openHAB 5.x (tested with 5.2.1+)
- Java 21

## Installation

Copy the JAR to your openHAB `addons/` directory:

```bash
cp org.openhab.binding.mobileaudio-5.3.0-SNAPSHOT.jar /opt/openhab/addons/
```

The binding loads automatically. No restart required.

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
