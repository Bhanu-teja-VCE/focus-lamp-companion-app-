#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <Preferences.h>
#include <ESPmDNS.h>

// ============================================================================
// FOCUS LAMP — ESP32 AMBIENT IOT FIRMWARE (ZERO-FRICTION EDITION)
// Part of the Community-Centered Design Thinking (CCDT) Project
// Features: NVS Credential Storage, mDNS (focuslamp.local), /provision API
// ============================================================================

// --- Default WiFi Fallback ---
const char* DEFAULT_WIFI_SSID     = "Bunty ";       // Wi-Fi SSID
const char* DEFAULT_WIFI_PASSWORD = "9182736451";   // Wi-Fi Password

// --- GPIO Hardware Pin Assignment ---
#define PIN_WHITE   27   // Approaching daily screen time limit (Warning)
#define PIN_GREEN   14   // Within focus limit / Ready state (Focus)
#define PIN_RED     13   // Screen time limit exceeded (Distraction)
#define PIN_BUTTON  0    // ESP32 BOOT Button for Factory NVS Reset

// --- LEDC PWM Channel Allocation ---
#define CH_WHITE   0
#define CH_GREEN   1
#define CH_RED     2

// --- Web Server & NVS Configuration ---
WebServer server(80);
Preferences preferences;

// --- System State Definition ---
enum LampMode { IDLE, FOCUS, WARNING, DISTRACTION };
LampMode currentMode = IDLE;

// Timing & Animation Parameters
unsigned long lastUpdate = 0;
int brightness = 0;
int fadeAmount = 5;
bool blinkState = false;
unsigned long buttonPressStart = 0;

const int LED_OFF = 0;
const int LED_ON = 255;
const int TRANSITION_BLINK_COUNT = 2;
const unsigned long TRANSITION_BLINK_MS = 120;

// ============================================================================
// LED Control Functions
// ============================================================================

void writeAllLights(int value) {
  ledcWrite(CH_WHITE, value);
  ledcWrite(CH_GREEN, value);
  ledcWrite(CH_RED, value);
}

void allOff() {
  writeAllLights(LED_OFF);
}

void blinkAllLightsBriefly() {
  for (int i = 0; i < TRANSITION_BLINK_COUNT; i++) {
    writeAllLights(LED_ON);
    delay(TRANSITION_BLINK_MS);
    allOff();
    delay(TRANSITION_BLINK_MS);
  }
}

void processAnimation() {
  switch (currentMode) {
    case IDLE:
    case FOCUS:
      allOff();
      ledcWrite(CH_GREEN, LED_ON); // Solid Green: Within limits / active focus
      break;

    case WARNING:
      allOff();
      ledcWrite(CH_WHITE, LED_ON); // Solid White: Approaching limit warning
      break;

    case DISTRACTION:
      allOff();
      ledcWrite(CH_RED, LED_ON);   // Solid Red: Limit exceeded alert
      break;
  }
}

void setLampMode(LampMode nextMode) {
  if (nextMode != currentMode) {
    blinkAllLightsBriefly();
  }

  currentMode = nextMode;
  brightness = 0;
  fadeAmount = 5;
  blinkState = false;
  lastUpdate = millis();
  processAnimation();
}

void enableCORS() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "*");
}

// ============================================================================
// HTTP API Route Handlers
// ============================================================================

void handleFocus() {
  enableCORS();
  Serial.println("[API] Received: /focus -> Mode: FOCUS (Green)");
  setLampMode(FOCUS);
  server.send(200, "application/json", "{\"status\":\"success\",\"mode\":\"focus\",\"color\":\"green\"}");
}

void handleWarning() {
  enableCORS();
  Serial.println("[API] Received: /warning -> Mode: WARNING (White)");
  setLampMode(WARNING);
  server.send(200, "application/json", "{\"status\":\"success\",\"mode\":\"warning\",\"color\":\"white\"}");
}

