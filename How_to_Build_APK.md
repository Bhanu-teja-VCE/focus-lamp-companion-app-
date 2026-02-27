# 📱 How to Build the APK and Install on Your Phone

This is a step-by-step guide to get the Focus Lamp app running on your Android phone using a USB data cable.

---

## Prerequisites

| What you need | Why |
|---|---|
| **Android Studio** | To build the app ([Download](https://developer.android.com/studio)) |
| **USB Data Cable** | To connect your phone to PC |
| **Android Phone** | Running Android 8.0 (Oreo) or above |

---

## Step 1: Open the Project in Android Studio

1. Open **Android Studio**
2. Click **File → Open**
3. Navigate to this folder:
   ```
   C:\Users\bhanu\Downloads\google anti gravity\focus lamp-the ultimate hardware project
   ```
4. Click **OK** and wait for **Gradle Sync** to finish (loading bars at the bottom — this can take 2-5 minutes on first load because it downloads dependencies like OkHttp)

> ⚠️ If you see "SDK location not found", Android Studio will usually auto-fix this. Click "OK" on any prompts.

---

## Step 2: Enable Developer Mode on Your Phone

1. Go to **Settings → About Phone**
2. Tap **Build Number 7 times** rapidly
3. You'll see: *"You are now a developer!"*
4. Go back to **Settings → System → Developer Options**
5. Turn ON **USB Debugging**

---

## Step 3: Connect Phone via USB

1. Plug your phone into your PC with the USB **data cable** (not a charging-only cable!)
2. On your phone, a popup will appear: **"Allow USB debugging?"**
3. Tap **Allow** (check "Always allow from this computer")
4. In Android Studio, look at the top toolbar — your phone name should appear (e.g., "Samsung SM-A525F")

> 💡 If it says "No Devices", try: different USB port, different cable, or restart Android Studio.

---

## Step 4: Run the App (Method 1 — Direct Install)

1. In Android Studio, click the green **▶ Run** button (top toolbar)
2. Select your phone from the device list
3. Click **OK**
4. Wait 30-60 seconds while it builds and installs
5. **The app will open automatically on your phone!** 🎉

---

## Step 5: Build APK File (Method 2 — Shareable File)

If you want a **standalone APK file** you can share with anyone:

1. In Android Studio, go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for the build to complete (bottom notification bar)
3. When done, click **"Locate"** in the notification that says "APK(s) generated successfully"
4. The file is at:
   ```
   focus lamp-the ultimate hardware project\app\build\outputs\apk\debug\app-debug.apk
   ```

### Transfer the APK to your phone:
- **Option A:** Copy via USB (drag to phone's Downloads folder)
- **Option B:** Upload to **Google Drive** → open on phone
- **Option C:** Send via **WhatsApp Web**

### Install the APK on phone:
1. On your phone, tap the APK file
2. If prompted: **"Allow installing unknown apps"** → Enable for Files/Drive
3. Tap **Install**
4. Done! The app is installed. 🚀

---

## Step 6: Grant Permissions (IMPORTANT!)

When you first open the app, it will ask for permissions:

### 1. Usage Access (Required)
- A dialog will appear → tap **"Open Settings"**
- Find **Focus Lamp** in the list
- Toggle it **ON**
- Press **Back** to return to the app

### 2. Notification Permission (Android 13+)
- Tap **Allow** when prompted
- This lets the monitoring service show its persistent notification

---

## Step 7: Connect to Your ESP32

1. Go to the **Settings** tab in the app (gear icon)
2. Enter your ESP32's IP address (default: `192.168.4.1`)
3. Tap **"Sync Lamp"** to test the connection
4. Set your **Screen Time Limit** (e.g., 30 minutes)
5. Tap **"Save Settings"**
6. Go back to **Home** and tap **"Start Monitoring"**

The app will now check your screen time every 10 seconds and signal the lamp! 💡

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | Check internet connection, click "Try Again" |
| "No Devices" in toolbar | Try different USB cable/port, re-enable USB debugging |
| App crashes on open | Make sure you granted Usage Access permission |
| Lamp not responding | Check ESP32 is powered on, verify IP address in Settings |
| "Install blocked" on phone | Go to Settings → Security → Allow unknown sources |

---

## Architecture Overview (For Judges)

```
📂 com.focuslamp.app
├── 📂 data/
│   ├── 📂 local/        → Room DB (session history)
│   ├── 📂 network/      → OkHttp (HTTP) + TCP Socket
│   └── 📂 tracking/     → UsageStatsManager + Blocked Apps
├── 📂 service/          → Foreground Service (background monitor)
├── 📂 ui/               → Fragments, ViewModel, Activities
└── 📂 utils/            → Settings, Resource wrapper
```

**Key Technologies:**
- **Kotlin** — Modern Android development language
- **MVVM Architecture** — Clean separation of UI and logic
- **UsageStatsManager** — Android's official screen time API
- **OkHttp** — HTTP client for ESP32 communication
- **Room Database** — Local persistence for session history
- **Foreground Service** — Battery-saver-proof background monitoring
