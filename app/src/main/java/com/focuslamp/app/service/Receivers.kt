package com.focuslamp.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.focuslamp.app.Graph
import kotlinx.coroutines.launch

/** Restarts monitoring after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusMonitorService.startIfAllowed(context)
    }
}

/** Approve / deny buttons on the parent's request notification. */
class RequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                Graph.family.resolve(requestId, approve, minutes)
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST = "request_id"
        const val EXTRA_APPROVE = "approve"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_NOTIFICATION = "notification_id"
    }
}

/**
 * Uninstall protection. While active, Android won't uninstall Focus Lamp until this is turned
 * off in Settings, and turning it off is reported to the parent.
 */
class ProtectionAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "If you turn this off, your parent will see that Focus Lamp's protection is off."

    override fun onDisabled(context: Context, intent: Intent) {
        FocusMonitorService.startIfAllowed(context, publishNow = true)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        FocusMonitorService.startIfAllowed(context, publishNow = true)
    }
}
