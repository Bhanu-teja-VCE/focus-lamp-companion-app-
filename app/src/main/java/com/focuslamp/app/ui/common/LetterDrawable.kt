package com.focuslamp.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.focuslamp.app.R

/** Rounded tile with the app's first letter, for apps whose icon isn't available. */
class LetterDrawable(context: Context, label: String) : Drawable() {
    private val letter = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_raised) }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.paper)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.SERIF
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        val r = b.width() * 0.28f
        canvas.drawRoundRect(rect, r, r, bg)
        fg.textSize = b.height() * 0.48f
        canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (fg.descent() + fg.ascent()) / 2, fg)
    }

    override fun setAlpha(alpha: Int) { bg.alpha = alpha; fg.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fg.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
