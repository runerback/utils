#!/usr/bin/env python3
"""
Camera capture orchestration service
Responsibilities:
  1. Connect to the ESP32-CAM over BLE and send WAKE
  2. On MQTT "standby", request a 5-minute PUT presigned URL from the
     signing service on the Aliyun ECS
  3. Publish the capture command (with the signed URL) over MQTT
  4. On ack: send SLEEP over BLE and broadcast the result to other
     devices (which device, where the photo is)

Run:    python3 cam_device_broker.py
Deps:   pip install aiomqtt bleak aiohttp
"""

import asyncio
import json
import logging
import time

import aiohttp
import aiomqtt
from bleak import BleakClient, BleakScanner
from bleak.exc import BleakError

# ---------------- Configuration ----------------
MQTT_HOST = "127.0.0.1"
MQTT_PORT = 1883
MQTT_USER = "cam"
MQTT_PASS = "cam-password"

SIGN_SERVICE = "http://127.0.0.1:8090/sign"

# Cameras under management
DEVICES = {
    "esp32cam-01": {
        "ble_name": "esp32cam-01",          # BLE advertising name
        "timeout_capture": 45,              # seconds from cmd to ack
    },
    # "esp32cam-02": {...},
}

# BLE Nordic UART Service
BLE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
BLE_CHAR_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"   # write to ESP

# Topic other devices subscribe to for photo notifications
NOTIFY_TOPIC = "home/events/photo"

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("cam-agent")


# ---------------- BLE helpers ----------------
async def ble_find_and_connect(name: str, timeout: float = 10.0) -> BleakClient:
    """Scan for an ESP32-CAM by advertising name and connect."""
    log.info("[BLE] scanning for %s ...", name)
    device = await BleakScanner.find_device_by_name(name, timeout=timeout)
    if device is None:
        raise RuntimeError(f"BLE device {name} not found")
    client = BleakClient(device, timeout=15.0)
    await client.connect()
    log.info("[BLE] connected %s (%s)", name, device.address)
    return client


async def ble_send(client: BleakClient, text: str):
    await client.write_gatt_char(BLE_CHAR_RX_UUID, text.encode(), response=False)
    log.info("[BLE] -> %s", text)


# ---------------- Signing service ----------------
async def request_signed_urls(session: aiohttp.ClientSession, device_id: str) -> dict:
    """
    Request a pair of URLs from the signing service:
      put_url : for the ESP32's direct upload, valid 5 minutes
      get_url : photo viewing address shared in notifications, default 1 hour
      object  : OSS object key
    """
    params = {"device": device_id, "put_expires": 300, "get_expires": 3600}
    async with session.get(SIGN_SERVICE, params=params, timeout=aiohttp.ClientTimeout(total=10)) as r:
        r.raise_for_status()
        return await r.json()


# ---------------- One full capture cycle for a single device ----------------
async def capture_cycle(mqtt: aiomqtt.Client, device_id: str):
    cfg = DEVICES[device_id]
    t_status = f"esp32cam/{device_id}/status"
    t_cmd    = f"esp32cam/{device_id}/cmd"
    t_ack    = f"esp32cam/{device_id}/ack"

    ble = None
    try:
        # 1) BLE wake
        ble = await ble_find_and_connect(cfg["ble_name"])
        await ble_send(ble, "WAKE")

        # 2) Wait for the ESP to report standby
        standby = await wait_for(mqtt, t_status,
                                 lambda d: d.get("state") == "standby",
                                 timeout=20)
        log.info("[%s] standby: %s", device_id, standby)

        # 3) Request an OSS presigned URL (5 minutes)
        async with aiohttp.ClientSession() as session:
            signed = await request_signed_urls(session, device_id)
        log.info("[%s] got signed url, object=%s", device_id, signed["object"])

        # 4) Issue the capture command
        seq = int(time.time())
        cmd = {"action": "capture", "url": signed["put_url"],
               "object": signed["object"], "seq": seq}
        await mqtt.publish(t_cmd, json.dumps(cmd), qos=1)

        # 5) Wait for the ack
        ack = await wait_for(mqtt, t_ack,
                             lambda d: d.get("seq") == seq,
                             timeout=cfg["timeout_capture"])
        if ack.get("result") != "ok":
            raise RuntimeError(f"device reported upload failure: {ack}")
        log.info("[%s] upload ok: %s", device_id, ack["object"])

        # 6) Tell it to sleep over BLE (camera powers down)
        await ble_send(ble, "SLEEP")

        # 7) Broadcast the result to other devices
        notify = {
            "event": "photo_taken",
            "device": device_id,
            "object": signed["object"],
            "url": signed["get_url"],      # presigned GET — open to view the photo
            "ts": seq,
        }
        await mqtt.publish(NOTIFY_TOPIC, json.dumps(notify, ensure_ascii=False), qos=1)
        log.info("[%s] notified -> %s", device_id, NOTIFY_TOPIC)
        return notify

    finally:
        if ble and ble.is_connected:
            try:
                await ble.disconnect()
            except BleakError:
                pass


# ---------------- MQTT message waiter ----------------
_waiters: dict[str, list[tuple[object, asyncio.Future]]] = {}