void handleDistraction() {
  enableCORS();
  Serial.println("[API] Received: /distraction -> Mode: DISTRACTION (Red)");
  setLampMode(DISTRACTION);
  server.send(200, "application/json", "{\"status\":\"success\",\"mode\":\"distraction\",\"color\":\"red\"}");
}

void handleIdle() {
  enableCORS();
  Serial.println("[API] Received: /idle -> Mode: IDLE (Green)");
  setLampMode(IDLE);
  server.send(200, "application/json", "{\"status\":\"success\",\"mode\":\"idle\",\"color\":\"green\"}");
}

void handleStatus() {
  enableCORS();
  String modeStr = "idle";
  if (currentMode == FOCUS) modeStr = "focus";
  if (currentMode == WARNING) modeStr = "warning";
  if (currentMode == DISTRACTION) modeStr = "distraction";

  String json = "{\"mode\":\"" + modeStr + "\",\"ip\":\"" + WiFi.localIP().toString() + "\",\"hostname\":\"focuslamp.local\",\"device\":\"ESP32_FocusLamp\"}";
  server.send(200, "application/json", json);
}

void handleProvision() {
  enableCORS();
  if (server.hasArg("ssid") && server.hasArg("password")) {
    String newSSID = server.arg("ssid");
    String newPass = server.arg("password");
    
    preferences.begin("wifi_config", false);
    preferences.putString("ssid", newSSID);
    preferences.putString("password", newPass);
    preferences.end();
    
    Serial.println("[Provision] Received new Wi-Fi credentials via HTTP API!");
    Serial.println("[Provision] Saving to NVS and rebooting...");
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Credentials updated. Rebooting ESP32...\"}");
    delay(1000);
    ESP.restart();
  } else {
    server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Missing ssid or password query parameters\"}");
  }
}

void handleOptions() {
  enableCORS();
  server.send(204);
}

