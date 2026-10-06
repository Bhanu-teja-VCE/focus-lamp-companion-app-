package com.focuslamp.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.focuslamp.app.R
import com.focuslamp.app.data.tracking.DayTotal
import com.focuslamp.app.util.Format

/** Seven bars: full height = total screen time, red part = distracting apps. Today is brighter. */
class WeekChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var days: List<DayTotal> = emptyList()
    private val dp = resources.displayMetrics.density
    private val total = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_raised) }
    private val distract = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.lamp_red) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_faint)
        textSize = 11 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setDays(list: List<DayTotal>) {
        days = list
        contentDescription = list.joinToString(". ") {
            "${Format.shortDay(it.dayStart)}: ${Format.durationWords(it.totalMs)}, ${Format.durationWords(it.distractionMs)} distracting"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val labelH = 20 * dp
        val chartH = height - labelH
        val max = days.maxOf { it.totalMs }.coerceAtLeast(60 * 60_000L).toFloat()
        val slot = width / days.size.toFloat()
        val barW = minOf(slot * 0.5f, 28 * dp)
        val radius = 6 * dp
        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val isToday = i == days.lastIndex
            total.alpha = if (isToday) 255 else 170
            distract.alpha = if (isToday) 255 else 150
            val h = (d.totalMs / max) * chartH
            rect.set(cx - barW / 2, chartH - h, cx + barW / 2, chartH)
            canvas.drawRoundRect(rect, radius, radius, total)
            val dh = (d.distractionMs / max) * chartH
            if (dh > 0f) {
                rect.set(cx - barW / 2, chartH - dh, cx + barW / 2, chartH)
                canvas.drawRoundRect(rect, radius, radius, distract)
            }
            label.color = context.getColor(if (isToday) R.color.paper else R.color.text_faint)
            canvas.drawText(Format.shortDay(d.dayStart), cx, height - 4 * dp, label)
        }
    }
}
