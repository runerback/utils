/*
 * ESP32-CAM Home Assistant onboarding firmware — implementation
 * See esp32cam.h for the provisioning flow description and shared declarations.
 */
#include "esp32cam.h"

// ============== Globals ==============
Preferences prefs;
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
ESP32QRCodeReader qrReader(CAMERA_MODEL_AI_THINKER);

bool provisioned = false;
bool cameraOk = false;

static uint32_t lastCamCheck = 0;
static uint32_t lastFaultBlink = 0;
static bool faultLedOn = false;

DeviceCreds creds;
ProvPayload prov;
WifiCreds wifiCreds;

char statusTopic[64];
char cmdTopic[64];
char ackTopic[64];

// ============== setup / loop ==============
void setup() {
  Serial.begin(115200);
  delay(100);
  pinMode(FLASH_LED_PIN, OUTPUT);
  pinMode(STATUS_LED_PIN, OUTPUT);
  setFaultLeds(false);
  factoryResetCheck();

  if (loadCredentials()) {
    provisioned = true;
    shutdownBle();
    runNormalMode();
    return;
  }

  runProvisioningMode();
}

void loop() {
  if (provisioned) {
    ensureMqtt();
    mqtt.loop();
    updateCameraHealth();
    if (!cameraOk && millis() - lastFaultBlink > 300) {
      lastFaultBlink = millis();
      faultLedOn = !faultLedOn;
      setFaultLeds(faultLedOn);
    }
  }
  delay(10);
}

// ============== BLE ==============
void startBleAdvertising() {
  char name[24];
  snprintf(name, sizeof(name), "%s-%06X", PROV_BLE_PREFIX,
           (unsigned int)(ESP.getEfuseMac() & 0xFFFFFF));

  NimBLEDevice::init(name);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);

  NimBLEServer* server = NimBLEDevice::createServer();
  NimBLEService* svc = server->createService(PROV_SERVICE_UUID);
  svc->start();

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  // Name first: flags(3) + name(19) = 22 of the 31-byte legacy advertising
  // packet. The 128-bit UUID no longer fits, so NimBLE automatically moves
  // it to the scan response buffer; enableScanResponse makes it transmittable.
  // The server discovers devices by name anyway.
  adv->setName(name);
  adv->addServiceUUID(PROV_SERVICE_UUID);
  adv->enableScanResponse(true);
  if (!adv->start()) {
    Serial.println("[ble] advertising FAILED to start");
    return;
  }
  Serial.printf("[ble] advertising as %s\n", name);
}

void stopBleAdvertising() {
  NimBLEDevice::stopAdvertising();
}

void shutdownBle() {
  NimBLEDevice::stopAdvertising();
  NimBLEDevice::deinit(true);
  btStop();
  Serial.println("[ble] shutdown");
}

// ============== QR scanner ==============
bool initQrScanner() {
  QRCodeReaderSetupErr err = qrReader.setup();
  if (err != SETUP_OK || !cameraHealthy()) {
    Serial.printf("[qr] camera init failed (%d) — check the camera ribbon cable\n",
                  (int)err);
    return false;
  }
  qrReader.begin();  // starts the detect task that fills the QR queue
  Serial.println("[qr] scanner ready");
  return true;
}

// ============== Camera health ==============
bool cameraHealthy() {
  sensor_t *s = esp_camera_sensor_get();
  return s != NULL && s->id.PID != 0;
}

bool cameraFrameOk() {
  // The first frame(s) right after init can legitimately fail while the
  // sensor warms up, so allow a few attempts before declaring a fault.
  for (int attempt = 0; attempt < 3; attempt++) {
    camera_fb_t *fb = esp_camera_fb_get();
    if (fb != NULL) {
      esp_camera_fb_return(fb);
      return true;
    }
    delay(200);
  }
  return false;
}

