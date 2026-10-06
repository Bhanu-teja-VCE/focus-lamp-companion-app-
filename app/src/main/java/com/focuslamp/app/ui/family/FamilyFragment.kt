package com.focuslamp.app.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.family.RequestStatus
import com.focuslamp.app.data.family.TimeRequest
import com.focuslamp.app.databinding.FragmentFamilyBinding
import com.focuslamp.app.databinding.ItemAppRowBinding
import com.focuslamp.app.ui.MainActivity
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Student's Family tab: link to a parent, see the rules they set, and past requests. */
class FamilyFragment : Fragment() {

    private var _b: FragmentFamilyBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentFamilyBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.linkButton.setOnClickListener { link() }
        b.openControls.setOnClickListener { openLocalControls() }
        b.unlinkButton.setOnClickListener { unlink() }

        repeatWhileStarted {
            combine(Graph.rules.version, Graph.family.myRequests, Graph.family.connected) { _, req, conn -> req to conn }
                .collect { (requests, connected) -> render(requests, connected) }
        }
    }

    private fun render(requests: List<TimeRequest>, connected: Boolean) {
        val family = Graph.family
        val paired = family.isPaired
        b.linkCard.visible(!paired)
        b.linkedSection.visible(paired)
        b.unlinkButton.visible(paired)
        if (!family.isAvailable) {
            b.linkText.text = getString(R.string.link_unavailable)
            b.codeLayout.visible(false)
            b.linkButton.visible(false)
        }
        if (paired) {
            b.linkedTitle.text = "Linked to ${Graph.settings.parentName.ifBlank { "your parent" }}"
            b.linkedSub.text = if (connected) "Rules and answers arrive here automatically."
            else "Offline right now. Rules you already have still apply."
        }

        // Rules from the parent
        b.rulesList.removeAllViews()
        val limits = Graph.rules.parentLimits()
        val schedules = Graph.rules.schedules().filter { it.enabled }
        limits.entries.sortedBy { Graph.appInfo.label(it.key).lowercase() }.forEach { (pkg, min) ->
            val row = ItemAppRowBinding.inflate(layoutInflater, b.rulesList, false)
            val label = Graph.appInfo.label(pkg)
            row.rowIcon.setAppIcon(pkg, label)
            row.rowTitle.text = label
            row.rowSubtitle.text = if (pkg in Graph.rules.holds()) "Paused until your parent opens it" else "A day"
            row.rowValue.text = Format.minutes(min)
            b.rulesList.addView(row.root)
        }
        schedules.forEach { s ->
            val row = ItemAppRowBinding.inflate(layoutInflater, b.rulesList, false)
            row.rowIcon.setImageResource(R.drawable.ic_lock)
            row.rowIcon.setColorFilter(requireContext().getColor(R.color.amber))
            row.rowTitle.text = s.name
            row.rowSubtitle.text = "${Format.clockFromMinute(s.startMinute)} to ${Format.clockFromMinute(s.endMinute)}, ${daysText(s.days)}"
            row.rowValue.text = ""
            b.rulesList.addView(row.root)
        }
        b.rulesEmpty.visible(limits.isEmpty() && schedules.isEmpty())

        // Requests
        val showRequests = paired
        b.requestsHeading.visible(showRequests)
        b.requestsList.visible(showRequests)
        b.requestsEmpty.visible(showRequests && requests.isEmpty())
        b.requestsList.removeAllViews()
        requests.take(15).forEach { r ->
            val row = ItemAppRowBinding.inflate(layoutInflater, b.requestsList, false)
            row.rowIcon.setAppIcon(r.packageName, r.appLabel)
            row.rowTitle.text = r.appLabel
            row.rowSubtitle.text = when (r.status) {
                RequestStatus.PENDING -> "Waiting for an answer"
                RequestStatus.APPROVED -> if (r.byParent) "Opened by your parent" else "Approved"
                RequestStatus.DENIED -> "Not approved"
            } + ", " + Format.ago(if (r.resolvedAt > 0) r.resolvedAt else r.createdAt)
            row.rowValue.text = if (r.status == RequestStatus.APPROVED) "+${r.grantedMinutes}m" else "${r.minutes}m"
            b.requestsList.addView(row.root)
        }

        b.localControls.visible(!Graph.rules.managedRemotely)
    }

    private fun link() {
        val code = b.codeInput.text?.toString()?.trim().orEmpty()
        if (code.length != 6) {
            b.codeLayout.error = "Enter the 6-digit code from the parent's phone."
            return
        }
        b.codeLayout.error = null
        b.linkButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val name = Graph.settings.displayName.ifBlank { "Student" }
            Graph.family.join(code, name)
                .onSuccess {
                    Snackbar.make(b.root, "Linked. Your parent can now set rules and answer requests.", Snackbar.LENGTH_LONG).show()
                }
                .onFailure { b.codeLayout.error = it.message ?: "Couldn't link. Check the internet connection." }
            b.linkButton.isEnabled = true
            render(Graph.family.myRequests.value, Graph.family.connected.value)
        }
    }

    private fun openLocalControls() {
        val open = { (activity as? MainActivity)?.tabs?.open(R.id.rulesEditorFragment); Unit }
        if (Graph.pin.isPinSet()) {
            PinDialog.verify(this, "Parent controls are protected so only a parent can change them.") { open() }
        } else {
            PinDialog.create(this, "A parent should choose this. It's needed to change these rules or open a paused app.") { p ->
                Graph.pin.setPin(p)
                open()
            }
        }
    }

    private fun unlink() {
        val doUnlink = {
            viewLifecycleOwner.lifecycleScope.launch { Graph.family.leave() }
            Unit
        }
        if (Graph.pin.isPinSet()) {
            PinDialog.verify(this, "Unlinking stops your parent from seeing this phone.") { doUnlink() }
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ask your parent first")
                .setMessage("Unlinking needs the parent PIN. Your parent can set one in their app under Rules.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        fun daysText(days: Set<Int>): String = when (days) {
            setOf(1, 2, 3, 4, 5) -> "weekdays"
            setOf(6, 7) -> "weekends"
            setOf(1, 2, 3, 4, 5, 6, 7) -> "every day"
            else -> days.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
        }
    }
}
