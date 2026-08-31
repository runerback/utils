# ESP32-CAM Remote Capture System: Architecture & Implementation

## 1. System Architecture

```
┌──────────────┐   BLE(WAKE/SLEEP)  ┌───────────────┐
│ ESP32-CAM    │ ◄─────────────────►│   Broker     │
│(shoot/upload)│                    │ (orchestrator)│
└──────┬───────┘                    └──────┬────────┘
       │ WiFi                              │
       │ MQTT ◄─── OpenWrt + Mosquitto ────┘ (LAN)
       │
       │ HTTPS PUT (presigned URL, 5 min)
       ▼
┌─────────────┐                    ┌──────────────────┐
│ Aliyun OSS  │                    │ Aliyun ECS       │
│ (photos)    │◄───────────────────│ (signing :8090)  │
└─────────────┘                    └─────────▲────────┘
                                             │ HTTP /sign
                                          Broker
```

**Key design decisions:**

1. **The AccessKey lives only on the Aliyun ECS.** Broker requests URLs from the signing service; the ESP32 only ever sees a URL that expires in 5 minutes. Compromising any local device leaks nothing.
2. **Images never transit MQTT or the LAN.** The ESP32 PUTs straight to OSS over HTTPS; the LAN carries only control messages of a few dozen bytes. A UXGA JPEG is ~150-400 KB — relaying it through the broker would be slow and wasteful.
3. **MQTT is signaling only**: standby, commands, acks, sleep, notifications.

## 2. Message Sequence

```
Broker                               ESP32-CAM       ECS                 OSS
   │─────────────  BLE: WAKE ────────────►│            │                   │
   │◄────── MQTT status: standby ─────────│            │                   │
   │──────────── GET /sign?device=... ───────────────► │                   │
   │◄──────── {put_url(300s), get_url, object} ────────┘                   │
   │── MQTT cmd: {action:capture, url} ─► |                                │
   │                                      | capture                        |
   │                                      │──── HTTPS PUT image/jpeg ─────►│
   │                                      │◄─────────── 200 OK ────────────┘
   │◄───── MQTT ack: {result:"ok"} ───────│
   │───────────────BLE: SLEEP ───────────►│
   │                                      │ (camera off, ESP stays online)
   │───── MQTT events/photo ─────► other devices (who + where)
```

MQTT topics (one set per device):

| Topic | Direction | Content |
|---|---|---|
| `esp32cam/<id>/status` | ESP -> server | online / standby(JSON) / sleeping / offline (LWT) |
| `esp32cam/<id>/cmd` | server -> ESP | capture command JSON (with signed URL) |
| `esp32cam/<id>/ack` | ESP -> server | `{seq, device, object, result}` |
| `esp32cam/all/cmd` | server -> all ESPs | broadcast (e.g. WiFi switch) |
| `home/events/photo` | server -> other devices | photo notification (device, object, get_url, timestamp) |

## 3. Question 1: How to get a 5-minute OSS signed URL

Use an OSS **presigned URL** with the **PUT** method and a 300-second expiry. Signing is computed locally on the server — no network call involved. Three things to know today:

1. **Use V4 signing.** Aliyun recommends V4, and regions opened after 2024 support V4 only. With the Python SDK (oss2):

```python
import oss2
from oss2.credentials import EnvironmentVariableCredentialsProvider

auth = oss2.ProviderAuthV4(EnvironmentVariableCredentialsProvider())
bucket = oss2.Bucket(auth, "https://oss-cn-hangzhou.aliyuncs.com",
                     "your-bucket", region="cn-hangzhou")   # V4 requires region

key = "cam/esp32cam-01/20260830/1712345678-ab12cd.jpg"
put_url = bucket.sign_url("PUT", key, 300, slash_safe=True,
                          headers={"Content-Type": "image/jpeg"})
```

- `300` = 5 minutes; V4 presigned URLs can be valid up to 7 days.
- `slash_safe=True`: keeps `/` in the key unescaped so the URL is usable as-is.
- **Once `headers={"Content-Type": "image/jpeg"}` is signed in, the ESP32 must send the byte-identical `Content-Type: image/jpeg` header**, or signature verification fails (403 SignatureDoesNotMatch). The benefit: browsers open the GET link as an inline image instead of a download.

2. **The signing identity needs `oss:PutObject`.** Create a dedicated RAM user with a least-privilege policy scoped to this bucket:

```json
{
  "Version": "1",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["oss:PutObject"],
    "Resource": ["acs:oss:*:*:your-bucket/cam/*"]
  }]
}
```

3. **The ESP32 only needs to start the PUT while the URL is valid** — an upload that outlives the expiry does not fail. Five minutes is generous for a few hundred KB.

Issue a companion **GET presigned URL** (e.g. 1 hour) for the notification, so other devices can view the photo while the bucket stays private.

Full signing service: `oss_sign_server.py`, deployed on the ECS.

