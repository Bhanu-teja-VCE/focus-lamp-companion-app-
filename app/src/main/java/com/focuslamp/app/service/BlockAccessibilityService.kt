package com.focuslamp.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focuslamp.app.Graph

/**
 * Tells the [Enforcer] the moment a different app comes to the front, so a blocked app is
 * covered as it opens instead of up to 10 seconds later. Reads no screen content.
 */
class BlockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Graph.enforcer.attachHost(this)
        FocusMonitorService.startIfAllowed(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Graph.enforcer.onWindowChanged(pkg, event.className?.toString())
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Graph.enforcer.detachHost(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Graph.enforcer.detachHost(this)
        super.onDestroy()
    }
}
