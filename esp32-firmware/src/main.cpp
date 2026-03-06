/**
 * ============================================================
 *  Focus Lamp — ESP32 Firmware
 *  Board  : ESP32-WROOM-32 on Gooouuu Expansion Board
 *  Purpose: Receive "Focus Mode" status from Android app via
 *           HTTP, and control an LED accordingly.
 *
 *  Endpoints (called by the Android app):
 *    GET /focus      → Red breathing effect (Focus == True)
 *    GET /distraction → Solid green (Focus == False)
 *    GET /idle       → LED off
 *    GET /status     → Returns current mode as JSON
 *
 *  LED connected to GPIO 13 (supports PWM via LEDC).
 *  Non-blocking: uses millis() — no delay() anywhere.
 * ============================================================
 */

#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>

// ============================================================
// ⚙️  CONFIGURE THESE BEFORE FLASHING
// ============================================================
const char *WIFI_SSID = "bunty";
const char *WIFI_PASSWORD = "9182736451";
// ============================================================

// --- Hardware ---
const int LED_PIN = 13; // GPIO 13 on Gooouuu GVS header

// --- LEDC (PWM) settings ---
const int PWM_CHANNEL = 0;
const int PWM_FREQ = 5000;    // 5 kHz
const int PWM_RESOLUTION = 8; // 8-bit → 0–255

// --- WebServer ---
WebServer server(80);

// --- Mode state ---
enum LampMode { MODE_IDLE, MODE_FOCUS, MODE_DISTRACTION };
LampMode currentMode = MODE_IDLE;

// --- Breathing effect state (non-blocking) ---
int breathValue = 0;
int breathDirection = 1; // +1 = increasing, -1 = decreasing
unsigned long lastBreathUpdate = 0;
const int BREATH_STEP_MS = 8; // milliseconds per brightness step
const int BREATH_STEP_PX = 2; // brightness units per step (0–255)

// ============================================================
// WiFi Setup
// ============================================================
void setupWiFi() {
  Serial.print("[WiFi] Connecting to ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.print("[WiFi] Connected! IP address: ");
    Serial.println(WiFi.localIP());
    Serial.println(
        "[WiFi] >>> Enter this IP in the Focus Lamp Android app <<<");
  } else {
    Serial.println();
    Serial.println(
        "[WiFi] ❌ Failed to connect. Check SSID/password and restart.");
  }
}

// ============================================================
// LED Helpers
// ============================================================
void setLedBrightness(int brightness) { ledcWrite(PWM_CHANNEL, brightness); }

void setLedSolid(int brightness) {
  // Disable breathing, set fixed brightness
  breathValue = brightness;
  setLedBrightness(brightness);
}

// ============================================================
// Breathing Effect (call every loop)
// ============================================================
void updateBreathingEffect() {
  if (currentMode != MODE_FOCUS)
    return;

  unsigned long now = millis();
  if (now - lastBreathUpdate < BREATH_STEP_MS)
    return;
  lastBreathUpdate = now;

  breathValue += (breathDirection * BREATH_STEP_PX);

  if (breathValue >= 255) {
    breathValue = 255;
    breathDirection = -1; // start dimming
  } else if (breathValue <= 10) {
    breathValue = 10;
    breathDirection = 1; // start brightening
  }

  setLedBrightness(breathValue);
}

// ============================================================
// HTTP Route Handlers
// ============================================================
void handleFocus() {
  currentMode = MODE_FOCUS;
  breathValue = 0;
  breathDirection = 1;
  Serial.println("[HTTP] 🔴 FOCUS mode activated — red breathing effect");
  server.send(200, "application/json",
              "{\"status\":\"focus\",\"led\":\"breathing\"}");
}

void handleDistraction() {
  currentMode = MODE_DISTRACTION;
  setLedSolid(200); // solid green (you'll swap this for NeoPixel later)
  Serial.println("[HTTP] 🟢 DISTRACTION mode — solid green");
  server.send(200, "application/json",
              "{\"status\":\"distraction\",\"led\":\"solid_green\"}");
}

void handleIdle() {
  currentMode = MODE_IDLE;
  setLedSolid(0);
  Serial.println("[HTTP] ⚪ IDLE mode — LED off");
  server.send(200, "application/json", "{\"status\":\"idle\",\"led\":\"off\"}");
}

void handleStatus() {
  String modeStr;
  switch (currentMode) {
  case MODE_FOCUS:
    modeStr = "focus";
    break;
  case MODE_DISTRACTION:
    modeStr = "distraction";
    break;
  default:
    modeStr = "idle";
    break;
  }
  String json = "{\"mode\":\"" + modeStr + "\",\"ip\":\"" +
                WiFi.localIP().toString() + "\"}";
  server.send(200, "application/json", json);
}

void handleNotFound() {
  String msg = "Not found: " + server.uri();
  server.send(404, "text/plain", msg);
  Serial.println("[HTTP] 404: " + server.uri());
}

// ============================================================
// Setup
// ============================================================
void setup() {
  Serial.begin(115200);
  delay(500); // let serial settle
  Serial.println();
  Serial.println("============================================");
  Serial.println("  Focus Lamp — ESP32 Firmware Booting...");
  Serial.println("============================================");

  // Configure PWM for LED
  ledcSetup(PWM_CHANNEL, PWM_FREQ, PWM_RESOLUTION);
  ledcAttachPin(LED_PIN, PWM_CHANNEL);
  setLedSolid(0); // LED off at startup

  // Connect to WiFi
  setupWiFi();

  // Register HTTP routes
  server.on("/focus", HTTP_GET, handleFocus);
  server.on("/distraction", HTTP_GET, handleDistraction);
  server.on("/idle", HTTP_GET, handleIdle);
  server.on("/status", HTTP_GET, handleStatus);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println("[Server] HTTP server started on port 80");
  Serial.println("[Ready] Waiting for commands from Focus Lamp app...");
  Serial.println("============================================");
}

// ============================================================
// Main Loop — non-blocking
// ============================================================
void loop() {
  // Handle incoming HTTP requests
  server.handleClient();

  // Non-blocking breathing effect for Focus mode
  updateBreathingEffect();

  // Optional: reconnect WiFi if it drops
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Lost connection — reconnecting...");
    WiFi.reconnect();
    delay(1000); // one-time delay only on reconnect
  }
}
