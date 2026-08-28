package com.focuslamp.app.data.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar

/**
 * ScreenTimeTracker — ActionDash & Digital Wellbeing grade screen time engine.
 * Reconstructs exact per-app foreground usage and screen time from Android UsageEvents stream,
 * handling screen off events, app switching, and OEM fallback.
 */
class ScreenTimeTracker(private val context: Context) {

    /**
     * Checks if the user has granted Usage Access permission (PACKAGE_USAGE_STATS).
     * Uses AppOpsManager.checkOpNoThrow for reliable runtime check across OEM devices.
     */
    fun hasUsagePermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Reconstructs exact foreground durations for all apps from UsageEvents stream since midnight.
     * Properly handles SCREEN_NON_INTERACTIVE and KEYGUARD_SHOWN events so screen-off time
     * does not inflate app usage.
     */
    private fun getExactAppDurationsToday(): Map<String, Long> {
        if (!hasUsagePermission()) return emptyMap()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()

        val startTime = getMidnightTimestamp()
        val endTime = System.currentTimeMillis()

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return emptyMap()
        val event = UsageEvents.Event()

        val appDurations = mutableMapOf<String, Long>()
        val ignoredPackages = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin"
        )

        var lastResumedPkg: String? = null
        var lastResumedTime: Long = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue

            if (ignoredPackages.contains(pkg)) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Close previous active app session if user switched apps without pause event
                    if (lastResumedPkg != null && lastResumedPkg != pkg) {
                        val duration = event.timeStamp - lastResumedTime
                        if (duration in 1..86400000L) {
                            appDurations[lastResumedPkg!!] = (appDurations[lastResumedPkg!!] ?: 0L) + duration
                        }
                    }
                    lastResumedPkg = pkg
                    lastResumedTime = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (lastResumedPkg == pkg) {
                        val duration = event.timeStamp - lastResumedTime
                        if (duration in 1..86400000L) {
                            appDurations[pkg] = (appDurations[pkg] ?: 0L) + duration
                        }
                        lastResumedPkg = null
                    }
                }

                // Handle screen off or device lock — end active foreground session
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> {
                    if (lastResumedPkg != null) {
                        val duration = event.timeStamp - lastResumedTime
                        if (duration in 1..86400000L) {
                            appDurations[lastResumedPkg!!] = (appDurations[lastResumedPkg!!] ?: 0L) + duration
                        }
                        lastResumedPkg = null
                    }
                }
            }
        }

        // Account for currently active app running up to current time
        if (lastResumedPkg != null) {
            val duration = endTime - lastResumedTime
            if (duration in 1..86400000L) {
                appDurations[lastResumedPkg!!] = (appDurations[lastResumedPkg!!] ?: 0L) + duration
            }
        }

        // Fallback to queryUsageStats if queryEvents returned no data (e.g., OEM restriction or fresh boot)
        if (appDurations.isEmpty()) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (!stats.isNullOrEmpty()) {
                for (s in stats) {
                    if (!ignoredPackages.contains(s.packageName) && s.lastTimeUsed >= startTime) {
                        appDurations[s.packageName] = s.totalTimeInForeground
                    }
                }
            }
        }

        return appDurations
    }

    /**
     * Calculates total active daily screen time (in minutes).
     */
    fun getTotalScreenTimeToday(): Long {
        val appDurations = getExactAppDurationsToday()
        val totalMs = appDurations.values.sum()
        return (totalMs / 1000 / 60).coerceAtLeast(0L)
    }

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val usageMinutes: Long,
        val isDistracting: Boolean = false
    )

    /**
     * Returns a sorted list of apps with individual foreground usage today using queryEvents stream.
     */
    fun getPerAppUsageToday(distractingPackages: Set<String> = emptySet()): List<AppUsageInfo> {
        val appDurations = getExactAppDurationsToday()
        val pm = context.packageManager
        val results = mutableListOf<AppUsageInfo>()

        for ((packageName, totalTimeMs) in appDurations) {
            if (totalTimeMs >= 60_000) { // >= 1 minute
                val minutes = totalTimeMs / (1000 * 60)

                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    continue
                }

                val icon = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }

                val isDistracting = distractingPackages.contains(packageName)
                results.add(AppUsageInfo(packageName, appName, icon, minutes, isDistracting))
            }
        }

        return results.sortedByDescending { it.usageMinutes }
    }

    /**
     * Returns the total screen time (in minutes) for only the user-blacklisted distracting apps.
     */
    fun getDistractionTimeOnly(distractingPackages: Set<String>): Long {
        if (distractingPackages.isEmpty()) return 0L

        val appDurations = getExactAppDurationsToday()
        val totalDistractionMs = appDurations
            .filter { distractingPackages.contains(it.key) }
            .values
            .sum()

        return (totalDistractionMs / 1000 / 60).coerceAtLeast(0L)
    }

    private fun getMidnightTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Determines the exact app package currently in the foreground by checking
     * the most recent ACTIVITY_RESUMED event in the last 5 minutes.
     */
    fun getCurrentForegroundApp(): String? {
        if (!hasUsagePermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 5) // Look back 5 minutes

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return null
        val event = UsageEvents.Event()
        var currentForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundApp = event.packageName
            }
        }

        return currentForegroundApp
    }
}
