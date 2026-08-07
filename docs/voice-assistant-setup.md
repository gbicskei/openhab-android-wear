# Voice Assistant Setup

The openHAB Wear OS app can be used as the default voice assistant on your watch.
Long-press the Home (power) button to trigger voice commands that are sent to
the openHAB voice interpreter.

## How It Works

When triggered, the app:
1. Listens for your voice command using Google Speech Recognition
2. Sends the recognized text to openHAB's REST API (`POST /rest/voice/interpreters`)
3. Displays the response (and optionally reads it aloud via TTS)

## Setup (One-Time)

The setup requires a single ADB command from a PC. This grants the watch app
permission to register itself as the system assistant.

### Prerequisites

- openHAB Wear OS app installed on your watch
- ADB installed on your PC ([download](https://developer.android.com/tools/releases/platform-tools))
- Watch connected to the same WiFi network as your PC (for wireless ADB)

### Steps

1. **Enable Developer Options on the watch:**
   - Settings → About watch → tap "Software version" 5 times
   - A toast will confirm Developer options are enabled

2. **Enable Wireless Debugging:**
   - Settings → Developer options → Wireless debugging → enable
   - Tap "Wireless debugging" to see the IP:port
   - Tap "Pair new device" to get a pairing code

3. **Connect from your PC:**
   ```bash
   # Pair (first time only)
   adb pair <ip>:<pairing-port>
   # Enter the 6-digit code when prompted

   # Connect
   adb connect <ip>:<connection-port>
   ```

4. **Grant the permission:**
   ```bash
   adb shell pm grant org.openhab.habdroid.wear android.permission.WRITE_SECURE_SETTINGS
   ```

5. **Open the openHAB app on the watch** (once, so it registers itself)

6. **Done!** Long-press the Home button to use voice commands.

You can now disable Wireless debugging on the watch. The permission persists
across reboots and the app re-registers itself automatically.

## Verification

In the phone companion app, go to Settings → "Watch Voice Assistant" section
and tap "Check watch status" to verify the setup is complete.

## Why ADB Is Required

Android restricts `WRITE_SECURE_SETTINGS` to system apps and the ADB shell user.
Third-party apps cannot grant themselves this permission — it must come from ADB.
This is an Android platform limitation affecting all third-party assistant apps
(including the Claude voice assistant, custom Gemini replacements, etc.).

Samsung Wear OS additionally has a known issue where their "Default digital
assistant" settings UI doesn't properly write the `voice_interaction_service`
secure setting for third-party apps, making the ADB approach the only reliable
method.
