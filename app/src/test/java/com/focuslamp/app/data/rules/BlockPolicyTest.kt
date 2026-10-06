package com.focuslamp.app.data.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {

    private val min = 60_000L
    private val now = 10_000_000L
    private val midnight = now + 5 * 60 * min
    private val ctx = BlockPolicy.Context(now, isoDay = 3, minuteOfDay = 17 * 60, nextMidnight = midnight)

    private fun rules(
        self: Map<String, Int> = emptyMap(),
        parent: Map<String, Int> = emptyMap(),
        budget: Int = 60,
        blockOnBudget: Boolean = true,
        schedules: List<Schedule> = emptyList(),
        grants: List<Grant> = emptyList(),
        holds: Set<String> = emptySet()
    ) = RulesSnapshot("2026-10-07", self, parent, budget, blockOnBudget, schedules, grants, holds)

    @Test
    fun allowedUnderAllLimits() {
        assertNull(BlockPolicy.decide("yt", 10 * min, 10 * min, true, rules(self = mapOf("yt" to 30)), ctx))
    }

    @Test
    fun selfLimitBlocksUntilMidnight() {
        val d = BlockPolicy.decide("yt", 30 * min, 30 * min, true, rules(self = mapOf("yt" to 30)), ctx)!!
        assertEquals(BlockReason.APP_LIMIT, d.reason)
        assertEquals(midnight, d.until)
    }

    @Test
    fun budgetBlocksOnlyDistractingApps() {
        val r = rules(budget = 60)
        assertEquals(BlockReason.DAILY_BUDGET, BlockPolicy.decide("yt", 5 * min, 61 * min, true, r, ctx)!!.reason)
        assertNull(BlockPolicy.decide("notes", 5 * min, 61 * min, false, r, ctx))
    }

    @Test
    fun budgetBlockingCanBeTurnedOff() {
        assertNull(BlockPolicy.decide("yt", 5 * min, 61 * min, true, rules(blockOnBudget = false), ctx))
    }

    @Test
    fun parentLimitHasNoAutomaticEnd() {
        val d = BlockPolicy.decide("ig", 45 * min, 0, false, rules(parent = mapOf("ig" to 45)), ctx)!!
        assertEquals(BlockReason.PARENT_LIMIT, d.reason)
        assertNull(d.until)
    }

    @Test
    fun parentHoldSurvivesNewDayWithZeroUsage() {
        val d = BlockPolicy.decide("ig", 0, 0, false, rules(parent = mapOf("ig" to 45), holds = setOf("ig")), ctx)!!
        assertEquals(BlockReason.PARENT_HOLD, d.reason)
    }

    @Test
    fun approvedExtraTimeExtendsParentLimit() {
        val g = Grant("ig", 30, "2026-10-07", now - 5 * min, "req1")
        assertNull(BlockPolicy.decide("ig", 60 * min, 0, false, rules(parent = mapOf("ig" to 45), grants = listOf(g)), ctx))
        val d = BlockPolicy.decide("ig", 75 * min, 0, false, rules(parent = mapOf("ig" to 45), grants = listOf(g)), ctx)
        assertEquals(BlockReason.PARENT_LIMIT, d!!.reason)
    }

    @Test
    fun yesterdaysGrantDoesNotCount() {
        val g = Grant("ig", 30, "2026-10-06", now - 1440 * min, "req1")
        assertEquals(BlockReason.PARENT_LIMIT,
            BlockPolicy.decide("ig", 50 * min, 0, false, rules(parent = mapOf("ig" to 45), grants = listOf(g)), ctx)!!.reason)
    }

    @Test
    fun parentRuleWinsOverSelfRule() {
        val d = BlockPolicy.decide("ig", 50 * min, 0, true,
            rules(self = mapOf("ig" to 20), parent = mapOf("ig" to 45)), ctx)!!
        assertTrue(d.reason.setByParent)
    }

    @Test
    fun scheduleBlocksDistractingAppsUntilItEnds() {
        val school = Schedule("s", "School hours", true, 8 * 60, 18 * 60, Schedule.WEEKDAYS)
        val d = BlockPolicy.decide("yt", 0, 0, true, rules(schedules = listOf(school)), ctx)!!
        assertEquals(BlockReason.SCHEDULE, d.reason)
        assertEquals(now + 60 * min, d.until)
        assertNull(BlockPolicy.decide("notes", 0, 0, false, rules(schedules = listOf(school)), ctx))
    }

    @Test
    fun disabledScheduleDoesNothing() {
        val school = Schedule("s", "School hours", false, 8 * 60, 18 * 60, Schedule.WEEKDAYS)
        assertNull(BlockPolicy.decide("yt", 0, 0, true, rules(schedules = listOf(school)), ctx))
    }

    @Test
    fun overnightScheduleCoversEarlyMorning() {
        val bed = Schedule("b", "Bedtime", true, 21 * 60, 6 * 60, setOf(2))
        assertTrue(bed.isActive(isoDay = 3, minuteOfDay = 5 * 60))
        assertTrue(bed.isActive(isoDay = 2, minuteOfDay = 22 * 60))
        assertTrue(!bed.isActive(isoDay = 3, minuteOfDay = 22 * 60))
    }

    @Test
    fun grantDuringScheduleIsWallClockTime() {
        val school = Schedule("s", "School hours", true, 8 * 60, 18 * 60, Schedule.WEEKDAYS)
        val g = Grant("yt", 15, "2026-10-07", now - 10 * min, "r")
        assertNull(BlockPolicy.decide("yt", 0, 0, true, rules(schedules = listOf(school), grants = listOf(g)), ctx))
        val expired = Grant("yt", 15, "2026-10-07", now - 20 * min, "r")
        assertEquals(BlockReason.SCHEDULE,
            BlockPolicy.decide("yt", 0, 0, true, rules(schedules = listOf(school), grants = listOf(expired)), ctx)!!.reason)
    }

    @Test
    fun strictFocusPausesDistractingApps() {
        val c = ctx.copy(focusEndsAt = now + 10 * min)
        assertEquals(BlockReason.FOCUS_SESSION, BlockPolicy.decide("yt", 0, 0, true, rules(), c)!!.reason)
        assertNull(BlockPolicy.decide("notes", 0, 0, false, rules(), c))
    }
}
