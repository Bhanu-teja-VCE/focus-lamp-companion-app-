package com.focuslamp.app

import android.app.Application
import com.focuslamp.app.util.Notifications
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class FocusLampApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannels(this)

        // Rule or checkbox changes take effect immediately: an app that just got blocked
        // (or unblocked by a parent) is covered (or uncovered) without waiting for the next tick.
        Graph.appScope.launch {
            combine(Graph.rules.version, Graph.distracting.apps) { _, _ -> }
                .drop(1)
                .collect {
                    Graph.usage.reapplyDistracting()
                    Graph.enforcer.evaluate()
                }
        }
        Graph.appScope.launch {
            Graph.focus.state.drop(1).collect { Graph.enforcer.evaluate() }
        }
    }
}
