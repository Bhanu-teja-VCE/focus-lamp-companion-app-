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
 * Calculates exact foreground usage and total screen time using Android UsageStatsManager.
 */
class ScreenTimeTracker(private val context: Context) {

    /**
     * Check if the user has granted Usage Access permission.
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
     * Calculates the total time the user has actively been using the phone today (Screen On / Foreground Apps).
     * Uses ActionDash-grade event tracking (ACTIVITY_RESUMED & ACTIVITY_PAUSED) with daily aggregate fallback.
     */
    fun getTotalScreenTimeToday(): Long {
        if (!hasUsagePermission()) return 0L

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return 0L

        val startTime = getMidnightTimestamp()
        val endTime = System.currentTimeMillis()

        // 1. Primary Method: Query exact Activity events since midnight
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var totalForegroundMs = 0L
        val appResumedTimes = mutableMapOf<String, Long>()
        val ignoredPackages = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin"
        )

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue

            if (ignoredPackages.contains(pkg)) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    appResumedTimes[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = appResumedTimes.remove(pkg)
                    if (start != null && start > 0) {
                        val duration = event.timeStamp - start
                        if (duration in 1..86400000L) {
                            totalForegroundMs += duration
                        }
                    }
                }
            }
        }

        // Include currently active foreground app
        for ((_, start) in appResumedTimes) {
            val duration = endTime - start
            if (duration in 1..86400000L) {
                totalForegroundMs += duration
            }
        }

        // 2. Fallback Method: Sum from UsageStats Manager daily interval
        if (totalForegroundMs <= 0L) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (!stats.isNullOrEmpty()) {
                totalForegroundMs = stats
                    .filter { !ignoredPackages.contains(it.packageName) && it.lastTimeUsed >= startTime }
                    .sumOf { it.totalTimeInForeground }
            }
        }

        return (totalForegroundMs / 1000 / 60).coerceAtLeast(0L)
    }

    // -------------------------------------------------------------------------
    // Per-App Usage (ActionDash & Digital Wellbeing Accuracy)
    // -------------------------------------------------------------------------

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val usageMinutes: Long,
        val isDistracting: Boolean = false
    )

    /**
     * Returns a list of apps with their individual foreground usage today,
     * sorted by most usage first. Marks apps in the distracting set.
     */
    fun getPerAppUsageToday(distractingPackages: Set<String> = emptySet()): List<AppUsageInfo> {
        if (!hasUsagePermission()) return emptyList()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val endTime = System.currentTimeMillis()
        val startTime = getMidnightTimestamp()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val pm = context.packageManager
        val results = mutableListOf<AppUsageInfo>()

        val grouped = stats?.filter { it.lastTimeUsed >= startTime }?.groupBy { it.packageName } ?: emptyMap()

        for ((packageName, pkgStats) in grouped) {
            if (packageName == "android" || packageName == "com.android.systemui") continue

            val totalTimeMs = pkgStats.sumOf { it.totalTimeInForeground }
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
     * Returns the total screen time (in minutes) for only the apps
     * the user has marked as "distracting".
     */
    fun getDistractionTimeOnly(distractingPackages: Set<String>): Long {
        if (!hasUsagePermission() || distractingPackages.isEmpty()) return 0L

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return 0L

        val endTime = System.currentTimeMillis()
        val startTime = getMidnightTimestamp()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val totalDistractionMs = stats
            ?.filter { distractingPackages.contains(it.packageName) && it.lastTimeUsed >= startTime }
            ?.sumOf { it.totalTimeInForeground } ?: 0L

        return (totalDistractionMs / 1000 / 60).coerceAtLeast(0L)
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

    /**
     * Determines the exact app package currently in the foreground by looking
     * at the most recent ACTIVITY_RESUMED event in the last 5 minutes.
     */
    fun getCurrentForegroundApp(): String? {
        if (!hasUsagePermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 5) // Look back 5 minutes

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
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
