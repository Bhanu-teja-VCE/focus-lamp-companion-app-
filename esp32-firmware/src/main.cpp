#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>

// ============================================================================
// FOCUS LAMP — ESP32 AMBIENT IOT FIRMWARE
// Part of the Community-Centered Design Thinking (CCDT) Project
// ============================================================================

// --- WiFi Configuration ---
const char* WIFI_SSID     = "Your_WiFi_SSID";     // Change to your network SSID
const char* WIFI_PASSWORD = "Your_WiFi_Password"; // Change to your network password

// --- GPIO Hardware Pin Assignment ---
// Assign distinct PWM pins for RGB LED groups or channels
#define PIN_WHITE  27   // Approaching daily screen time limit (Warning)
#define PIN_GREEN  14   // Within focus limit / Ready state (Focus)
#define PIN_RED    13   // Screen time limit exceeded (Distraction)

// --- LEDC PWM Channel Allocation ---
#define CH_WHITE   0
#define CH_GREEN   1
#define CH_RED     2

// --- Web Server Configuration ---
WebServer server(80);

// --- System State Definition ---
enum LampMode { IDLE, FOCUS, WARNING, DISTRACTION };
LampMode currentMode = IDLE;

// Timing & Animation Parameters
unsigned long lastUpdate = 0;
int brightness = 0;
int fadeAmount = 5;
bool blinkState = false;

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

  String json = "{\"mode\":\"" + modeStr + "\",\"ip\":\"" + WiFi.localIP().toString() + "\",\"device\":\"ESP32_FocusLamp\"}";
  server.send(200, "application/json", json);
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

  Serial.println("\n\n=======================================================");
  Serial.println("  💡 Focus Lamp — ESP32 Ambient IoT Controller Booting");
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

  // Connect to WiFi
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.disconnect();
  delay(100);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("[WiFi] Connecting to network: ");
  Serial.println(WIFI_SSID);

  int attemptCounter = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    attemptCounter++;

    blinkState = !blinkState;
    ledcWrite(CH_WHITE, blinkState ? LED_ON : LED_OFF);

    if (attemptCounter > 40) {
      Serial.println("\n[WiFi] Connection timeout. Retrying...");
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      attemptCounter = 0;
    }
  }

  Serial.println("\n[WiFi] Connected successfully!");
  Serial.print("[WiFi] IP Address: ");
  Serial.println(WiFi.localIP());
  Serial.println("[WiFi] Enter this IP address in the Focus Lamp Mobile App");

  allOff();

  // Register HTTP endpoints
  server.on("/focus", HTTP_GET, handleFocus);
  server.on("/warning", HTTP_GET, handleWarning);
  server.on("/distraction", HTTP_GET, handleDistraction);
  server.on("/idle", HTTP_GET, handleIdle);
  server.on("/status", HTTP_GET, handleStatus);

  // Handle CORS Pre-flight options
  server.on("/focus", HTTP_OPTIONS, handleOptions);
  server.on("/warning", HTTP_OPTIONS, handleOptions);
  server.on("/distraction", HTTP_OPTIONS, handleOptions);
  server.on("/idle", HTTP_OPTIONS, handleOptions);
  server.on("/status", HTTP_OPTIONS, handleOptions);

  server.begin();
  Serial.println("[Server] HTTP API server active on port 80");
  Serial.println("[Ready] Awaiting signals from Focus Lamp Mobile App...");
  Serial.println("=======================================================\n");
}

void loop() {
  server.handleClient();
  processAnimation();

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

