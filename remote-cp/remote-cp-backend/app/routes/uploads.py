from pathlib import Path

from flask import Blueprint, current_app, jsonify, send_from_directory

uploads_bp = Blueprint("uploads", __name__)


@uploads_bp.get("/uploads/<path:filename>")
def uploaded_file(filename: str):
    upload_folder = Path(current_app.config["UPLOAD_FOLDER"])
    return send_from_directory(upload_folder, filename)


@uploads_bp.get("/downloads/<path:stored_name>/<path:download_name>")
def download_file(stored_name: str, download_name: str):
    upload_folder = Path(current_app.config["UPLOAD_FOLDER"])
    safe_download_name = Path(download_name).name.replace("\x00", "").replace("\n", "").replace("\r", "")
    if not safe_download_name:
        return jsonify({"error": "Downloaded file names must include letters or numbers."}), 400
    return send_from_directory(
        upload_folder,
        stored_name,
        as_attachment=True,
        download_name=safe_download_name,
    )
