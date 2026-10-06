package com.focuslamp.app

import android.app.Application
import com.focuslamp.app.data.ai.ChatRepository
import com.focuslamp.app.data.ai.CoachContext
import com.focuslamp.app.data.family.FamilyRepository
import com.focuslamp.app.data.focus.FocusTimer
import com.focuslamp.app.data.lamp.LampRepository
import com.focuslamp.app.data.local.AppDatabase
import com.focuslamp.app.data.rules.ParentPinManager
import com.focuslamp.app.data.rules.RulesStore
import com.focuslamp.app.data.tracking.AppInfoCache
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.data.tracking.UsageRepository
import com.focuslamp.app.service.Enforcer
import com.focuslamp.app.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * One instance of each long-lived object, shared by screens, the monitor service and the
 * blocker. The old app created a new SettingsManager / tracker / repository in every screen,
 * so they could hold different values at the same time.
 */
object Graph {
    lateinit var app: Application
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { SettingsManager(app) }
    val distracting by lazy { DistractingAppsManager(app) }
    val appInfo by lazy { AppInfoCache(app) }
    val tracker by lazy { ScreenTimeTracker(app, appInfo) }
    val usage by lazy { UsageRepository(tracker, appInfo, distracting) }
    val rules by lazy { RulesStore(app) }
    val pin by lazy { ParentPinManager(app) }
    val lamp by lazy { LampRepository(settings) }
    val db by lazy { AppDatabase.getInstance(app) }
    val focus by lazy { FocusTimer(app, settings, db.sessionDao(), appScope) }
    val family by lazy { FamilyRepository(app, settings, rules, pin, appInfo) }
    val enforcer by lazy { Enforcer(app) }
    val coachContext by lazy { CoachContext(usage, rules, settings, lamp, focus, appInfo) }
    val chat by lazy { ChatRepository(db.chatMessageDao(), settings, coachContext) }

    fun init(application: Application) {
        app = application
    }
}
