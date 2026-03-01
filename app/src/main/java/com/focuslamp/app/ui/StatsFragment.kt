package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.focuslamp.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        val tvTotalTimeToday = view.findViewById<TextView>(R.id.tvTotalTimeToday)
        val tvCurrentDate = view.findViewById<TextView>(R.id.tvCurrentDate)
        val chartContainer = view.findViewById<LinearLayout>(R.id.chartContainer)
        val layoutAppUsageList = view.findViewById<LinearLayout>(R.id.layoutAppUsageList)

        // Set current date string
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        tvCurrentDate.text = sdf.format(Calendar.getInstance().time)

        // Observe Total Screen Time
        viewModel.distractionMinutes.observe(viewLifecycleOwner) { minutes ->
            val hours = minutes / 60
            val remMins = minutes % 60

            if (hours > 0) {
                tvTotalTimeToday.text = "${hours} hr, ${remMins} min"
            } else {
                tvTotalTimeToday.text = "0 hr, $minutes min"
            }
        }

        // Hide the chart for now (weekly tracking requires historical data)
        chartContainer.visibility = View.GONE

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

            // Hint text
            val hint = TextView(requireContext()).apply {
                text = "Toggle apps as distracting — the lamp tracks only these"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(4, 0, 0, 16)
            }
            layoutAppUsageList.addView(hint)

            for (app in appList) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f
                        setColor(
                            if (app.isDistracting) Color.parseColor("#2D1B1B")
                            else Color.parseColor("#1E293B")
                        )
                    }
                    background = bg
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = 8
                    layoutParams = params
                }

                // Distraction toggle checkbox
                val checkbox = CheckBox(requireContext()).apply {
                    isChecked = app.isDistracting
                    val size = (24 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (8 * resources.displayMetrics.density).toInt()
                    }
                    buttonTintList = android.content.res.ColorStateList.valueOf(
                        if (app.isDistracting) Color.parseColor("#EF4444") else Color.parseColor("#64748B")
                    )
                    setOnCheckedChangeListener { _, isChecked ->
                        viewModel.toggleDistractingApp(app.packageName, isChecked)
                    }
                }
                row.addView(checkbox)

                // App icon
                val icon = ImageView(requireContext()).apply {
                    val size = (36 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (10 * resources.displayMetrics.density).toInt()
                    }
                    if (app.icon != null) {
                        setImageDrawable(app.icon)
                    } else {
                        setImageResource(R.drawable.ic_lamp)
                    }
                }
                row.addView(icon)

                // App name + distraction badge
                val nameLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val name = TextView(requireContext()).apply {
                    text = app.appName
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                nameLayout.addView(name)
                if (app.isDistracting) {
                    val badge = TextView(requireContext()).apply {
                        text = "⚡ Distracting"
                        setTextColor(Color.parseColor("#EF4444"))
                        textSize = 11f
                    }
                    nameLayout.addView(badge)
                }
                row.addView(nameLayout)

                // Usage time
                val time = TextView(requireContext()).apply {
                    val h = app.usageMinutes / 60
                    val m = app.usageMinutes % 60
                    text = if (h > 0) "${h}h ${m}m" else "${m}m"
                    setTextColor(
                        if (app.isDistracting) Color.parseColor("#EF4444")
                        else Color.parseColor("#3B82F6")
                    )
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                }
                row.addView(time)

                layoutAppUsageList.addView(row)
            }
        }

        // Request data load
        viewModel.loadDetailedStats()
        viewModel.loadPerAppUsage()
    }
}
