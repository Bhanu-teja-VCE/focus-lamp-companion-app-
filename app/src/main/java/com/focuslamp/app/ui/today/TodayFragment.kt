package com.focuslamp.app.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.lamp.LampState
import com.focuslamp.app.data.tracking.UsageSnapshot
import com.focuslamp.app.databinding.FragmentTodayBinding
import com.focuslamp.app.databinding.ItemAppRowBinding
import com.focuslamp.app.service.FocusMonitorService
import com.focuslamp.app.ui.MainActivity
import com.focuslamp.app.ui.common.PermissionRows
import com.focuslamp.app.ui.common.describeBlock
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Format
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Today: one number (distracting-app time against the daily budget), the same number the lamp
 * uses. The old Home screen showed total screen time here while the lamp used distracting-only
 * time, so the two disagreed.
 */
class TodayFragment : Fragment() {

    private var _b: FragmentTodayBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentTodayBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.seeAll.setOnClickListener { (activity as? MainActivity)?.tabs?.go(R.id.usageFragment) }
        b.focusButton.setOnClickListener { (activity as? MainActivity)?.tabs?.go(R.id.focusFragment) }

        repeatWhileStarted {
            combine(Graph.usage.snapshot, Graph.rules.version, Graph.enforcer.active) { s, _, _ -> s }
                .collect { render(it) }
        }
        repeatWhileStarted {
            Graph.focus.state.collect { f ->
                b.focusButton.text = if (f.running) "Focus session: ${remainingClock(f.remainingMs)} left"
                else getString(R.string.start_focus)
            }
        }
        repeatWhileStarted {
            Graph.db.sessionDao().focusMinutesSinceFlow(DayClock.startOfDay()).collect {
                b.focusToday.text = if (it == 0L) "0m" else Format.duration(it * 60_000L)
            }
        }
        // Keep numbers moving while the screen is open, independent of the service.
        repeatWhileStarted {
            while (true) {
                Graph.usage.refresh()
                delay(15_000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderSetup()
        FocusMonitorService.startIfAllowed(requireContext())
        viewLifecycleOwner.lifecycleScope.launch { Graph.usage.refresh() }
    }

    private fun renderSetup() {
        val ctx = requireContext()
        val needed = listOf(PermissionItem.USAGE, PermissionItem.BLOCKER, PermissionItem.OVERLAY, PermissionItem.NOTIFICATIONS)
        val missing = Permissions.missing(ctx, needed).toMutableList()
        // Blocking needs only one of the two; hide the fallback once the main one is on.
        if (Permissions.isGranted(ctx, PermissionItem.BLOCKER)) missing.remove(PermissionItem.OVERLAY)
        b.setupCard.visible(missing.isNotEmpty())
        b.setupText.text = if (PermissionItem.USAGE in missing)
            "Focus Lamp can't see screen time yet. Turn on usage access to start."
        else "A couple more switches so limits can actually pause apps."
        PermissionRows.render(this, b.setupRows, missing, showGranted = false)
    }

    private fun render(s: UsageSnapshot) {
        val budget = Graph.settings.timeLimitMinutes
        val budgetMs = budget * 60_000L
        val used = s.distractionMs
        val state = LampState.forBudget(used, budget)
        val ctx = requireContext()

        b.glow.setLampColor(ctx.getColor(state.colorRes))
        b.budgetRing.setIndicatorColor(ctx.getColor(state.colorRes))
        b.budgetRing.setProgressCompat(((used.toDouble() / budgetMs.coerceAtLeast(1)) * 1000).toInt().coerceIn(0, 1000), true)
        b.usedBig.text = Format.duration(used).let { if (used == 0L) "0m" else it }
        b.usedCaption.text = "of ${Format.minutes(budget)} on distracting apps"
        b.hero.contentDescription = "${Format.durationWords(used)} of ${Format.minutes(budget)} used on distracting apps"

        val left = budgetMs - used
        b.statusLine.text = when {
            !s.hasPermission -> "Turn on usage access to start tracking."
            state == LampState.DISTRACTION -> "Budget used up. The lamp is red."
            state == LampState.WARNING -> "${Format.durationWords(left)} left. The lamp is warm white."
            else -> "${Format.durationWords(left)} left. The lamp is green."
        }

        b.screenTime.text = Format.duration(s.totalMs).let { if (s.totalMs == 0L) "0m" else it }
        b.pickups.text = s.pickups.toString()

        // Paused right now
        val blocks = Graph.enforcer.currentBlocks()
        b.blocksSection.visible(blocks.isNotEmpty())
        b.blocksList.removeAllViews()
        blocks.forEach { d ->
            val row = ItemAppRowBinding.inflate(layoutInflater, b.blocksList, false)
            val shared = d.reason.name in setOf("DAILY_BUDGET", "SCHEDULE", "FOCUS_SESSION")
            val label = if (shared) "Distracting apps" else Graph.appInfo.label(d.packageName)
            if (shared) row.rowIcon.setImageResource(R.drawable.ic_lock) else row.rowIcon.setAppIcon(d.packageName, label)
            row.rowTitle.text = label
            row.rowSubtitle.text = describeBlock(d)
            row.rowValue.text = ""
            b.blocksList.addView(row.root)
        }

        // Top distracting apps
        val top = s.apps.filter { it.distracting }.take(3)
        b.topList.removeAllViews()
        b.topEmpty.visible(top.isEmpty())
        b.topEmpty.text = if (Graph.distracting.getAll().isEmpty())
            "No apps are ticked as distracting yet. Tick them on the Usage tab."
        else getString(R.string.top_empty)
        top.forEach { app ->
            val row = ItemAppRowBinding.inflate(layoutInflater, b.topList, false)
            row.rowIcon.setAppIcon(app.packageName, app.label)
            row.rowTitle.text = app.label
            val limit = Graph.rules.parentLimits()[app.packageName] ?: Graph.rules.selfLimits()[app.packageName]
            row.rowSubtitle.text = if (limit != null) "Limit ${Format.minutes(limit)}" else ""
            row.rowSubtitle.visible(limit != null)
            row.rowValue.text = Format.duration(app.ms)
            row.root.setOnClickListener { (activity as? MainActivity)?.tabs?.go(R.id.usageFragment) }
            b.topList.addView(row.root)
        }
    }

    private fun remainingClock(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
