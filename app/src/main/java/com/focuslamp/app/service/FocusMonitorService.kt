package com.focuslamp.app.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.lamp.LampState
import com.focuslamp.app.data.tracking.ForegroundWatcher
import com.focuslamp.app.data.tracking.UsageSnapshot
import com.focuslamp.app.util.Format
import com.focuslamp.app.util.Notifications
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Permissions
import com.focuslamp.app.util.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Focus Lamp running in the background on the student's phone:
 *  - refreshes today's usage every 10 s (every 60 s with the screen off)
 *  - re-checks the app in front and blocks it if a limit ran out
 *  - drives the lamp (with retries and a once-a-minute heartbeat)
 *  - reports to the parent's phone every 5 minutes when linked
 *  - polls the foreground app every second when the accessibility blocker is off
 */
class FocusMonitorService : LifecycleService() {

    private var fastPoll: Job? = null
    private lateinit var watcher: ForegroundWatcher
    private var lastIssues: List<String>? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        watcher = ForegroundWatcher(Graph.tracker)
        startInForeground(buildNotification(null))
        Graph.family.startChildSync()
        lifecycleScope.launch { mainLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PUBLISH_NOW) Graph.settings.lastStatusPublish = 0L
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        fastPoll?.cancel()
        super.onDestroy()
    }

    private suspend fun mainLoop() {
        while (lifecycleScope.isActive) {
            if (Graph.settings.role != Role.STUDENT) {
                stopSelf()
                return
            }
            val snapshot = Graph.usage.refresh()
            val enforcer = Graph.enforcer

            if (!enforcer.hasAccessibilityHost) {
                enforcer.onForeground(snapshot.foreground)
                ensureFastPoll()
            } else {
                fastPoll?.cancel()
                fastPoll = null
                enforcer.evaluate()
            }

            Graph.lamp.apply(lampStateFor(snapshot))
            updateNotification(snapshot)
            maybePublish(snapshot)

            delay(if (isScreenOn()) TICK_MS else TICK_SCREEN_OFF_MS)
        }
    }

    /** Fallback when accessibility access is off: check the app in front once a second. */
    private fun ensureFastPoll() {
        if (fastPoll?.isActive == true) return
        if (!Permissions.isGranted(this, PermissionItem.OVERLAY)) return
        fastPoll = lifecycleScope.launch {
            while (isActive && !Graph.enforcer.hasAccessibilityHost) {
                if (isScreenOn()) {
                    val pkg = withContext(Dispatchers.IO) { watcher.poll() }
                    if (pkg != Graph.enforcer.foreground) Graph.enforcer.onForeground(pkg)
                }
                delay(1_000L)
            }
        }
    }

    private fun lampStateFor(s: UsageSnapshot): LampState {
        val fg = Graph.enforcer.foreground
        return when {
            Graph.enforcer.active.value != null -> LampState.DISTRACTION
            Graph.focus.state.value.running && fg != null && Graph.distracting.isDistracting(fg) -> LampState.DISTRACTION
            else -> LampState.forBudget(s.distractionMs, Graph.settings.timeLimitMinutes)
        }
    }

    private suspend fun maybePublish(s: UsageSnapshot) {
        if (!Graph.family.isPaired) return
        val issues = protectionIssues()
        val due = System.currentTimeMillis() - Graph.settings.lastStatusPublish > PUBLISH_MS
        if (!due && issues == lastIssues) return
        lastIssues = issues
        val blocks = Graph.enforcer.currentBlocks()
        withContext(Dispatchers.IO) {
            Graph.family.publishStatus(s, Graph.settings.timeLimitMinutes, blocks, issues)
        }
    }

    private fun protectionIssues(): List<String> = buildList {
        if (!Permissions.isGranted(this@FocusMonitorService, PermissionItem.USAGE)) add("Usage access is off")
        if (!Permissions.canBlock(this@FocusMonitorService)) add("App blocking is off")
        if (!Permissions.isGranted(this@FocusMonitorService, PermissionItem.UNINSTALL)) add("Uninstall protection is off")
    }

    private fun isScreenOn() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_MONITOR, n, type)
    }

    private fun updateNotification(s: UsageSnapshot) {
        val n = buildNotification(s)
        try {
            androidx.core.app.NotificationManagerCompat.from(this).notify(Notifications.ID_MONITOR, n)
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(s: UsageSnapshot?): Notification {
        val budget = Graph.settings.timeLimitMinutes
        val text = when {
            s == null -> "Starting…"
            !s.hasPermission -> "Usage access is off. Tap to turn it on."
            else -> "${Format.durationWords(s.distractionMs)} of $budget min used on distracting apps"
        }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_lamp)
            .setContentTitle("Focus Lamp is on")
            .setContentText(text)
            .setContentIntent(Notifications.openAppIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TICK_MS = 10_000L
        private const val TICK_SCREEN_OFF_MS = 60_000L
        private const val PUBLISH_MS = 5 * 60_000L
        const val ACTION_PUBLISH_NOW = "com.focuslamp.app.PUBLISH_NOW"

        @Volatile
        var running = false
            private set

        /** Starts monitoring if this phone is a student's and usage access is granted. */
        fun startIfAllowed(context: Context, publishNow: Boolean = false) {
            if (Graph.settings.role != Role.STUDENT) return
            if (!Graph.tracker.hasUsagePermission()) return
            val intent = Intent(context, FocusMonitorService::class.java)
            if (publishNow) intent.action = ACTION_PUBLISH_NOW
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+ refuses background starts in some states; the next app open retries.
            }
        }
    }
}
