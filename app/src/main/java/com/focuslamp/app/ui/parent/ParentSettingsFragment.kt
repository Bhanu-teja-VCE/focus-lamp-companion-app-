package com.focuslamp.app.ui.parent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.focuslamp.app.BuildConfig
import com.focuslamp.app.Graph
import com.focuslamp.app.databinding.FragmentParentSettingsBinding
import com.focuslamp.app.ui.WelcomeActivity
import com.focuslamp.app.ui.common.PermissionRows
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Role
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ParentSettingsFragment : Fragment() {

    private var _b: FragmentParentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentParentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.who.text = "${Graph.settings.displayName.ifBlank { "Parent" }}'s phone. " +
            if (Graph.family.isPaired) "Your family is set up." else "No family yet."
        b.version.text = "Focus Lamp ${BuildConfig.VERSION_NAME}"
        b.switchToStudent.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Use this phone as a student's?")
                .setMessage("You'll stop getting requests on this phone. Use this only if it was set up as the wrong one.")
                .setPositiveButton("Switch") { _, _ ->
                    Graph.settings.familyId = null
                    Graph.settings.role = Role.STUDENT
                    startActivity(Intent(requireContext(), WelcomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        PermissionRows.render(this, b.permissionRows, listOf(PermissionItem.NOTIFICATIONS, PermissionItem.BATTERY), showGranted = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
