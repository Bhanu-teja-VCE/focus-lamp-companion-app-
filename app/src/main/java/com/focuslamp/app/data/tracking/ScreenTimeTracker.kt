package com.focuslamp.app.data.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar


class ScreenTimeTracker(private val context: Context) {

    /**
     * Check if the user has granted Usage Access permission.
     * Direct the user to Settings > Apps > Special app access > Usage access if false.
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
     * Calculates the total time the user has actively been using the phone today (Screen On/Interactive).
     *
     * This bypasses Vivo's aggressive per-app tracking block by looking at hardware events
     * instead of specific application packages.
     */
    fun getTotalScreenTimeToday(): Long {
        if (!hasUsagePermission()) return 0L

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return 0L

        val startTime = getMidnightTimestamp()
        val endTime = System.currentTimeMillis()

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var totalInteractiveTime = 0L
        var lastInteractiveTimestamp = 0L
        var isCurrentlyInteractive = false

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    if (!isCurrentlyInteractive) {
                        lastInteractiveTimestamp = event.timeStamp
                        isCurrentlyInteractive = true
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    if (isCurrentlyInteractive && lastInteractiveTimestamp > 0) {
                        val sessionDuration = event.timeStamp - lastInteractiveTimestamp
                        if (sessionDuration > 0) {
                            totalInteractiveTime += sessionDuration
                        }
                        isCurrentlyInteractive = false
                        lastInteractiveTimestamp = 0L
                    }
                }
            }
        }

        // If the screen is still on right now, add the ongoing session
        if (isCurrentlyInteractive && lastInteractiveTimestamp > 0) {
            val ongoingDuration = endTime - lastInteractiveTimestamp
            if (ongoingDuration > 0) {
                totalInteractiveTime += ongoingDuration
            }
        }

        return totalInteractiveTime / 1000 / 60 // Return exactly in minutes
    }

    // -------------------------------------------------------------------------
    // Per-App Usage (using queryAndAggregateUsageStats — more Vivo-compatible)
    // -------------------------------------------------------------------------

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val usageMinutes: Long
    )

    /**
     * Returns a list of apps with their individual foreground usage today,
     * sorted by most usage first.
     *
     * Uses queryAndAggregateUsageStats which pre-aggregates per-app data
     * and is less susceptible to Vivo OEM event suppression.
     */
    fun getPerAppUsageToday(): List<AppUsageInfo> {
        if (!hasUsagePermission()) return emptyList()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val endTime = System.currentTimeMillis()
        val startTime = getMidnightTimestamp()

        val aggregateStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val pm = context.packageManager

        val results = mutableListOf<AppUsageInfo>()

        for ((packageName, stats) in aggregateStats) {
            val totalTimeMs = stats.totalTimeInForeground
            if (totalTimeMs > 60_000) { // Only show apps with > 1 minute usage
                val minutes = totalTimeMs / (1000 * 60)

                // Try to get app name and icon
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }

                val icon = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }

                results.add(AppUsageInfo(packageName, appName, icon, minutes))
            }
        }

        return results.sortedByDescending { it.usageMinutes }
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
}
