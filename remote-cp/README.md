# Remote Copy/Paste

Ephemeral cross-device text, picture, video, and file sharing.

This repo contains three projects:

- **`remote-cp-backend/`** — Flask + Socket.IO API server
- **`remote-cp-web/`** — Vite + Preact + TypeScript web app
- **`remote-cp-android/`** — Native Android app (Kotlin + Jetpack Compose)

## Quick Start

### Backend

```bash
cd remote-cp-backend
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python run.py
```

Server runs on `http://localhost:5000`.

### Web App

```bash
cd remote-cp-web
npm install
npm run dev
```

Web app runs on `http://localhost:5180`.

### Android App

Open `remote-cp-android/` in Android Studio, sync Gradle, and run on an emulator or device.

Default backend URL for emulator: `http://10.0.2.2:5000`. Use the settings icon to change it.

## LAN Access

To use the web app from other devices on your local network:

1. **Find your machine's LAN IP** (e.g. `192.168.1.100`)
2. **Update the web app's backend URL** in `remote-cp-web/.env`:
   ```
   VITE_BACKEND_URL=http://192.168.1.100:5000
   ```
3. **Allow CORS on the backend** by setting the environment variable:
   ```bash
   # Windows PowerShell
   $env:CORS_ALLOWED_ORIGINS="*"
   python run.py
   
   # Or restrict to your LAN IP only
   $env:CORS_ALLOWED_ORIGINS="http://192.168.1.100:5180"
   python run.py
   ```
4. **Access from other devices** at `http://192.168.1.100:5180`

The web dev server already binds to `0.0.0.0` (all interfaces), so no additional config is needed there.

## Architecture

All three clients talk to the same Flask backend:

- `GET /api/messages` — fetch existing messages
- `POST /api/messages` — send a new message (multipart/form-data)
- `GET /uploads/<filename>` — serve media inline
- `GET /downloads/<stored>/<name>` — download a file
- Socket.IO `message:new` — real-time message broadcast

Messages are stored in memory only and disappear when the server restarts.
