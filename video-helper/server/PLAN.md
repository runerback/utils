# video-helper plan (agreed 2026-11-02)

Goal: run the existing FastAPI app as a uvicorn server on Linux, then build an Android app
(`video-helper-android/`) that replaces the web UI and covers ALL features.

## Phase 1 — Server (Linux deployment)

Status: ~done in this branch (`vid-helper-2`). Code in place:

- `app/auth.py` — `TokenAuthMiddleware`: shared token via `VAE_TOKEN` env, sent as `X-Token`
  header or `?token=` query param (query needed for media clients). Exempt: `/`, `/health`, `/static/*`.
  Disabled when `VAE_TOKEN` unset (local dev).
- `app/jobs.py` — `JobRunner`: single background thread, serial renders, history capped at 200.
- Job endpoints (POST returns `{job_id}`, poll `GET /api/jobs/{id}`):
  - `POST /api/projects/{id}/jobs/preview`
  - `POST /api/projects/{id}/jobs/export` (mp4)
  - `POST /api/projects/{id}/jobs/export/gif`
- Download endpoint: `GET /api/projects/{id}/export/download`
- `deploy/video-helper.service` — systemd unit (uvicorn, `0.0.0.0:31692`, user `vae`,
  working dir `/opt/video-helper`).
- Linux path hardening: `VAE_UPLOADS_PATH` / `VAE_EXPORT_PATH` / `VAE_WORK_PATH` / `VAE_LOG_PATH` env
  vars (+ `--*-path` CLI flags), log file resolution handles missing `.venv`.

### Remaining steps

1. Deploy to Linux host:
   - copy repo to `/opt/video-helper`, create `vae` user
   - `python -m venv .venv && .venv/bin/pip install -r requirements.txt`
   - ffmpeg must be on the host; TransNetV2 model via `VAE_TRANSNETV2_MODEL_PATH` if AI scene split is used
2. Install service:
   - copy `deploy/video-helper.service` to `/etc/systemd/system/`
   - set a real `VAE_TOKEN` in the unit
   - `systemctl daemon-reload && systemctl enable --now video-helper`
3. Verify:
   - `curl http://<host>:31692/health` → 200 without token
   - `curl http://<host>:31692/api/projects` → 401 without token, 200 with `X-Token`

## Phase 2 — Android app (`video-helper-android/`)

**Progress (2026-11-04):** scaffolded at `D:\Projects\utils-vid-helper-2\video-helper-android`
(package `com.runerback.videohelper`). Done: gradle wrapper + build files (AGP 8.7.3, Kotlin 2.1.0,
Hilt 2.55, KSP, Compose BOM 2024.12.01, Ktor 2.3.13, Media3 1.5.1, navigation-compose), manifest,
resources/launcher icon, `VideoHelperApp`, `MainActivity`, theme, `SettingsRepository` (DataStore:
server URL + token), API DTOs mirroring server schemas (`Dtos.kt`), `VideoHelperApi` (Ktor: list,
upload multipart, get, PUT state, gif-estimate, job create/poll, mediaUrl with ?token=),
`ExportDownloader` (DownloadManager → public Movies dir), NavHost (projects / settings /
editor/{projectId}), Settings screen, Projects screen (list + upload via GetContent),
Editor screen + ViewModel (ExoPlayer playback of original/preview, trim RangeSlider, crop, rotate,
speed chips, resize, freeze frame, scene split incl. ffmpeg/AI detectors + fixed length, clip
selection, preview/export MP4/GIF via jobs + polling, GIF estimate, download-to-device).

**Not done / verify next:** project has never been built — first `./gradlew assembleDebug` may
surface compile fixes (imports, API mismatches). UX polish: error retry, upload progress bar,
preview part thumbnails, delete-project action. Also: 2 pre-existing `test_main.py` log-file tests
fail in video-helper (resolve_log_file checks `.venv` existence — fix: always prefer
`ROOT/.venv/vae.log`).

Stack copied from reference app at `D:\Projects\utils-cmf-api\comfyui-api-app`
(read its Android files only, not ComfyUI specifics):

- Kotlin, Compose M3, Hilt, Ktor (OkHttp), DataStore, MVVM
- compileSdk 35 / minSdk 26
- Media3/ExoPlayer for playback
- Results saved to device via MediaStore
- Server URL + token in Settings screen (token sent as `X-Token` header)

### Feature coverage (all existing features)

- upload video, project list, playback of original
- trim, crop, resize, rotate, speed, fps
- scene split: ffmpeg detector AND TransNetV2 AI mode, clip selection
- export MP4 + GIF, with GIF size estimate

### Build order

1. Scaffold project: Gradle files, Hilt app, Compose theme, NavHost, Settings screen
   (server URL + token persisted in DataStore).
2. API layer (Ktor): project list (`GET /api/projects`), upload (`POST /api/projects`),
   playback of `/api/projects/{id}/original` in ExoPlayer.
3. Editing UI: state models mirroring `EditState`, PUT `/api/projects/{id}/state`,
   preview via `POST .../jobs/preview` + poll `GET /api/jobs/{id}`.
4. Scene split UI: detector picker (ffmpeg / AI), thresholds, clip grid with selection.
5. Export UI: MP4 + GIF, GIF estimate (`POST .../export/gif-estimate`), export via jobs +
   polling, then download `GET .../export/download` into MediaStore.
6. Polish: error handling, progress indicators, token retry on 401.

## Reference notes

- Server port: 31692
- Auth: `X-Token: <VAE_TOKEN>` header, or `?token=` query for media streams
- Poll jobs: `GET /api/jobs/{job_id}` → status queued/running/done/error, `result` holds the
  render response (output_url / parts), `error` holds failure message
