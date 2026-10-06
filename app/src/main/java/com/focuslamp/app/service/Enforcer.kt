package com.focuslamp.app.service

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.provider.Settings
import com.focuslamp.app.Graph
import com.focuslamp.app.data.rules.BlockDecision
import com.focuslamp.app.data.rules.BlockPolicy
import com.focuslamp.app.data.rules.BlockReason
import com.focuslamp.app.ui.block.BlockOverlay
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides whether the app in front is allowed, and shows or hides the block screen.
 * All methods except [decide] / [currentBlocks] run on the main thread.
 *
 * Foreground changes arrive instantly from [BlockAccessibilityService], or every second from
 * the monitor service's fallback poll when accessibility access isn't granted.
 */
class Enforcer(private val app: Application) {

    private var host: AccessibilityService? = null
    private var overlay: BlockOverlay? = null

    var foreground: String? = null
        private set

    private val _active = MutableStateFlow<BlockDecision?>(null)
    /** The block currently on screen, if any. */
    val active: StateFlow<BlockDecision?> = _active.asStateFlow()

    val hasAccessibilityHost: Boolean get() = host != null

    fun attachHost(service: AccessibilityService) {
        if (host === service) return
        overlay?.hide()
        overlay = null
        host = service
        evaluate()
    }

    fun detachHost(service: AccessibilityService) {
        if (host !== service) return
        overlay?.hide()
        overlay = null
        host = null
        _active.value = null
    }

    /** From the accessibility service: a window came to the front. */
    fun onWindowChanged(packageName: String, className: String?) {
        when {
            packageName == "com.android.systemui" || packageName == "android" -> return
            Graph.appInfo.isKeyboard(packageName) -> return
            // Our own overlay also produces window events; only our real screens count.
            packageName == app.packageName && className?.startsWith("com.focuslamp.app.ui.") != true -> return
        }
        onForeground(packageName)
    }

    fun onForeground(packageName: String?) {
        foreground = packageName
        evaluate()
    }

    fun evaluate() {
        val pkg = foreground
        val decision = if (pkg == null || Graph.settings.role != Role.STUDENT) null else decide(pkg)
        if (decision?.reason == BlockReason.PARENT_LIMIT) {
            // The parent limit ran out: from now on only a parent can reopen this app, even tomorrow.
            Graph.rules.addHold(decision.packageName)
        }
        if (decision == null) {
            overlay?.hide()
            _active.value = null
            return
        }
        val o = overlay ?: createOverlay() ?: run {
            _active.value = null
            return
        }
        o.show(decision)
        _active.value = decision
    }

    /** Thread-safe. Null means the app may open. */
    fun decide(packageName: String, now: Long = System.currentTimeMillis()): BlockDecision? {
        if (!isBlockable(packageName)) return null
        val snapshot = Graph.usage.snapshot.value
        // After midnight, until the next refresh, yesterday's numbers must not count.
        val sameDay = snapshot.dayStart == DayClock.startOfDay(now)
        val used = if (sameDay) snapshot.msFor(packageName) else 0L
        val distraction = if (sameDay) snapshot.distractionMs else 0L
        val rules = Graph.rules.snapshot(DayClock.dayKey(now), Graph.settings.timeLimitMinutes)
        val ctx = BlockPolicy.Context(
            now = now,
            isoDay = DayClock.isoDayOfWeek(now),
            minuteOfDay = DayClock.minuteOfDay(now),
            nextMidnight = DayClock.nextMidnight(now),
            focusEndsAt = Graph.focus.strictEndsAt()
        )
        return BlockPolicy.decide(packageName, used, distraction, Graph.distracting.isDistracting(packageName), rules, ctx)
    }

    /** Every app that would be blocked right now (Today screen and the parent's dashboard). */
    fun currentBlocks(): List<BlockDecision> {
        if (Graph.settings.role != Role.STUDENT) return emptyList()
        val snapshot = Graph.usage.snapshot.value
        val candidates = LinkedHashSet<String>()
        snapshot.apps.mapTo(candidates) { it.packageName }
        candidates += Graph.rules.parentLimits().keys
        candidates += Graph.rules.selfLimits().keys
        candidates += Graph.rules.holds()
        candidates += Graph.distracting.getAll()
        val now = System.currentTimeMillis()
        val all = candidates.mapNotNull { decide(it, now) }
        // When the whole budget or a schedule is used up it applies to every distracting app;
        // listing each of them adds nothing, so collapse those into one entry.
        val shared = setOf(BlockReason.DAILY_BUDGET, BlockReason.SCHEDULE, BlockReason.FOCUS_SESSION)
        return all.filter { it.reason !in shared } + all.filter { it.reason in shared }.distinctBy { it.reason }
    }

    private fun isBlockable(pkg: String): Boolean =
        pkg != app.packageName &&
            pkg !in Graph.appInfo.excludedPackages() &&
            pkg != "com.android.settings" // never lock the student out of system settings

    private fun createOverlay(): BlockOverlay? {
        val h = host
        val o = when {
            h != null -> BlockOverlay(h, viaAccessibility = true) { goHome() }
            Settings.canDrawOverlays(app) -> BlockOverlay(app, viaAccessibility = false) { goHome() }
            else -> null
        }
        overlay = o
        return o
    }

    private fun goHome() {
        val h = host
        if (h != null) {
            h.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            app.startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
