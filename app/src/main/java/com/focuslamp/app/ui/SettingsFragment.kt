package com.focuslamp.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        val etGroqApiKey = view.findViewById<EditText>(R.id.etGroqApiKey)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSettings)
        val seekBarLimit = view.findViewById<SeekBar>(R.id.seekBarTimeLimit)
        val tvLimitValue = view.findViewById<TextView>(R.id.tvTimeLimitValue)
        val tvBlockedApps = view.findViewById<TextView>(R.id.tvBlockedAppsList)
        val btnSyncLamp = view.findViewById<Button>(R.id.btnSyncLamp)
        val btnGrantOverlay = view.findViewById<Button>(R.id.btnGrantOverlay)
        val btnOemAutoStart = view.findViewById<Button>(R.id.btnOemAutoStart)
        val btnTestGroqKey = view.findViewById<Button>(R.id.btnTestGroqKey)

        // Load current values
        etIpAddress.setText(settingsManager.espIp)
        etGroqApiKey.setText(settingsManager.groqApiKey)
        seekBarLimit.progress = settingsManager.timeLimitMinutes
        tvLimitValue.text = "${settingsManager.timeLimitMinutes} minutes"

        // Show blocked apps
        val blockedApps = com.focuslamp.app.data.tracking.DistractingAppsManager(requireContext()).getAll()
        tvBlockedApps.text = blockedApps.joinToString("\n") { packageName ->
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

        // Grant Overlay Permission
        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        // Open Vivo / OEM AutoStart settings
        btnOemAutoStart.setOnClickListener {
            com.focuslamp.app.utils.OemUtils.openOemAutoStartSettings(requireContext())
        }

        // Test Groq API Key
        btnTestGroqKey.setOnClickListener {
            val apiKey = etGroqApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                etGroqApiKey.error = "Enter a Groq API Key first"
                return@setOnClickListener
            }

            btnTestGroqKey.isEnabled = false
            btnTestGroqKey.text = "Testing connection..."

            androidx.lifecycle.lifecycleScope.launchWhenResumed {
                val client = com.focuslamp.app.data.network.GroqApiClient()
                val result = client.validateApiKey(apiKey)

                btnTestGroqKey.isEnabled = true
                btnTestGroqKey.text = "Test Groq API Key"

                if (result.isSuccess) {
                    settingsManager.groqApiKey = apiKey
                    Toast.makeText(requireContext(), "✅ Groq API Key valid and saved!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "❌ Invalid Groq API Key: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Save button
        btnSave.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isEmpty()) {
                etIpAddress.error = "Enter ESP32 IP address"
                return@setOnClickListener
            }

            val limit = maxOf(seekBarLimit.progress, 5)
            val apiKey = etGroqApiKey.text.toString().trim()

            viewModel.updateEspIp(ip)
            viewModel.updateTimeLimit(limit)
            settingsManager.groqApiKey = apiKey

            Toast.makeText(requireContext(), "✅ All Settings saved!", Toast.LENGTH_SHORT).show()
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
