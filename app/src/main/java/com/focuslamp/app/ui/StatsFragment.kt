package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

            for (app in appList) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
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
