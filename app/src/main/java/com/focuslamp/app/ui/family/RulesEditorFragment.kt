package com.focuslamp.app.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.family.ChildStatus
import com.focuslamp.app.data.family.RemoteApp
import com.focuslamp.app.data.family.RemoteRules
import com.focuslamp.app.data.rules.ParentPinManager
import com.focuslamp.app.data.rules.Schedule
import com.focuslamp.app.databinding.FragmentRulesEditorBinding
import com.focuslamp.app.databinding.ItemRuleLimitBinding
import com.focuslamp.app.databinding.ItemScheduleBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.ui.parent.ParentActivity
import com.focuslamp.app.util.Format
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Parent rules: per-app daily limits, schedules and the unlock PIN.
 *
 * On the parent's phone it edits a linked child's rules remotely (pick the child at the top).
 * On the student's phone it edits rules locally, opened from Family behind the parent PIN.
 */
class RulesEditorFragment : Fragment() {

    private var _b: FragmentRulesEditorBinding? = null
    private val b get() = _b!!

    private val remote get() = activity is ParentActivity
    private var children: List<ChildStatus> = emptyList()
    private var childUid: String? = null

    // Working copy, saved with the button
    private var limits = linkedMapOf<String, Int>()
    private var schedules = mutableListOf<Schedule>()
    private var pinHash: String? = null
    private var pinSalt: String? = null
    private var pinChanged = false
    private var dirty = false
        set(value) {
            field = value
            _b?.save?.isEnabled = value
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentRulesEditorBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.addLimit.setOnClickListener { pickApp() }
        b.setPin.setOnClickListener { changePin() }
        b.save.setOnClickListener { save() }

        if (remote) {
            b.childPicker.visible(true)
            if (!Graph.family.isAvailable || !Graph.family.isPaired) {
                showEmpty("Link a child's phone on the Children tab first.")
                return
            }
            repeatWhileStarted {
                Graph.family.observeChildren().collect { list ->
                    children = list
                    renderChildPicker()
                }
            }
        } else {
            loadLocal()
        }
    }

    // ---------- Loading ----------

    private fun loadLocal() {
        limits = LinkedHashMap(Graph.rules.parentLimits())
        schedules = Graph.rules.schedules().toMutableList()
        pinHash = null
        renderAll()
        dirty = false
    }