// Normal-mode camera init mirrors the QR reader's config (AI Thinker pins,
// grayscale QVGA). Kept initialized so future capture commands can use it.
bool initCameraNormalMode() {
  CameraPins pins = CAMERA_MODEL_AI_THINKER;
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0;
  c.ledc_timer   = LEDC_TIMER_0;
  c.pin_d0 = pins.Y2_GPIO_NUM;
  c.pin_d1 = pins.Y3_GPIO_NUM;
  c.pin_d2 = pins.Y4_GPIO_NUM;
  c.pin_d3 = pins.Y5_GPIO_NUM;
  c.pin_d4 = pins.Y6_GPIO_NUM;
  c.pin_d5 = pins.Y7_GPIO_NUM;
  c.pin_d6 = pins.Y8_GPIO_NUM;
  c.pin_d7 = pins.Y9_GPIO_NUM;
  c.pin_xclk  = pins.XCLK_GPIO_NUM;
  c.pin_pclk  = pins.PCLK_GPIO_NUM;
  c.pin_vsync = pins.VSYNC_GPIO_NUM;
  c.pin_href  = pins.HREF_GPIO_NUM;
  c.pin_sccb_sda = pins.SIOD_GPIO_NUM;
  c.pin_sccb_scl = pins.SIOC_GPIO_NUM;
  c.pin_pwdn  = pins.PWDN_GPIO_NUM;
  c.pin_reset = pins.RESET_GPIO_NUM;
  c.xclk_freq_hz = 10000000;
  c.pixel_format = PIXFORMAT_GRAYSCALE;
  c.frame_size   = FRAMESIZE_QVGA;
  c.fb_count     = 1;

  esp_err_t err = esp_camera_init(&c);
  if (err != ESP_OK) {
    Serial.printf("[cam] init failed 0x%x — check the camera ribbon cable\n", err);
    return false;
  }
  if (!cameraHealthy() || !cameraFrameOk()) {
    Serial.println("[cam] sensor not responding or no frames — check the ribbon cable");
    esp_camera_deinit();
    return false;
  }
  Serial.println("[cam] camera healthy");
  return true;
}

// Periodic recheck in normal mode: grab a frame (or re-init from scratch when
// the camera was never initialized) and publish on state change so the user
// sees a fault — and a recovery — immediately in the app.
void updateCameraHealth() {
  if (millis() - lastCamCheck < CAMERA_CHECK_INTERVAL_MS) {
    return;
  }
  lastCamCheck = millis();

  bool ok = cameraFrameOk();
  if (!ok) {
    Serial.println("[cam] frame grab failed, re-initializing camera");
    esp_camera_deinit();
    ok = initCameraNormalMode();
  }
  if (ok != cameraOk) {
    cameraOk = ok;
    mqttPublish(statusTopic, ok ? "ok" : "camera_error");
    Serial.printf("[cam] health changed: %s\n", ok ? "ok" : "camera_error");
  }
  if (cameraOk) {
    setFaultLeds(false);
    faultLedOn = false;
  }
}

void setFaultLeds(bool on) {
  digitalWrite(FLASH_LED_PIN, on ? HIGH : LOW);
  digitalWrite(STATUS_LED_PIN, on ? LOW : HIGH);  // red LED is active-low
}

// Blocking fault pattern: alternating flash/red blinks the user cannot miss.
// Used when the camera is unusable during provisioning; the caller restarts
// afterwards so a reseated cable gets a clean retry.
void blinkCameraFault(uint32_t durationMs) {
  uint32_t start = millis();
  bool on = false;
  while (millis() - start < durationMs) {
    on = !on;
    setFaultLeds(on);
    delay(250);
  }
  setFaultLeds(false);
}

// Remove the backslash escapes defined by the ZXing WiFi QR format
// (\ before any of \ ; , : " marks a literal character).
static String wifiUnescape(const String &s) {
  String out;
  for (size_t i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '\\' && i + 1 < s.length()) {
      char n = s[i + 1];
      if (n == '\\' || n == ';' || n == ',' || n == ':' || n == '"') {
        out += n;
        i++;
        continue;
      }
    }
    out += c;
  }
  return out;
}

// Parse a standard WiFi sharing QR payload:
//   WIFI:T:<WPA|WEP|nopass>;S:<ssid>;P:<pass>;H:<true|false>;;
// Fields may appear in any order and are ';'-terminated.
bool parseWifiQr(const String &text, WifiCreds &out) {
  if (!text.startsWith("WIFI:")) {
    return false;
  }
  String body = text.substring(5);
  String sec, ssid, pass;
  int i = 0;
  while (i < (int)body.length()) {
    int colon = body.indexOf(':', i);
    if (colon < 0) {
      break;
    }
    String key = body.substring(i, colon);
    String val;
    int j = colon + 1;
    for (; j < (int)body.length(); j++) {
      char c = body[j];
      if (c == '\\' && j + 1 < (int)body.length()) {
        val += c;
        val += body[++j];
        continue;
      }
      if (c == ';') {
        break;
      }
      val += c;
    }
    if (key == "S") {
      ssid = wifiUnescape(val);
    } else if (key == "P") {
      pass = wifiUnescape(val);
    } else if (key == "T") {
      sec = val;
    }
    i = j + 1;
  }
  if (ssid.length() == 0) {
    return false;
  }
  if (sec == "nopass") {
    pass = "";
  }
  out.ssid = ssid;
  out.pass = pass;
  return true;
}

