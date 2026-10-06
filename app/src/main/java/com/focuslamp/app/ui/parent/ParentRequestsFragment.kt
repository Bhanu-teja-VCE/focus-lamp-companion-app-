package com.focuslamp.app.ui.parent

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
import com.focuslamp.app.databinding.FragmentRequestsBinding
import com.focuslamp.app.databinding.ItemAppRowBinding
import com.focuslamp.app.databinding.ItemRequestBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** "More time" requests waiting for the parent, and the ones already answered. */
class ParentRequestsFragment : Fragment() {

    private var _b: FragmentRequestsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentRequestsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!Graph.family.isAvailable || !Graph.family.isPaired) {
            b.pendingEmpty.text = "Requests appear here once a child's phone is linked."
            b.historyEmpty.visible(false)
            return
        }
        repeatWhileStarted { Graph.family.observeRequests().collect { render(it) } }
    }

    private fun render(all: List<TimeRequest>) {
        val v = _b ?: return
        val pending = all.filter { it.status == RequestStatus.PENDING }
        val answered = all.filter { it.status != RequestStatus.PENDING }.take(30)

        v.pendingList.removeAllViews()
        v.pendingEmpty.visible(pending.isEmpty())
        pending.forEach { v.pendingList.addView(pendingCard(it)) }

        v.historyList.removeAllViews()
        v.historyEmpty.visible(answered.isEmpty())
        answered.forEach { r ->
            val row = ItemAppRowBinding.inflate(layoutInflater, v.historyList, false)
            row.rowIcon.setAppIcon(r.packageName, r.appLabel)
            row.rowTitle.text = "${r.childName}: ${r.appLabel}"
            row.rowSubtitle.text = when {
                r.byParent -> "You opened it"
                r.status == RequestStatus.APPROVED -> "Allowed"
                else -> "Not now"
            } + ", " + Format.ago(r.resolvedAt)
            row.rowValue.text = if (r.status == RequestStatus.APPROVED) "+${r.grantedMinutes}m" else ""
            v.historyList.addView(row.root)
        }
    }

    private fun pendingCard(r: TimeRequest): View {
        val card = ItemRequestBinding.inflate(layoutInflater, b.pendingList, false)
        card.icon.setAppIcon(r.packageName, r.appLabel)
        card.title.text = "${r.childName}: ${r.appLabel}"
        card.detail.text = buildString {
            append("Asked for ${Format.minutes(r.minutes)} ")
            append(Format.ago(r.createdAt))
            if (r.reason.isNotBlank()) append(". ${r.reason}")
        }
        val options = (listOf(15, 30, 60) + r.minutes).distinct().sorted()
        var selected = r.minutes
        options.forEach { m ->
            card.minutes.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = Format.minutes(m)
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(requireContext().getColorStateList(R.color.chip_text))
                chipStrokeWidth = 0f
                isChecked = m == r.minutes
                setOnClickListener { selected = m; card.allow.text = "Allow ${Format.minutes(m)}" }
            })
        }
        card.allow.text = "Allow ${Format.minutes(selected)}"
        card.allow.setOnClickListener { answer(r, true, selected, card) }
        card.deny.setOnClickListener { answer(r, false, 0, card) }
        return card.root
    }

    private fun answer(r: TimeRequest, approve: Boolean, minutes: Int, card: ItemRequestBinding) {
        card.allow.isEnabled = false
        card.deny.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            Graph.family.resolve(r.id, approve, minutes).onFailure {
                card.allow.isEnabled = true
                card.deny.isEnabled = true
                _b?.let { v -> Snackbar.make(v.root, "Couldn't send your answer. Check the connection.", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
