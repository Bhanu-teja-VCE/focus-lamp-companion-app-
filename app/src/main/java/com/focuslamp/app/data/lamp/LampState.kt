package com.focuslamp.app.data.lamp

import com.focuslamp.app.R

/**
 * The states the ESP32 firmware actually serves. The old app also called /blue, /purple,
 * /amber and /pulse_*, which the firmware doesn't have, so those silently returned 404.
 */
enum class LampState(val endpoint: String, val label: String, val colorRes: Int) {
    FOCUS("focus", "Green", R.color.lamp_green),
    WARNING("warning", "Warm white", R.color.lamp_warm),
    DISTRACTION("distraction", "Red", R.color.lamp_red),
    IDLE("idle", "Idle", R.color.lamp_green);

    companion object {
        fun fromMode(mode: String?): LampState? = values().firstOrNull { it.endpoint == mode }

        /** Green under 80% of the budget, warm white from 80%, red at 100%. Same rule as the firmware README. */
        fun forBudget(usedMs: Long, budgetMinutes: Int): LampState {
            if (budgetMinutes <= 0) return FOCUS
            val ratio = usedMs.toDouble() / (budgetMinutes * 60_000.0)
            return when {
                ratio >= 1.0 -> DISTRACTION
                ratio >= 0.8 -> WARNING
                else -> FOCUS
            }
        }
    }
}
