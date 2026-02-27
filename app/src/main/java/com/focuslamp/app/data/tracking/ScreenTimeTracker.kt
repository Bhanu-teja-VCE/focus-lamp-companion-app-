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
     * Checks if the app has Usage Access permission using AppOpsManager.
     */
    fun hasUsagePermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
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

        // Get all packages that are actual "apps" with a launcher icon
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val launchablePackages = resolveInfos.map { it.activityInfo.packageName }.toSet()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // The most accurate system-aggregated stats map
        val usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(
            startTime,
            endTime
        ) ?: emptyMap()

        val appUsageList = mutableListOf<AppUsageItem>()
        
        for ((packageName, stats) in usageStatsMap) {
            val totalMillis = stats.totalTimeInForeground
            // Only show launchable user apps, used > 1 minute, and exclude our own app
            if (totalMillis > 60_000 && packageName in launchablePackages && packageName != context.packageName) {
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
                    // Skip if fails
                }
            }
        }
        
        // Sort descending
        return appUsageList.sortedByDescending { it.usageMillis }
    }
}
