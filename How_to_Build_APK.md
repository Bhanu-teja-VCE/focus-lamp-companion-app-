# 📱 How to Build the APK and Install on Your Android Device

> **Focus Lamp Companion App** — Developer Build & Installation Manual

---

## 📋 Prerequisites

| Component | Minimum Requirement | Purpose |
|---|---|---|
| **Android Studio** | Hedgehog (2023.1.1) or newer | Compilation & Gradle dependency resolution |
| **Android Device** | Android 8.0 (API 26 - Oreo) or higher | Testing `UsageStatsManager` & Foreground Service |
| **USB Data Cable** | USB 2.0/3.0 Data Transfer Cable | Direct device debugging |
| **Local Network** | Shared 2.4GHz Wi-Fi Network | ESP32 REST HTTP sync |

---

## 🛠 Step 1: Clone & Open in Android Studio

1. Launch **Android Studio**.
2. Select **File → Open**.
3. Navigate to the project root directory:
   ```bash
   focus-lamp-companion-app-/
   ```
4. Allow **Gradle Sync** to finish downloading dependencies (`OkHttp3`, `Room DB`, `Lifecycle ViewModel`).

> [!NOTE]
> If prompted for missing Android SDK platforms, click **"Install Missing Components"** in the Android Studio notification bar.

---

## 🔓 Step 2: Enable Developer Options & USB Debugging

1. On your Android phone, open **Settings → About Phone**.
2. Tap **Build Number** 7 times until you see the notification: *"You are now a developer!"*
3. Go back to **Settings → System → Developer Options**.
4. Enable **USB Debugging**.

---

## 🔌 Step 3: Connect Device via USB

1. Connect your device to your PC using the USB data cable.
2. Accept the phone prompt: **"Allow USB Debugging from this computer?"** (check *"Always allow"*).
3. Confirm your device appears in the top toolbar device selector in Android Studio.

---

## 🚀 Step 4: Build & Run App

### Option A: Direct Debug Run
1. Click the green **▶ Run** button in Android Studio.
2. Select your connected device and click **OK**.
3. The app will compile, install, and open automatically.

### Option B: Build Standalone APK File
1. In Android Studio, select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Upon build completion, click **"Locate"** to retrieve the generated APK file:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```
3. Transfer `app-debug.apk` to your device via USB, Google Drive, or ADB.

---

## 🛡 Step 5: Grant Required Android Permissions

The app requires two critical system permissions to function:

### 1. Usage Access Permission (Essential for Screen Time Telemetry)
- Upon first launch, tap **"Grant Usage Access"**.
- System Settings will open → Select **Focus Lamp** → Toggle **ON**.
- Return to the app.

### 2. Notification Permission (Android 13+)
- Tap **Allow** when prompted to ensure the Foreground Service persistent notification runs smoothly.

---

## ⚙️ Step 6: Connect to ESP32 Focus Lamp

1. Navigate to the **Settings Tab** (Gear Icon) inside the app.
2. Input your ESP32's IP address (e.g., `192.168.1.120`).
3. Tap **"Sync Lamp"** to trigger a test HTTP GET `/status` ping.
4. Set your **Daily Screen Time Limit** for Distracting Apps (e.g., 30 minutes).
5. Tap **"Start Monitoring"**.

---

## 🔧 Troubleshooting Matrix

| Problem | Cause | Solution |
|---|---|---|
| **Gradle Sync Failed** | Missing internet / offline mode | Toggle Offline Mode OFF in Gradle settings & re-sync |
| **Lamp Not Changing Color** | Incorrect IP or Wi-Fi subnet | Ensure phone and ESP32 are connected to the exact same Wi-Fi router |
| **Screen Time Not Updating** | Usage Access Permission revoked | Re-grant permission in `Settings → Security → Usage Access` |
| **App Killed in Background** | OS Battery Optimization | Disable Battery Optimization for Focus Lamp in Phone Settings |
