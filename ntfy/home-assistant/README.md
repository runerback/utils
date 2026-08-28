# Home Assistant Dashboard

A small dashboard for Home Assistant with ntfy messaging.

## Structure

- `server/` — FastAPI Python server (JSON API + SSE).
  - `server/home-assistant.nginx` — nginx config for production.
  - `server/home-assistant.service` — systemd unit.
- `web/` — Vite + Preact single-page app.
  - `web/home-assistant.web.nginx` — web-focused nginx sample.

## Development

1. Copy `server/.env.example` to `server/.env.local` and fill in values.
2. Start the server:
   ```bash
   cd server
   pip install -e ".[test]"
   uvicorn home_assistant.main:app --reload --port 8000
   ```
3. In another terminal, start the web dev server:
   ```bash
   cd web
   npm install
   npm run dev
   ```

The Vite dev server proxies `/api` to `http://127.0.0.1:8000`.

## Production

1. Build the web app:
   ```bash
   cd web
   npm install
   npm run build
   ```
2. Serve with nginx using `server/home-assistant.nginx` and run the server behind it:
   ```bash
   cd server
   pip install -e .
   uvicorn home_assistant.main:app --host 127.0.0.1 --port 8000
   ```

Alternatively, the server can self-serve `web/dist` when it exists; run uvicorn and open the server's URL.

## Tests

```bash
cd server
pytest
```
