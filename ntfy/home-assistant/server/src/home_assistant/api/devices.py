"""Device onboarding API for the web and Android frontends."""
import asyncio
import json
import logging
import time
from typing import Annotated

from bleak.exc import BleakError
from fastapi import APIRouter, Form, HTTPException, Request
from fastapi.responses import StreamingResponse

from .. import auth, config
from ..devices import ble_scanner, qr_display, security as device_security
from ..devices import db as devices_db

logger = logging.getLogger(__name__)

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
async def start_ble_scan(request: Request, current_user: auth.CurrentUser, csrf: auth.CsrfRequired):
    scanner = request.app.state.ble_scanner
    try:
        await scanner.start()
    except BleakError as exc:
        # e.g. no powered Bluetooth adapter on the host
        logger.warning("BLE scan start failed: %s", exc)
        raise HTTPException(
            status_code=503,
            detail=f"Bluetooth is not available on the server: {exc}",
        ) from exc
    logger.info("BLE scan start requested by %s", current_user)
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
    logger.info("BLE scan stream connected by %s", current_user)

    async def event_generator():
        streamed: set[str] = set()
        try:
            while True:
                if await request.is_disconnected():
                    break
                device = await queue.get()
                payload = {
                    "address": device.address,
                    "name": device.name,
                    "rssi": device.rssi,
                }
                if device.address not in streamed:
                    streamed.add(device.address)
                    logger.info(
                        "BLE stream -> client: %s (%s) rssi=%d",
                        device.name, device.address, device.rssi,
                    )
                yield f"data: {json.dumps(payload)}\n\n"
        finally:
            scanner.off_device(_on_device)
            logger.info("BLE scan stream disconnected by %s", current_user)

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
    # Unsalted deterministic digest so /api/v1/claim can look the row up.
    claim_salt, claim_hash = device_security.hash_secret(claim_key, "")

    devices_db.add_device(
        device_id=device_id,
        token=token,
        token_salt=token_salt,
        token_hash=token_hash,
        claim_key=claim_key,
        claim_salt=claim_salt,
        claim_hash=claim_hash,
        ble_mac=ble_address,
        name=name,
    )

    server_host = _lan_ip()
    try:
        payload = qr_display.show_pairing_qr(
            claim_key=claim_key,
            ssid=ssid,
            password=password,
            server_host=server_host,
            server_port=config.PORT,
            serial_port=config.MSU_SCREEN_PORT or None,
        )
    except Exception as exc:
        # Don't leave an orphaned pending_claim row holding a plaintext token.
        devices_db.remove_device(device_id)
        raise HTTPException(status_code=500, detail=f"QR display failed: {exc}") from exc

    return {
        "device_id": device_id,
        "name": name,
        "status": "pending_claim",
        "qr_payload": payload,
    }
