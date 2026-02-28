package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
 * Shows dual circular rings, stat cards, virtual lamp, and monitoring toggle.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]
        val settingsManager = SettingsManager(requireContext())

        // === Find all views ===
        val progressFocusRing = view.findViewById<ProgressBar>(R.id.progressFocusTime)
        val tvTotalFocus = view.findViewById<TextView>(R.id.tvTotalFocusTime)

        val progressScreenRing = view.findViewById<ProgressBar>(R.id.progressScreenTimeRing)
        val tvTotalScreenTimeRing = view.findViewById<TextView>(R.id.tvTotalScreenTimeRing)

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

        // Virtual Lamp views
        val viewLampGlow = view.findViewById<View>(R.id.viewLampGlow)
        val tvLampStatus = view.findViewById<TextView>(R.id.tvLampStatus)

        // Waste Time Limit views
        val etWasteTimeLimit = view.findViewById<EditText>(R.id.etWasteTimeLimit)
        val btnSetLimit = view.findViewById<Button>(R.id.btnSetLimit)
        val tvCurrentLimit = view.findViewById<TextView>(R.id.tvCurrentLimit)

        // Load saved IP
        etIpAddress.setText(settingsManager.espIp)

        // Show current limit
        val currentLimit = settingsManager.timeLimitMinutes
        tvCurrentLimit.text = "Current limit: ${currentLimit} min"
        etWasteTimeLimit.setText(currentLimit.toString())

        // === Observe Total Focus Time (Ring) ===
        viewModel.totalFocusMinutes.observe(viewLifecycleOwner) { minutes ->
            val hours = minutes / 60
            val mins = minutes % 60
            tvTotalFocus.text = "${hours}h ${mins}m"

            // Also update productive card
            tvProductiveTime.text = "${hours}h ${mins}m"

            // Ring progress — based on daily goal (8 hours = 480 min)
            val goalProgress = ((minutes.toFloat() / 480) * 100).toInt().coerceAtMost(100)
            progressFocusRing.progress = goalProgress
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
        }

        // === Observe Distraction Time (Screen Time) ===
        viewModel.distractionMinutes.observe(viewLifecycleOwner) { minutes ->
            // Update distraction card
            tvDistractionTime.text = "${minutes}m"
            val limit = viewModel.timeLimitMinutes.value ?: 30
            val progress = if (limit > 0) ((minutes.toFloat() / limit) * 100).toInt().coerceAtMost(100) else 0
            progressDistraction.progress = progress

            // Update Screen Time ring
            val hours = minutes / 60
            val mins = minutes % 60
            tvTotalScreenTimeRing.text = "${hours}h ${mins}m"
            progressScreenRing.progress = progress

            // Update Virtual Lamp
            updateVirtualLamp(minutes, limit.toLong(), viewLampGlow, tvLampStatus)
        }

        viewModel.timeLimitMinutes.observe(viewLifecycleOwner) { limit ->
            tvTimeLimit.text = "/ $limit min limit"
            tvCurrentLimit.text = "Current limit: $limit min"

            // Re-evaluate lamp with updated limit
            val currentScreenTime = viewModel.distractionMinutes.value ?: 0L
            updateVirtualLamp(currentScreenTime, limit.toLong(), viewLampGlow, tvLampStatus)
        }

        viewModel.isLimitExceeded.observe(viewLifecycleOwner) { exceeded ->
            if (exceeded) {
                tvLimitWarning.text = "⚠️ Screen time limit exceeded!"
            } else {
                tvLimitWarning.text = "Tap to start tracking screen time"
            }
        }

        // === Set Waste Time Limit ===
        btnSetLimit.setOnClickListener {
            val input = etWasteTimeLimit.text.toString().trim()
            val newLimit = input.toIntOrNull()
            if (newLimit != null && newLimit > 0) {
                viewModel.updateTimeLimit(newLimit)
                Toast.makeText(requireContext(), "Limit set to $newLimit min", Toast.LENGTH_SHORT).show()
            } else {
                etWasteTimeLimit.error = "Enter a valid number"
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

    /**
     * Updates the Virtual Lamp glow color and status text.
     *  - GREEN:  screenTime < limit
     *  - ORANGE: limit <= screenTime < limit + 15 min
     *  - RED:    screenTime >= limit + 15 min
     */
    private fun updateVirtualLamp(
        screenTimeMinutes: Long, limitMinutes: Long,
        glowView: View, statusView: TextView
    ) {
        val lampColor: Int
        val statusText: String
        val statusColor: Int

        when {
            screenTimeMinutes < limitMinutes -> {
                lampColor = Color.parseColor("#22C55E")
                statusText = "✅ Within Limit"
                statusColor = Color.parseColor("#22C55E")
            }
            screenTimeMinutes < limitMinutes + 15 -> {
                lampColor = Color.parseColor("#F59E0B")
                statusText = "⚠️ Approaching Limit"
                statusColor = Color.parseColor("#F59E0B")
            }
            else -> {
                lampColor = Color.parseColor("#EF4444")
                statusText = "🔴 Limit Exceeded!"
                statusColor = Color.parseColor("#EF4444")
            }
        }

        val glowDrawable = GradientDrawable()
        glowDrawable.shape = GradientDrawable.OVAL
        glowDrawable.setColor(lampColor)
        glowView.background = glowDrawable

        statusView.text = statusText
        statusView.setTextColor(statusColor)
    }
}
