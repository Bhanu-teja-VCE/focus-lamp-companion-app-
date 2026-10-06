package com.focuslamp.app.data.rules

/**
 * A daily time window, e.g. school hours. Minutes are minutes after midnight.
 * Days use 1 = Monday ... 7 = Sunday. A window may cross midnight (bedtime 21:00 - 06:00).
 */
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int>
) {
    fun isActive(isoDay: Int, minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            isoDay in days && minuteOfDay in startMinute until endMinute
        } else {
            val previousDay = if (isoDay == 1) 7 else isoDay - 1
            (isoDay in days && minuteOfDay >= startMinute) || (previousDay in days && minuteOfDay < endMinute)
        }
    }

    /** Minutes from [minuteOfDay] until this window ends (assumes it is active). */
    fun minutesUntilEnd(minuteOfDay: Int): Int {
        val diff = endMinute - minuteOfDay
        return if (diff > 0) diff else diff + 24 * 60
    }

    companion object {
        val WEEKDAYS = setOf(1, 2, 3, 4, 5)
        val EVERY_DAY = setOf(1, 2, 3, 4, 5, 6, 7)

        fun defaults() = listOf(
            Schedule("school", "School hours", false, 8 * 60, 15 * 60, WEEKDAYS),
            Schedule("bedtime", "Bedtime", false, 21 * 60, 6 * 60, EVERY_DAY)
        )
    }
}

/** Extra time a parent approved. Applies to one app on one day. */
data class Grant(
    val packageName: String,
    val minutes: Int,
    val dayKey: String,
    val grantedAt: Long,
    val requestId: String?
)

data class RulesSnapshot(
    val dayKey: String,
    /** Per-app daily limits the student set for themselves. */
    val selfLimits: Map<String, Int>,
    /** Per-app daily limits set by a parent. */
    val parentLimits: Map<String, Int>,
    /** Budget across all distracting apps, in minutes. */
    val dailyBudgetMinutes: Int,
    val blockWhenBudgetUsed: Boolean,
    val schedules: List<Schedule>,
    val grants: List<Grant>,
    /** Apps whose parent limit ran out and that stay blocked until a parent lifts them. */
    val holds: Set<String>
)

enum class BlockReason {
    PARENT_HOLD,
    PARENT_LIMIT,
    SCHEDULE,
    APP_LIMIT,
    DAILY_BUDGET,
    FOCUS_SESSION;

    val setByParent: Boolean get() = this == PARENT_HOLD || this == PARENT_LIMIT || this == SCHEDULE
}

data class BlockDecision(
    val packageName: String,
    val reason: BlockReason,
    /** The limit that ran out, in minutes, when there is one. */
    val limitMinutes: Int?,
    val usedMs: Long,
    /** When the block lifts by itself. Null means only a parent can lift it. */
    val until: Long?,
    val scheduleName: String? = null
)
