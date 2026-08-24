# ntfy-mgr

A minimal web UI for managing a local ntfy server: users, topic access rules, and user tokens.

## Features

- Login validated against the running ntfy server (admin only).
- **Users** tab: add/delete users, grant/revoke topic access, add/delete tokens.
- **Topics** tab: create/delete topics, grant/revoke user access per topic.
- Runs as a systemd service on Linux.

## Requirements

- Python 3.x
- ntfy server running on the same host
- Root access (the service executes the `ntfy` CLI directly)

## Install

1. Copy the project to `/opt/ntfy-mgr`:
   ```bash
   cp -r . /opt/ntfy-mgr
   cd /opt/ntfy-mgr
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

4. Install and start the systemd service:
   ```bash
   cp ntfy-mgr.service /etc/systemd/system/
   systemctl daemon-reload
   systemctl enable --now ntfy-mgr
   ```

5. Open the chosen port in your firewall as needed.

## Local development

Create a `.env` file in the project root:

```bash
NTFY_MGR_HOST=127.0.0.1
NTFY_MGR_PORT=20808
SECRET_KEY=dev-secret-change-me
NTFY_BASE_URL=http://localhost
```

Then run:

```bash
python app.py
```

## Notes

- The session cookie expires when the browser tab is closed or the user clicks **Logout**.
- "Create topic" and "Delete topic" in the UI operate on access rules; ntfy itself creates topics on first publish.
- All state changes are performed through the `ntfy` CLI; no separate database is used.
