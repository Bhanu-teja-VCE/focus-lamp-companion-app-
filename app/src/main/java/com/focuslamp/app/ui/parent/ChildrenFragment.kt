package com.focuslamp.app.ui.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.family.ChildStatus
import com.focuslamp.app.data.lamp.LampState
import com.focuslamp.app.databinding.FragmentChildrenBinding
import com.focuslamp.app.databinding.ItemAppRowBinding
import com.focuslamp.app.databinding.ItemBlockedAppBinding
import com.focuslamp.app.databinding.ItemChildBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The parent's dashboard: each linked child's day, what's paused, and a one-tap unblock. */
class ChildrenFragment : Fragment() {

    private var _b: FragmentChildrenBinding? = null
    private val b get() = _b!!
    private var lastChildren: List<ChildStatus> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentChildrenBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val family = Graph.family
        b.createFamily.setOnClickListener { createFamily() }
        b.newCode.setOnClickListener { newCode() }
        renderState()
        if (family.isAvailable && family.isPaired) {
            repeatWhileStarted { family.observeChildren().collect { render(it) } }
            // Keeps "last update 3 min ago" accurate without waiting for new data.
            repeatWhileStarted {
                while (true) {
                    delay(60_000L)
                    render(lastChildren)
                }
            }
        }
    }

    private fun renderState() {
        val family = Graph.family
        b.setupCard.visible(!family.isAvailable)
        b.createSection.visible(family.isAvailable && !family.isPaired)
        b.codeCard.visible(family.isAvailable && family.isPaired)
    }

    private fun createFamily() {
        b.createFamily.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            Graph.family.createFamily(Graph.settings.displayName.ifBlank { "Parent" })
                .onSuccess {
                    renderState()
                    newCode()
                    repeatWhileStarted { Graph.family.observeChildren().collect { render(it) } }
                }
                .onFailure {
                    _b?.let { v ->
                        v.createFamily.isEnabled = true
                        Snackbar.make(v.root, it.message ?: "Couldn't create the family.", Snackbar.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun newCode() {
        b.newCode.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val r = Graph.family.createPairingCode()
            val v = _b ?: return@launch
            v.newCode.isEnabled = true
            r.onSuccess { code ->
                v.code.visible(true)
                v.code.text = code.chunked(3).joinToString(" ")
                v.code.contentDescription = code.toCharArray().joinToString(" ")
                v.codeHint.text = "On the student's phone, open Family and enter this code. It works for 15 minutes."
                v.newCode.text = getString(R.string.new_code)
            }.onFailure { Snackbar.make(v.root, it.message ?: "Couldn't make a code.", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun render(children: List<ChildStatus>) {
        val v = _b ?: return
        lastChildren = children
        v.childrenList.removeAllViews()
        children.forEach { c -> v.childrenList.addView(childCard(c)) }
    }

    private fun childCard(c: ChildStatus): View {
        val ctx = requireContext()
        val card = ItemChildBinding.inflate(layoutInflater, b.childrenList, false)
        card.name.text = c.name
        val stale = c.isStale()
        card.seen.text = "${c.device}. Last update ${Format.ago(c.lastSeen)}."
        card.seen.setTextColor(ctx.getColor(if (stale) R.color.amber else R.color.text_secondary))

        val issues = c.issues.toMutableList()
        if (stale) issues.add(0, "No update for a while. The phone may be off, offline, or Focus Lamp was stopped.")
        card.issues.visible(issues.isNotEmpty())
        card.issues.text = issues.joinToString("\n")

        val budgetMs = c.budgetMinutes * 60_000L
        val state = LampState.forBudget(c.distractionMs, c.budgetMinutes)
        card.budgetLine.text = "${Format.durationWords(c.distractionMs)} of ${Format.minutes(c.budgetMinutes)} on distracting apps"
        card.budgetBar.setIndicatorColor(ctx.getColor(state.colorRes))
        card.budgetBar.setProgressCompat(if (budgetMs > 0) ((c.distractionMs * 1000) / budgetMs).toInt().coerceIn(0, 1000) else 0, false)
        card.totalLine.text = "${Format.durationWords(c.totalMs)} screen time, ${c.pickups} pickups"

        val parentBlocks = c.blocked.filter { it.byParent }
        val otherBlocks = c.blocked.filter { !it.byParent }
        card.pausedHeading.visible(c.blocked.isNotEmpty())
        (parentBlocks + otherBlocks).forEach { blocked ->
            val row = ItemBlockedAppBinding.inflate(layoutInflater, card.pausedList, false)
            val shared = blocked.reason in setOf("DAILY_BUDGET", "SCHEDULE", "FOCUS_SESSION")
            val label = if (shared) "Distracting apps" else blocked.label
            row.icon.setAppIcon(blocked.packageName, label)
            row.name.text = label
            row.reason.text = when (blocked.reason) {
                "PARENT_HOLD", "PARENT_LIMIT" -> "Your limit ran out"
                "SCHEDULE" -> "Schedule is on"
                "APP_LIMIT" -> "Their own limit"
                "DAILY_BUDGET" -> "Their daily budget is used up"
                "FOCUS_SESSION" -> "They're in a focus session"
                else -> ""
            }
            // Parents open apps their own limit closed. Students' own limits are left to them,
            // and a schedule is changed from the Rules tab rather than app by app.
            row.open.visible(blocked.byParent && !shared)
            row.open.setOnClickListener {
                row.open.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    Graph.family.unblock(c.uid, c.name, blocked.packageName, label, 30)
                        .onSuccess { row.open.text = getString(R.string.opened) }
                        .onFailure { row.open.isEnabled = true }
                }
            }
            card.pausedList.addView(row.root)
        }

        c.apps.take(5).forEach { app ->
            val row = ItemAppRowBinding.inflate(layoutInflater, card.topList, false)
            row.rowIcon.setAppIcon(app.packageName, app.label)
            row.rowTitle.text = app.label
            row.rowSubtitle.visible(app.distracting)
            row.rowSubtitle.text = "Marked distracting"
            row.rowValue.text = Format.duration(app.ms)
            card.topList.addView(row.root)
        }
        return card.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
