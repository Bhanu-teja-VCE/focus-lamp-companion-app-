package com.focuslamp.app.data.tracking

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

/**
 * Queries the UsageStatsManager API to check how much time the user
 * has spent on "distracting" apps today.
 *
 * IMPORTANT: Requires PACKAGE_USAGE_STATS permission.
 * The user must manually enable "Usage Access" in Settings > Security > Apps with usage access.
 */
class ScreenTimeTracker(private val context: Context) {

    companion object {
        private const val TAG = "ScreenTimeTracker"
    }

    /**
     * Returns the total foreground time (in minutes) spent on the given package names today.
     */
    fun getDistractionTimeToday(distractingPackages: Set<String>): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager not available")
            return 0
        }

        // Query from midnight today until now
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        var totalDistractionMillis = 0L

        for (stats in usageStatsList) {
            if (stats.packageName in distractingPackages) {
                totalDistractionMillis += stats.totalTimeInForeground
                Log.d(TAG, "${stats.packageName}: ${stats.totalTimeInForeground / 1000 / 60} min")
            }
        }

        val totalMinutes = totalDistractionMillis / 1000 / 60
        Log.d(TAG, "Total distraction time today: $totalMinutes minutes")
        return totalMinutes
    }

    /**
     * Checks if the app has Usage Access permission.
     */
    fun hasUsagePermission(): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MINUTE, -1)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats != null && stats.isNotEmpty()
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
            val endTime = startTime + (24 * 60 * 60 * 1000) // End of that day

            // Adjust end time to not be in the future for today
            val finalEndTime = if (i == 0) System.currentTimeMillis() else endTime

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                finalEndTime
            ) ?: emptyList()

            var totalMillis = 0L
            stats.forEach { 
                // Ignore extremely small usages (like < 1 second) and focus on user apps
                if (it.totalTimeInForeground > 1000) {
                    totalMillis += it.totalTimeInForeground
                }
            }
            weeklyStats.add(totalMillis / 1000 / 60)
        }
        
        return weeklyStats
    }

    /**
     * Gets a detailed list of all apps used today, sorted by duration.
     */
    fun getAllAppsUsageToday(): List<AppUsageItem> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
            
        val pm = context.packageManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Some custom Android skins (like Vivo/Xiaomi) have bugs with queryAndAggregateUsageStats
        // The most reliable way is to query INTERVAL_DAILY and manually sum the components
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        // Group by package name and sum the total time in foreground
        val aggregatedStats = usageStatsList
            .groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }

        val appUsageList = mutableListOf<AppUsageItem>()
        
        for ((packageName, totalMillis) in aggregatedStats) {
            // Only show apps used for more than 1 minute (60,000 ms)
            // also filter out this own app to match digital wellbeing
            if (totalMillis > 60_000 && packageName != context.packageName) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    
                    appUsageList.add(
                        AppUsageItem(
                            packageName = packageName,
                            appName = appName,
                            icon = icon,
                            usageMillis = totalMillis
                        )
                    )
                } catch (e: Exception) {
                    // Package not found or system app, skip
                }
            }
        }
        
        // Sort descending
        return appUsageList.sortedByDescending { it.usageMillis }
    }
}
