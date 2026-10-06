package com.focuslamp.app.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.util.Notifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Push messages sent by the Cloud Function in functions/index.js.
 *  - time_request     (to parents)  -> notification with Approve / Deny buttons
 *  - request_answered (to student)  -> apply the answer even if the app was closed
 *  - protection       (to parents)  -> "protection is off on <child>'s phone"
 */
class FocusMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Graph.appScope.launch { Graph.family.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        when (d["type"]) {
            "time_request" -> showRequest(d)
            "request_answered" -> {
                Graph.family.applyAnswerFromPush(d)
                FocusMonitorService.startIfAllowed(this)
            }
            "protection" -> Notifications.post(
                this, ("protection" + d["childUid"]).hashCode(), Notifications.CHANNEL_FAMILY,
                "Protection is off on ${d["childName"] ?: "your child"}'s phone",
                d["issues"]?.ifBlank { null } ?: "Some Focus Lamp permissions were turned off."
            )
        }
    }

    private fun showRequest(d: Map<String, String>) {
        val id = d["requestId"] ?: return
        val minutes = d["minutes"]?.toIntOrNull() ?: 15
        val child = d["childName"] ?: "Your child"
        val app = d["appLabel"] ?: "an app"
        val reason = d["reason"].orEmpty()
        val notifId = id.hashCode()

        fun action(approve: Boolean, min: Int, code: Int): PendingIntent = PendingIntent.getBroadcast(
            this, notifId * 4 + code,
            Intent(this, RequestActionReceiver::class.java)
                .putExtra(RequestActionReceiver.EXTRA_REQUEST, id)
                .putExtra(RequestActionReceiver.EXTRA_APPROVE, approve)
                .putExtra(RequestActionReceiver.EXTRA_MINUTES, min)
                .putExtra(RequestActionReceiver.EXTRA_NOTIFICATION, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            append("$child asked for $minutes more minutes on $app.")
            if (reason.isNotBlank()) append(" Reason: $reason.")
        }
        Notifications.post(this, notifId, Notifications.CHANNEL_FAMILY, "$app: more time?", text) {
            setPriority(NotificationCompat.PRIORITY_HIGH)
            addAction(R.drawable.ic_add, "Allow $minutes min", action(true, minutes, 0))
            if (minutes > 15) addAction(R.drawable.ic_add, "Allow 15 min", action(true, 15, 1))
            addAction(R.drawable.ic_close, "Not now", action(false, 0, 2))
        }
    }
}
