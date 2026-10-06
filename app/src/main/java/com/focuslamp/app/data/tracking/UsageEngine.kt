package com.focuslamp.app.data.tracking

/**
 * The usage events we care about, translated from Android's UsageEvents.Event types.
 */
enum class UsageEventKind {
    RESUMED,      // an activity came to the front
    PAUSED,       // an activity left the front
    STOPPED,      // an activity is no longer visible (arrives after the next one resumed)
    SCREEN_ON,
    SCREEN_OFF,
    LOCKED,       // lock screen shown
    UNLOCKED,     // lock screen dismissed: one "pickup"
    SHUTDOWN,
    STARTUP
}

data class RawUsageEvent(
    val time: Long,
    val packageName: String,
    val className: String?,
    val kind: UsageEventKind
)

data class UsageComputation(
    /** Foreground milliseconds per package inside the window, excluding [excluded] packages. */
    val perAppMs: Map<String, Long>,
    /** Unlocks inside the window (falls back to screen-on count on phones without a lock screen). */
    val pickups: Int,
    /** The package still in front at the end of the window, if any. */
    val foregroundAtEnd: String?
) {
    val totalMs: Long get() = perAppMs.values.sum()
}

/**
 * Rebuilds per-app foreground time from the usage event stream.
 *
 * Why this replaced the old tracker:
 *  1. Android reports lifecycle per *activity*, not per app. The old code kept one
 *     "current app" and treated any PAUSED/STOPPED from that package as the app closing.
 *     When an app moves between its own screens the order is PAUSED(A), RESUMED(B), STOPPED(A),
 *     so STOPPED(A) silently ended screen B's session and everything after it was lost.
 *     Here every package keeps a set of its resumed activities and only closes when that set is empty.
 *  2. Screen-off, lock and shutdown events are reported under the package "android", which the
 *     old code filtered out before handling them, so apps kept "running" with the screen off.
 *     Here they are handled first and always close the open session.
 *  3. Sessions that started before the window (e.g. before midnight) are recovered by feeding
 *     events from before [windowStart] and clipping, instead of being dropped.
 *  4. Excluded packages (launcher, keyboard, Focus Lamp itself) still take part in the state
 *     machine, so opening the launcher correctly ends the previous app; they're just not counted.
 */
object UsageEngine {

    fun compute(
        events: List<RawUsageEvent>,
        windowStart: Long,
        windowEnd: Long,
        excluded: Set<String> = emptySet(),
        countOnly: Set<String>? = null
    ): UsageComputation {
        val resumed = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        var unlocks = 0
        var screenOns = 0
        var lastForeground: String? = null

        fun counts(pkg: String): Boolean =
            pkg !in excluded && (countOnly == null || pkg in countOnly)

        fun close(pkg: String, at: Long) {
            val start = openSince.remove(pkg) ?: return
            resumed.remove(pkg)
            val from = maxOf(start, windowStart)
            val to = minOf(at, windowEnd)
            if (to > from && counts(pkg)) {
                totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            }
            if (lastForeground == pkg) lastForeground = null
        }

        fun closeAll(at: Long) {
            for (pkg in openSince.keys.toList()) close(pkg, at)
        }

        for (e in events.sortedBy { it.time }) {
            if (e.time > windowEnd) break
            when (e.kind) {
                UsageEventKind.RESUMED -> {
                    // A phone shows one app at a time. A new app coming forward ends the previous
                    // one even if its PAUSED event was dropped (some OEMs lose events).
                    for (other in openSince.keys.toList()) {
                        if (other != e.packageName) close(other, e.time)
                    }
                    if (e.packageName !in openSince) openSince[e.packageName] = e.time
                    resumed.getOrPut(e.packageName) { mutableSetOf() }.add(e.className ?: "")
                    lastForeground = e.packageName
                }

                UsageEventKind.PAUSED, UsageEventKind.STOPPED -> {
                    val set = resumed[e.packageName]
                    if (set != null) {
                        set.remove(e.className ?: "")
                        if (set.isEmpty()) close(e.packageName, e.time)
                    }
                }

                UsageEventKind.SCREEN_OFF,
                UsageEventKind.LOCKED,
                UsageEventKind.SHUTDOWN -> closeAll(e.time)

                UsageEventKind.STARTUP -> {
                    // A reboot without a shutdown event (crash, battery pull): we don't know when the
                    // last app stopped, so drop it rather than stretch it across the reboot.
                    openSince.clear()
                    resumed.clear()
                    lastForeground = null
                }

                UsageEventKind.UNLOCKED -> if (e.time >= windowStart) unlocks++
                UsageEventKind.SCREEN_ON -> if (e.time >= windowStart) screenOns++
            }
        }

        val foreground = lastForeground?.takeIf { it in openSince }
        closeAll(windowEnd)

        return UsageComputation(
            perAppMs = totals,
            pickups = if (unlocks > 0) unlocks else screenOns,
            foregroundAtEnd = foreground
        )
    }
}
