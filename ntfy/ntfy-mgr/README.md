# ntfy-mgr

A management interface for a local ntfy server: users, topic access rules, and user tokens.

This repository is split into three sub-projects:

- [`ntfy-mgr-server`](ntfy-mgr-server/) — Headless Python API server (FastAPI) that wraps the local `ntfy` CLI.
- [`ntfy-mgr-app`](ntfy-mgr-app/) — Android client written in Kotlin and Jetpack Compose.
- [`ntfy-mgr-web`](ntfy-mgr-web/) — Lightweight browser client built with React and Vite.

All clients talk to `ntfy-mgr-server` over the same HTTP/JSON API.

## Requirements

- A running ntfy server on the same host as `ntfy-mgr-server`.
- Root access on the server host (the service executes the `ntfy` CLI directly).
- Python 3.x for the server.
- Android SDK for the Android app.
- Node.js 18+ for the web client.

## Server deployment

1. Copy the project to `/opt/ntfy-mgr`:
   ```bash
   cp -r . /opt/ntfy-mgr
   cd /opt/ntfy-mgr/ntfy-mgr-server
   ```

2. Create a virtual environment and install dependencies:
   ```bash
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   ```

3. Edit `ntfy-mgr.service` and set the required environment variables:
   - `NTFY_MGR_PORT` — a free port greater than 20000.
   - `SECRET_KEY` — a long random string, e.g. output of `openssl rand -hex 32`.
   - `NTFY_BASE_URL` — the URL of the running ntfy server (default `http://localhost`).
   - `NTFY_MGR_CORS_ORIGINS` — comma-separated origins allowed to call the API.

4. Install and start the systemd service:
   ```bash
   cp ntfy-mgr.service /etc/systemd/system/
   systemctl daemon-reload
   systemctl enable --now ntfy-mgr
   ```

5. Open the chosen port in your firewall as needed.

6. Configure nginx using `ntfy-mgr.nginx` as a template.

## Server local development

Create a `.env.local` file in `ntfy-mgr-server/`:

```bash
NTFY_MGR_HOST=127.0.0.1
NTFY_MGR_PORT=20808
SECRET_KEY=dev-secret-change-me
NTFY_BASE_URL=http://localhost
NTFY_CLI=ntfy
NTFY_MGR_CORS_ORIGINS=http://localhost:5173
```

Then run:

```bash
cd ntfy-mgr-server
.venv/bin/uvicorn ntfy_mgr_server.main:app --reload
```

FastAPI auto-generated docs are available at `/docs`.

## Web client

```bash
cd ntfy-mgr-web
npm install
npm run dev
```

Set `VITE_API_URL` to point at the server, or use the Vite dev proxy (default `http://127.0.0.1:20808`).

## Android client

Open `ntfy-mgr-app/` in Android Studio, or build from the command line:

```bash
cd ntfy-mgr-app
./gradlew assembleDebug
```

Set the server URL in `local.properties` (`API_URL`) or in the app login screen.

## API contract

All endpoints return JSON. Errors use `{ "detail": "..." }`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/login` | no | Validate ntfy admin credentials, return token |
| POST | `/auth/logout` | yes | (Optional) invalidate token |
| GET | `/users` | yes | List users and their accesses/tokens |
| POST | `/users` | yes | Create user |
| DELETE | `/users/{name}` | yes | Delete user |
| POST | `/users/{name}/access` | yes | Grant topic access |
| DELETE | `/users/{name}/access/{topic}` | yes | Revoke topic access |
| POST | `/users/{name}/tokens` | yes | Create token |
| DELETE | `/users/{name}/tokens/{token}` | yes | Delete token |
| GET | `/topics` | yes | List topics with accessors |
| POST | `/topics/{topic}/access` | yes | Grant user access |
| DELETE | `/topics/{topic}/access/{username}` | yes | Revoke user access |
| DELETE | `/topics/{topic}` | yes | Delete topic |

## Notes

- The server validates admin credentials against the running ntfy server (`GET {NTFY_BASE_URL}/v1/account`).
- Successful login returns a short-lived JWT; clients must send it as `Authorization: Bearer <token>`.
- "Create topic" and "Delete topic" operate on access rules; ntfy itself creates topics on first publish.
- No separate database is used; all state changes are performed through the `ntfy` CLI.