// Decode one QR frame and route it: a WIFI: payload fills the network
// credentials, anything else is treated as the claim JSON. Returns true when
// a previously missing piece was completed.
bool scanQrCode(ProvPayload &provOut, WifiCreds &wifiOut, bool &haveClaim, bool &haveWifi) {
  struct QRCodeData qrCodeData;
  if (!qrReader.receiveQrCode(&qrCodeData, QR_SCAN_INTERVAL_MS)) {
    return false;
  }
  if (!qrCodeData.valid) {
    Serial.println("[qr] invalid code, retrying");
    return false;
  }

  char buf[512];
  int len = qrCodeData.payloadLen;
  if (len >= (int)sizeof(buf)) {
    len = sizeof(buf) - 1;
  }
  memcpy(buf, qrCodeData.payload, len);
  buf[len] = '\0';

  String text(buf);
  Serial.printf("[qr] decoded (%d bytes): %s\n", len, text.c_str());

  if (text.startsWith("WIFI:")) {
    if (!parseWifiQr(text, wifiOut)) {
      Serial.println("[qr] WIFI payload parse failed");
      return false;
    }
    haveWifi = true;
    Serial.printf("[qr] wifi creds received, ssid %s\n", wifiOut.ssid.c_str());
    return true;
  }

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, text);
  if (err) {
    Serial.printf("[qr] JSON parse failed: %s\n", err.c_str());
    return false;
  }

  provOut.claimKey   = doc["claim_key"]   | "";
  provOut.serverHost = doc["server_host"] | "";
  provOut.serverPort = doc["server_port"] | 0;

  if (provOut.claimKey.length() == 0) {
    Serial.println("[qr] missing claim_key");
    return false;
  }
  haveClaim = true;
  Serial.println("[qr] claim payload received");
  return true;
}

// ============== Provisioning ==============
void runProvisioningMode() {
  Serial.println("[prov] entering provisioning mode");
  startBleAdvertising();

  if (!initQrScanner()) {
    // Blink the alert pattern so the user immediately sees the camera module
    // is faulty (e.g. loose ribbon cable), then restart for a clean retry.
    Serial.println("[prov] camera fault detected");
    blinkCameraFault(CAMERA_FAULT_BLINK_MS);
    ESP.restart();
  }

  bool haveClaim = false;
  bool haveWifi = false;
  while (!(haveClaim && haveWifi)) {
    scanQrCode(prov, wifiCreds, haveClaim, haveWifi);
    delay(10);
  }

  Serial.println("[prov] QR payloads complete, stopping BLE advertising");
  stopBleAdvertising();

  if (!connectWiFi(wifiCreds.ssid.c_str(), wifiCreds.pass.c_str())) {
    Serial.println("[prov] WiFi failed, rebooting");
    delay(2000);
    ESP.restart();
  }

  if (!claimDevice(prov, creds)) {
    Serial.println("[prov] claim failed, rebooting");
    delay(2000);
    ESP.restart();
  }

  saveCredentials();
  shutdownBle();
  provisioned = true;
  runNormalMode();
}

bool connectWiFi(const char* ssid, const char* pass) {
  WiFi.disconnect(true);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(true);
  WiFi.begin(ssid, pass);
  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < WIFI_TIMEOUT_MS) {
    delay(250);
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[wifi] connected, IP %s\n", WiFi.localIP().toString().c_str());
    return true;
  }
  Serial.println("[wifi] connection failed");
  return false;
}

bool discoverServer(IPAddress &ip, uint16_t &port) {
  if (!MDNS.begin("esp32-prov-client")) {
    Serial.println("[mdns] begin failed");
    return false;
  }
  uint32_t start = millis();
  while (millis() - start < MDNS_TIMEOUT_MS) {
    int n = MDNS.queryService(PROV_SERVICE_TYPE, PROV_SERVICE_PROTO);
    if (n > 0) {
      ip = MDNS.IP(0);
      port = MDNS.port(0);
      Serial.printf("[mdns] found %s:%u\n", ip.toString().c_str(), port);
      return true;
    }
    delay(500);
  }
  Serial.println("[mdns] service not found");
  return false;
}

bool claimDevice(const ProvPayload &p, DeviceCreds &out) {
  IPAddress ip;
  uint16_t port = 0;

  if (!discoverServer(ip, port)) {
    if (p.serverHost.length() == 0) {
      return false;
    }
    ip.fromString(p.serverHost.c_str());
    port = p.serverPort > 0 ? (uint16_t)p.serverPort : 8000;
    Serial.printf("[claim] falling back to %s:%u\n", ip.toString().c_str(), port);
  }

  HTTPClient http;
  String url = "http://" + ip.toString() + ":" + String(port) + CLAIM_PATH;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  JsonDocument req;
  req["claim_key"] = p.claimKey;
  String body;
  serializeJson(req, body);

  int code = http.POST(body);
  Serial.printf("[claim] HTTP %d\n", code);
  String respBody = http.getString();
  http.end();

  if (code != 200) {
    Serial.printf("[claim] failed: %s\n", respBody.c_str());
    return false;
  }

  JsonDocument resp;
  DeserializationError err = deserializeJson(resp, respBody);
  if (err) {
    Serial.printf("[claim] JSON parse failed: %s\n", err.c_str());
    return false;
  }

  out.deviceId    = resp["device_id"].as<String>();
  out.accessToken = resp["access_token"].as<String>();
  out.mqttHost    = resp["mqtt"]["host"].as<String>();
  out.mqttPort    = resp["mqtt"]["port"].as<int>();
  out.mqttUser    = resp["mqtt"]["username"].as<String>();
  out.mqttPass    = resp["mqtt"]["password"].as<String>();

  if (out.deviceId.length() == 0 || out.accessToken.length() == 0) {
    Serial.println("[claim] missing fields in response");
    return false;
  }
  return true;
}

