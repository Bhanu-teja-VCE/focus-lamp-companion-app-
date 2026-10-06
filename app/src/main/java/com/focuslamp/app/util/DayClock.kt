package com.focuslamp.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DayClock {

    fun startOfDay(now: Long = System.currentTimeMillis(), offsetDays: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun nextMidnight(now: Long = System.currentTimeMillis()): Long = startOfDay(now, 1)

    fun dayKey(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    /** 1 = Monday ... 7 = Sunday */
    fun isoDayOfWeek(now: Long = System.currentTimeMillis()): Int {
        val d = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    fun minuteOfDay(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}
