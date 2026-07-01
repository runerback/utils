import os

ALLOWED_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}
ALLOWED_VIDEO_EXTENSIONS = {".mp4"}
ALLOWED_FILE_EXTENSIONS = {
    ".7z",
    ".apk",
    ".csv",
    ".doc",
    ".docx",
    ".json",
    ".md",
    ".pdf",
    ".ppt",
    ".pptx",
    ".pt",
    ".py",
    ".rtf",
    ".txt",
    ".xls",
    ".xlsx",
    ".zip",
}
FILE_INPUT_ACCEPT = ",".join(sorted(ALLOWED_FILE_EXTENSIONS))
MAX_UPLOAD_SIZE_MB = 500
MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024

CORS_ALLOWED_ORIGINS = os.getenv(
    "CORS_ALLOWED_ORIGINS",
    "*",
).split(",")
