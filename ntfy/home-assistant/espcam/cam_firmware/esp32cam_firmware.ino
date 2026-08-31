/*
 * ESP32-CAM compatible board (GC2640/OV2640 module) firmware
 *
 * Power strategy (the core of this firmware):
 *   The ESP32 stays awake with WiFi/MQTT always online; the camera module
 *   stays powered down (PWDN pulled HIGH) and is only powered up on a
 *   capture command: init -> shoot -> upload -> power down immediately.
 *   While idle, sensor current drops from ~120 mA to uA level — the lens
 *   no longer heats up and the image sensor is not burning lifetime.
 *
 * Flow:
 *   1. BLE "WAKE"   -> MQTT status "standby" (BLE is an out-of-band
 *      channel; the ESP32 is awake anyway)
 *   2. MQTT cmd (JSON with an OSS presigned PUT URL) -> camera power on
 *      -> capture -> HTTPS PUT -> power off -> publish ack
 *   3. BLE "SLEEP"  -> camera power off (if still on) -> MQTT "sleeping";
 *      the ESP32 itself stays online
 *
 * Required libraries (Arduino IDE):
 *   - PubSubClient (Nick O'Leary)
 *   - NimBLE-Arduino (h2zero)   // ~60 KB lighter than Bluedroid; mandatory
 *                               // for WiFi+BLE coexistence
 *   - ArduinoJson (bblanchon, v7)
 * Partition scheme: Huge APP (3MB No OTA); Tools -> PSRAM -> Enabled
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <PubSubClient.h>
#include <NimBLEDevice.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// ================== User configuration ==================
static const char* DEVICE_ID   = "esp32cam-01";
static const char* WIFI_SSID   = "YourWifiSSID";
static const char* WIFI_PASS   = "YourWifiPassword";
static const char* MQTT_HOST   = "192.168.1.1";      // OpenWrt router (Mosquitto)
static const uint16_t MQTT_PORT = 1883;
static const char* MQTT_USER   = "cam";
static const char* MQTT_PASS   = "cam-password";

static char T_STATUS[64];   // esp32cam/<id>/status
static char T_CMD[64];      // esp32cam/<id>/cmd
static char T_ACK[64];      // esp32cam/<id>/ack
static const char* T_BROADCAST = "esp32cam/all/cmd";   // broadcast (e.g. WiFi switch)

static const int CAPTURE_RETRY   = 3;
static const int HTTP_TIMEOUT_MS = 30000;

// Fill light (GPIO4). Comment out if not needed
#define USE_FLASH
#define FLASH_GPIO 4

// ============ Standard ESP32-CAM pinout (common to compatible boards) ============
#define PWDN_GPIO_NUM     32   // camera power: HIGH = power-down, LOW = active
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// ================== BLE UUIDs (Nordic UART Service) ==================
#define BLE_SERVICE_UUID  "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define BLE_CHAR_RX_UUID  "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // broker -> ESP
#define BLE_CHAR_TX_UUID  "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // ESP -> broker

// ================== Globals ==================
WiFiClient      wifiClient;
PubSubClient    mqtt(wifiClient);
NimBLECharacteristic* txChar = nullptr;
volatile bool   bleWakeFlag  = false;
volatile bool   sleepRequest = false;
volatile bool   bleConnected = false;
bool            camPowered   = false;   // is the camera currently powered

// ================== Camera power management ==================
void cameraPowerOff() {
  if (camPowered) {
    esp_camera_deinit();              // release frame buffers, LEDC, SCCB
    camPowered = false;
  }
  // PWDN HIGH: OV2640/GC2640 enters power-down. Rails stay on but current
  // drops below 1 mA — no more heat.
  pinMode(PWDN_GPIO_NUM, OUTPUT);
  digitalWrite(PWDN_GPIO_NUM, HIGH);
  Serial.println("[CAM] powered off (PWDN high)");
}

bool cameraPowerOn() {
  if (camPowered) return true;

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM; config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM; config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM; config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM; config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_LATEST;

  if (psramFound()) {
    config.frame_size   = FRAMESIZE_UXGA;   // 1600x1200, ~150-400 KB
    config.jpeg_quality = 10;
    config.fb_count     = 2;
    config.fb_location  = CAMERA_FB_IN_PSRAM;
  } else {
    config.frame_size   = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count     = 1;
  }

  if (esp_camera_init(&config) != ESP_OK) {
    Serial.println("[CAM] init failed");
    cameraPowerOff();
    return false;
  }
  camPowered = true;

  // Critical: AE/AWB need a few frames to converge after power-on;
  // the first frame would be greenish/dark — discard some frames first.
  for (int i = 0; i < 5; i++) {
    camera_fb_t* t = esp_camera_fb_get();
    if (t) esp_camera_fb_return(t);
    delay(80);
  }
  Serial.println("[CAM] powered on, warmed up");
  return true;
}

// ================== MQTT ==================
void mqttPublish(const char* topic, const char* payload) {
  if (mqtt.connected()) mqtt.publish(topic, payload);
}

void handleWifiUpdate(const char* ssid, const char* pass, long seq);
void switchToPendingWifi();
bool tryWifiConnect(const char* ssid, const char* pass, uint32_t timeoutMs);
void ensureMqtt();

void onMqttMessage(char* topic, byte* payload, unsigned int len) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, len) != DeserializationError::Ok) return;
  const char* action = doc["action"] | "";
  long        seq    = doc["seq"]    | 0;

  // ---- WiFi credential rotation: store first, switch later ----
  if (strcmp(action, "wifi_update") == 0) {
    handleWifiUpdate(doc["ssid"] | "", doc["pass"] | "", seq);
    return;
  }
  if (strcmp(action, "wifi_switch") == 0) {   // broadcast: switch to stored creds
    needSwitch = true;                        // blocking op — run it in loop()
    return;
  }

  // ---- Capture ----
  if (strcmp(action, "capture") != 0) return;
  const char* url    = doc["url"]    | "";
  const char* object = doc["object"] | "";
  if (strlen(url) < 16) return;

  bool ok = captureAndUpload(url);   // handles power on/off; powers off on failure too

  JsonDocument ack;
  ack["seq"]    = seq;
  ack["device"] = DEVICE_ID;
  ack["object"] = object;
  ack["result"] = ok ? "ok" : "fail";
  char buf[256];
  serializeJson(ack, buf, sizeof(buf));
  mqttPublish(T_ACK, buf);
  Serial.printf("[MQTT] ack sent, upload=%s\n", ok ? "OK" : "FAIL");
}

void ensureMqtt() {
  while (!mqtt.connected()) {
    String cid = String(DEVICE_ID) + "-" + String((uint32_t)ESP.getEfuseMac(), HEX);
    bool ok = (strlen(MQTT_USER) > 0)
      ? mqtt.connect(cid.c_str(), MQTT_USER, MQTT_PASS, T_STATUS, 1, true, "offline")
      : mqtt.connect(cid.c_str(), T_STATUS, 1, true, "offline");
    if (ok) {
      mqtt.subscribe(T_CMD);
      mqtt.subscribe(T_BROADCAST);   // broadcast commands (e.g. WiFi switch)
      mqttPublish(T_STATUS, "online");
      Serial.println("[MQTT] connected");
    } else {
      Serial.printf("[MQTT] rc=%d, retry in 2s\n", mqtt.state());
      delay(2000);
    }
  }
}

// ================== Capture + PUT upload (all power on/off lives here) ==================
bool captureAndUpload(const char* url) {
  if (!cameraPowerOn()) return false;

#ifdef USE_FLASH
  digitalWrite(FLASH_GPIO, HIGH);
  delay(150);
#endif
  camera_fb_t* fb = nullptr;
  for (int i = 0; i < CAPTURE_RETRY && !fb; i++) {
    fb = esp_camera_fb_get();
    if (!fb) delay(200);
  }
#ifdef USE_FLASH
  digitalWrite(FLASH_GPIO, LOW);
#endif

  bool ok = false;
  if (fb) {
    Serial.printf("[CAM] captured %u bytes\n", fb->len);
    WiFiClientSecure tls;
    tls.setInsecure();   // for strict verification see "HTTPS certificate" in the docs
    HTTPClient http;
    http.setTimeout(HTTP_TIMEOUT_MS);
    if (http.begin(tls, url)) {
      // Must be byte-identical to the Content-Type signed into the URL
      http.addHeader("Content-Type", "image/jpeg");
      int code = http.PUT(fb->buf, fb->len);
      Serial.printf("[OSS] PUT -> HTTP %d\n", code);
      if (code != 200) Serial.println(http.getString());
      ok = (code == 200);
      http.end();
    }
    esp_camera_fb_return(fb);
  } else {
    Serial.println("[CAM] capture failed");
  }

  cameraPowerOff();   // power down immediately after capture, success or not
  return ok;
}

// ================== BLE callbacks ==================
class RxCallback : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& connInfo) override {
    std::string v = c->getValue();
    String cmd(v.c_str());
    cmd.trim();
    // BLE rescue channel: WiFi credentials as JSON (pushed by broker when
    // the device has lost all known WiFi networks)
    if (cmd.startsWith("{")) {
      JsonDocument doc;
      if (deserializeJson(doc, cmd) == DeserializationError::Ok && doc["ssid"].is<const char*>()) {
        handleWifiUpdate(doc["ssid"] | "", doc["pass"] | "", 0);
        bleNotify("WIFI_STORED");   // broker triggers wifi_switch later
      }
      return;
    }
    cmd.toUpperCase();
    Serial.printf("[BLE] rx: %s\n", cmd.c_str());
    if (cmd == "WAKE")       bleWakeFlag  = true;
    else if (cmd == "SLEEP") sleepRequest = true;
  }
};
class ServerCallback : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* s, NimBLEConnInfo& connInfo) override { bleConnected = true; }
  void onDisconnect(NimBLEServer* s, NimBLEConnInfo& connInfo, int reason) override {
    bleConnected = false;
    NimBLEDevice::startAdvertising();
  }
};

void initBle() {
  NimBLEDevice::init(DEVICE_ID);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  NimBLEDevice::setMTU(247);   // allow the ~100-byte credential JSON over BLE
  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallback());
  NimBLEService* svc = server->createService(BLE_SERVICE_UUID);
  txChar = svc->createCharacteristic(BLE_CHAR_TX_UUID, NIMBLE_PROPERTY::NOTIFY);
  NimBLECharacteristic* rxChar =
      svc->createCharacteristic(BLE_CHAR_RX_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  rxChar->setCallbacks(new RxCallback());
  svc->start();
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(BLE_SERVICE_UUID);
  adv->start();
  Serial.println("[BLE] advertising");
}

void bleNotify(const char* msg) {
  if (txChar && bleConnected) {
    txChar->setValue((uint8_t*)msg, strlen(msg));
    txChar->notify();
  }
}

// ================== WiFi credential management (dual slots in NVS) ==================
// NVS keys: active(0/1) slot in use; ssid0/pass0, ssid1/pass1;
//           pending(1 = new creds waiting), pslot(slot of pending creds)
Preferences prefs;
uint8_t  activeSlot   = 0;
bool     pendingWifi  = false;
uint8_t  pendingSlot  = 1;
bool     needSwitch   = false;      // actual switch happens in loop()

void loadCreds(uint8_t slot, char* ssid, size_t ssidLen, char* pass, size_t passLen) {
  char k[8];
  snprintf(k, sizeof(k), "ssid%u", slot);
  if (ssid) prefs.getString(k, ssid, ssidLen);
  snprintf(k, sizeof(k), "pass%u", slot);
  if (pass) prefs.getString(k, pass, passLen);
}

void initCreds() {
  prefs.begin("wifi", false);
  activeSlot  = prefs.getUChar("active", 0);
  pendingWifi = prefs.getBool("pending", false);
  pendingSlot = prefs.getUChar("pslot", 1);
  // First boot after flashing: seed slot 0 with the compile-time credentials
  char tmp[33] = {0};
  loadCreds(activeSlot, tmp, sizeof(tmp), nullptr, 0);
  if (strlen(tmp) == 0) {
    prefs.putString("ssid0", WIFI_SSID);
    prefs.putString("pass0", WIFI_PASS);
    activeSlot = 0;
    prefs.putUChar("active", 0);
  }
}

void handleWifiUpdate(const char* ssid, const char* pass, long seq) {
  JsonDocument ack;
  ack["seq"] = seq; ack["device"] = DEVICE_ID; ack["action"] = "wifi_update";
  if (strlen(ssid) == 0 || strlen(ssid) > 32 || strlen(pass) > 64) {
    ack["result"] = "fail";
  } else {
    pendingSlot = activeSlot ^ 1;                 // write to the inactive slot
    char k[8];
    snprintf(k, sizeof(k), "ssid%u", pendingSlot); prefs.putString(k, ssid);
    snprintf(k, sizeof(k), "pass%u", pendingSlot); prefs.putString(k, pass);
    prefs.putBool("pending", true);
    prefs.putUChar("pslot", pendingSlot);
    pendingWifi = true;
    ack["result"] = "stored";
    Serial.printf("[WIFI] new creds stored to slot %u (ssid=%s)\n", pendingSlot, ssid);
  }
  char buf[192];
  serializeJson(ack, buf, sizeof(buf));
  mqttPublish(T_ACK, buf);
}

bool tryWifiConnect(const char* ssid, const char* pass, uint32_t timeoutMs) {
  WiFi.disconnect(true);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(true);
  WiFi.begin(ssid, pass);
  uint32_t t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < timeoutMs) delay(250);
  return WiFi.status() == WL_CONNECTED;
}

// Switch to the pending credentials; roll back on failure; if both fail,
// keep BLE advertising and wait for rescue.
void switchToPendingWifi() {
  if (!pendingWifi) return;
  char newSsid[33], newPass[65], oldSsid[33], oldPass[65];
  loadCreds(pendingSlot, newSsid, sizeof(newSsid), newPass, sizeof(newPass));
  loadCreds(activeSlot,  oldSsid, sizeof(oldSsid), oldPass, sizeof(oldPass));

  mqtt.disconnect();
  Serial.printf("[WIFI] switching -> %s\n", newSsid);
  if (tryWifiConnect(newSsid, newPass, 20000)) {
    activeSlot = pendingSlot;
    prefs.putUChar("active", activeSlot);
    prefs.putBool("pending", false);
    pendingWifi = false;
    Serial.printf("[WIFI] on new ssid, ip=%s\n", WiFi.localIP().toString().c_str());
    return;   // ensureMqtt() in loop() reconnects and reports online
  }
  Serial.println("[WIFI] new ssid FAILED, rolling back");
  if (tryWifiConnect(oldSsid, oldPass, 20000)) {
    prefs.putBool("pending", false);   // rollback OK — discard pending creds
    pendingWifi = false;
    ensureMqtt();
    mqttPublish(T_STATUS, "{\"state\":\"wifi_rollback\"}");
  } else {
    // Neither network reachable: keep BLE advertising; broker will find us
    // and push the new credentials over BLE.
    Serial.println("[WIFI] both ssids failed, waiting for BLE rescue");
  }
}

// ================== WiFi ==================
void ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  char ssid[33], pass[65];
  loadCreds(activeSlot, ssid, sizeof(ssid), pass, sizeof(pass));
  Serial.printf("[WiFi] connecting %s", ssid);
  tryWifiConnect(ssid, pass, 20000);
  Serial.printf("\n[WiFi] %s\n", WiFi.localIP().toString().c_str());
}

// ================== setup / loop ==================
void setup() {
  Serial.begin(115200);
#ifdef USE_FLASH
  pinMode(FLASH_GPIO, OUTPUT);
  digitalWrite(FLASH_GPIO, LOW);
#endif
  snprintf(T_STATUS, sizeof(T_STATUS), "esp32cam/%s/status", DEVICE_ID);
  snprintf(T_CMD,    sizeof(T_CMD),    "esp32cam/%s/cmd",    DEVICE_ID);
  snprintf(T_ACK,    sizeof(T_ACK),    "esp32cam/%s/ack",    DEVICE_ID);

  cameraPowerOff();   // first thing after boot: camera powered down
  initCreds();        // load WiFi credentials from NVS (incl. rotated ones)
  ensureWifi();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setBufferSize(2048);   // !!! V4-signed URLs are 500+ chars; default 256 truncates them
  mqtt.setKeepAlive(30);
  initBle();
}

void loop() {
  // 0) Pending WiFi switch (triggered by broadcast; blocking, so run it here)
  if (needSwitch) {
    needSwitch = false;
    switchToPendingWifi();
  }

  ensureWifi();
  ensureMqtt();
  mqtt.loop();

  // 1) BLE WAKE -> announce standby
  if (bleWakeFlag) {
    bleWakeFlag = false;
    JsonDocument d;
    d["device"] = DEVICE_ID;
    d["state"]  = "standby";
    d["ip"]     = WiFi.localIP().toString();
    d["rssi"]   = WiFi.RSSI();
    char buf[192];
    serializeJson(d, buf, sizeof(buf));
    mqttPublish(T_STATUS, buf);
    bleNotify("STANDBY");
  }

  // 2) BLE SLEEP -> camera power off + status report (ESP32 stays online)
  if (sleepRequest) {
    sleepRequest = false;
    cameraPowerOff();
    mqttPublish(T_STATUS, "sleeping");   // semantic: camera off, device quiet
    bleNotify("CAM_OFF");
  }

  delay(10);
}
