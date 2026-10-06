package com.focuslamp.app.ui.usage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.databinding.SheetAppLimitBinding
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

/** Tap an app on the Usage tab: mark it distracting and set your own daily limit for it. */
class AppLimitSheet : BottomSheetDialogFragment() {

    private var _b: SheetAppLimitBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetAppLimitBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val pkg = requireArguments().getString(ARG_PKG) ?: return dismiss()
        val label = Graph.appInfo.label(pkg)
        b.icon.setAppIcon(pkg, label)
        b.name.text = label
        b.used.text = "${Format.durationWords(Graph.usage.snapshot.value.msFor(pkg))} today"

        b.distractingSwitch.isChecked = Graph.distracting.isDistracting(pkg)
        b.distractingSwitch.setOnCheckedChangeListener { _, checked -> Graph.distracting.setDistracting(pkg, checked) }

        val current = Graph.rules.selfLimits()[pkg]
        val options = listOf<Int?>(null, 15, 30, 45, 60, 90, 120)
        options.forEach { m ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = if (m == null) getString(R.string.no_limit) else Format.minutes(m)
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(requireContext().getColorStateList(R.color.chip_text))
                chipStrokeWidth = 0f
                isChecked = m == current || (m == null && current == null)
                setOnClickListener { Graph.rules.setSelfLimit(pkg, m) }
            }
            b.limitGroup.addView(chip)
        }

        val parentLimit = Graph.rules.parentLimits()[pkg]
        b.parentNote.visible(parentLimit != null)
        if (parentLimit != null) {
            b.parentNote.text = "${Graph.settings.parentName.ifBlank { "A parent" }} set ${Format.minutes(parentLimit)} a day for $label. Only they can change that one."
        }
        b.done.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        const val TAG = "app_limit"
        private const val ARG_PKG = "pkg"
        fun newInstance(pkg: String) = AppLimitSheet().apply { arguments = bundleOf(ARG_PKG to pkg) }
    }
}
