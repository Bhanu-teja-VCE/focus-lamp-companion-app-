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
}
