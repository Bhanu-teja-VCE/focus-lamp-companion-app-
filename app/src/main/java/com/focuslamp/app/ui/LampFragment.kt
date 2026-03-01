package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.focuslamp.app.R
import com.focuslamp.app.utils.SettingsManager

/**
 * Dedicated Lamp screen — the centerpiece of the Focus Lamp product.
 * Shows the virtual lamp status (Green/Orange/Red), waste time limit input,
 * and per-app screen time breakdown.
 */
class LampFragment : Fragment(R.layout.fragment_lamp) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]
        val settingsManager = SettingsManager(requireContext())

        // === Find views ===
        val viewLampGlow = view.findViewById<View>(R.id.viewLampGlow)
        val tvLampStatus = view.findViewById<TextView>(R.id.tvLampStatus)
        val tvLampDetail = view.findViewById<TextView>(R.id.tvLampDetail)

        val etWasteTimeLimit = view.findViewById<EditText>(R.id.etWasteTimeLimit)
        val btnSetLimit = view.findViewById<Button>(R.id.btnSetLimit)
        val tvCurrentLimit = view.findViewById<TextView>(R.id.tvCurrentLimit)

        // Show current limit
        val currentLimit = settingsManager.timeLimitMinutes
        tvCurrentLimit.text = "Current limit: ${currentLimit} min"
        etWasteTimeLimit.setText(currentLimit.toString())

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

        // === Observe Screen Time and update lamp ===
        viewModel.distractionMinutes.observe(viewLifecycleOwner) { minutes ->
            val limit = viewModel.timeLimitMinutes.value ?: 30
            updateVirtualLamp(minutes, limit.toLong(), viewLampGlow, tvLampStatus)
            tvLampDetail.text = "${minutes}m used of ${limit}m limit"
        }

        viewModel.timeLimitMinutes.observe(viewLifecycleOwner) { limit ->
            tvCurrentLimit.text = "Current limit: $limit min"
            val currentScreenTime = viewModel.distractionMinutes.value ?: 0L
            updateVirtualLamp(currentScreenTime, limit.toLong(), viewLampGlow, tvLampStatus)
            tvLampDetail.text = "${currentScreenTime}m used of ${limit}m limit"
        }

        // Refresh data
        viewModel.refreshScreenTime()
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
