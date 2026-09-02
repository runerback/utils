"""Device-facing claim API used by the ESP32 after scanning the QR code."""
import time
from typing import Any

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from .. import config
from ..devices import db as devices_db
from ..devices import mqtt_accounts, security as device_security

router = APIRouter()

_RATE_LIMIT = 5
_rate: dict[str, list[float]] = {}


class ClaimRequest(BaseModel):
    claim_key: str


class ClaimResponse(BaseModel):
    device_id: str
    access_token: str
    mqtt: dict[str, Any]


def _rate_ok(ip: str) -> bool:
    now = int(time.time())
    bucket = _rate.setdefault(ip, [now, 0])
    if bucket[0] != now:
        bucket[0], bucket[1] = now, 0
    bucket[1] += 1
    return bucket[1] <= _RATE_LIMIT


@router.post("/api/v1/claim")
def claim(request: Request, body: ClaimRequest) -> ClaimResponse:
    ip = request.client.host if request.client else ""
    if not _rate_ok(ip):
        raise HTTPException(status_code=429, detail="rate limited")

    if not body.claim_key:
        raise HTTPException(status_code=400, detail="claim_key required")

    claim_salt, claim_hash = device_security.hash_secret(body.claim_key)
    row = devices_db.get_device_by_claim_hash(claim_hash)

    # Unknown device or already claimed: same message to prevent enumeration.
    if not row or row["status"] != "pending_claim":
        raise HTTPException(status_code=403, detail="invalid credentials")

    if not device_security.verify_secret(body.claim_key, row["claim_salt"], row["claim_hash"]):
        raise HTTPException(status_code=403, detail="invalid credentials")

    device_id = row["device_id"]
    mqtt_password = device_security.generate_mqtt_password()

    try:
        mqtt_accounts.create_mqtt_user(device_id, mqtt_password)
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    devices_db.set_mqtt_password(device_id, mqtt_password)
    devices_db.set_status(device_id, "pending_status")

    return ClaimResponse(
        device_id=device_id,
        access_token=row["token_plain"],
        mqtt={
            "host": config.MQTT_HOST,
            "port": config.MQTT_PORT,
            "username": device_id,
            "password": mqtt_password,
        },
    )
