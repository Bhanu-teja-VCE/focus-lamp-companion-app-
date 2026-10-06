package com.focuslamp.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.BuildConfig
import com.focuslamp.app.Graph
import com.focuslamp.app.data.ai.GroqApiClient
import com.focuslamp.app.databinding.FragmentSettingsBinding
import com.focuslamp.app.ui.WelcomeActivity
import com.focuslamp.app.ui.common.PermissionRows
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.ui.family.PinDialog
import com.focuslamp.app.util.Format
import com.focuslamp.app.util.OemUtils
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Role
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Real settings only. Every feature that used to be hidden here now has its own place. */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = Graph.settings

        // Daily budget
        b.budgetSlider.value = settings.timeLimitMinutes.coerceIn(5, 240).let { it - it % 5 }.toFloat()
        b.budgetValue.text = Format.minutes(settings.timeLimitMinutes)
        b.budgetSlider.addOnChangeListener { _, value, _ ->
            settings.timeLimitMinutes = value.toInt()
            b.budgetValue.text = Format.minutes(value.toInt())
            Graph.enforcer.evaluate()
        }
        b.blockOnBudget.isChecked = Graph.rules.blockWhenBudgetUsed
        b.blockOnBudget.setOnCheckedChangeListener { _, on -> Graph.rules.blockWhenBudgetUsed = on }

        // Background
        b.autoStart.visible(OemUtils.hasAutoStartScreen())
        b.autoStart.setOnClickListener { OemUtils.openAutoStartSettings(requireContext()) }

        // Coach
        b.keyInput.setText(settings.groqApiKey)
        b.saveKey.setOnClickListener { saveKey() }

        // This phone
        b.roleText.text = "This is ${settings.displayName.ifBlank { "the student" }}'s phone. Limits and blocking run here."
        b.switchRole.setOnClickListener { switchToParent() }
        b.version.text = "Focus Lamp ${BuildConfig.VERSION_NAME}"
    }

    override fun onResume() {
        super.onResume()
        PermissionRows.render(this, b.permissionRows, PermissionItem.values().toList(), showGranted = true)
    }

    private fun saveKey() {
        val key = b.keyInput.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            Graph.settings.groqApiKey = ""
            b.keyLayout.error = null
            Snackbar.make(b.root, "Key removed. The coach is off.", Snackbar.LENGTH_SHORT).show()
            return
        }
        b.saveKey.isEnabled = false
        b.keyLayout.helperText = "Checking…"
        viewLifecycleOwner.lifecycleScope.launch {
            val r = GroqApiClient().validate(key)
            val v = _b ?: return@launch
            v.saveKey.isEnabled = true
            v.keyLayout.helperText = null
            r.onSuccess {
                Graph.settings.groqApiKey = key
                v.keyLayout.error = null
                Snackbar.make(v.root, "Key works. The coach is ready.", Snackbar.LENGTH_SHORT).show()
            }.onFailure { v.keyLayout.error = it.message }
        }
    }

    /**
     * Switching a student's phone to parent mode turns off blocking, so it needs the parent PIN.
     * Without a PIN, a linked phone can't switch (that would be a way around a parent's limits).
     */
    private fun switchToParent() {
        val go = {
            Graph.settings.role = Role.PARENT
            startActivity(Intent(requireContext(), WelcomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        when {
            Graph.pin.isPinSet() -> PinDialog.verify(this, "Switching this phone to parent mode turns off its limits.") { go() }
            Graph.family.isPaired || Graph.rules.parentLimits().isNotEmpty() -> MaterialAlertDialogBuilder(requireContext())
                .setTitle("A parent needs to do this")
                .setMessage("This phone has parent rules. A parent can set a PIN in their app (Rules) and then switch it.")
                .setPositiveButton("OK", null)
                .show()
            else -> MaterialAlertDialogBuilder(requireContext())
                .setTitle("Use this phone as a parent?")
                .setMessage("Limits and blocking stop on this phone. Use this on the parent's own phone.")
                .setPositiveButton("Switch") { _, _ -> go() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
