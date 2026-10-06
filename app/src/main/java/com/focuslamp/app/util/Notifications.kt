package com.focuslamp.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focuslamp.app.R
import com.focuslamp.app.ui.WelcomeActivity

object Notifications {
    const val CHANNEL_MONITOR = "focus_lamp_service_channel"
    const val CHANNEL_FAMILY = "focus_lamp_family"
    const val CHANNEL_FOCUS = "focus_lamp_focus"

    const val ID_MONITOR = 1001
    const val ID_FOCUS_DONE = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Focus Lamp is tracking screen time"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FAMILY, "Family requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Requests for more time and parent answers"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a focus session ends"
            }
        )
    }

    fun openAppIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, WelcomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Posts a simple notification; silently skipped if notifications are off. */
    fun post(context: Context, id: Int, channel: String, title: String, text: String,
             build: (NotificationCompat.Builder.() -> Unit)? = null) {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_lamp)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, id))
            .setAutoCancel(true)
        build?.invoke(n)
        try {
            NotificationManagerCompat.from(context).notify(id, n.build())
        } catch (_: SecurityException) {
        }
    }
}
