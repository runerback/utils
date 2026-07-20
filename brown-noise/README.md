# Brown Noise

Realtime brown/white/pink noise streaming from a Python server to an Android app.

## Project layout

- `brown-noise-backend/` — Python server using `sounddevice` and `numpy`
- `brown-noise-android/` — Android app using Jetpack Compose and `AudioTrack`

## Quick start

### Python server

```bash
python -m venv .venv
./.venv/Scripts/pip install -r brown-noise-backend/requirements.txt
./.venv/Scripts/python brown-noise-backend/run.py --port 54545
```

Options:

```bash
./.venv/Scripts/python brown-noise-backend/run.py --type brown --channels 2 --surround 0.3 --reverb 0.2
```

Supported noise types: `brown`, `white`, `pink`, plus a `tune` test tone to verify settings are applied.

### Android app

```powershell
cd brown-noise-android
./build.ps1
```

The APK is built to `app/build/outputs/apk/debug/brownnoise-debug.apk`.

Install it on a device or emulator, enter the server IP and port, and tap **Connect**.

## Features

- Stereo streaming over TCP (port `54545`)
- Control channel on TCP port `54546` for realtime adjustments
- Local playback on the server via `sounddevice`
- Background playback with a foreground service
- Adjustable volume and server address
- Android settings dialog: noise type, gain, surround, reverb
- Optional stereo widening / reverb effects
- Modular noise sources for future sound types
