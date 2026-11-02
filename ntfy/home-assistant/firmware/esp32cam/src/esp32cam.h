/*
 * ESP32-CAM Home Assistant onboarding firmware — shared declarations
 *
 * Flow:
 *   1. On first boot: advertise BLE name "ESP32-PROV-XXXXXX" so the server can
 *      discover this device.
 *   2. Scan QR codes until both pieces are known, in any order:
 *      - WiFi creds from a standard WiFi sharing QR (WIFI:S:..;T:..;P:..;;),
 *        e.g. Android Settings > Wi-Fi > tap network > Share.
 *      - Claim JSON {"claim_key","server_host","server_port"} shown by the
 *        companion app (or the MSU mini screen).
 *   3. Connect to WiFi, discover the provisioning server via mDNS (_devprov._tcp)
 *      or fall back to server_host.
 *   4. POST /api/v1/claim with the claim_key; receive device_id, access_token,
 *      mqtt_user and mqtt_pass.
 *   5. Persist credentials to NVS, shut down Bluetooth, then run normal MQTT mode.
 *
 * Required libraries (Arduino IDE Library Manager):
 *   - NimBLE-Arduino (h2zero)
 *   - ArduinoJson (bblanchon, v7)
 *   - ESP32QRCodeReader (alvarowolfx)
 *   - PubSubClient (Nick O'Leary)
 *
 * Board: AI Thinker ESP32-CAM, PSRAM Enabled, Huge APP partition.
 */
#pragma once

#include <Arduino.h>
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

// Camera fault signalling. AI Thinker ESP32-CAM: GPIO 4 is the bright white
// flash LED, GPIO 33 is the onboard red LED (active-low).
#define FLASH_LED_PIN             4
#define STATUS_LED_PIN            33
#define CAMERA_CHECK_INTERVAL_MS  30000
#define CAMERA_FAULT_BLINK_MS     10000

// ============== Globals ==============
extern Preferences prefs;
extern WiFiClient wifiClient;
extern PubSubClient mqtt;
extern ESP32QRCodeReader qrReader;

extern bool provisioned;
extern bool cameraOk;

struct DeviceCreds {
  String deviceId;
  String accessToken;
  String mqttHost;
  int    mqttPort;
  String mqttUser;
  String mqttPass;
};

extern DeviceCreds creds;

struct ProvPayload {
  String claimKey;
  String serverHost;
  int    serverPort = 0;
};

extern ProvPayload prov;

struct WifiCreds {
  String ssid;
  String pass;
};

extern WifiCreds wifiCreds;

extern char statusTopic[64];
extern char cmdTopic[64];
extern char ackTopic[64];

// ============== Function declarations ==============
void factoryResetCheck();
bool loadCredentials();
void saveCredentials();
void runProvisioningMode();
void runNormalMode();
void shutdownBle();

void startBleAdvertising();
void stopBleAdvertising();

bool initQrScanner();
bool parseWifiQr(const String &text, WifiCreds &out);
bool scanQrCode(ProvPayload &provOut, WifiCreds &wifiOut, bool &haveClaim, bool &haveWifi);

// Camera health. cameraHealthy() checks the sensor answered on the SCCB bus
// (a loose ribbon cable makes the probe fail). cameraFrameOk() additionally
// grabs a test frame — only call it when the QR reader task is NOT running,
// since with fb_count=1 both consumers race for the same buffer.
bool cameraHealthy();
bool cameraFrameOk();
bool initCameraNormalMode();
void updateCameraHealth();
void setFaultLeds(bool on);
void blinkCameraFault(uint32_t durationMs);

bool connectWiFi(const char* ssid, const char* pass);
bool discoverServer(IPAddress &ip, uint16_t &port);
bool claimDevice(const ProvPayload &p, DeviceCreds &out);

void ensureMqtt();
void onMqttMessage(char* topic, byte* payload, unsigned int len);
void mqttPublish(const char* topic, const char* payload, bool retained = true);
