# Remote Copy/Paste app

Small Flask + Socket.IO chat room for anonymous text, picture, video, and file sharing.

## Setup

1. Create a virtual environment:
   - `py -3 -m venv .venv`
2. Activate it:
   - PowerShell: `.\.venv\Scripts\Activate.ps1`
3. Install dependencies:
   - `python -m pip install -r requirements.txt`

## Run

1. Start the app:
   - `python app.py`
2. Open `http://localhost:5000`
3. If other devices should join, browse to `http://<your-computer-ip>:5000`

## Notes

- Chat messages are kept in memory only and disappear when the app stops.
- Uploaded images and files are stored in `instance\uploads` for the current app run only.
- Video uploads support `.mp4` and appear inline in the room with muted autoplay and playback controls.
- General file uploads are limited to document-style formats: `.csv`, `.doc`, `.docx`, `.json`, `.md`, `.pdf`, `.ppt`, `.pptx`, `.rtf`, `.txt`, `.xls`, `.xlsx`, and `.zip`.
- Uploaded non-image files appear in the feed with download links for everyone in the room.
- Uploaded videos appear in the feed as playable cards for everyone in the room.
- The message box no longer has a fixed input cap. `Send text` posts inline messages up to 4,000 characters and automatically sends longer content to the room as a generated `.txt` file attachment.
- Room messages show newest first.
- Long text posts collapse after 8 visible lines and can be expanded inline.
- `Copy text` first uses the async Clipboard API, then falls back to a browser-compatible selection copy path so it can still work in many Chrome LAN/HTTP sessions.
- `Paste as file` reads clipboard text and saves it locally. Browsers with the File System Access API show a native save dialog; other supported browsers fall back to downloading a generated `.txt` file. If direct clipboard read is blocked, the app falls back to a manual paste dialog.
- `Paste picture` reads an image from the clipboard and posts it straight into the room using the existing picture upload flow. Browsers that block direct clipboard image reads should use `Send pictures` instead.
- Direct clipboard image copy still needs a secure browser context. It works on `localhost`; for other devices, use HTTPS if the browser blocks clipboard image writes on plain HTTP.
