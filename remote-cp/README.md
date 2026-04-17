# Remote Copy/Paste app

Small Flask + Socket.IO chat room for anonymous text, picture, and file sharing.

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
- General file uploads are limited to document-style formats: `.csv`, `.doc`, `.docx`, `.json`, `.md`, `.pdf`, `.ppt`, `.pptx`, `.rtf`, `.txt`, `.xls`, `.xlsx`, and `.zip`.
- Uploaded non-image files appear in the feed with download links for everyone in the room.
- Video uploads are not supported.
- Direct clipboard image copy needs a secure browser context. It works on `localhost`; for other devices, use HTTPS if the browser blocks clipboard image writes on plain HTTP.
