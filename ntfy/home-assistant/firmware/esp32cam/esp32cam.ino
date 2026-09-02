/*
 * ESP32-CAM Home Assistant onboarding firmware
 *
 * Flow:
 *   1. On first boot: advertise BLE name "ESP32-PROV-XXXXXX" so the server can
 *      discover this device.
 *   2. Power on the camera and scan for a QR code shown on the MSU mini screen.
 *   3. Decode QR JSON: {"claim_key","ssid","pass","server_host"}
 *   4. Connect to WiFi, discover the provisioning server via mDNS (_devprov._tcp)
 *      or fall back to server_host.
 *   5. POST /api/v1/claim with the claim_key; receive device_id, access_token,
 *      mqtt_user and mqtt_pass.
 *   6. Persist credentials to NVS, shut down Bluetooth, then run normal MQTT mode.
 *
 * Required libraries (Arduino IDE Library Manager):
 *   - NimBLE-Arduino (h2zero)
 *   - ArduinoJson (bblanchon, v7)
 *   - ESP32QRCodeReader (alvarowolfx)
 *   - PubSubClient (Nick O'Leary)
 *
 * Board: AI Thinker ESP32-CAM, PSRAM Enabled, Huge APP partition.
 */

#include <NimBLEDevice.h>
#include <WiFi.h>
#include <ESPmDNS.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <ESP32QRCodeReader.h>

// ============== Configuration ==============
#define PROV_BLE_PREFIX     "ESP32-PROV"
#define PROV_SERVICE_UUID   "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define PROV_SERVICE_TYPE   "devprov"
#define PROV_SERVICE_PROTO  "tcp"
#define CLAIM_PATH          "/api/v1/claim"
#define STATUS_TOPIC_FMT    "devices/%s/status"
#define CMD_TOPIC_FMT       "devices/%s/cmd"
#define ACK_TOPIC_FMT       "devices/%s/ack"

#define WIFI_TIMEOUT_MS     20000
#define MDNS_TIMEOUT_MS     8000
#define QR_SCAN_INTERVAL_MS 100
#define FACTORY_RESET_MS    3000
#define BOOT_BUTTON_PIN     0

// ============== Globals ==============
Preferences prefs;
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
ESP32QRCodeReader qrReader(CAMERA_MODEL_AI_THINKER);

bool provisioned = false;

struct DeviceCreds {
  String deviceId;
  String accessToken;
  String mqttHost;
  int    mqttPort;
  String mqttUser;
  String mqttPass;
} creds;

struct ProvPayload {
  String claimKey;
  String ssid;
  String pass;
  String serverHost;
} prov;

char statusTopic[64];
char cmdTopic[64];
char ackTopic[64];

// ============== Forward declarations ==============
void factoryResetCheck();
bool loadCredentials();
void saveCredentials();
void runProvisioningMode();
void runNormalMode();
void shutdownBle();

void startBleAdvertising();
void stopBleAdvertising();

bool initQrScanner();
bool scanQrPayload(ProvPayload &out);

bool connectWiFi(const char* ssid, const char* pass);
bool discoverServer(IPAddress &ip, uint16_t &port);
bool claimDevice(const ProvPayload &p, DeviceCreds &out);

void ensureMqtt();
void onMqttMessage(char* topic, byte* payload, unsigned int len);
void mqttPublish(const char* topic, const char* payload);

// ============== setup / loop ==============
void setup() {
  Serial.begin(115200);
  delay(100);
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
  adv->addServiceUUID(PROV_SERVICE_UUID);
  adv->setName(name);
  adv->start();
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
  qrReader.setup();
  Serial.println("[qr] scanner ready");
  return true;
}

bool scanQrPayload(ProvPayload &out) {
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

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, text);
  if (err) {
    Serial.printf("[qr] JSON parse failed: %s\n", err.c_str());
    return false;
  }

  out.claimKey   = doc["claim_key"]   | "";
  out.ssid       = doc["ssid"]        | "";
  out.pass       = doc["pass"]        | "";
  out.serverHost = doc["server_host"] | "";

  if (out.claimKey.length() == 0 || out.ssid.length() == 0) {
    Serial.println("[qr] missing claim_key or ssid");
    return false;
  }
  return true;
}

// ============== Provisioning ==============
void runProvisioningMode() {
  Serial.println("[prov] entering provisioning mode");
  startBleAdvertising();

  if (!initQrScanner()) {
    Serial.println("[prov] QR scanner init failed, rebooting");
    delay(2000);
    ESP.restart();
  }

  bool gotQr = false;
  while (!gotQr) {
    gotQr = scanQrPayload(prov);
    delay(10);
  }

  Serial.println("[prov] QR received, stopping BLE advertising");
  stopBleAdvertising();

  if (!connectWiFi(prov.ssid.c_str(), prov.pass.c_str())) {
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
    port = 8000;
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
  mqttPublish(statusTopic, "ok");
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

void mqttPublish(const char* topic, const char* payload) {
  if (mqtt.connected()) {
    mqtt.publish(topic, payload, true);
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
  mqttPublish(ackTopic, buf);
}