// ============================================================================
// Setup & Main Loop
// ============================================================================

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(PIN_BUTTON, INPUT_PULLUP);

  Serial.println("\n\n=======================================================");
  Serial.println("  💡 Focus Lamp — ESP32 Zero-Friction IoT Controller");
  Serial.println("  Community-Centered Design Thinking Project (CCDT)");
  Serial.println("=======================================================");

  // Configure LED PWM Channels
  ledcSetup(CH_WHITE, 5000, 8);
  ledcAttachPin(PIN_WHITE, CH_WHITE);

  ledcSetup(CH_GREEN, 5000, 8);
  ledcAttachPin(PIN_GREEN, CH_GREEN);

  ledcSetup(CH_RED, 5000, 8);
  ledcAttachPin(PIN_RED, CH_RED);

  allOff();

  // Load Saved Wi-Fi Credentials from NVS
  preferences.begin("wifi_config", false);
  String activeSSID = preferences.getString("ssid", DEFAULT_WIFI_SSID);
  String activePass = preferences.getString("password", DEFAULT_WIFI_PASSWORD);
  preferences.end();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.disconnect();
  delay(100);

  Serial.print("[WiFi] Attempting connection to: ");
  Serial.println(activeSSID);

  // Force WPA2 auth threshold for max compatibility
  wifi_config_t conf;
  memset(&conf, 0, sizeof(conf));
  strncpy((char*)conf.sta.ssid, activeSSID.c_str(), sizeof(conf.sta.ssid));
  strncpy((char*)conf.sta.password, activePass.c_str(), sizeof(conf.sta.password));
  conf.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
  conf.sta.pmf_cfg.capable = true;
  conf.sta.pmf_cfg.required = false;
  esp_wifi_set_config(WIFI_IF_STA, &conf);

  WiFi.begin(activeSSID.c_str(), activePass.c_str());

  int attemptCounter = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    attemptCounter++;

    blinkState = !blinkState;
    ledcWrite(CH_WHITE, blinkState ? LED_ON : LED_OFF);

    if (attemptCounter > 30) {
      Serial.println("\n[WiFi] Failed to connect with primary credentials.");
      
      // Fallback to default credentials if primary wasn't default
      if (activeSSID != DEFAULT_WIFI_SSID) {
        Serial.print("[WiFi] Trying default fallback SSID: ");
        Serial.println(DEFAULT_WIFI_SSID);
        activeSSID = DEFAULT_WIFI_SSID;
        activePass = DEFAULT_WIFI_PASSWORD;
        WiFi.disconnect();
        WiFi.begin(activeSSID.c_str(), activePass.c_str());
        attemptCounter = 0;
      } else {
        Serial.println("[WiFi] Retrying...");
        WiFi.disconnect();
        WiFi.begin(activeSSID.c_str(), activePass.c_str());
        attemptCounter = 0;
      }
    }
  }

  Serial.println("\n[WiFi] Connected successfully!");
  Serial.print("[WiFi] IP Address: ");
  Serial.println(WiFi.localIP());

  // Save successful credentials into NVS for auto-connect on next boot
  preferences.begin("wifi_config", false);
  preferences.putString("ssid", activeSSID);
  preferences.putString("password", activePass);
  preferences.end();
  Serial.println("[NVS] Credentials verified and saved to flash memory.");

  // Start mDNS Responder (focuslamp.local)
  if (MDNS.begin("focuslamp")) {
    MDNS.addService("http", "tcp", 80);
    Serial.println("[mDNS] Hostname active: http://focuslamp.local");
  } else {
    Serial.println("[mDNS] Failed to start mDNS responder.");
  }

  allOff();

  // Register HTTP endpoints
  server.on("/focus", HTTP_GET, handleFocus);
  server.on("/warning", HTTP_GET, handleWarning);
  server.on("/distraction", HTTP_GET, handleDistraction);
  server.on("/idle", HTTP_GET, handleIdle);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/provision", HTTP_GET, handleProvision);

  // Handle CORS Pre-flight options
  server.on("/focus", HTTP_OPTIONS, handleOptions);
  server.on("/warning", HTTP_OPTIONS, handleOptions);
  server.on("/distraction", HTTP_OPTIONS, handleOptions);
  server.on("/idle", HTTP_OPTIONS, handleOptions);
  server.on("/status", HTTP_OPTIONS, handleOptions);
  server.on("/provision", HTTP_OPTIONS, handleOptions);

  server.begin();
  Serial.println("[Server] HTTP API server active on port 80");
  Serial.println("[Ready] Awaiting signals from Focus Lamp Mobile App...");
  Serial.println("=======================================================\n");
}

void loop() {
  server.handleClient();
  processAnimation();

  // Check BOOT button for Factory Reset (hold for > 4 seconds)
  if (digitalRead(PIN_BUTTON) == LOW) {
    if (buttonPressStart == 0) {
      buttonPressStart = millis();
    } else if (millis() - buttonPressStart > 4000) {
      Serial.println("\n[Reset] BOOT button held for 4 seconds! Performing Factory NVS Reset...");
      
      // Blink Red 5 times to confirm reset
      for (int i = 0; i < 5; i++) {
        writeAllLights(LED_OFF);
        delay(100);
        ledcWrite(CH_RED, LED_ON);
        delay(100);
      }
      
      preferences.begin("wifi_config", false);
      preferences.clear();
      preferences.end();
      
      Serial.println("[Reset] NVS flash cleared! Rebooting...");
      ESP.restart();
    }
  } else {
    buttonPressStart = 0;
  }

  // Automatic WiFi recovery mechanism
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Network connection lost. Reconnecting...");
    allOff();
    WiFi.disconnect();
    WiFi.reconnect();
    
    // Blinking Red LED signals network disconnect
    ledcWrite(CH_RED, LED_ON);
    delay(500);
    ledcWrite(CH_RED, LED_OFF);
    delay(500);
  }
}
