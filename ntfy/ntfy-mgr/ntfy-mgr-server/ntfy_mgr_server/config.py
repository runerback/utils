import os
from pathlib import Path

from dotenv import load_dotenv

_PROJECT_ROOT = Path(__file__).resolve().parent.parent

load_dotenv(_PROJECT_ROOT / ".env")
load_dotenv(_PROJECT_ROOT / ".env.local", override=True)


def _require(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Environment variable {name} is required")
    return value


HOST = os.environ.get("NTFY_MGR_HOST", "0.0.0.0").strip() or "0.0.0.0"
PORT = int(_require("NTFY_MGR_PORT"))
SECRET_KEY = _require("SECRET_KEY")
_TTL_RAW = os.environ.get("NTFY_MGR_TOKEN_TTL_SECONDS", "86400").strip()
TOKEN_TTL_SECONDS = int(_TTL_RAW) if _TTL_RAW else 86400

NTFY_BASE_URL = os.environ.get("NTFY_BASE_URL", "http://localhost").rstrip("/") or "http://localhost"
NTFY_CLI = os.environ.get("NTFY_CLI", "ntfy").strip() or "ntfy"

_CORS_RAW = os.environ.get("NTFY_MGR_CORS_ORIGINS", "")
CORS_ORIGINS = [origin.strip() for origin in _CORS_RAW.split(",") if origin.strip()]
