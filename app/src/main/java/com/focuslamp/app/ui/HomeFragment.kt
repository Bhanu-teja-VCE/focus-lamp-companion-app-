package com.focuslamp.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.focuslamp.app.R
import com.focuslamp.app.utils.SettingsManager

/**
 * Home screen — the premium dashboard matching the reference design.
 * Shows circular focus ring, stat cards, daily goal, hardware sync, and monitoring toggle.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]
        val settingsManager = SettingsManager(requireContext())

        // === Find all views ===
        val progressRing = view.findViewById<ProgressBar>(R.id.progressScreenTime)
        val tvTotalFocus = view.findViewById<TextView>(R.id.tvTotalFocusTime)
        val tvTrend = view.findViewById<TextView>(R.id.tvTrendBadge)

        val tvProductiveTime = view.findViewById<TextView>(R.id.tvProductiveTime)
        val tvProductivePercent = view.findViewById<TextView>(R.id.tvProductivePercent)
        val progressProductive = view.findViewById<ProgressBar>(R.id.progressProductive)

        val tvDistractionTime = view.findViewById<TextView>(R.id.tvDistractionTime)
        val tvTimeLimit = view.findViewById<TextView>(R.id.tvTimeLimit)
        val progressDistraction = view.findViewById<ProgressBar>(R.id.progressDistraction)

        val tvDailyGoalPercent = view.findViewById<TextView>(R.id.tvDailyGoalPercent)
        val progressDailyGoal = view.findViewById<ProgressBar>(R.id.progressDailyGoal)
        val tvGoalMessage = view.findViewById<TextView>(R.id.tvGoalMessage)

        val etIpAddress = view.findViewById<EditText>(R.id.etIpAddress)
        val btnSyncLamp = view.findViewById<Button>(R.id.btnSyncLamp)
        val tvDeviceStatus = view.findViewById<TextView>(R.id.tvDeviceStatus)
        val tvConnectionBadge = view.findViewById<TextView>(R.id.tvConnectionBadge)

        val tvMonitoringStatus = view.findViewById<TextView>(R.id.tvMonitoringStatus)
        val tvLimitWarning = view.findViewById<TextView>(R.id.tvLimitWarning)
        val btnToggleMonitoring = view.findViewById<Button>(R.id.btnToggleMonitoring)

        val btnStartFocus = view.findViewById<Button>(R.id.btnStartFocus)
        val tvSessions = view.findViewById<TextView>(R.id.tvCompletedSessions)
        val tvEspIp = view.findViewById<TextView>(R.id.tvEspIp)

        // Load saved IP
        etIpAddress.setText(settingsManager.espIp)

        // === Observe Total Focus Time (Ring) ===
        viewModel.totalFocusMinutes.observe(viewLifecycleOwner) { minutes ->
            val hours = minutes / 60
            val mins = minutes % 60
            tvTotalFocus.text = "${hours}h ${mins}m"

            // Also update productive card
            tvProductiveTime.text = "${hours}h ${mins}m"

            // Ring progress — based on daily goal (8 hours = 480 min)
            val goalProgress = ((minutes.toFloat() / 480) * 100).toInt().coerceAtMost(100)
            progressRing.progress = goalProgress
            progressProductive.progress = goalProgress

            tvDailyGoalPercent.text = "${goalProgress}%"
            progressDailyGoal.progress = goalProgress

            val remaining = (480 - minutes).coerceAtLeast(0)
            val remHours = remaining / 60
            val remMins = remaining % 60
            tvGoalMessage.text = "ℹ You're ${remHours}h ${remMins}m away from your target"
        }

        viewModel.completedSessions.observe(viewLifecycleOwner) { count ->
            tvSessions.text = "$count"
            tvTrend.text = "$count sessions today"
        }

        // === Observe Distraction Time ===
        viewModel.distractionMinutes.observe(viewLifecycleOwner) { minutes ->
            tvDistractionTime.text = "${minutes}m"
            val limit = viewModel.timeLimitMinutes.value ?: 30
            val progress = if (limit > 0) ((minutes.toFloat() / limit) * 100).toInt().coerceAtMost(100) else 0
            progressDistraction.progress = progress
        }

        viewModel.timeLimitMinutes.observe(viewLifecycleOwner) { limit ->
            tvTimeLimit.text = "/ $limit min limit"
        }

        viewModel.isLimitExceeded.observe(viewLifecycleOwner) { exceeded ->
            if (exceeded) {
                tvLimitWarning.text = "⚠️ Screen time limit exceeded!"
            } else {
                tvLimitWarning.text = "Tap to start tracking screen time"
            }
        }

        // === Monitoring Toggle ===
        viewModel.isMonitoring.observe(viewLifecycleOwner) { isActive ->
            if (isActive) {
                btnToggleMonitoring.text = "Stop"
                tvMonitoringStatus.text = "🟢 Monitoring Active"
                tvLimitWarning.text = "Tracking your screen time..."
            } else {
                btnToggleMonitoring.text = "Start"
                tvMonitoringStatus.text = "Live Monitoring"
            }
        }

        btnToggleMonitoring.setOnClickListener {
            if (viewModel.isMonitoring.value == true) {
                viewModel.stopMonitoringService()
            } else {
                viewModel.startMonitoringService()
            }
        }

        // === Hardware Sync ===
        btnSyncLamp.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) {
                viewModel.updateEspIp(ip)
                viewModel.checkConnection()
                Toast.makeText(requireContext(), "Connecting to lamp...", Toast.LENGTH_SHORT).show()
            } else {
                etIpAddress.error = "Enter IP address"
            }
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            tvDeviceStatus.text = "Device: $status"
            if (status == "Connected") {
                tvConnectionBadge.text = "ONLINE"
            } else {
                tvConnectionBadge.text = "OFFLINE"
            }
        }

        viewModel.espIp.observe(viewLifecycleOwner) { ip ->
            tvEspIp.text = ip
        }

        // === Focus Timer ===
        btnStartFocus.setOnClickListener {
            val bottomSheet = FocusSetupBottomSheet()
            bottomSheet.show(parentFragmentManager, "FocusSetupBottomSheet")
        }

        viewModel.isSessionActive.observe(viewLifecycleOwner) { isActive ->
            if (isActive) {
                if (findNavController().currentDestination?.id == R.id.homeFragment) {
                    findNavController().navigate(R.id.action_homeFragment_to_sessionFragment)
                }
            }
        }

        // Refresh data
        viewModel.refreshScreenTime()
        viewModel.loadStats()
    }
}
