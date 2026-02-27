package com.focuslamp.app.data.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val usageMillis: Long
)

class ScreenTimeTracker(private val context: Context) {

    /**
     * Check if the user has granted Usage Access permission.
     * Direct the user to Settings > Apps > Special app access > Usage access if false.
     */
    fun hasUsageAccessPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns today's per-app foreground usage, built from raw UsageEvents.
     *
     * WHY queryEvents() instead of queryUsageStats() / queryAndAggregateUsageStats():
     * OEM ROMs (Vivo/Funtouch, MIUI, ColorOS, etc.) frequently corrupt or truncate
     * the aggregated stats that the higher-level APIs return. However, they still feed
     * raw events into the event stream that the lower-level queryEvents() reads from,
     * because that stream is also used internally by the OS itself. Apps like Action Dash
     * rely exclusively on this event-based approach for exactly this reason.
     *
     * The algorithm:
     *  1. Iterate every event from midnight → now.
     *  2. When we see ACTIVITY_RESUMED for a package, record the timestamp as a session start.
     *  3. When we see ACTIVITY_PAUSED / ACTIVITY_STOPPED for a package, close the open
     *     session and accumulate the elapsed time.
     *  4. If a session is still open at query time (app is currently in foreground),
     *     close it against the current time so live usage is included.
     */
    fun getAllAppsUsageToday(): List<AppUsageItem> {
        if (!hasUsageAccessPermission()) return emptyList()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val startTime = getMidnightTimestamp()
        val endTime = System.currentTimeMillis()

        // --- Step 1: Collect launchable packages so we only show real user-facing apps ---
        val launchablePackages = getLaunchablePackages()

        // --- Step 2: Walk the raw event stream ---
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        // packageName -> timestamp when the app last came to foreground
        val sessionStarts = mutableMapOf<String, Long>()
        // packageName -> total accumulated milliseconds today
        val accumulatedTime = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // App came to foreground. Record start time.
                    // Only update if there isn't already an open session (handles multi-window edge cases).
                    if (!sessionStarts.containsKey(pkg)) {
                        sessionStarts[pkg] = event.timeStamp
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    // App left foreground. Close the open session if one exists.
                    val sessionStart = sessionStarts.remove(pkg) ?: continue
                    val elapsed = event.timeStamp - sessionStart
                    if (elapsed > 0) {
                        accumulatedTime[pkg] = (accumulatedTime[pkg] ?: 0L) + elapsed
                    }
                }
            }
        }

        // --- Step 3: Close any sessions still open (app is currently in foreground) ---
        for ((pkg, sessionStart) in sessionStarts) {
            val elapsed = endTime - sessionStart
            if (elapsed > 0) {
                accumulatedTime[pkg] = (accumulatedTime[pkg] ?: 0L) + elapsed
            }
        }

        // --- Step 4: Build the result list ---
        val pm = context.packageManager
        val result = mutableListOf<AppUsageItem>()

        for ((packageName, totalMillis) in accumulatedTime) {
            // Skip: less than 1 second, non-launchable (system services), or this app itself
            if (totalMillis < MIN_USAGE_THRESHOLD_MS) continue
            if (packageName !in launchablePackages) continue
            if (packageName == context.packageName) continue

            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)

                result.add(
                    AppUsageItem(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        usageMillis = totalMillis
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Package was uninstalled during the query window — skip silently.
            }
        }

        return result.sortedByDescending { it.usageMillis }
    }

    /**
     * Gets total foreground time per day for the last 7 days.
     * Returns a list of Long values (minutes) ordered from 6 days ago to today.
     */
    fun getWeeklyUsage(): List<Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return List(7) { 0L }

        val weeklyStats = mutableListOf<Long>()
        
        // Go 7 days back
        for (i in 6 downTo 0) {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = startTime + (24 * 60 * 60 * 1000)

            val finalEndTime = if (i == 0) System.currentTimeMillis() else endTime

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                finalEndTime
            ) ?: emptyList()

            var totalMillis = 0L
            stats.forEach { 
                if (it.totalTimeInForeground > 1000) {
                    totalMillis += it.totalTimeInForeground
                }
            }
            weeklyStats.add(totalMillis / 1000 / 60)
        }
        
        return weeklyStats
    }

    /**
     * Returns the total foreground time (in minutes) spent on the given package names today.
     */
    fun getDistractionTimeToday(distractingPackages: Set<String>): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0

        val startTime = getMidnightTimestamp()
        val endTime = System.currentTimeMillis()

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        var totalDistractionMillis = 0L

        val aggregatedStats = usageStatsList
            .groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }

        for ((packageName, totalMillis) in aggregatedStats) {
            if (packageName in distractingPackages) {
                totalDistractionMillis += totalMillis
            }
        }

        return totalDistractionMillis / 1000 / 60
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun getMidnightTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getLaunchablePackages(): Set<String> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    companion object {
        // Only show apps used for more than 1 second (filters out transient system touches)
        private const val MIN_USAGE_THRESHOLD_MS = 1_000L
    }
}