## 4. Camera Power Management (fixing heat and sensor aging)

The real requirement: **the ESP32 can stay awake — what must be cut is the camera.** This is pure software, no hardware mod:

> On standard-layout ESP32-CAM compatible boards (GC2640/OV2640 modules), camera power is controlled by the **PWDN pin (GPIO32)**. A running sensor draws ~100-150 mA — that is what keeps the lens hot. Pulling PWDN high puts the sensor into power-down: microamp-level current, and the lens cools within seconds. Sensor aging comes mainly from being powered and hot for long periods — power it down and the problem is gone.

The resulting model:

| Component | Idle | Capturing |
|---|---|---|
| ESP32 | awake, WiFi/MQTT online (modem-sleep for RF savings) | unchanged |
| Camera module | **PWDN high, powered down** | power on → discard 5 warm-up frames (AE/AWB convergence, else frame 1 is greenish/dark) → capture → upload → **power down immediately** |

Command semantics: BLE `WAKE` = announce standby; MQTT `cmd` triggers one full "power on - shoot - upload - power off" cycle; BLE `SLEEP` = ensure camera off and report `sleeping`. The camera is energized only 1-2 seconds per cycle.

Two notes:
- PWDN gates sensor operation; the 3.3V/2.8V rails stay connected but static draw is negligible. Physically cutting the rails would need an external MOSFET — unnecessary board surgery.
- `esp_camera_init/deinit` can be called repeatedly, but each power-on costs a few hundred ms (register setup + exposure convergence). A 45-second ack timeout is plenty.

## 5. Firmware Notes (full code: `esp32cam_firmware.ino`)

| Point | Notes |
|---|---|
| Camera power | `cameraPowerOn()/cameraPowerOff()` wrap the whole capture-upload; failure paths power off too; 5 warm-up frames discarded before shooting |
| Libraries | `NimBLE-Arduino` (mandatory — Bluedroid + WiFi + camera blows the memory budget), `PubSubClient`, `ArduinoJson` |
| Partition | Huge APP (3MB No OTA); Tools → PSRAM → Enabled |
| **MQTT buffer** | `mqtt.setBufferSize(2048)` — V4 signed URLs are 500+ chars; the 256-byte default truncates them. Easiest trap to fall into |
| Capture | `PIXFORMAT_JPEG` + `FRAMESIZE_UXGA`, frame buffers in PSRAM; GPIO4 fill light on 150 ms before capture |
| Upload | `WiFiClientSecure` + `HTTPClient.PUT(fb->buf, fb->len)` with `Content-Type: image/jpeg` |
| HTTPS cert | `setInsecure()` for the demo; for production use `setCACert()` with the OSS domain's root (DigiCert Global Root G2) |
| Outage signal | MQTT LWT publishes `offline` so the agent sees dead devices |

## 6. Deployment

### 6.1 OpenWrt / Mosquitto

```sh
opkg update && opkg install mosquitto-ssl
# append to /etc/mosquitto/mosquitto.conf:
#   listener 1883 0.0.0.0
#   password_file /etc/mosquitto/passwd
#   allow_anonymous false
mosquitto_passwd -c /etc/mosquitto/passwd cam
/etc/init.d/mosquitto enable && /etc/init.d/mosquitto restart
# firewall: LAN side 1883 (LAN is usually fully open already)
```

### 6.2 Aliyun ECS (signing service)

```sh
pip install oss2 flask
export OSS_ACCESS_KEY_ID=...  OSS_ACCESS_KEY_SECRET=...
python3 oss_sign_server.py
```

- Run under systemd (inject the keys with `Environment=` before `ExecStart`).

### 6.3 Broker

```sh
pip install aiomqtt bleak aiohttp
# rootless BLE: sudo setcap 'cap_net_raw,cap_net_admin+eip' $(readlink -f $(which python3))
python3 cam_device_broker.py
```

set `SIGN_SERVICE` to the ECS's IP.

### 6.4 ESP32-CAM flashing

1. Install the esp32 core in the Arduino IDE. Compatible boards have no dedicated entry — select **AI Thinker ESP32-CAM** (same pinout), PSRAM Enabled, Huge APP partition.
2. Install the three libraries (see the firmware header comment).
3. Edit the config block (WiFi, MQTT, DEVICE_ID), flash with GPIO0 to GND, then remove and reset.

## 7. Debugging Checklist

