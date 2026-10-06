package com.focuslamp.app.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.rules.BlockDecision
import com.focuslamp.app.data.rules.BlockReason
import com.focuslamp.app.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Collects flows only while the fragment's view is visible. */
fun Fragment.repeatWhileStarted(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

/** App icon, or a lettered tile when the app isn't installed on this phone (parent's view). */
fun ImageView.setAppIcon(packageName: String, label: String) {
    val icon: Drawable? = Graph.appInfo.icon(packageName)
    if (icon != null) {
        setImageDrawable(icon)
        background = null
    } else {
        setImageDrawable(LetterDrawable(context, label))
    }
}

fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** One short line describing a block, used on Today and on the parent's dashboard. */
fun describeBlock(d: BlockDecision): String = when (d.reason) {
    BlockReason.APP_LIMIT -> "Your ${Format.minutes(d.limitMinutes ?: 0)} limit, back at midnight"
    BlockReason.DAILY_BUDGET -> "All distracting apps, back at midnight"
    BlockReason.FOCUS_SESSION -> "Distracting apps, until ${Format.clock(d.until ?: 0)}"
    BlockReason.SCHEDULE -> "${d.scheduleName ?: "Schedule"}, until ${Format.clock(d.until ?: 0)}"
    BlockReason.PARENT_LIMIT, BlockReason.PARENT_HOLD -> "Parent limit, until a parent opens it"
}

fun TextView.setLampDot(colorRes: Int) {
    val d = ContextCompat.getDrawable(context, R.drawable.bg_dot)?.mutate()
    d?.setTint(context.getColor(colorRes))
    val size = context.dp(10)
    d?.setBounds(0, 0, size, size)
    setCompoundDrawablesRelative(d, null, null, null)
    compoundDrawablePadding = context.dp(8)
}
