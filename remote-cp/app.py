from __future__ import annotations

import shutil
from pathlib import Path
from threading import Lock
from uuid import uuid4

from flask import Flask, jsonify, render_template, request, send_from_directory, url_for
from flask_socketio import SocketIO
from werkzeug.exceptions import RequestEntityTooLarge
from werkzeug.utils import secure_filename

ALLOWED_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}
ALLOWED_VIDEO_EXTENSIONS = {".mp4"}
ALLOWED_FILE_EXTENSIONS = {
    ".7z",
    ".csv",
    ".doc",
    ".docx",
    ".json",
    ".md",
    ".pdf",
    ".ppt",
    ".pptx",
    ".pt",
    ".rtf",
    ".txt",
    ".xls",
    ".xlsx",
    ".zip",
}
MAX_UPLOAD_SIZE_MB = 500
MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024
MESSAGE_STORE: list[dict] = []
STORE_LOCK = Lock()

socketio = SocketIO(cors_allowed_origins="*", async_mode="threading")


def create_app() -> Flask:
    app = Flask(__name__, instance_relative_config=True)
    app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_SIZE_BYTES
    app.config["UPLOAD_FOLDER"] = Path(app.instance_path) / "uploads"

    upload_folder = app.config["UPLOAD_FOLDER"]
    _prepare_upload_folder(upload_folder)
    socketio.init_app(app)

    @app.get("/")
    def index() -> str:
        with STORE_LOCK:
            existing_messages = list(MESSAGE_STORE)
        return render_template("index.html", existing_messages=existing_messages)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/api/messages")
    def create_message():
        text = request.form.get("text", "").strip()
        device_type = request.form.get("device_type", "").strip() or "Unknown device"
        client_timestamp = request.form.get("client_timestamp", "").strip() or "Unknown time"
        image_files = request.files.getlist("images")
        video_files = request.files.getlist("videos")
        attachment_files = request.files.getlist("files")

        try:
            image_payload = _save_images(upload_folder, image_files)
            video_payload = _save_videos(upload_folder, video_files)
            file_payload = _save_files(upload_folder, attachment_files)
        except ValueError as exc:
            return jsonify({"error": str(exc)}), 400

        for uploaded_file in file_payload:
            uploaded_file["downloadUrl"] = url_for(
                "download_file",
                stored_name=uploaded_file.pop("storedName"),
                download_name=uploaded_file["name"],
            )

        if not text and not image_payload and not video_payload and not file_payload:
            return jsonify({"error": "Send text, images, videos, files, or a mix of them."}), 400

        message = {
            "id": str(uuid4()),
            "text": text,
            "deviceType": device_type,
            "clientTimestamp": client_timestamp,
            "images": image_payload,
            "videos": video_payload,
            "files": file_payload,
        }

        with STORE_LOCK:
            MESSAGE_STORE.append(message)

        socketio.emit("message:new", message)
        return jsonify(message), 201

    @app.get("/uploads/<path:filename>")
    def uploaded_file(filename: str):
        return send_from_directory(upload_folder, filename)

    @app.get("/downloads/<path:stored_name>/<path:download_name>")
    def download_file(stored_name: str, download_name: str):
        safe_download_name = secure_filename(download_name)
        if not safe_download_name:
            return jsonify({"error": "Downloaded file names must include letters or numbers."}), 400
        return send_from_directory(
            upload_folder,
            stored_name,
            as_attachment=True,
            download_name=safe_download_name,
        )

    @app.errorhandler(RequestEntityTooLarge)
    def handle_file_too_large(_: RequestEntityTooLarge):
        return (
            jsonify(
                {
                    "error": (
                        f"The upload is too large. Keep the total request under {MAX_UPLOAD_SIZE_MB} MB."
                    )
                }
            ),
            413,
        )

    return app


def _prepare_upload_folder(upload_folder: Path) -> None:
    upload_folder.mkdir(parents=True, exist_ok=True)
    for item in upload_folder.iterdir():
        if item.is_dir():
            shutil.rmtree(item)
        else:
            item.unlink()


def _save_images(upload_folder: Path, image_files: list) -> list[dict[str, str]]:
    return _save_uploads(
        upload_folder,
        image_files,
        ALLOWED_IMAGE_EXTENSIONS,
        upload_kind="image",
        required_mimetype_prefix="image/",
    )


def _save_videos(upload_folder: Path, video_files: list) -> list[dict[str, str]]:
    return _save_uploads(
        upload_folder,
        video_files,
        ALLOWED_VIDEO_EXTENSIONS,
        upload_kind="video",
        required_mimetype_prefix="video/",
    )


def _save_files(upload_folder: Path, attachment_files: list) -> list[dict[str, str]]:
    return _save_uploads(
        upload_folder,
        attachment_files,
        ALLOWED_FILE_EXTENSIONS,
        upload_kind="file",
        blocked_mimetype_prefixes=("image/", "video/"),
        include_stored_name=True,
    )


def _save_uploads(
    upload_folder: Path,
    uploaded_files: list,
    allowed_extensions: set[str],
    *,
    upload_kind: str,
    required_mimetype_prefix: str | None = None,
    blocked_mimetype_prefixes: tuple[str, ...] = (),
    include_stored_name: bool = False,
) -> list[dict[str, str]]:
    saved_uploads: list[dict[str, str]] = []

    for uploaded_file in uploaded_files:
        if not uploaded_file or not uploaded_file.filename:
            continue

        original_name = secure_filename(uploaded_file.filename)

        if not original_name:
            raise ValueError(f"Uploaded {upload_kind} names must include letters or numbers.")

        extension = Path(original_name).suffix.lower()

        if extension not in allowed_extensions:
            raise ValueError(f"Unsupported {upload_kind} type for '{original_name}'.")

        mimetype = (uploaded_file.mimetype or "").lower()

        if required_mimetype_prefix and not mimetype.startswith(required_mimetype_prefix):
            raise ValueError(f"'{original_name}' is not recognized as an {upload_kind} upload.")

        if any(mimetype.startswith(prefix) for prefix in blocked_mimetype_prefixes):
            raise ValueError(f"Unsupported {upload_kind} type for '{original_name}'.")

        stored_name = f"{uuid4().hex}{extension}"
        destination = upload_folder / stored_name
        uploaded_file.save(destination)
        upload_payload = {
            "name": original_name,
            "url": f"/uploads/{stored_name}",
        }
        if include_stored_name:
            upload_payload["storedName"] = stored_name
        saved_uploads.append(upload_payload)

    return saved_uploads


app = create_app()


if __name__ == "__main__":
    socketio.run(app, host="0.0.0.0", port=5000, debug=False)