void saveCredentials() {
  prefs.begin("hapro", false);
  prefs.putString("devid",   creds.deviceId);
  prefs.putString("token",   creds.accessToken);
  prefs.putString("mq_host", creds.mqttHost);
  prefs.putInt("mq_port",    creds.mqttPort);
  prefs.putString("mq_user", creds.mqttUser);
  prefs.putString("mq_pass", creds.mqttPass);
  prefs.putBool("done", true);
  prefs.end();
  Serial.println("[nvs] credentials saved");
}

bool loadCredentials() {
  prefs.begin("hapro", true);
  bool done = prefs.getBool("done", false);
  if (!done) {
    prefs.end();
    return false;
  }
  creds.deviceId    = prefs.getString("devid");
  creds.accessToken = prefs.getString("token");
  creds.mqttHost    = prefs.getString("mq_host");
  creds.mqttPort    = prefs.getInt("mq_port", 1883);
  creds.mqttUser    = prefs.getString("mq_user");
  creds.mqttPass    = prefs.getString("mq_pass");
  prefs.end();

  snprintf(statusTopic, sizeof(statusTopic), STATUS_TOPIC_FMT, creds.deviceId.c_str());
  snprintf(cmdTopic,    sizeof(cmdTopic),    CMD_TOPIC_FMT,    creds.deviceId.c_str());
  snprintf(ackTopic,    sizeof(ackTopic),    ACK_TOPIC_FMT,    creds.deviceId.c_str());
  Serial.printf("[nvs] loaded device %s\n", creds.deviceId.c_str());
  return true;
}

void factoryResetCheck() {
  pinMode(BOOT_BUTTON_PIN, INPUT_PULLUP);
  if (digitalRead(BOOT_BUTTON_PIN) == LOW) {
    uint32_t start = millis();
    while (digitalRead(BOOT_BUTTON_PIN) == LOW) {
      if (millis() - start > FACTORY_RESET_MS) {
        prefs.begin("hapro", false);
        prefs.clear();
        prefs.end();
        Serial.println("[nvs] factory reset, rebooting");
        delay(500);
        ESP.restart();
      }
      delay(50);
    }
  }
}

// ============== MQTT application ==============
void runNormalMode() {
  Serial.println("[app] normal mode");
  mqtt.setServer(creds.mqttHost.c_str(), creds.mqttPort);
  mqtt.setCallback(onMqttMessage);
  mqtt.setBufferSize(2048);
  mqtt.setKeepAlive(30);
  ensureMqtt();
  cameraOk = initCameraNormalMode();
  lastCamCheck = millis();
  mqttPublish(statusTopic, cameraOk ? "ok" : "camera_error");
}

void ensureMqtt() {
  if (mqtt.connected()) return;
  String cid = creds.deviceId + "-" + String((uint32_t)ESP.getEfuseMac(), HEX);
  bool ok = mqtt.connect(cid.c_str(), creds.mqttUser.c_str(), creds.mqttPass.c_str(),
                         statusTopic, 1, true, "offline");
  if (ok) {
    mqtt.subscribe(cmdTopic);
    Serial.println("[mqtt] connected");
  } else {
    Serial.printf("[mqtt] rc=%d, retry in 2s\n", mqtt.state());
    delay(2000);
  }
}

void mqttPublish(const char* topic, const char* payload, bool retained) {
  if (mqtt.connected()) {
    mqtt.publish(topic, payload, retained);
  }
}

void onMqttMessage(char* topic, byte* payload, unsigned int len) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, len) != DeserializationError::Ok) return;

  const char* action = doc["action"] | "";
  Serial.printf("[mqtt] cmd: %s\n", action);

  // TODO: implement capture, WiFi rotation, sleep/wake, etc. here.
  // This firmware focuses on onboarding; application commands are out of scope.

  JsonDocument ack;
  ack["device"] = creds.deviceId;
  ack["action"] = action;
  ack["result"] = "not_implemented";
  char buf[256];
  serializeJson(ack, buf, sizeof(buf));
  mqttPublish(ackTopic, buf, false);  // acks are transient, don't retain them
}
