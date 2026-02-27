package com.focuslamp.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.focuslamp.app.R

/**
 * Home screen — the main dashboard showing:
 * - Circular progress bar for today's distraction time
 * - Current monitoring status
 * - Start/Stop monitoring button
 * - Quick stats (total focus time, completed sessions)
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        // Find views
        val progressBar = view.findViewById<ProgressBar>(R.id.progressScreenTime)
        val tvDistractionTime = view.findViewById<TextView>(R.id.tvDistractionTime)
        val tvTimeLimit = view.findViewById<TextView>(R.id.tvTimeLimit)
        val tvStatus = view.findViewById<TextView>(R.id.tvMonitoringStatus)
        val btnMonitor = view.findViewById<Button>(R.id.btnToggleMonitoring)
        val btnStartFocus = view.findViewById<Button>(R.id.btnStartFocus)
        val tvTotalFocus = view.findViewById<TextView>(R.id.tvTotalFocusTime)
        val tvSessions = view.findViewById<TextView>(R.id.tvCompletedSessions)
        val tvEspIp = view.findViewById<TextView>(R.id.tvEspIp)
        val tvLimitWarning = view.findViewById<TextView>(R.id.tvLimitWarning)

        // === Observe distraction time ===
        viewModel.distractionMinutes.observe(viewLifecycleOwner) { minutes ->
            tvDistractionTime.text = "${minutes} min"
            val limit = viewModel.timeLimitMinutes.value ?: 30
            val progress = if (limit > 0) ((minutes.toFloat() / limit) * 100).toInt().coerceAtMost(100) else 0
            progressBar.progress = progress
        }

        viewModel.timeLimitMinutes.observe(viewLifecycleOwner) { limit ->
            tvTimeLimit.text = "/ $limit min limit"
        }

        viewModel.isLimitExceeded.observe(viewLifecycleOwner) { exceeded ->
            if (exceeded) {
                tvLimitWarning.visibility = View.VISIBLE
                tvLimitWarning.text = "⚠️ Screen time limit exceeded!"
            } else {
                tvLimitWarning.visibility = View.GONE
            }
        }

        // === Monitoring toggle ===
        viewModel.isMonitoring.observe(viewLifecycleOwner) { isActive ->
            if (isActive) {
                btnMonitor.text = "Stop Monitoring"
                tvStatus.text = "🟢 Monitoring Active"
            } else {
                btnMonitor.text = "Start Monitoring"
                tvStatus.text = "⚫ Monitoring Off"
            }
        }

        btnMonitor.setOnClickListener {
            if (viewModel.isMonitoring.value == true) {
                viewModel.stopMonitoringService()
            } else {
                viewModel.startMonitoringService()
            }
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

        // === Stats ===
        viewModel.totalFocusMinutes.observe(viewLifecycleOwner) { minutes ->
            val hours = minutes / 60
            val mins = minutes % 60
            tvTotalFocus.text = "${hours}h ${mins}m"
        }

        viewModel.completedSessions.observe(viewLifecycleOwner) { count ->
            tvSessions.text = "$count"
        }

        viewModel.espIp.observe(viewLifecycleOwner) { ip ->
            tvEspIp.text = "ESP32: $ip"
        }

        // Refresh data when view is created
        viewModel.refreshScreenTime()
        viewModel.loadStats()
    }
}
