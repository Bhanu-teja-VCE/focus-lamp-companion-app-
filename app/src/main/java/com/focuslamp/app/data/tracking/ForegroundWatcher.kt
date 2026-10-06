package com.focuslamp.app.data.tracking

/**
 * Cheap "which app is in front right now" for when the accessibility blocker is off.
 * Reads only the events since the last poll, so it can run every second.
 *
 * The old version looked back 5 minutes for the last RESUMED event, so after 5 minutes
 * inside an app it returned null and the block screen disappeared.
 */
class ForegroundWatcher(private val tracker: ScreenTimeTracker) {

    private var lastQuery = 0L
    var current: String? = null
        private set

    fun seed(foreground: String?, at: Long) {
        current = foreground
        lastQuery = at
    }

    fun poll(now: Long = System.currentTimeMillis()): String? {
        if (lastQuery == 0L) {
            seed(UsageEngine.compute(tracker.readEvents(now - 60 * 60_000L, now), now - 60 * 60_000L, now).foregroundAtEnd, now)
            return current
        }
        // Small overlap so an event stamped just before the last query isn't missed.
        for (e in tracker.readEvents(lastQuery - 2_000L, now)) {
            when (e.kind) {
                UsageEventKind.RESUMED -> current = e.packageName
                UsageEventKind.SCREEN_OFF, UsageEventKind.LOCKED, UsageEventKind.SHUTDOWN -> current = null
                else -> Unit
            }
        }
        lastQuery = now
        return current
    }
}
