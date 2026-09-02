"""Device onboarding API for the web and Android frontends."""
import asyncio
import json
import time
from typing import Annotated

from fastapi import APIRouter, Form, HTTPException, Request
from fastapi.responses import StreamingResponse

from .. import auth, config
from ..devices import ble_scanner, qr_display, security as device_security
from ..devices import db as devices_db

router = APIRouter()


def _lan_ip() -> str:
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


@router.get("/devices")
def list_devices(current_user: auth.CurrentUser):
    return devices_db.list_devices()


@router.post("/devices/ble-scan/start")
async def start_ble_scan(request: Request, current_user: auth.CurrentUser):
    scanner = request.app.state.ble_scanner
    await scanner.start()
    return {"ok": True}


@router.get("/devices/ble-scan")
async def ble_scan_stream(request: Request, current_user: auth.CurrentUser):
    scanner = request.app.state.ble_scanner
    queue: asyncio.Queue[ble_scanner.BleDevice] = asyncio.Queue()

    def _on_device(device: ble_scanner.BleDevice) -> None:
        try:
            queue.put_nowait(device)
        except asyncio.QueueFull:
            pass

    scanner.on_device(_on_device)

    async def event_generator():
        while True:
            device = await queue.get()
            payload = {
                "address": device.address,
                "name": device.name,
                "rssi": device.rssi,
            }
            yield f"data: {json.dumps(payload)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
    )


@router.post("/devices/pair")
def pair_device(
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
    ble_address: Annotated[str, Form()],
    name: Annotated[str, Form()],
    ssid: Annotated[str, Form()],
    password: Annotated[str, Form()],
):
    """Generate credentials for the selected BLE device and show the QR code."""
    device_id = device_security.generate_device_id()
    token = device_security.generate_access_token()
    claim_key = device_security.generate_claim_key()
    token_salt, token_hash = device_security.hash_secret(token)
    claim_salt, claim_hash = device_security.hash_secret(claim_key)

    devices_db.add_device(
        device_id=device_id,
        token=token,
        token_salt=token_salt,
        token_hash=token_hash,
        claim_key=claim_key,
        claim_salt=claim_salt,
        claim_hash=claim_hash,
        ble_mac=ble_address,
    )

    server_host = _lan_ip()
    payload = qr_display.show_pairing_qr(
        claim_key=claim_key,
        ssid=ssid,
        password=password,
        server_host=server_host,
        serial_port=config.MSU_SCREEN_PORT or None,
    )

    return {
        "device_id": device_id,
        "name": name,
        "status": "pending_claim",
        "qr_payload": payload,
    }
