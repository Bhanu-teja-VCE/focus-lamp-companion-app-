package com.focuslamp.app.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * ScheduleManager — Configures and enforces Parent-controlled Scheduled Restriction Windows:
 * 1. School Hours Mode (Mon–Fri, default 8:00 AM – 3:00 PM)
 * 2. Bedtime Mode (Everyday, default 9:00 PM – 6:00 AM)
 */
class ScheduleManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "schedule_settings", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_SCHOOL_ENABLED = "school_mode_enabled"
        private const val KEY_SCHOOL_START_HOUR = "school_start_hour" // 0-23
        private const val KEY_SCHOOL_END_HOUR = "school_end_hour"     // 0-23

        private const val KEY_BEDTIME_ENABLED = "bedtime_mode_enabled"
        private const val KEY_BEDTIME_START_HOUR = "bedtime_start_hour" // 0-23 (e.g. 21 = 9 PM)
        private const val KEY_BEDTIME_END_HOUR = "bedtime_end_hour"     // 0-23 (e.g. 6 = 6 AM)

        const val DEFAULT_SCHOOL_START = 8   // 8:00 AM
        const val DEFAULT_SCHOOL_END = 15    // 3:00 PM
        const val DEFAULT_BEDTIME_START = 21 // 9:00 PM
        const val DEFAULT_BEDTIME_END = 6    // 6:00 AM
    }

    // --- School Mode Settings ---
    var isSchoolModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHOOL_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_SCHOOL_ENABLED, value).apply()

    var schoolStartHour: Int
        get() = prefs.getInt(KEY_SCHOOL_START_HOUR, DEFAULT_SCHOOL_START)
        set(value) = prefs.edit().putInt(KEY_SCHOOL_START_HOUR, value).apply()

    var schoolEndHour: Int
        get() = prefs.getInt(KEY_SCHOOL_END_HOUR, DEFAULT_SCHOOL_END)
        set(value) = prefs.edit().putInt(KEY_SCHOOL_END_HOUR, value).apply()

    // --- Bedtime Mode Settings ---
    var isBedtimeModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEDTIME_ENABLED, true) // Enabled by default
        set(value) = prefs.edit().putBoolean(KEY_BEDTIME_ENABLED, value).apply()

    var bedtimeStartHour: Int
        get() = prefs.getInt(KEY_BEDTIME_START_HOUR, DEFAULT_BEDTIME_START)
        set(value) = prefs.edit().putInt(KEY_BEDTIME_START_HOUR, value).apply()

    var bedtimeEndHour: Int
        get() = prefs.getInt(KEY_BEDTIME_END_HOUR, DEFAULT_BEDTIME_END)
        set(value) = prefs.edit().putInt(KEY_BEDTIME_END_HOUR, value).apply()

    /**
     * Checks if School Hours restriction is currently active right now.
     * Mon–Fri between schoolStartHour and schoolEndHour.
     */
    fun isSchoolHoursActive(): Boolean {
        if (!isSchoolModeEnabled) return false

        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isInHourRange = currentHour in schoolStartHour until schoolEndHour

        return isWeekday && isInHourRange
    }

    /**
     * Checks if Bedtime Mode restriction is currently active right now.
     * Handles overnight roll-over (e.g. 21:00 PM to 06:00 AM).
     */
    fun isBedtimeModeActive(): Boolean {
        if (!isBedtimeModeEnabled) return false

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return if (bedtimeStartHour > bedtimeEndHour) {
            // Overnight window (e.g. 21:00 -> 06:00)
            currentHour >= bedtimeStartHour || currentHour < bedtimeEndHour
        } else {
            // Same-day window (e.g. 22:00 -> 23:59)
            currentHour in bedtimeStartHour until bedtimeEndHour
        }
    }

    /**
     * Returns a human-readable active restriction message, or null if no schedule window is active.
     */
    fun getActiveRestrictionReason(): String? {
        if (isSchoolHoursActive()) {
            val startFormatted = formatHour(schoolStartHour)
            val endFormatted = formatHour(schoolEndHour)
            return "🏫 School Hours Active ($startFormatted - $endFormatted)"
        }

        if (isBedtimeModeActive()) {
            val startFormatted = formatHour(bedtimeStartHour)
            val endFormatted = formatHour(bedtimeEndHour)
            return "🌙 Bedtime Mode Active ($startFormatted - $endFormatted)"
        }

        return null
    }

    private fun formatHour(hour24: Int): String {
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        val amPm = if (hour24 >= 12) "PM" else "AM"
        return "$hour12:00 $amPm"
    }
}
