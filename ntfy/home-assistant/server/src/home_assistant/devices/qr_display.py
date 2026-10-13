"""Generate a QR code PNG and show it on the MSU mini USB screen."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path
from typing import Optional

import qrcode
from PIL import Image

from .msu_screen_driver import MSUMiniUSBScreen

MSU_WIDTH = 160
MSU_HEIGHT = 80


def make_qr_payload(
    claim_key: str,
    ssid: str,
    password: str,
    server_host: str,
    server_port: int = 0,
) -> str:
    return json.dumps(
        {
            "claim_key": claim_key,
            "ssid": ssid,
            "pass": password,
            "server_host": server_host,
            "server_port": server_port,
        },
        separators=(",", ":"),
    )


def render_qr_png(
    payload: str,
    border: int = 2,
    size: tuple[int, int] = (MSU_WIDTH, MSU_HEIGHT),
) -> Image.Image:
    """Render at an integer pixels-per-module scale and center on the canvas.

    Resizing a QR to arbitrary pixel dimensions distorts module widths and
    makes the code hard for camera readers to scan, so the box size is chosen
    as an integer and the image is padded instead of stretched.
    """
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=1,
        border=border,
    )
    qr.add_data(payload)
    qr.make(fit=True)
    modules = len(qr.get_matrix())
    box = max(1, min(size[0] // modules, size[1] // modules))
    qr.box_size = box
    img = qr.make_image(fill_color="black", back_color="white").convert("RGB")

    canvas = Image.new("RGB", size, "white")
    canvas.paste(img, ((size[0] - img.width) // 2, (size[1] - img.height) // 2))
    return canvas


def save_qr_png(payload: str, path: str | Path) -> None:
    img = render_qr_png(payload)
    img.save(path)


def show_qr_on_msu(
    payload: str,
    serial_port: str,
) -> None:
    """Render the payload as a QR code and display it on the MSU mini screen."""
    img = render_qr_png(payload, size=(MSU_WIDTH, MSU_HEIGHT))
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        img.save(tmp_path)
        with MSUMiniUSBScreen(serial_port) as screen:
            screen.show_image_file(tmp_path, mode="pad", position="center")
    finally:
        Path(tmp_path).unlink(missing_ok=True)


def show_pairing_qr(
    claim_key: str,
    ssid: str,
    password: str,
    server_host: str,
    server_port: int = 0,
    serial_port: Optional[str] = None,
) -> str:
    """Build the QR payload, optionally display it, and return the payload."""
    payload = make_qr_payload(claim_key, ssid, password, server_host, server_port)
    if serial_port:
        show_qr_on_msu(payload, serial_port)
    return payload