    private fun renderChildPicker() {
        if (children.isEmpty()) {
            showEmpty("No child is linked yet. Add one on the Children tab.")
            return
        }
        b.emptyText.visible(false)
        b.editor.visible(true)
        b.childPicker.removeAllViews()
        if (childUid == null || children.none { it.uid == childUid }) selectChild(children.first().uid)
        children.forEach { c ->
            b.childPicker.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = c.name
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(requireContext().getColorStateList(R.color.chip_text))
                chipStrokeWidth = 0f
                isChecked = c.uid == childUid
                setOnClickListener { if (c.uid != childUid) selectChild(c.uid) }
            })
        }
        b.childPicker.visible(children.size > 1)
    }

    private fun selectChild(uid: String) {
        childUid = uid
        viewLifecycleOwner.lifecycleScope.launch {
            val r = Graph.family.observeRules(uid).first()
            limits = LinkedHashMap(r?.parentLimits ?: emptyMap())
            schedules = (r?.schedules ?: Schedule.defaults()).toMutableList()
            pinHash = r?.pinHash
            pinSalt = r?.pinSalt
            pinChanged = false
            renderAll()
            dirty = false
        }
    }

    private fun showEmpty(text: String) {
        b.emptyText.text = text
        b.emptyText.visible(true)
        b.editor.visible(false)
        b.save.visible(false)
    }

    // ---------- Rendering ----------

    private fun renderAll() {
        renderLimits()
        renderSchedules()
        renderPin()
    }

    private fun renderLimits() {
        b.limitsList.removeAllViews()
        limits.forEach { (pkg, min) ->
            val row = ItemRuleLimitBinding.inflate(layoutInflater, b.limitsList, false)
            val label = labelFor(pkg)
            row.icon.setAppIcon(pkg, label)
            row.name.text = label
            row.value.text = Format.minutes(min)
            row.root.setOnClickListener { pickMinutes(pkg, label, min) }
            row.remove.setOnClickListener {
                limits.remove(pkg); dirty = true; renderLimits()
            }
            row.remove.contentDescription = "Remove limit for $label"
            b.limitsList.addView(row.root)
        }
    }

    private fun renderSchedules() {
        b.schedulesList.removeAllViews()
        schedules.forEachIndexed { index, s ->
            val row = ItemScheduleBinding.inflate(layoutInflater, b.schedulesList, false)
            row.enabled.text = s.name
            row.enabled.isChecked = s.enabled
            row.enabled.setOnCheckedChangeListener { _, on ->
                schedules[index] = schedules[index].copy(enabled = on); dirty = true
            }
            row.times.text = "${Format.clockFromMinute(s.startMinute)} to ${Format.clockFromMinute(s.endMinute)}"
            row.times.setOnClickListener { editTimes(index) }
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, letter ->
                val day = i + 1
                row.days.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                    text = letter
                    contentDescription = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i]
                    isCheckable = true
                    isCheckedIconVisible = false
                    setChipBackgroundColorResource(R.color.chip_bg)
                    setTextColor(requireContext().getColorStateList(R.color.chip_text))
                    chipStrokeWidth = 0f
                    isChecked = day in s.days
                    setOnCheckedChangeListener { _, on ->
                        val cur = schedules[index]
                        schedules[index] = cur.copy(days = if (on) cur.days + day else cur.days - day)
                        dirty = true
                    }
                })
            }
            b.schedulesList.addView(row.root)
        }
    }

    private fun renderPin() {
        val isSet = if (remote) !pinHash.isNullOrEmpty() else Graph.pin.isPinSet()
        b.pinStatus.text = if (isSet)
            "A PIN is set. On the student's phone it opens a paused app and protects these rules."
        else "No PIN yet. Set one so you can open a paused app on the student's phone without internet."
        b.setPin.text = if (isSet) "Change PIN" else "Set PIN"
    }

    // ---------- Editing ----------

    private fun candidateApps(): List<RemoteApp> {
        if (!remote) {
            return Graph.appInfo.launchableApps().values
                .filter { it.packageName != requireContext().packageName }
                .map { RemoteApp(it.packageName, it.label) }
                .sortedBy { it.label.lowercase() }
        }
        val c = children.firstOrNull { it.uid == childUid } ?: return emptyList()
        // Most-used apps first, then everything installed on the child's phone.
        val used = c.apps.sortedByDescending { it.ms }
        val rest = c.installed.filter { i -> used.none { it.packageName == i.packageName } }.sortedBy { it.label.lowercase() }
        return used + rest
    }

    private fun labelFor(pkg: String): String {
        if (remote) {
            val c = children.firstOrNull { it.uid == childUid }
            (c?.apps.orEmpty() + c?.installed.orEmpty()).firstOrNull { it.packageName == pkg }?.let { return it.label }
        }
        return Graph.appInfo.label(pkg)
    }

    private fun pickApp() {
        val apps = candidateApps().filter { it.packageName !in limits }
        if (apps.isEmpty()) {
            Snackbar.make(b.root, if (remote) "The child's app list hasn't arrived yet. It updates within a few minutes." else "No apps found.", Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose an app")
            .setItems(apps.map { a -> if (a.ms > 0) "${a.label}  (${Format.duration(a.ms)} today)" else a.label }.toTypedArray()) { _, i ->
                pickMinutes(apps[i].packageName, apps[i].label, null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickMinutes(pkg: String, label: String, current: Int?) {
        val options = listOf(5, 15, 30, 45, 60, 90, 120, 180)
        val checked = options.indexOf(current).coerceAtLeast(2)
        var choice = options[checked]
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$label: time a day")
            .setSingleChoiceItems(options.map { Format.minutes(it) }.toTypedArray(), checked) { _, i -> choice = options[i] }
            .setPositiveButton("Set limit") { _, _ ->
                limits[pkg] = choice; dirty = true; renderLimits()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editTimes(index: Int) {
        val s = schedules[index]
        fun picker(title: String, minute: Int) = MaterialTimePicker.Builder()
            .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(minute / 60).setMinute(minute % 60).setTitleText(title).build()
        val start = picker("${s.name} starts", s.startMinute)
        start.addOnPositiveButtonClickListener {
            val startMin = start.hour * 60 + start.minute
            val end = picker("${s.name} ends", s.endMinute)
            end.addOnPositiveButtonClickListener {
                schedules[index] = schedules[index].copy(startMinute = startMin, endMinute = end.hour * 60 + end.minute)
                dirty = true
                renderSchedules()
            }
            end.show(childFragmentManager, "end")
        }
        start.show(childFragmentManager, "start")
    }

    private fun changePin() {
        PinDialog.create(this, "Used on the student's phone to open a paused app or change these rules.") { p ->
            if (remote) {
                val salt = ParentPinManager.newSalt()
                pinSalt = salt
                pinHash = ParentPinManager.hash(p, salt)
                pinChanged = true
                dirty = true
            } else {
                Graph.pin.setPin(p)
                Snackbar.make(b.root, "PIN saved.", Snackbar.LENGTH_SHORT).show()
            }
            renderPin()
        }
    }

    private fun save() {
        val cleanSchedules = schedules.map { if (it.days.isEmpty()) it.copy(enabled = false) else it }
        if (!remote) {
            Graph.rules.setParentRules(limits, cleanSchedules)
            dirty = false
            Snackbar.make(b.root, "Rules saved. They apply right away.", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uid = childUid ?: return
        b.save.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            Graph.family.saveRules(uid, RemoteRules(limits, cleanSchedules, pinHash, pinSalt, System.currentTimeMillis()))
                .onSuccess {
                    dirty = false
                    Snackbar.make(b.root, "Saved. The phone picks it up within seconds when it's online.", Snackbar.LENGTH_LONG).show()
                }
                .onFailure {
                    b.save.isEnabled = true
                    Snackbar.make(b.root, "Couldn't save: ${it.message}", Snackbar.LENGTH_LONG).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
