"""Asyncio BLE scanner for ESP32 provisioning beacons.

Adapted from devices/server/ble_provisioner.py.
"""
import asyncio
import logging
from dataclasses import dataclass

from bleak import BleakScanner

logger = logging.getLogger(__name__)

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
        self._loop: asyncio.AbstractEventLoop | None = None
        self._seen: dict[str, BleDevice] = {}
        self._adv_count = 0
        self._callbacks: list[callable] = []

    def on_device(self, callback: callable) -> None:
        self._callbacks.append(callback)

    def off_device(self, callback: callable) -> None:
        try:
            self._callbacks.remove(callback)
        except ValueError:
            pass

    def _emit(self, device: BleDevice) -> None:
        for cb in self._callbacks:
            try:
                cb(device)
            except Exception:
                pass

    def _detection_callback(self, device, advertising_data) -> None:
        self._adv_count += 1
        if self._adv_count == 1:
            logger.info(
                "first BLE advertisement received: address=%s name=%r "
                "(scanner is receiving from the adapter)",
                device.address, device.name,
            )
        # bleak's device.name is often None on early advertisements; the
        # complete local name from the adv/scan-response is more reliable.
        name = advertising_data.local_name or device.name or ""
        if not name.startswith(self.prefix):
            logger.debug(
                "BLE adv ignored: address=%s name=%r local_name=%r rssi=%s",
                device.address, device.name, advertising_data.local_name,
                advertising_data.rssi,
            )
            return
        bd = BleDevice(address=device.address, name=name, rssi=advertising_data.rssi)
        previous = self._seen.get(device.address)
        self._seen[device.address] = bd
        if previous is None:
            logger.info(
                "BLE device found: %s (%s) rssi=%d, %d device(s) total",
                bd.name, bd.address, bd.rssi, len(self._seen),
            )
        elif previous.rssi != bd.rssi:
            logger.debug("BLE device update: %s (%s) rssi=%d", bd.name, bd.address, bd.rssi)
        if previous is None or previous.rssi != bd.rssi:
            # bleak invokes this callback on its own thread; hop to the loop.
            if self._loop is not None:
                self._loop.call_soon_threadsafe(self._emit, bd)

    async def start(self) -> None:
        if self._task is not None:
            logger.info("BLE scan already running, %d device(s) seen", len(self._seen))
            return
        self._loop = asyncio.get_running_loop()
        self._seen.clear()
        self._adv_count = 0
        scanner = BleakScanner(detection_callback=self._detection_callback)
        try:
            await scanner.start()
        except Exception:
            # leave clean state so a later start() can retry
            self._loop = None
            logger.exception("BLE scan failed to start")
            raise
        self._scanner = scanner
        self._task = asyncio.create_task(self._run())
        logger.info("BLE scan started (prefix=%r)", self.prefix)

    async def _run(self) -> None:
        while True:
            await asyncio.sleep(5)
            if self._adv_count == 0:
                logger.info(
                    "BLE scan active: no advertisements received yet "
                    "(adapter not delivering?)"
                )
            elif not self._seen:
                logger.info(
                    "BLE scan active: %d advertisement(s) received, "
                    "none matching prefix %r",
                    self._adv_count, self.prefix,
                )
            else:
                logger.info(
                    "BLE scan active: %d advertisement(s), %d matching device(s)",
                    self._adv_count, len(self._seen),
                )

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
        self._loop = None
        logger.info("BLE scan stopped, %d device(s) seen", len(self._seen))

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
