package com.focuslamp.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focuslamp.app.FocusLampApp
import com.focuslamp.app.R
import com.focuslamp.app.data.network.HttpLampController
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.ui.MainActivity
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.*

/**
 * Foreground Service that runs continuously in the background.
 *
 * Every 10 seconds, it:
 * 1. Checks total distraction time via UsageStatsManager
 * 2. If the limit is exceeded → sends HTTP GET to ESP32 /distraction
 * 3. If under limit → sends /focus
 *
 * The persistent notification ensures Android doesn't kill this service.
 */
class FocusMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var screenTimeTracker: ScreenTimeTracker
    private lateinit var distractingAppsManager: DistractingAppsManager
    private lateinit var httpLampController: HttpLampController
    private lateinit var settingsManager: SettingsManager

    private var monitoringJob: Job? = null

    companion object {
        private const val TAG = "FocusMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 10_000L // 10 seconds
    }

    override fun onCreate() {
        super.onCreate()
        screenTimeTracker = ScreenTimeTracker(this)
        distractingAppsManager = DistractingAppsManager(this)
        httpLampController = HttpLampController()
        settingsManager = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Start as a foreground service with a persistent notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Begin the monitoring loop
        startMonitoring()

        return START_STICKY // Restart if killed by OS
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        serviceScope.cancel()
        settingsManager.isMonitoringActive = false
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Creates the persistent notification shown while monitoring is active.
     */
    private fun createNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FocusLampApp.CHANNEL_ID)
            .setContentTitle("Focus Lamp Active")
            .setContentText("Monitoring your screen time...")
            .setSmallIcon(R.drawable.ic_lamp) // We'll create this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * The main monitoring loop.
     * Runs every 10 seconds, checks distraction time, and signals the lamp.
     */
    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            settingsManager.isMonitoringActive = true
            var lastModeSent = -1 // 0=Focus, 1=Warning, 2=Distraction

            while (isActive) {
                try {
                    // Only count apps the user has marked as distracting!
                    val distractingLog = distractingAppsManager.getAll()
                    val distractionMinutes = screenTimeTracker.getDistractionTimeOnly(distractingLog)
                    val limit = settingsManager.timeLimitMinutes

                    Log.d(TAG, "DistractionOnly: ${distractionMinutes}min / Limit: ${limit}min")

                    val espIp = settingsManager.espIp
                    val newMode: Int

                    if (distractionMinutes < limit) {
                        newMode = 0
                    } else if (distractionMinutes < limit + 15) {
                        newMode = 1
                    } else {
                        newMode = 2
                    }

                    // Only send HTTP if the mode actually changed
                    if (newMode != lastModeSent) {
                        lastModeSent = newMode
                        when (newMode) {
                            0 -> httpLampController.sendFocus(espIp)
                            1 -> httpLampController.sendWarning(espIp)
                            2 -> httpLampController.sendDistraction(espIp)
                        }
                        Log.d(TAG, "Mode changed to $newMode. Sent command to ESP32.")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Monitoring error: ${e.message}")
                }

                delay(CHECK_INTERVAL_MS)
            }
        }
    }
}