async def wait_for(mqtt: aiomqtt.Client, topic: str, predicate, timeout: float):
    """Wait for a JSON message on `topic` satisfying `predicate`."""
    loop = asyncio.get_running_loop()
    fut = loop.create_future()
    _waiters.setdefault(topic, []).append((predicate, fut))
    try:
        return await asyncio.wait_for(fut, timeout)
    finally:
        _waiters[topic] = [(p, f) for p, f in _waiters.get(topic, []) if f is not fut]


def dispatch(topic: str, payload: bytes):
    try:
        data = json.loads(payload)
    except ValueError:
        data = {"raw": payload.decode(errors="replace")}
    for pred, fut in list(_waiters.get(topic, [])):
        if not fut.done():
            try:
                if pred(data):
                    fut.set_result(data)
            except Exception as e:      # a predicate error is not fatal
                log.warning("predicate error: %s", e)


# ---------------- WiFi credential rotation ----------------
async def rotate_wifi(mqtt: aiomqtt.Client, new_ssid: str, new_pass: str):
    """
    Orchestrates a network-wide SSID/password change. Before running this,
    bring up the new SSID on OpenWrt (old and new SSIDs in parallel, bridged
    to the same lan). Only delete the old SSID after every device has been
    confirmed online.

    Steps: push new credentials device by device (stored in NVS, not applied)
           -> once all ack, broadcast the switch command
           -> wait for devices to check in on the new network
           -> BLE rescue for any device that never comes back.
    """
    seq = int(time.time())

    # 1) Push new credentials to each device; every one must confirm "stored"
    for dev in DEVICES:
        cmd = {"action": "wifi_update", "ssid": new_ssid, "pass": new_pass, "seq": seq}
        await mqtt.publish(f"esp32cam/{dev}/cmd", json.dumps(cmd), qos=1)
        try:
            ack = await wait_for(mqtt, f"esp32cam/{dev}/ack",
                                 lambda d: d.get("seq") == seq and d.get("action") == "wifi_update",
                                 timeout=15)
            assert ack.get("result") == "stored", ack
            log.info("[%s] new wifi creds stored", dev)
        except Exception as e:
            log.error("[%s] creds NOT confirmed (%s) -- aborting rotation, fix this one first", dev, e)
            return False   # better to abort than strand a device outside the old network

    # 2) All confirmed — broadcast the switch
    await mqtt.publish("esp32cam/all/cmd",
                       json.dumps({"action": "wifi_switch"}), qos=1)
    log.info("broadcast wifi_switch sent, waiting for devices on new ssid ...")

    # 3) Wait for devices to check in on the new network
    #    (after switching they reconnect and publish online;
    #     main() must subscribe to the esp32cam/+/status wildcard)
    async def wait_online(dev):
        msg = await wait_for(mqtt, f"esp32cam/{dev}/status",
                             lambda d: True, timeout=60)   # first status after reconnect = online
        return dev, msg

    results = await asyncio.gather(
        *(wait_online(d) for d in DEVICES), return_exceptions=True)
    online = {r[0] for r in results if isinstance(r, tuple)}
    missing = set(DEVICES) - online

    # 4) BLE rescue: devices that can reach neither network still advertise BLE
    for dev in missing:
        try:
            ble = await ble_find_and_connect(DEVICES[dev]["ble_name"], timeout=15)
            await ble_send(ble, json.dumps({"ssid": new_ssid, "pass": new_pass}))
            await asyncio.sleep(1)
            await ble.disconnect()
            log.info("[%s] creds pushed via BLE rescue", dev)
        except Exception as e:
            log.error("[%s] BLE rescue failed: %s -- physical access required", dev, e)

    log.info("rotation done. online=%s missing=%s -- delete the old SSID only if missing is empty",
             online, missing)
    return not missing


# ---------------- Main loop ----------------
async def main():
    topics = []
    for dev in DEVICES:
        topics += [f"esp32cam/{dev}/status", f"esp32cam/{dev}/ack"]
    topics.append("esp32cam/+/status")   # wildcard: wait for all devices during rotation
    topics.append(NOTIFY_TOPIC)

    while True:
        try:
            async with aiomqtt.Client(MQTT_HOST, MQTT_PORT,
                                      username=MQTT_USER or None,
                                      password=MQTT_PASS or None) as mqtt:
                for t in topics:
                    await mqtt.subscribe(t)
                log.info("[MQTT] connected, subscribed: %s", topics)

                # Demo: run a full cycle right after startup;
                # replace with your own trigger (cron / MQTT from other
                # devices / HTTP API)
                asyncio.create_task(trigger_loop(mqtt))

                async for msg in mqtt.messages:
                    dispatch(str(msg.topic), msg.payload)
        except aiomqtt.MqttError as e:
            log.warning("[MQTT] %s, reconnect in 5s", e)
            await asyncio.sleep(5)


async def trigger_loop(mqtt: aiomqtt.Client):
    """Example trigger: capture on every device every 10 minutes. Adapt as needed."""
    await asyncio.sleep(3)
    while True:
        for dev in DEVICES:
            try:
                await capture_cycle(mqtt, dev)
            except Exception as e:
                log.error("[%s] capture cycle failed: %s", dev, e)
        await asyncio.sleep(600)


if __name__ == "__main__":
    asyncio.run(main())
