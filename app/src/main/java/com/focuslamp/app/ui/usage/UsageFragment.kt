package com.focuslamp.app.ui.usage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.Graph
import com.focuslamp.app.data.tracking.DayTotal
import com.focuslamp.app.data.tracking.UsageSnapshot
import com.focuslamp.app.databinding.FragmentUsageBinding
import com.focuslamp.app.databinding.HeaderUsageBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class UsageFragment : Fragment() {

    private var _b: FragmentUsageBinding? = null
    private val b get() = _b!!
    private val header = HeaderAdapter()
    private val apps = AppUsageAdapter(
        onToggle = { row, checked -> Graph.distracting.setDistracting(row.packageName, checked) },
        onOpen = { row -> AppLimitSheet.newInstance(row.packageName).show(childFragmentManager, AppLimitSheet.TAG) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentUsageBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = ConcatAdapter(header, apps)
        b.list.itemAnimator?.changeDuration = 120

        repeatWhileStarted {
            combine(Graph.usage.snapshot, Graph.rules.version, Graph.distracting.apps) { s, _, _ -> s }
                .collect { render(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            Graph.usage.refresh()
            header.week = Graph.usage.lastDays(7)
        }
    }

    private fun render(s: UsageSnapshot) {
        header.snapshot = s
        val parent = Graph.rules.parentLimits()
        val self = Graph.rules.selfLimits()
        val distracting = Graph.distracting.getAll()
        val maxMs = s.apps.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L

        val used = s.apps.map { app ->
            row(app.packageName, app.label, app.ms, app.distracting, parent[app.packageName], self[app.packageName], maxMs)
        }
        // Ticked apps not opened today still appear, so they can be unticked.
        val launchable = Graph.appInfo.launchableApps()
        val seen = s.apps.map { it.packageName }.toSet()
        val idle = distracting.filter { it !in seen && it in launchable }.map { pkg ->
            row(pkg, launchable[pkg]?.label ?: Graph.appInfo.label(pkg), 0L, true, parent[pkg], self[pkg], maxMs)
        }.sortedBy { it.label.lowercase() }

        apps.submitList(used + idle)
    }

    private fun row(pkg: String, label: String, ms: Long, distracting: Boolean, parentLimit: Int?, selfLimit: Int?, maxMs: Long): AppUsageRow {
        val decision = Graph.enforcer.decide(pkg)
        val detail = when {
            decision != null -> "Paused now"
            parentLimit != null -> "Parent limit ${Format.minutes(parentLimit)}"
            selfLimit != null -> "Your limit ${Format.minutes(selfLimit)}"
            else -> null
        }
        return AppUsageRow(pkg, label, ms, distracting, detail, decision != null, ((ms * 1000) / maxMs).toInt())
    }

    override fun onDestroyView() {
        b.list.adapter = null
        super.onDestroyView()
        _b = null
    }

    /** The screen-time summary and week chart, scrolling with the list. */
    private class HeaderAdapter : RecyclerView.Adapter<HeaderAdapter.Holder>() {
        var snapshot: UsageSnapshot = UsageSnapshot.EMPTY
            set(value) { field = value; notifyItemChanged(0) }
        var week: List<DayTotal> = emptyList()
            set(value) { field = value; notifyItemChanged(0) }

        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(HeaderUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val h = holder.b
            val s = snapshot
            h.total.text = if (s.totalMs == 0L) "0m" else Format.duration(s.totalMs)
            h.totalCaption.text = "Screen time today, ${s.pickups} pickups. " +
                "${Format.durationWords(s.distractionMs)} on distracting apps."
            h.week.setDays(week)
            h.week.visible(week.isNotEmpty())
            h.empty.visible(!s.hasPermission || s.apps.isEmpty())
            h.empty.text = if (!s.hasPermission) "Usage access is off. Turn it on from the Today tab to see your apps."
            else "No app use recorded yet today."
        }

        class Holder(val b: HeaderUsageBinding) : RecyclerView.ViewHolder(b.root)
    }
}
