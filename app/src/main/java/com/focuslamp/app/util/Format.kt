package com.focuslamp.app.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    /** "1h 05m", "42m", "<1m" */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000L
        if (ms in 1..59_999) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    }

    /** "1 hr 5 min", "42 min" for sentences */
    fun durationWords(ms: Long): String {
        val totalMin = (ms + 59_999L) / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "$h hr $m min"
            h > 0 -> "$h hr"
            else -> "$m min"
        }
    }

    fun minutes(min: Int): String = if (min >= 60 && min % 60 == 0) "${min / 60} hr" else if (min >= 60) "${min / 60} hr ${min % 60} min" else "$min min"

    fun clock(millis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

    fun clockFromMinute(minuteOfDay: Int): String {
        val c = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(java.util.Calendar.MINUTE, minuteOfDay % 60)
        }
        return clock(c.timeInMillis)
    }

    fun dayAndTime(millis: Long): String =
        SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(millis))

    fun shortDay(millis: Long): String =
        SimpleDateFormat("EEEEE", Locale.getDefault()).format(Date(millis))

    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        val min = (now - millis) / 60_000L
        return when {
            millis <= 0L -> "never"
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} hr ago"
            else -> "${min / (24 * 60)} days ago"
        }
    }
}
