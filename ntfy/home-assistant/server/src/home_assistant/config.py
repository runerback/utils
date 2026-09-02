import os
from pathlib import Path

from dotenv import load_dotenv

_ENV_DIR = Path(__file__).parents[2]

load_dotenv(_ENV_DIR / ".env")
load_dotenv(_ENV_DIR / ".env.local", override=True)


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

# MQTT broker advertised to devices (may be on a different host than this server).
# The real host/port live in .env.local and must not be committed.
MQTT_HOST = os.environ.get("MQTT_HOST", "localhost").strip() or "localhost"
MQTT_PORT = int(os.environ.get("MQTT_PORT", "1883").strip() or "1883")

# Mosquitto CLI aliases/commands. The actual host/port/user/password/cafile live
# in the alias definition on the server (e.g. alias mqsub='mosquitto_sub ...').
MOSQUITTO_PUB_BIN = os.environ.get("MOSQUITTO_PUB_BIN", "mosquitto_pub")
MOSQUITTO_SUB_BIN = os.environ.get("MOSQUITTO_SUB_BIN", "mosquitto_sub")
MOSQUITTO_CTRL_BIN = os.environ.get("MOSQUITTO_CTRL_BIN", "mosquitto_ctrl")

# Optional MSU mini USB screen serial port. If unset, the QR code is generated
# but not displayed locally.
MSU_SCREEN_PORT = os.environ.get("MSU_SCREEN_PORT", "")
