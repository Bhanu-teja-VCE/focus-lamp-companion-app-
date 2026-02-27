package com.focuslamp.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.focuslamp.app.R
import com.focuslamp.app.utils.SettingsManager

/**
 * Settings screen — configure ESP32 IP, screen time limit, and manage blocked apps.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]
        val settingsManager = SettingsManager(requireContext())

        // Find views
        val etIpAddress = view.findViewById<EditText>(R.id.etIpAddress)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSettings)
        val seekBarLimit = view.findViewById<SeekBar>(R.id.seekBarTimeLimit)
        val tvLimitValue = view.findViewById<TextView>(R.id.tvTimeLimitValue)
        val tvBlockedApps = view.findViewById<TextView>(R.id.tvBlockedAppsList)
        val btnSyncLamp = view.findViewById<Button>(R.id.btnSyncLamp)

        // Load current values
        etIpAddress.setText(settingsManager.espIp)
        seekBarLimit.progress = settingsManager.timeLimitMinutes
        tvLimitValue.text = "${settingsManager.timeLimitMinutes} minutes"

        // Show blocked apps
        val blockedApps = com.focuslamp.app.data.tracking.DistractingAppsManager(requireContext()).getAll()
        tvBlockedApps.text = blockedApps.joinToString("\n") { packageName ->
            // Show a friendly name by extracting from package
            "• ${packageName.substringAfterLast(".")}"
        }

        // SeekBar listener
        seekBarLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = maxOf(progress, 5) // Minimum 5 minutes
                tvLimitValue.text = "$value minutes"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Save button
        btnSave.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isEmpty()) {
                etIpAddress.error = "Enter ESP32 IP address"
                return@setOnClickListener
            }

            val limit = maxOf(seekBarLimit.progress, 5)

            viewModel.updateEspIp(ip)
            viewModel.updateTimeLimit(limit)

            Toast.makeText(requireContext(), "✅ Settings saved!", Toast.LENGTH_SHORT).show()
        }

        // Sync Lamp button — test connection
        btnSyncLamp.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) {
                viewModel.updateEspIp(ip)
                viewModel.checkConnection()
                Toast.makeText(requireContext(), "Syncing with lamp...", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe connection status
        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            btnSyncLamp.text = "Sync Lamp ($status)"
        }
    }
}
