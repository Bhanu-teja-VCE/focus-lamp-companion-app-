package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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

        val layoutAppUsageList = view.findViewById<LinearLayout>(R.id.layoutAppUsageList)

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

        // === Observe Per-App Usage ===
        viewModel.perAppUsage.observe(viewLifecycleOwner) { appList ->
            layoutAppUsageList.removeAllViews()

            if (appList.isEmpty()) {
                val emptyView = TextView(requireContext()).apply {
                    text = "No app usage data yet. Make sure Usage Access is enabled."
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 13f
                    setPadding(0, 16, 0, 16)
                }
                layoutAppUsageList.addView(emptyView)
                return@observe
            }

            for (app in appList) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f
                        setColor(Color.parseColor("#1E293B"))
                    }
                    background = bg
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = 8
                    layoutParams = params
                }

                // App icon
                val icon = ImageView(requireContext()).apply {
                    val size = (40 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (12 * resources.displayMetrics.density).toInt()
                    }
                    if (app.icon != null) {
                        setImageDrawable(app.icon)
                    } else {
                        setImageResource(R.drawable.ic_lamp)
                    }
                }
                row.addView(icon)

                // App name
                val name = TextView(requireContext()).apply {
                    text = app.appName
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                row.addView(name)

                // Usage time
                val time = TextView(requireContext()).apply {
                    val h = app.usageMinutes / 60
                    val m = app.usageMinutes % 60
                    text = if (h > 0) "${h}h ${m}m" else "${m}m"
                    setTextColor(Color.parseColor("#3B82F6"))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                row.addView(time)

                layoutAppUsageList.addView(row)
            }
        }

        // Refresh data
        viewModel.refreshScreenTime()
        viewModel.loadPerAppUsage()
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
