package com.focuslamp.app.ui.parent

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.focuslamp.app.R
import com.focuslamp.app.data.repository.FamilyRepository
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.utils.ScheduleManager
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.launch

/**
 * ParentDashboardFragment — Main landing dashboard after Parent PIN authentication.
 * Displays aggregate telemetry, state-colored restriction cards, extension badge counts, and quick navigation.
 */
class ParentDashboardFragment : Fragment() {

    private val familyRepository = FamilyRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_parent_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTotalTime = view.findViewById<TextView>(R.id.tvDashboardTotalTime)
        val tvDistractionTime = view.findViewById<TextView>(R.id.tvDashboardDistractionTime)

        val cardRestrictionStatus = view.findViewById<LinearLayout>(R.id.cardRestrictionStatus)
        val tvRestrictionText = view.findViewById<TextView>(R.id.tvDashboardRestrictionText)
        val tvRestrictionSub = view.findViewById<TextView>(R.id.tvDashboardRestrictionSub)

        val tvExtensionBadge = view.findViewById<TextView>(R.id.tvExtensionBadge)
        val tvExtensionDetails = view.findViewById<TextView>(R.id.tvDashboardExtensionDetails)
        val btnQuickApprove = view.findViewById<Button>(R.id.btnQuickApproveExtension)

        val btnNavSchedules = view.findViewById<LinearLayout>(R.id.btnNavSchedules)
        val btnNavExtensionQueue = view.findViewById<LinearLayout>(R.id.btnNavExtensionQueue)
        val btnNavFamilySettings = view.findViewById<LinearLayout>(R.id.btnNavFamilySettings)

        // 1. Fetch Real Telemetry from queryEvents Engine
        val tracker = ScreenTimeTracker(requireContext())
        val settings = SettingsManager(requireContext())
        val distractingManager = DistractingAppsManager(requireContext())

        val totalMins = tracker.getTotalScreenTimeToday()
        val totalHours = totalMins / 60
        val totalRemMins = totalMins % 60
        tvTotalTime.text = "${totalHours}h ${totalRemMins}m"

        val distractingSet = distractingManager.getDistractingApps()
        val distractionMins = tracker.getDistractionTimeOnly(distractingSet)
        val limitMins = settings.timeLimitMinutes
        tvDistractionTime.text = "${distractionMins}m / ${limitMins}m"

        // 2. Evaluate Restriction Status Card Colors & State
        val scheduleManager = ScheduleManager(requireContext())
        val activeReason = scheduleManager.getActiveRestrictionReason()

        if (activeReason != null) {
            // RED CARD: Active restriction (School Hours / Bedtime Mode)
            cardRestrictionStatus.setBackgroundColor(Color.parseColor("#DC2626"))
            tvRestrictionText.text = "🚨 $activeReason"
            tvRestrictionSub.text = "Focus Lamp LED is RED. Non-emergency apps are currently locked."
        } else if (distractionMins >= limitMins) {
            // RED CARD: Daily limit breached
            cardRestrictionStatus.setBackgroundColor(Color.parseColor("#DC2626"))
            tvRestrictionText.text = "🚨 DAILY FOCUS LIMIT EXCEEDED"
            tvRestrictionSub.text = "Distraction usage reached ${distractionMins}m (Limit: ${limitMins}m). Focus Lamp is RED."
        } else {
            // GREEN CARD: Normal state
            cardRestrictionStatus.setBackgroundColor(Color.parseColor("#16A34A"))
            tvRestrictionText.text = "🟢 NO ACTIVE RESTRICTIONS"
            tvRestrictionSub.text = "Child device is within limits and active schedule windows. Focus Lamp is GREEN."
        }

        // 3. Observe Extension Request Queue
        lifecycleScope.launch {
            familyRepository.extensionRequests.collect { requests ->
                val pendingList = requests.filter { it.isApproved == null }
                if (pendingList.isNotEmpty()) {
                    val firstPending = pendingList.first()
                    tvExtensionBadge.text = "${pendingList.size} PENDING"
                    tvExtensionBadge.setBackgroundColor(Color.parseColor("#D97706"))
                    tvExtensionDetails.text = "${firstPending.childName}: Requesting +${firstPending.requestedMinutes} mins (${firstPending.reason})"
                    btnQuickApprove.visibility = View.VISIBLE

                    btnQuickApprove.setOnClickListener {
                        familyRepository.approveExtension("REQ_101")
                        Toast.makeText(requireContext(), "✅ Extension Approved! Lamp switched to Amber state.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tvExtensionBadge.text = "0 PENDING"
                    tvExtensionBadge.setBackgroundColor(Color.parseColor("#475569"))
                    tvExtensionDetails.text = "No pending extension requests at this time."
                    btnQuickApprove.visibility = View.GONE
                }
            }
        }

        // 4. Quick Nav Actions
        btnNavSchedules.setOnClickListener {
            findNavController().navigate(R.id.action_parentDashboard_to_scheduleManager)
        }

        btnNavExtensionQueue.setOnClickListener {
            findNavController().navigate(R.id.action_parentDashboard_to_extensionQueue)
        }

        btnNavFamilySettings.setOnClickListener {
            findNavController().navigate(R.id.action_parentDashboard_to_familySettings)
        }
    }
}
