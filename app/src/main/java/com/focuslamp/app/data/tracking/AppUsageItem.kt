package com.focuslamp.app.data.tracking

import android.graphics.drawable.Drawable

/**
 * Represents the screen time usage for a single application.
 */
data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val usageMillis: Long
)
