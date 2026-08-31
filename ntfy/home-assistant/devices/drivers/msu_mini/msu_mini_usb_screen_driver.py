#!/usr/bin/env python3
"""
Python driver for the Mori Ateliers / MSU Mini USB Screen.

Reverse-engineered from the C# logic in UsbScreen.Core.
The screen presents itself as a USB-serial device (CDC ACM) and expects
19200 baud, 8N1 communication.

Screen specs (from project logic):
- Resolution: 160 x 80 pixels
- Color format: 16-bit RGB565, big-endian
- Transfer: 256-byte chunks sent as 64 x 6-byte packets + 1 commit packet
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Optional

try:
    import serial
except ImportError as exc:  # pragma: no cover
    raise ImportError("pyserial is required: pip install pyserial") from exc

try:
    from PIL import Image, ImageDraw, ImageFont, ImageOps
except ImportError as exc:  # pragma: no cover
    raise ImportError("Pillow is required: pip install Pillow") from exc


class MSUMiniUSBScreen:
    """Driver for the MSU Mini USB Screen."""

    WIDTH = 160
    HEIGHT = 80
    BAUDRATE = 19200
    PACKET_SIZE = 6
    CHUNK_SIZE = 256  # bytes per LCD transfer chunk
    PACKETS_PER_CHUNK = CHUNK_SIZE // 4  # 64

    def __init__(self, port: str, baudrate: int = BAUDRATE, timeout: float = 2.0):
        self.port = port
        self.baudrate = baudrate
        self.timeout = timeout
        self.ser: Optional[serial.Serial] = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------
    def connect(self) -> "MSUMiniUSBScreen":
        """Open the serial port and send the MSNCN handshake."""
        self.ser = serial.Serial(
            port=self.port,
            baudrate=self.baudrate,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=self.timeout,
            write_timeout=self.timeout,
        )
        self.ser.flushInput()
        self.ser.flushOutput()
        self.wake_up()
        return self

    def close(self) -> None:
        """Close the serial connection."""
        if self.ser and self.ser.is_open:
            self.ser.close()
        self.ser = None

    def __enter__(self) -> "MSUMiniUSBScreen":
        return self.connect()

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    # ------------------------------------------------------------------
    # Low-level serial helpers
    # ------------------------------------------------------------------
    def _write(self, data: bytes) -> None:
        if self.ser is None or not self.ser.is_open:
            raise RuntimeError("Serial port is not open")
        self.ser.write(data)

    def _wait_for_response(self, timeout_ms: int = 2000) -> Optional[bytes]:
        """Consume any bytes the device echoes back within the timeout."""
        if self.ser is None:
            return None
        deadline = time.monotonic() + (timeout_ms / 1000.0)
        while time.monotonic() < deadline:
            if self.ser.in_waiting:
                return self.ser.read(self.ser.in_waiting)
            time.sleep(0.01)
        return None

    def wake_up(self) -> None:
        """Send the MSNCN wake-up handshake and discard the reply."""
        self.ser.flushInput()
        self.ser.flushOutput()
        self._write(bytes([0x00, 0x4D, 0x53, 0x4E, 0x43, 0x4E]))
        time.sleep(0.25)
        self._wait_for_response()

    def _send_cmd(self, b0: int, b1: int, b2: int, b3: int, b4: int, b5: int) -> None:
        self._write(bytes([b0 & 0xFF, b1 & 0xFF, b2 & 0xFF, b3 & 0xFF, b4 & 0xFF, b5 & 0xFF]))

    def _send_cmd_and_wait_ack(
        self, b0: int, b1: int, b2: int, b3: int, b4: int, b5: int
    ) -> bool:
        """Send a 6-byte command and wait for the device to echo the first two bytes."""
        self._send_cmd(b0, b1, b2, b3, b4, b5)
        deadline = time.monotonic() + 2.0
        while time.monotonic() < deadline:
            if self.ser.in_waiting:
                resp = self.ser.read(self.ser.in_waiting)
                if len(resp) >= 2 and resp[0] == b0 and resp[1] == b1:
                    return True
                return False
            time.sleep(0.001)
        return False

    # ------------------------------------------------------------------
    # Rendering helpers
    # ------------------------------------------------------------------
    @staticmethod
    def rgb_to_rgb565(r: int, g: int, b: int) -> bytes:
        """Convert an RGB triplet to big-endian RGB565 (2 bytes)."""
        # 5-6-5 layout: RRRRRGGG GGGBBBBB
        high = ((r & 0xF8) | (g >> 5)) & 0xFF
        low = (((g & 0x1C) << 3) | (b >> 3)) & 0xFF
        return bytes([high, low])

    @classmethod
    def image_to_rgb565(cls, image: Image.Image) -> bytes:
        """Convert a PIL Image to the screen's native RGB565 frame buffer."""
        image = image.convert("RGB")
        if image.size != (cls.WIDTH, cls.HEIGHT):
            raise ValueError(
                f"Image must be {cls.WIDTH}x{cls.HEIGHT}, got {image.size}"
            )
        pixels = list(image.getdata())
        out = bytearray(cls.WIDTH * cls.HEIGHT * 2)
        idx = 0
        for r, g, b in pixels:
            out[idx] = ((r & 0xF8) | (g >> 5)) & 0xFF
            out[idx + 1] = (((g & 0x1C) << 3) | (b >> 3)) & 0xFF
            idx += 2
        return bytes(out)

    # ------------------------------------------------------------------
    # Frame transfer
    # ------------------------------------------------------------------
    def _send_chunk(self, chunk: bytes, valid_size: int) -> None:
        """Send one 256-byte chunk to the LCD as 64 data packets + commit packet."""
        if len(chunk) < self.CHUNK_SIZE:
            chunk = chunk + bytes(self.CHUNK_SIZE - len(chunk))

        packet_buffer = bytearray((self.PACKETS_PER_CHUNK + 1) * self.PACKET_SIZE)
        offset = 0

        for i in range(self.PACKETS_PER_CHUNK):
            packet_buffer[offset] = 0x04
            packet_buffer[offset + 1] = i & 0xFF
            packet_buffer[offset + 2] = chunk[i * 4 + 0]
            packet_buffer[offset + 3] = chunk[i * 4 + 1]
            packet_buffer[offset + 4] = chunk[i * 4 + 2]
            packet_buffer[offset + 5] = chunk[i * 4 + 3]
            offset += self.PACKET_SIZE

        # Commit packet
        packet_buffer[offset] = 0x02
        packet_buffer[offset + 1] = 0x03
        packet_buffer[offset + 2] = 0x08
        packet_buffer[offset + 3] = (valid_size >> 8) & 0xFF
        packet_buffer[offset + 4] = valid_size & 0xFF
        packet_buffer[offset + 5] = 0x00

        self._write(bytes(packet_buffer))

    def draw_call(
        self,
        x: int,
        y: int,
        width: int,
        height: int,
        frame_data: bytes,
    ) -> None:
        """
        Draw raw RGB565 frame data at the specified screen location.
        frame_data must contain width*height*2 bytes.
        """
        expected = width * height * 2
        if len(frame_data) != expected:
            raise ValueError(
                f"Frame data size mismatch: expected {expected} bytes, got {len(frame_data)}"
            )

        for attempt in range(2):
            if attempt > 0:
                self.wake_up()

            # 1. Set draw area
            self._send_cmd(0x02, 0x00, (x >> 8) & 0xFF, x & 0xFF, (y >> 8) & 0xFF, y & 0xFF)
            self._send_cmd(0x02, 0x01, (width >> 8) & 0xFF, width & 0xFF, (height >> 8) & 0xFF, height & 0xFF)

            # 2. Init write; wait for echo of 0x02, 0x03
            if not self._send_cmd_and_wait_ack(0x02, 0x03, 0x07, 0x00, 0x00, 0x00):
                if attempt == 0:
                    continue
                raise RuntimeError("Screen did not acknowledge draw init command")

            # 3. Stream frame data in 256-byte chunks
            for i in range(0, len(frame_data), self.CHUNK_SIZE):
                chunk = frame_data[i : i + self.CHUNK_SIZE]
                valid_size = len(chunk)
                self._send_chunk(chunk, valid_size)
            return

    # ------------------------------------------------------------------
    # High-level helpers
    # ------------------------------------------------------------------
    def show_image(
        self,
        image: Image.Image,
        mode: str = "crop",
        position: str = "center",
    ) -> None:
        """
        Display a PIL Image on the screen.

        mode:  "crop" | "pad" | "stretch"
        position: anchor position used for crop/pad modes
            ("center", "top", "bottom", "left", "right",
             "top-left", "top-right", "bottom-left", "bottom-right")
        """
        resized = _fit_image(image, self.WIDTH, self.HEIGHT, mode, position)
        frame = self.image_to_rgb565(resized)
        self.draw_call(0, 0, self.WIDTH, self.HEIGHT, frame)

    def show_image_file(self, path: str | Path, **kwargs) -> None:
        """Load an image file and display it."""
        with Image.open(path) as img:
            # Convert palette/transparent images to RGB for resizing
            rgb_img = img.convert("RGB")
            self.show_image(rgb_img, **kwargs)

    def show_text(
        self,
        text: str,
        x: int = 0,
        y: int = 0,
        font_path: Optional[str] = None,
        font_size: int = 16,
        fg_color: tuple[int, int, int] = (255, 255, 255),
        bg_color: tuple[int, int, int] = (0, 0, 0),
    ) -> None:
        """Render text onto a fresh 160x80 buffer and display it."""
        img = Image.new("RGB", (self.WIDTH, self.HEIGHT), bg_color)
        draw = ImageDraw.Draw(img)

        font = _load_font(font_path, font_size)
        draw.text((x, y), text, fill=fg_color, font=font)

        frame = self.image_to_rgb565(img)
        self.draw_call(0, 0, self.WIDTH, self.HEIGHT, frame)

    def show_multi_line_text(
        self,
        lines: list[str],
        font_path: Optional[str] = None,
        font_size: int = 16,
        fg_color: tuple[int, int, int] = (255, 255, 255),
        bg_color: tuple[int, int, int] = (0, 0, 0),
        line_spacing: int = 1,
    ) -> None:
        """Render multiple lines of text, top-aligned, with the given font size."""
        img = Image.new("RGB", (self.WIDTH, self.HEIGHT), bg_color)
        draw = ImageDraw.Draw(img)
        font = _load_font(font_path, font_size)

        y = 0
        for line in lines:
            if y >= self.HEIGHT:
                break
            draw.text((0, y), line, fill=fg_color, font=font)
            bbox = draw.textbbox((0, y), line, font=font)
            line_height = bbox[3] - bbox[1] if bbox else font_size
            y += line_height + line_spacing

        frame = self.image_to_rgb565(img)
        self.draw_call(0, 0, self.WIDTH, self.HEIGHT, frame)


