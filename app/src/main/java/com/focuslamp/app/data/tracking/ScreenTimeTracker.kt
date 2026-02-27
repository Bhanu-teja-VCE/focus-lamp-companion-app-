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

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Using queryEvents instead of queryUsageStats bypasses OEM aggregation bugs entirely.
        // We'll iterate through every time an app was opened or closed today.
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        
        val appUsageMap = mutableMapOf<String, Long>()
        val lastEventMap = mutableMapOf<String, Long>()
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val packageName = event.packageName ?: continue
            
            // 1 = ACTIVITY_RESUMED, 2 = ACTIVITY_PAUSED
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                lastEventMap[packageName] = event.timeStamp
            } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED ||
                       event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED) {
                val lastTime = lastEventMap[packageName]
                if (lastTime != null) {
                    val duration = event.timeStamp - lastTime
                    if (duration > 0) {
                        appUsageMap[packageName] = (appUsageMap[packageName] ?: 0L) + duration
                    }
                    lastEventMap.remove(packageName)
                }
            }
        }
        
        // Handle apps that are currently open (resumed but never paused yet)
        for ((packageName, lastTime) in lastEventMap) {
            val duration = endTime - lastTime
            if (duration > 0) {
                appUsageMap[packageName] = (appUsageMap[packageName] ?: 0L) + duration
            }
        }

        val appUsageList = mutableListOf<AppUsageItem>()
        
        for ((packageName, totalMillis) in appUsageMap) {
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
