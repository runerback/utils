from __future__ import annotations

import shutil
from pathlib import Path
from threading import Lock
from uuid import uuid4

from flask import Flask, jsonify, render_template, request, send_from_directory
from flask_socketio import SocketIO
from werkzeug.exceptions import RequestEntityTooLarge
from werkzeug.utils import secure_filename

ALLOWED_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}
ALLOWED_FILE_EXTENSIONS = {
    ".csv",
    ".doc",
    ".docx",
    ".json",
    ".md",
    ".pdf",
    ".ppt",
    ".pptx",
    ".rtf",
    ".txt",
    ".xls",
    ".xlsx",
    ".zip",
}
MESSAGE_STORE: list[dict] = []
STORE_LOCK = Lock()

socketio = SocketIO(cors_allowed_origins="*", async_mode="threading")


def create_app() -> Flask:
    app = Flask(__name__, instance_relative_config=True)
    app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024
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
        attachment_files = request.files.getlist("files")

        try:
            image_payload = _save_images(upload_folder, image_files)
            file_payload = _save_files(upload_folder, attachment_files)
        except ValueError as exc:
            return jsonify({"error": str(exc)}), 400

        if not text and not image_payload and not file_payload:
            return jsonify({"error": "Send text, images, files, or a mix of them."}), 400

        message = {
            "id": str(uuid4()),
            "text": text,
            "deviceType": device_type,
            "clientTimestamp": client_timestamp,
            "images": image_payload,
            "files": file_payload,
        }

        with STORE_LOCK:
            MESSAGE_STORE.append(message)

        socketio.emit("message:new", message)
        return jsonify(message), 201

    @app.get("/uploads/<path:filename>")
    def uploaded_file(filename: str):
        return send_from_directory(upload_folder, filename)

    @app.errorhandler(RequestEntityTooLarge)
    def handle_file_too_large(_: RequestEntityTooLarge):
        return jsonify({"error": "The upload is too large. Keep the total request under 16 MB."}), 413

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


def _save_files(upload_folder: Path, attachment_files: list) -> list[dict[str, str]]:
    return _save_uploads(
        upload_folder,
        attachment_files,
        ALLOWED_FILE_EXTENSIONS,
        upload_kind="file",
        blocked_mimetype_prefixes=("image/", "video/"),
    )


def _save_uploads(
    upload_folder: Path,
    uploaded_files: list,
    allowed_extensions: set[str],
    *,
    upload_kind: str,
    required_mimetype_prefix: str | None = None,
    blocked_mimetype_prefixes: tuple[str, ...] = (),
) -> list[dict[str, str]]:
    saved_images: list[dict[str, str]] = []

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
        saved_images.append(
            {
                "name": original_name,
                "url": f"/uploads/{stored_name}",
            }
        )

    return saved_images


app = create_app()


if __name__ == "__main__":
    socketio.run(app, host="0.0.0.0", port=5000, debug=False)
