package com.focuslamp.app.data.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Reads Android's usage event stream and turns it into numbers with [UsageEngine].
 * Every screen, the service, the parent dashboard and the AI coach read the result through
 * [UsageRepository], so they can never disagree with each other.
 */
class ScreenTimeTracker(
    private val context: Context,
    private val appInfo: AppInfoCache
) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Raw events between [from] and [to], translated to engine events. */
    fun readEvents(from: Long, to: Long): List<RawUsageEvent> {
        if (!hasUsagePermission()) return emptyList()
        val stream = usm.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<RawUsageEvent>(2048)
        val e = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(e)
            val kind = kindOf(e.eventType) ?: continue
            out += RawUsageEvent(e.timeStamp, e.packageName ?: "android", e.className, kind)
        }
        return out
    }

    /** Usage between [dayStart] and [end], recovering sessions that were already open at dayStart. */
    fun compute(dayStart: Long, end: Long): UsageComputation {
        val events = readEvents(dayStart - LOOKBACK_MS, end)
        return UsageEngine.compute(
            events = events,
            windowStart = dayStart,
            windowEnd = end,
            excluded = appInfo.excludedPackages(),
            countOnly = appInfo.launchableApps().keys
        )
    }

    /** Totals for several day windows from a single event read (for the weekly chart). */
    fun computeDays(dayStarts: List<Long>, now: Long): List<UsageComputation> {
        if (dayStarts.isEmpty()) return emptyList()
        val events = readEvents(dayStarts.first() - LOOKBACK_MS, now)
        val excluded = appInfo.excludedPackages()
        val launchable = appInfo.launchableApps().keys
        return dayStarts.mapIndexed { i, start ->
            val end = if (i + 1 < dayStarts.size) dayStarts[i + 1] else now
            UsageEngine.compute(events, start, minOf(end, now), excluded, launchable)
        }
    }

    companion object {
        /** How far before midnight we look to recover a session that was already open. */
        const val LOOKBACK_MS = 3 * 60 * 60 * 1000L

        // Raw values so they work on API 26 (several named constants are API 28/29+).
        fun kindOf(type: Int): UsageEventKind? = when (type) {
            1 -> UsageEventKind.RESUMED      // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
            2 -> UsageEventKind.PAUSED       // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
            23 -> UsageEventKind.STOPPED     // ACTIVITY_STOPPED
            15 -> UsageEventKind.SCREEN_ON   // SCREEN_INTERACTIVE
            16 -> UsageEventKind.SCREEN_OFF  // SCREEN_NON_INTERACTIVE
            17 -> UsageEventKind.LOCKED      // KEYGUARD_SHOWN
            18 -> UsageEventKind.UNLOCKED    // KEYGUARD_HIDDEN
            26 -> UsageEventKind.SHUTDOWN    // DEVICE_SHUTDOWN
            27 -> UsageEventKind.STARTUP     // DEVICE_STARTUP
            else -> null
        }
    }
}
