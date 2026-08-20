# Voice Commands

The voice command feature allows users to interact with the openHAB voice interpreter directly from the watch tile. The system recognizes natural language speech, sends it to the server for interpretation, and optionally reads the response aloud.

## Requirements

- openHAB server version **5.2.1** or later
- A configured voice interpreter on the server (Built-in Interpreter, Rule-based Interpreter, or LLM-based such as OpenAI/Gemini)
- Watch with microphone and speaker (for read-aloud)
- Google Cloud TTS API key (optional, for high-quality WaveNet voices)

## How It Works

1. Tap the mic button on the tile's main page
2. Speak a natural language command (e.g., "turn on the kitchen light")
3. The watch sends the text to the openHAB voice interpreter REST endpoint
4. The server interprets the command and returns a response
5. The response is displayed on screen, and optionally read aloud

### Auto-Dismiss Behavior

- **Read-aloud enabled**: the dialog stays open while the response is being spoken, then closes automatically
- **Read-aloud disabled**: the response is displayed for 2 seconds, then closes
- The user can always tap "OK" to dismiss early

## Tile Mic Button

- When voice commands are **enabled**, a mic pill button appears at the bottom of the tile's main page
- When voice commands are **disabled**, the mic button is hidden and the item grid shifts slightly to fill the available space
- On sub-pages, a back button is shown instead (independent of the voice setting)

## Configuration

Voice settings are configured on the **phone companion app** under **General Settings > Voice Settings**. After saving, press **Sync to Watch** on the home screen to apply changes.

### Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Enable voice commands | On | Shows/hides the mic button on the tile. Automatically disabled if OH < 5.2.1 |
| Read responses aloud | Off | Speaks the interpreter's response via TTS on the watch |
| Use Google WaveNet | Off | Use high-quality server-side voices instead of the watch's built-in TTS |
| Voice | en-US-Wavenet-D | WaveNet voice selection (only visible when WaveNet is enabled) |
| Speech rate | 1.0x | Built-in TTS speech rate (0.25x – 2.0x) |
| Pitch | 1.0x | Built-in TTS pitch (0.25x – 2.0x) |

### Google Cloud TTS Setup

1. Create a Google Cloud project and enable the **Cloud Text-to-Speech API**
2. Create an API key (restrict to Text-to-Speech API recommended)
3. Enter the key in the phone app under **Connection > Google Cloud TTS API Key**
4. In Voice Settings, enable **Use Google WaveNet voices**
5. Select a voice from the picker
6. Save, then Sync to Watch

### Testing Voices

A **Test voice** button is available when WaveNet is enabled. It plays a sample phrase on the phone speaker so you can preview the selected voice before syncing to the watch.

When WaveNet is disabled, the test button plays the built-in TTS engine with the configured speech rate and pitch.

## TTS Engines

| Engine | Quality | Latency | Works Offline |
|--------|---------|---------|---------------|
| Built-in (system TTS) | Varies by device | Instant | Yes |
| Google WaveNet | High (neural) | 1–2 seconds | No |

- **Built-in TTS** uses the watch's Android TextToSpeech engine. Quality depends on the TTS voices installed on the device. Respects the system language setting.
- **Google WaveNet** calls the Google Cloud TTS API from the watch, downloads an MP3 audio clip, and plays it on the watch speaker. Requires network access and a valid API key.

### Limits

- Built-in TTS: responses longer than 200 characters are not spoken
- WaveNet: responses longer than 300 characters are not spoken
- In both cases, the text response is still displayed on screen

## Sync Behavior

Voice settings are part of the global sync payload. When you change any voice setting and save:

1. The home screen shows "Watch config out of sync" above the Sync button
2. Press **Sync to Watch** to push all settings (credentials, theme, tile config, and voice settings) to the watch
3. The watch applies the new voice settings immediately for the next voice command

## Error Scenarios

| Situation | What happens |
|-----------|--------------|
| No interpreter configured on server | Error message shown in the response dialog |
| Network failure during command | Error with "Retry" button |
| Server returns empty response | "Done" is displayed |
| API key missing when WaveNet selected | Falls back to showing text only (no audio) |
| Watch has no speaker | TTS silently skipped, text still shown |
| Speech recognition unavailable | Error shown in dialog |

## openHAB Server Setup

The watch uses the `POST /rest/voice/interpreters` endpoint. Make sure:

1. At least one Human Language Interpreter is configured (Settings > Voice in openHAB UI)
2. The default interpreter is set
3. Items you want to control via voice have appropriate labels and/or semantic tags

Available interpreter types in openHAB 5.x:

- **Built-in Interpreter** — understands basic commands like "turn on the light" in English, German, French, Spanish
- **Rule-based Interpreter** — forwards commands to a configurable Item for custom rule processing
- **LLM-based Interpreters** (e.g., OpenAI, Gemini) — advanced natural language understanding with tool calling, conversation history, and multi-language support
