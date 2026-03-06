#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>

// --- WiFi Credentials ---
const char *WIFI_SSID = "bunty";
const char *WIFI_PASSWORD = "9182736451";

// --- Hardware Settings ---
#define LED_PIN 2 // Built-in blue LED on ESP32

// --- Web Server ---
WebServer server(80);

// --- State Variables ---
enum LampMode { IDLE, FOCUS, WARNING, DISTRACTION };
LampMode currentMode = IDLE;

// Animation timing
unsigned long lastUpdate = 0;
int brightness = 0;
int fadeAmount = 5;
bool ledState = false;

// ==========================================
// LED Helper Functions
// ==========================================

void processAnimation() {
  if (currentMode == IDLE) {
    ledcWrite(0, 0);
    return;
  }

  if (currentMode == DISTRACTION) {
    // Solid ON for Limit Exceeded
    ledcWrite(0, 255);
    return;
  }

  if (currentMode == WARNING) {
    // Fast blink for Approaching Limit
    if (millis() - lastUpdate > 200) {
      lastUpdate = millis();
      ledState = !ledState;
      ledcWrite(0, ledState ? 255 : 0);
    }
    return;
  }

  if (currentMode == FOCUS) {
    // Breathing effect for Within Limit
    if (millis() - lastUpdate > 30) {
      lastUpdate = millis();

      brightness = brightness + fadeAmount;
      if (brightness <= 0 || brightness >= 255) {
        fadeAmount = -fadeAmount; // reverse direction
      }

      ledcWrite(0, brightness);
    }
  }
}

// ==========================================
// HTTP Route Handlers
// ==========================================

void handleFocus() {
  Serial.println(">> MODE SWITCH: FOCUS (Breathing)");
  currentMode = FOCUS;
  brightness = 0; // Reset animation
  fadeAmount = 5;
  server.send(200, "text/plain", "Lamp set to FOCUS");
}

void handleWarning() {
  Serial.println(">> MODE SWITCH: WARNING (Fast Blink)");
  currentMode = WARNING;
  server.send(200, "text/plain", "Lamp set to WARNING");
}

void handleDistraction() {
  Serial.println(">> MODE SWITCH: DISTRACTION (Solid)");
  currentMode = DISTRACTION;
  server.send(200, "text/plain", "Lamp set to DISTRACTION");
}

void handleIdle() {
  Serial.println(">> MODE SWITCH: IDLE (Off)");
  currentMode = IDLE;
  server.send(200, "text/plain", "Lamp set to IDLE");
}

void handleStatus() {
  String modeStr = "idle";
  if (currentMode == FOCUS)
    modeStr = "focus";
  if (currentMode == WARNING)
    modeStr = "warning";
  if (currentMode == DISTRACTION)
    modeStr = "distraction";

  String json = "{\"mode\":\"" + modeStr + "\", \"ip\":\"" +
                WiFi.localIP().toString() + "\"}";
  server.send(200, "application/json", json);
}

// ==========================================
// Setup & Loop
// ==========================================

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n\n============================================");
  Serial.println("  Focus Lamp — ESP32 Built-in LED Booting...");
  Serial.println("============================================");

  // Setup LEDC (PWM)
  ledcSetup(0, 5000, 8); // Channel 0, 5KHz, 8-bit
  ledcAttachPin(LED_PIN, 0);
  ledcWrite(0, 0);

  // Connect to WiFi
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("[WiFi] Connecting to ");
  Serial.println(WIFI_SSID);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    // Blink while connecting
    ledState = !ledState;
    ledcWrite(0, ledState ? 255 : 0);
  }

  Serial.println("\n[WiFi] Connected! IP address: ");
  Serial.println(WiFi.localIP());
  Serial.println("[WiFi] >>> Enter this IP in the Focus Lamp Android app <<<");

  // Keep LED ON for 2 seconds to indicate successful connection
  ledcWrite(0, 255);
  delay(2000);
  ledcWrite(0, 0);
  delay(500);

  // Setup HTTP Routes
  server.on("/focus", HTTP_GET, handleFocus);
  server.on("/warning", HTTP_GET, handleWarning);
  server.on("/distraction", HTTP_GET, handleDistraction);
  server.on("/idle", HTTP_GET, handleIdle);
  server.on("/status", HTTP_GET, handleStatus);

  // Start HTTP server
  server.begin();
  Serial.println("[Server] HTTP server started on port 80");
  Serial.println("[Ready] Waiting for commands from Focus Lamp app...");
  Serial.println("============================================\n");
}

void loop() {
  // 1. Keep listening for HTTP requests
  server.handleClient();

  // 2. Animate the LEDs based on current mode
  processAnimation();

  // 3. Auto-reconnect WiFi if it drops
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Connection lost. Reconnecting...");
    WiFi.disconnect();
    WiFi.reconnect();
    ledcWrite(0, 255);
    delay(1000);
  }
}
