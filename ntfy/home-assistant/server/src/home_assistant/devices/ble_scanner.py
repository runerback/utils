"""Asyncio BLE scanner for ESP32 provisioning beacons.

Adapted from devices/server/ble_provisioner.py.
"""
import asyncio
from dataclasses import dataclass

from bleak import BleakScanner

BLE_NAME_PREFIX = "ESP32-PROV"


@dataclass
class BleDevice:
    address: str
    name: str
    rssi: int


class BleScanner:
    """Continuously scan for devices advertising ESP32-PROV-* and emit events."""

    def __init__(self, prefix: str = BLE_NAME_PREFIX) -> None:
        self.prefix = prefix
        self._scanner: BleakScanner | None = None
        self._task: asyncio.Task | None = None
        self._seen: dict[str, BleDevice] = {}
        self._callbacks: list[callable] = []

    def on_device(self, callback: callable) -> None:
        self._callbacks.append(callback)

    def _emit(self, device: BleDevice) -> None:
        for cb in self._callbacks:
            try:
                cb(device)
            except Exception:
                pass

    def _detection_callback(self, device, advertising_data) -> None:
        name = device.name or ""
        if not name.startswith(self.prefix):
            return
        bd = BleDevice(address=device.address, name=name, rssi=advertising_data.rssi)
        previous = self._seen.get(device.address)
        self._seen[device.address] = bd
        if previous is None or previous.rssi != bd.rssi:
            self._emit(bd)

    async def start(self) -> None:
        if self._task is not None:
            return
        self._seen.clear()
        self._scanner = BleakScanner(detection_callback=self._detection_callback)
        await self._scanner.start()
        self._task = asyncio.create_task(self._run())

    async def _run(self) -> None:
        while True:
            await asyncio.sleep(1)

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        if self._scanner is not None:
            await self._scanner.stop()
            self._scanner = None

    def list_devices(self) -> list[BleDevice]:
        return sorted(self._seen.values(), key=lambda d: d.rssi, reverse=True)


async def scan_devices(timeout: float = 15.0, prefix: str = BLE_NAME_PREFIX) -> list[BleDevice]:
    """One-shot scan for provisioning beacons."""
    found: list[BleDevice] = []

    def _cb(device, adv_data) -> None:
        name = device.name or ""
        if name.startswith(prefix):
            found.append(BleDevice(device.address, name, adv_data.rssi))

    scanner = BleakScanner(detection_callback=_cb)
    await scanner.start()
    await asyncio.sleep(timeout)
    await scanner.stop()
    return sorted(found, key=lambda d: d.rssi, reverse=True)