| Symptom | What to check |
|---|---|
| cmd not received / URL truncated | Missing `setBufferSize(2048)`; mosquitto `max_packet_size` default is fine |
| PUT 403 SignatureDoesNotMatch | ESP's Content-Type differs from the signed one; URL truncated/re-encoded; clock skew (signing happens on the ECS — the ESP needs no time sync, but the ECS clock must be right) |
| PUT 403 AccessDenied | RAM policy missing `oss:PutObject` on `bucket/cam/*` |
| PUT timeout | Can the ESP's WiFi reach the internet? OpenWrt firewall/NAT |
| BLE not found | Firmware advertising? (`advertising` on serial); verify with `bluetoothctl scan on`; range/antenna |
| Camera init failed | PSRAM off; ribbon cable loose; weak 5 V supply (capture spikes demand a solid 5V/2A) |
| GC2640 module probe failure | GC2640 is register-compatible with OV2640; recent esp32-camera versions light it up directly. If `esp_camera_init` reports a probe error, upgrade the Arduino core to 2.0.9+ / 3.x |
| First frame greenish/dark | Not enough warm-up frames — raise the discard count in `cameraPowerOn()` from 5 to 8, or lengthen the inter-frame delay |
| WiFi+BLE coexistence crash | Confirm you are on NimBLE, not BLEDevice (Bluedroid) |

## 8. Security & Robustness

- Set a Mosquitto password; if LAN sniffing worries you, move to 8883/TLS (PubSubClient supports it, at some RAM cost).
- The signing service allowlists the `device` parameter (already in the code) and should log every issued key for auditing.
- Set the GET URL expiry to what viewing actually needs (1-24 h); never make the bucket public-read.
- The agent's `trigger_loop` is a 10-minute demo loop — wire in your real trigger (Home Assistant, sensor MQTT, HTTP API); `capture_cycle()` needs no changes.

## 9. WiFi SSID/Password Change (Credential Rotation Protocol)

Changing WiFi knocks every wireless device offline at once, so notifications must be **delivered before the change, stored before applied, with a rescue path for the lost**:

```
T1  Bring up the new SSID on OpenWrt (old+new in parallel, same lan bridge)
T2  Broker sends esp32cam/<id>/cmd {action:wifi_update, ssid, pass} per device
    Device writes it to the inactive NVS slot (no switch), acks {result:"stored"}
    — if ANY device fails to confirm, abort the whole rotation
T3  Broker broadcasts esp32cam/all/cmd {action:wifi_switch}
    Device drops MQTT -> joins the new SSID -> on success promotes the new
    creds and reports online; on failure rolls back automatically and reports
    wifi_rollback; if neither network works, keeps BLE advertising for rescue
T4  Broker waits for check-ins; pushes creds over BLE to the missing ones
T5  With zero missing, delete the old SSID on OpenWrt
```

**Key points:**

- **Dual SSID in parallel is the single most important step.** On OpenWrt just add a second AP interface on the same radio (Network → Wireless → Add), bridged to `lan`. Never just change the old SSID's password — that strands everyone instantly and forces per-device BLE rescue.
- **Dual credential slots on-device** (NVS): active + pending. The new creds are promoted only after a successful connect, with automatic rollback — the device always holds one working set.
- **BLE is the rescue channel**: a device that reaches no known WiFi keeps advertising; Broker finds it by name and writes `{"ssid":"new","pass":"new"}` JSON (MTU negotiated to 247 for the long packet).
- MQTT retained messages **do not help here**: a device that cannot reach the router cannot reach the broker. Don't count on it.

**Broker side** (`rotate_wifi()` in `cam_device_broker.py`):

```python
await rotate_wifi(mqtt, "NewSSID", "NewPassword")
# delete the old SSID only if this returns True; otherwise handle `missing` per the logs
```

**Other wireless devices** in scope: have them subscribe to `home/events/#` or a dedicated `home/sys/wifi` topic for the change notice; devices with OTA/config interfaces should reuse the same "store-then-switch, atomic broadcast, rollback" protocol — the standard IoT credential-rotation pattern.

## Appendix: Files

| File | Runs on | Role |
|---|---|---|
| `esp32cam_firmware.ino` | ESP32-CAM | firmware: BLE I/O, MQTT, capture, direct HTTPS PUT, camera power gating, WiFi rotation |
| `cam_device_broker.py` | Broker | orchestration: BLE wake/sleep, MQTT signaling, signing requests, result broadcast, WiFi rotation |
| `oss_sign_server.py` | ECS | issues 5-minute PUT presigned URLs + GET viewing URLs |

## References

[^1^]: Aliyun OSS docs — Upload with presigned URL (Go SDK V2), V4 URLs valid up to 7 days: https://help.aliyun.com/zh/oss/developer-reference/v2-presign-upload
[^5^]: Aliyun OSS docs — Upload with presigned URL (Node.js SDK), signing is computed locally and uploads survive past expiry: https://help.aliyun.com/zh/oss/developer-reference/upload-objects-using-a-signed-url-generated-with-oss-sdk-for-node-js
[^7^]: Aliyun OSS docs — Upload with presigned URL (Python SDK V1), ProviderAuthV4 + sign_url('PUT', key, expires, slash_safe=True): https://help.aliyun.com/zh/oss/developer-reference/upload-an-object-using-a-signed-url-generated-with-oss-sdk-for-python
