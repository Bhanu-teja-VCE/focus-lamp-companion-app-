package com.focuslamp.app.data.rules

/**
 * Decides whether an app may open right now. Pure function, unit-tested.
 *
 * Order matters: anything a parent set wins over the student's own limits, because a
 * parent block needs a different way out (a request to the parent) than a self block
 * (waiting for midnight).
 *
 *  1. Parent hold   - a parent limit already ran out and nobody has lifted it yet.
 *  2. Parent limit  - parent limit plus any approved extra time is used up (caller creates a hold).
 *  3. Schedule      - school hours / bedtime, for parent-limited and distracting apps.
 *  4. Focus session - while a strict focus session runs, distracting apps are paused.
 *  5. App limit     - the student's own limit for this app.
 *  6. Daily budget  - the student's budget across all distracting apps (the lamp's limit).
 */
object BlockPolicy {

    data class Context(
        val now: Long,
        val isoDay: Int,
        val minuteOfDay: Int,
        val nextMidnight: Long,
        val focusEndsAt: Long? = null
    )

    fun decide(
        packageName: String,
        usedMs: Long,
        distractionMs: Long,
        isDistracting: Boolean,
        rules: RulesSnapshot,
        ctx: Context
    ): BlockDecision? {
        val grantsToday = rules.grants.filter { it.packageName == packageName && it.dayKey == rules.dayKey }
        val extraMinutes = grantsToday.sumOf { it.minutes }
        val parentLimit = rules.parentLimits[packageName]

        if (parentLimit != null) {
            if (packageName in rules.holds) {
                return BlockDecision(packageName, BlockReason.PARENT_HOLD, parentLimit, usedMs, until = null)
            }
            if (usedMs >= (parentLimit + extraMinutes) * MINUTE) {
                return BlockDecision(packageName, BlockReason.PARENT_LIMIT, parentLimit, usedMs, until = null)
            }
        }

        if (parentLimit != null || isDistracting) {
            val active = rules.schedules.firstOrNull { it.isActive(ctx.isoDay, ctx.minuteOfDay) }
            if (active != null) {
                // During a schedule, approved time is wall-clock time from the moment it was granted.
                val covered = grantsToday.any { ctx.now < it.grantedAt + it.minutes * MINUTE }
                if (!covered) {
                    val until = ctx.now + active.minutesUntilEnd(ctx.minuteOfDay) * MINUTE
                    return BlockDecision(packageName, BlockReason.SCHEDULE, null, usedMs, until, active.name)
                }
            }
        }

        if (isDistracting && ctx.focusEndsAt != null && ctx.now < ctx.focusEndsAt) {
            return BlockDecision(packageName, BlockReason.FOCUS_SESSION, null, usedMs, ctx.focusEndsAt)
        }

        val selfLimit = rules.selfLimits[packageName]
        if (selfLimit != null && usedMs >= selfLimit * MINUTE) {
            return BlockDecision(packageName, BlockReason.APP_LIMIT, selfLimit, usedMs, ctx.nextMidnight)
        }

        if (isDistracting && rules.blockWhenBudgetUsed && rules.dailyBudgetMinutes > 0 &&
            distractionMs >= rules.dailyBudgetMinutes * MINUTE
        ) {
            return BlockDecision(packageName, BlockReason.DAILY_BUDGET, rules.dailyBudgetMinutes, distractionMs, ctx.nextMidnight)
        }
        return null
    }

    private const val MINUTE = 60_000L
}
