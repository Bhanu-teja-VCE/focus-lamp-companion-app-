# 🛒 Focus Lamp — Hardware Components List

## Essential Components

| # | Component | Qty | Approx. Price (INR) | Notes |
|---|-----------|-----|---------------------|-------|
| 1 | **ESP32 DevKit V1** (38-pin) | 1 | ₹450–550 | The brain. Has built-in WiFi + Bluetooth. Get the 38-pin variant for more GPIO options. |
| 2 | **WS2812B NeoPixel Ring** (12 or 16 LED) | 1 | ₹150–250 | Addressable RGB LEDs. Smooth color transitions. 12-LED ring fits a desk lamp nicely. |
| 3 | **5V 2A Micro-USB Power Adapter** | 1 | ₹150–200 | Powers both ESP32 and LED ring. Make sure it's 2A — NeoPixels are current-hungry. |
| 4 | **Micro-USB Cable** | 1 | ₹50–100 | For programming the ESP32 and power supply. |
| 5 | **Breadboard** (400/830 point) | 1 | ₹60–100 | For prototyping. Get 830-point if you want room to grow. |
| 6 | **Jumper Wires** (Male-to-Male + Male-to-Female) | 1 pack | ₹50–80 | 20-piece pack is enough. You need both M-M and M-F. |
| 7 | **330Ω Resistor** | 1 | ₹2 | Data line protection for the NeoPixel. Placed between ESP32 GPIO and NeoPixel data-in. |
| 8 | **1000µF Capacitor** (6.3V or higher) | 1 | ₹5–10 | Protects NeoPixels from initial power surge. Place across 5V and GND. |

---

## For the Lamp Enclosure (Optional but makes it look professional)

| # | Component | Qty | Approx. Price (INR) | Notes |
|---|-----------|-----|---------------------|-------|
| 9 | **Frosted Acrylic Dome/Globe** (8–10cm) | 1 | ₹100–200 | Diffuses the LED light for a soft lamp glow. Can also use a ping pong ball for prototype. |
| 10 | **3D Printed Base** or **Wooden Block** | 1 | ₹50–150 | Houses the ESP32 + wiring. Can 3D print or use any small box. |
| 11 | **Hot Glue Gun + Sticks** | 1 | ₹150 | For securing components inside the enclosure. |

---

## Wiring Diagram

```
ESP32 DevKit V1          NeoPixel Ring (WS2812B)
┌─────────────┐          ┌─────────────────┐
│         3V3 │──────────│ VCC (5V)        │
│         GND │──────────│ GND             │
│      GPIO 5 │──[330Ω]──│ DIN (Data In)   │
└─────────────┘          └─────────────────┘
                              │     │
                         [1000µF Capacitor]
                         (across VCC & GND)
```

> **Note:** NeoPixels run on 5V but ESP32 GPIO outputs 3.3V. This works fine for data signal — the 330Ω resistor is just for protection.

---

## Where to Buy (India)

| Store | Link | Best For |
|-------|------|----------|
| **Robocraze** | [robocraze.com](https://robocraze.com) | ESP32, sensors, all-in-one kits |
| **Amazon India** | [amazon.in](https://amazon.in) | Quick delivery, NeoPixel rings |
| **Robu.in** | [robu.in](https://robu.in) | Cheapest prices, bulk components |
| **Quartzcomponents** | [quartzcomponents.com](https://quartzcomponents.com) | Breadboards, wires, resistors |
| **Electronicscomp** | [electronicscomp.com](https://electronicscomp.com) | ESP32 boards specifically |

---

## Software You'll Need (Free)

| Software | Purpose |
|----------|---------|
| **Arduino IDE 2.x** | To write and flash ESP32 code |
| **ESP32 Board Package** | Install via Arduino Board Manager |
| **Adafruit NeoPixel Library** | For controlling WS2812B LEDs |
| **CP2102/CH340 USB Driver** | So your PC recognizes the ESP32 (usually auto-installs) |

---

## Estimated Total Budget

| Category | Cost |
|----------|------|
| Essential components (1–8) | **₹900 – ₹1,300** |
| Enclosure (9–11) | **₹300 – ₹500** |
| **Total** | **₹1,200 – ₹1,800** |

---

## Quick Start Checklist

- [ ] Buy all essential components (1–8)
- [ ] Install Arduino IDE + ESP32 board package
- [ ] Wire ESP32 → NeoPixel on breadboard
- [ ] Flash test sketch (rainbow cycle) to verify hardware
- [ ] Flash Focus Lamp HTTP server sketch
- [ ] Connect ESP32 to your WiFi
- [ ] Enter ESP32 IP in the Focus Lamp app
- [ ] Hit "Sync Lamp" → watch it match your virtual lamp! 🎉
