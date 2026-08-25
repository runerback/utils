import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).with_name(".env"))
load_dotenv(Path(__file__).with_name(".env.local"), override=True)


def _require(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Environment variable {name} is required")
    return value


HOST = os.environ.get("HOME_ASSISTANT_HOST", "0.0.0.0").strip() or "0.0.0.0"
PORT = int(_require("HOME_ASSISTANT_PORT"))
SECRET_KEY = _require("SECRET_KEY")

NTFY_BASE_URL = os.environ.get("NTFY_BASE_URL", "http://localhost").rstrip("/")
DATABASE_PATH = os.environ.get("DATABASE_PATH", "/var/lib/home-assistant/home-assistant.db")
LOG_PATH = os.environ.get("LOG_PATH", "/var/log/home-assistant/app.log")
