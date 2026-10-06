package com.focuslamp.app.ui.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.focuslamp.app.R

/**
 * A soft pool of light in the lamp's current color, like light falling on a desk at night.
 * It eases to a new color when the lamp state changes; that is the app's one ambient animation.
 */
class LampGlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var color = context.getColor(R.color.lamp_off)
    private var animator: ValueAnimator? = null

    fun setLampColor(target: Int, animate: Boolean = true) {
        if (target == color) return
        animator?.cancel()
        val reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (!animate || reduceMotion || !isLaidOut) {
            color = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofObject(ArgbEvaluator(), color, target).apply {
            duration = 900
            addUpdateListener {
                color = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        if (r <= 0f) return
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 0x66),
                ColorUtils.setAlphaComponent(color, 0x22),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
