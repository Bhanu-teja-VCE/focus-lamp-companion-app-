package com.focuslamp.app.data.tracking

import com.focuslamp.app.util.DayClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppUsage(
    val packageName: String,
    val label: String,
    val ms: Long,
    val distracting: Boolean
)

data class UsageSnapshot(
    val dayStart: Long,
    val computedAt: Long,
    val apps: List<AppUsage>,
    val totalMs: Long,
    val distractionMs: Long,
    val pickups: Int,
    val foreground: String?,
    val hasPermission: Boolean
) {
    private val byPackage: Map<String, Long> by lazy { apps.associate { it.packageName to it.ms } }
    fun msFor(packageName: String): Long = byPackage[packageName] ?: 0L

    companion object {
        val EMPTY = UsageSnapshot(0, 0, emptyList(), 0, 0, 0, null, hasPermission = true)
    }
}

data class DayTotal(val dayStart: Long, val totalMs: Long, val distractionMs: Long)

/**
 * The single source of truth for today's numbers. Refreshed by the monitor service every
 * few seconds and on demand by screens.
 */
class UsageRepository(
    private val tracker: ScreenTimeTracker,
    private val appInfo: AppInfoCache,
    private val distracting: DistractingAppsManager
) {
    private val _snapshot = MutableStateFlow(UsageSnapshot.EMPTY)
    val snapshot: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()

    fun hasPermission() = tracker.hasUsagePermission()

    suspend fun refresh(): UsageSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val dayStart = DayClock.startOfDay(now)
            if (!tracker.hasUsagePermission()) {
                return@withLock UsageSnapshot.EMPTY.copy(dayStart = dayStart, computedAt = now, hasPermission = false)
                    .also { _snapshot.value = it }
            }
            val result = tracker.compute(dayStart, now)
            build(result, dayStart, now).also { _snapshot.value = it }
        }
    }

    /** Re-labels the current snapshot after the student ticks or unticks an app (no re-query). */
    fun reapplyDistracting() {
        val s = _snapshot.value
        val set = distracting.getAll()
        val apps = s.apps.map { it.copy(distracting = it.packageName in set) }
        _snapshot.value = s.copy(apps = apps, distractionMs = apps.filter { it.distracting }.sumOf { it.ms })
    }

    suspend fun lastDays(days: Int = 7): List<DayTotal> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val starts = (days - 1 downTo 0).map { DayClock.startOfDay(now, -it) }
        val set = distracting.getAll()
        tracker.computeDays(starts, now).mapIndexed { i, c ->
            DayTotal(starts[i], c.totalMs, c.perAppMs.filterKeys { it in set }.values.sum())
        }
    }

    private fun build(c: UsageComputation, dayStart: Long, now: Long): UsageSnapshot {
        val set = distracting.getAll()
        val apps = c.perAppMs
            .filterValues { it >= 1_000L }
            .map { (pkg, ms) -> AppUsage(pkg, appInfo.label(pkg), ms, pkg in set) }
            .sortedByDescending { it.ms }
        return UsageSnapshot(
            dayStart = dayStart,
            computedAt = now,
            apps = apps,
            totalMs = apps.sumOf { it.ms },
            distractionMs = apps.filter { it.distracting }.sumOf { it.ms },
            pickups = c.pickups,
            foreground = c.foregroundAtEnd,
            hasPermission = true
        )
    }
}
