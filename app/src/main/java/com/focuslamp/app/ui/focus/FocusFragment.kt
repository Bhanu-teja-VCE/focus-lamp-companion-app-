package com.focuslamp.app.ui.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.focus.FocusState
import com.focuslamp.app.databinding.FragmentFocusBinding
import com.focuslamp.app.databinding.ItemAppRowBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Format
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.combine

/** Focus sessions and their history in one place. */
class FocusFragment : Fragment() {

    private var _b: FragmentFocusBinding? = null
    private val b get() = _b!!
    private var selectedMinutes = 25

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentFocusBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedMinutes = Graph.settings.focusMinutes
        listOf(15, 25, 45, 60, 90).forEach { m ->
            b.lengthGroup.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = Format.minutes(m)
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(requireContext().getColorStateList(R.color.chip_text))
                chipStrokeWidth = 0f
                isChecked = m == selectedMinutes
                setOnClickListener {
                    selectedMinutes = m
                    if (!Graph.focus.state.value.running) b.clock.text = "%d:00".format(m)
                }
            })
        }
        b.strictSwitch.isChecked = Graph.settings.strictFocus
        b.strictSwitch.setOnCheckedChangeListener { _, checked -> Graph.settings.strictFocus = checked }

        b.startStop.setOnClickListener {
            if (Graph.focus.state.value.running) Graph.focus.stop() else Graph.focus.start(selectedMinutes)
        }

        repeatWhileStarted { Graph.focus.state.collect { render(it) } }

        val dao = Graph.db.sessionDao()
        val today = DayClock.startOfDay()
        repeatWhileStarted {
            combine(dao.focusMinutesSinceFlow(today), dao.completedSinceFlow(today)) { m, n -> m to n }.collect { (m, n) ->
                b.todaySummary.text = if (n == 0) "No finished sessions yet today."
                else "Today: $n session${if (n == 1) "" else "s"}, ${Format.durationWords(m * 60_000L)} of focus."
            }
        }
        repeatWhileStarted {
            dao.recentFlow(20).collect { sessions ->
                b.history.removeAllViews()
                b.historyEmpty.visible(sessions.isEmpty())
                sessions.forEach { s ->
                    val row = ItemAppRowBinding.inflate(layoutInflater, b.history, false)
                    row.rowIcon.setImageResource(R.drawable.ic_session)
                    row.rowIcon.setColorFilter(requireContext().getColor(if (s.isCompleted) R.color.lamp_green else R.color.text_faint))
                    row.rowTitle.text = Format.dayAndTime(s.timestamp)
                    row.rowSubtitle.text = if (s.isCompleted) "Finished" else "Ended early"
                    row.rowValue.text = Format.minutes(s.durationMinutes)
                    b.history.addView(row.root)
                }
            }
        }
    }

    private fun render(f: FocusState) {
        b.setupGroup.visible(!f.running)
        if (f.running) {
            val sec = f.remainingMs / 1000
            b.clock.text = "%d:%02d".format(sec / 60, sec % 60)
            b.clockCaption.text = "Ends at ${Format.clock(f.endsAt)}"
            b.ring.setProgressCompat((f.progress * 1000).toInt(), true)
            b.startStop.text = getString(R.string.end_session)
        } else {
            b.clock.text = "%d:00".format(selectedMinutes)
            b.clockCaption.text = getString(R.string.ready)
            b.ring.setProgressCompat(0, true)
            b.startStop.text = getString(R.string.start_session)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
