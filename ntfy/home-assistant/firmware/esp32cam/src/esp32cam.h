/*
 * ESP32-CAM Home Assistant onboarding firmware — shared declarations
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

// ============== Globals ==============
extern Preferences prefs;
extern WiFiClient wifiClient;
extern PubSubClient mqtt;
extern ESP32QRCodeReader qrReader;

extern bool provisioned;

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
  String ssid;
  String pass;
  String serverHost;
};

extern ProvPayload prov;

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
bool scanQrPayload(ProvPayload &out);

bool connectWiFi(const char* ssid, const char* pass);
bool discoverServer(IPAddress &ip, uint16_t &port);
bool claimDevice(const ProvPayload &p, DeviceCreds &out);

void ensureMqtt();
void onMqttMessage(char* topic, byte* payload, unsigned int len);
void mqttPublish(const char* topic, const char* payload);
