package com.focuslamp.app.data.tracking

import com.focuslamp.app.data.tracking.UsageEventKind.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageEngineTest {

    private val min = 60_000L
    private val day = 1_000_000_000L // arbitrary "midnight"

    private fun ev(t: Long, pkg: String, cls: String?, kind: UsageEventKind) =
        RawUsageEvent(day + t, pkg, cls, kind)

    private fun run(vararg events: RawUsageEvent, end: Long = 120 * min, excluded: Set<String> = emptySet()) =
        UsageEngine.compute(events.toList(), day, day + end, excluded)

    @Test
    fun simpleOpenAndClose() {
        val r = run(
            ev(0, "yt", "Main", RESUMED),
            ev(10 * min, "yt", "Main", PAUSED)
        )
        assertEquals(10 * min, r.perAppMs["yt"])
    }

    /** Old bug: STOPPED of the first screen ended the second screen's session. */
    @Test
    fun switchingScreensInsideAnAppKeepsCounting() {
        val r = run(
            ev(0, "yt", "Home", RESUMED),
            ev(5 * min, "yt", "Home", PAUSED),
            ev(5 * min + 50, "yt", "Watch", RESUMED),
            ev(5 * min + 400, "yt", "Home", STOPPED),
            ev(25 * min, "yt", "Watch", PAUSED),
            ev(25 * min + 300, "launcher", "L", RESUMED)
        )
        // 25 minutes minus a 50 ms gap between the two screens
        assertEquals(25 * min - 50, r.perAppMs["yt"])
    }

    /** Multi-resume ordering (Android 10+): new screen resumes before the old one pauses. */
    @Test
    fun resumeBeforePauseInSameApp() {
        val r = run(
            ev(0, "ig", "Feed", RESUMED),
            ev(3 * min, "ig", "Story", RESUMED),
            ev(3 * min + 10, "ig", "Feed", PAUSED),
            ev(8 * min, "ig", "Story", PAUSED)
        )
        assertEquals(8 * min, r.perAppMs["ig"])
    }

    /** Old bug: screen-off events (package "android") were filtered out and never closed sessions. */
    @Test
    fun screenOffEndsSession() {
        val r = run(
            ev(0, "video", "Player", RESUMED),
            ev(10 * min, "android", null, SCREEN_OFF),
            // app never reports a pause (keeps screen on / shows over lock screen)
            ev(60 * min, "android", null, SCREEN_ON)
        )
        assertEquals(10 * min, r.perAppMs["video"])
    }

    @Test
    fun rebootWithoutShutdownIsNotStretched() {
        val r = run(
            ev(0, "game", "G", RESUMED),
            ev(90 * min, "android", null, STARTUP)
        )
        assertNull(r.perAppMs["game"])
    }

    @Test
    fun sessionOpenAtMidnightIsClippedNotDropped() {
        val r = UsageEngine.compute(
            listOf(
                RawUsageEvent(day - 20 * min, "yt", "Main", RESUMED),
                RawUsageEvent(day + 15 * min, "yt", "Main", PAUSED)
            ),
            day, day + 60 * min
        )
        assertEquals(15 * min, r.perAppMs["yt"])
    }

    @Test
    fun launcherIsExcludedButStillEndsPreviousApp() {
        val r = run(
            ev(0, "chat", "C", RESUMED),
            ev(4 * min, "launcher", "L", RESUMED), // chat's PAUSED was dropped by the OEM
            ev(30 * min, "launcher", "L", PAUSED),
            excluded = setOf("launcher")
        )
        assertEquals(4 * min, r.perAppMs["chat"])
        assertNull(r.perAppMs["launcher"])
        assertEquals(4 * min, r.totalMs)
    }

    @Test
    fun openAppCountsUntilNowAndIsReportedAsForeground() {
        val r = run(ev(100 * min, "yt", "Main", RESUMED), end = 110 * min)
        assertEquals(10 * min, r.perAppMs["yt"])
        assertEquals("yt", r.foregroundAtEnd)
    }

    @Test
    fun pickupsCountUnlocks() {
        val r = run(
            ev(1 * min, "android", null, SCREEN_ON),
            ev(1 * min + 5, "android", null, UNLOCKED),
            ev(30 * min, "android", null, SCREEN_ON),
            ev(30 * min + 5, "android", null, UNLOCKED)
        )
        assertEquals(2, r.pickups)
    }
}
