from uuid import uuid4

from flask import Blueprint, current_app, jsonify, request, url_for

from ..config import MAX_UPLOAD_SIZE_MB
from ..models import message_store
from ..sockets import socketio
from ..storage import save_files, save_images, save_videos

messages_bp = Blueprint("messages", __name__)


@messages_bp.get("/api/messages")
def get_messages() -> dict:
    return {"messages": message_store.get_all()}


@messages_bp.post("/api/messages")
def create_message() -> tuple[dict, int]:
    text = request.form.get("text", "").strip()
    device_type = request.form.get("device_type", "").strip() or "Unknown device"
    client_timestamp = request.form.get("client_timestamp", "").strip() or "Unknown time"
    image_files = request.files.getlist("images")
    video_files = request.files.getlist("videos")
    attachment_files = request.files.getlist("files")

    upload_folder = current_app.config["UPLOAD_FOLDER"]

    try:
        image_payload = save_images(upload_folder, image_files)
        video_payload = save_videos(upload_folder, video_files)
        file_payload = save_files(upload_folder, attachment_files)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400

    for uploaded_file in file_payload:
        uploaded_file["downloadUrl"] = url_for(
            "uploads.download_file",
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

    message_store.append(message)
    socketio.emit("message:new", message)
    return jsonify(message), 201