# ----------------------------------------------------------------------
# Internal helpers
# ----------------------------------------------------------------------

def _load_font(font_path: Optional[str], font_size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    if font_path:
        return ImageFont.truetype(font_path, font_size)
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", font_size)
    except OSError:
        pass
    try:
        return ImageFont.truetype("DejaVuSans.ttf", font_size)
    except OSError:
        pass
    return ImageFont.load_default()


def _fit_image(
    image: Image.Image,
    target_w: int,
    target_h: int,
    mode: str,
    position: str,
) -> Image.Image:
    """Resize a PIL Image to the target size using the requested fit mode."""
    position_map = {
        "center": (0.5, 0.5),
        "top": (0.5, 0.0),
        "bottom": (0.5, 1.0),
        "left": (0.0, 0.5),
        "right": (1.0, 0.5),
        "top-left": (0.0, 0.0),
        "top-right": (1.0, 0.0),
        "bottom-left": (0.0, 1.0),
        "bottom-right": (1.0, 1.0),
    }
    anchor = position_map.get(position, (0.5, 0.5))

    if mode == "stretch":
        return image.resize((target_w, target_h), Image.Resampling.LANCZOS)

    if mode == "pad":
        image.thumbnail((target_w, target_h), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (target_w, target_h), (0, 0, 0))
        x = (target_w - image.width) // 2
        y = (target_h - image.height) // 2
        canvas.paste(image, (x, y))
        return canvas

    # Default: crop (cover) mode
    return ImageOps.fit(image, (target_w, target_h), method=Image.Resampling.LANCZOS, centering=anchor)


def _color_from_name(name: str) -> tuple[int, int, int]:
    colors = {
        "black": (0, 0, 0),
        "white": (255, 255, 255),
        "red": (255, 0, 0),
        "green": (0, 255, 0),
        "blue": (0, 0, 255),
        "yellow": (255, 255, 0),
        "cyan": (0, 255, 255),
        "magenta": (255, 0, 255),
        "orange": (255, 165, 0),
        "purple": (128, 0, 128),
        "gray": (128, 128, 128),
        "pink": (255, 192, 203),
    }
    return colors.get(name.lower(), (0, 0, 0))


# ----------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Display text or images on an MSU Mini USB Screen from an Armbian host."
    )
    parser.add_argument("--port", "-p", required=True, help="Serial port, e.g. /dev/ttyACM0")
    sub = parser.add_subparsers(dest="command", required=True)

    img_parser = sub.add_parser("image", help="Show an image")
    img_parser.add_argument("path", help="Path to image file")
    img_parser.add_argument(
        "--mode", "-m", choices=["crop", "pad", "stretch"], default="crop"
    )
    img_parser.add_argument(
        "--position", "-l",
        choices=["center", "top", "bottom", "left", "right",
                 "top-left", "top-right", "bottom-left", "bottom-right"],
        default="center",
    )

    text_parser = sub.add_parser("text", help="Show text")
    text_parser.add_argument("text", help="Text to display")
    text_parser.add_argument("--font", "-f", help="Path to a TTF font")
    text_parser.add_argument("--size", "-s", type=int, default=16)
    text_parser.add_argument("--fg", "-c", default="white", help="Foreground color name")
    text_parser.add_argument("--bg", "-b", default="black", help="Background color name")

    args = parser.parse_args()

    try:
        with MSUMiniUSBScreen(args.port) as screen:
            if args.command == "image":
                screen.show_image_file(args.path, mode=args.mode, position=args.position)
            elif args.command == "text":
                screen.show_text(
                    args.text,
                    font_path=args.font,
                    font_size=args.size,
                    fg_color=_color_from_name(args.fg),
                    bg_color=_color_from_name(args.bg),
                )
        print("Done.")
        return 0
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
