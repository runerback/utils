import shutil
from pathlib import Path
from uuid import uuid4

from werkzeug.utils import secure_filename


def prepare_upload_folder(upload_folder: Path) -> None:
    upload_folder.mkdir(parents=True, exist_ok=True)
    for item in upload_folder.iterdir():
        if item.is_dir():
            shutil.rmtree(item)
        else:
            item.unlink()


def save_images(upload_folder: Path, image_files: list) -> list[dict[str, str]]:
    from .config import ALLOWED_IMAGE_EXTENSIONS

    return _save_uploads(
        upload_folder,
        image_files,
        ALLOWED_IMAGE_EXTENSIONS,
        upload_kind="image",
        required_mimetype_prefix="image/",
    )


def save_videos(upload_folder: Path, video_files: list) -> list[dict[str, str]]:
    from .config import ALLOWED_VIDEO_EXTENSIONS

    return _save_uploads(
        upload_folder,
        video_files,
        ALLOWED_VIDEO_EXTENSIONS,
        upload_kind="video",
        required_mimetype_prefix="video/",
    )


def save_files(upload_folder: Path, attachment_files: list) -> list[dict[str, str]]:
    from .config import ALLOWED_FILE_EXTENSIONS

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
