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


HOST = os.environ.get("NTFY_MGR_HOST", "0.0.0.0").strip() or "0.0.0.0"
PORT = int(_require("NTFY_MGR_PORT"))
SECRET_KEY = _require("SECRET_KEY")

NTFY_BASE_URL = os.environ.get("NTFY_BASE_URL", "http://localhost").rstrip("/")
NTFY_CLI = os.environ.get("NTFY_CLI", "ntfy").strip() or "ntfy"
