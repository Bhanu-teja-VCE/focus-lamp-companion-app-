package com.focuslamp.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private lateinit var viewModel: FocusViewModel
    private val adapter = AppUsageAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        val tvTotalTimeToday = view.findViewById<TextView>(R.id.tvTotalTimeToday)
        val tvCurrentDate = view.findViewById<TextView>(R.id.tvCurrentDate)
        val chartContainer = view.findViewById<LinearLayout>(R.id.chartContainer)
        val rvAppsUsage = view.findViewById<RecyclerView>(R.id.rvAppsUsage)

        rvAppsUsage.layoutManager = LinearLayoutManager(requireContext())
        rvAppsUsage.adapter = adapter

        // Set current date string
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        tvCurrentDate.text = sdf.format(Calendar.getInstance().time)

        // Observe granular app usage
        viewModel.detailedAppUsage.observe(viewLifecycleOwner) { usageList ->
            adapter.submitList(usageList)
            
            // Calculate total time
            var totalMillis = 0L
            usageList.forEach { totalMillis += it.usageMillis }
            
            val minutes = totalMillis / 1000 / 60
            val hours = minutes / 60
            val remMins = minutes % 60
            
            if (hours > 0) {
                tvTotalTimeToday.text = "${hours} hr, ${remMins} min"
            } else {
                tvTotalTimeToday.text = "0 hr, $minutes min"
            }
        }

        // Observe weekly overall usage for the bar chart
        viewModel.weeklyUsage.observe(viewLifecycleOwner) { weeklyMins ->
            if (weeklyMins.size == 7) {
                buildBarChart(chartContainer, weeklyMins)
            }
        }

        // Request data load
        viewModel.loadDetailedStats()
    }

    private fun buildBarChart(container: LinearLayout, weeklyMins: List<Long>) {
        container.removeAllViews()
        container.weightSum = 7f

        val maxMins = weeklyMins.maxOrNull() ?: 1L
        // Avoid division by zero, set safe minimum scale
        val scaleMax = if (maxMins < 60) 60L else maxMins 

        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Data is 6-days ago to today
        val daysLabels = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            daysLabels.add(sdf.format(cal.time))
        }

        for (i in 0 until 7) {
            val isToday = (i == 6)
            val mins = weeklyMins[i]
            
            val columnLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, 
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    1f
                )
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }

            // The invisible spacer area
            val emptyWeight = scaleMax - mins
            val spacer = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    0,
                    emptyWeight.toFloat().coerceAtLeast(0f)
                )
            }

            // The visible Bar
            val barColor = if (isToday) ContextCompat.getColor(requireContext(), R.color.primary) 
                           else ContextCompat.getColor(requireContext(), R.color.surface_border) // Lighter blue for past days? No, standard in screenshot is blue and light blue. Let's make today bright blue, others dull blue.
            val barBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isToday) ContextCompat.getColor(requireContext(), R.color.primary) 
                           else ContextCompat.getColor(requireContext(), R.color.text_muted))
                cornerRadius = 8f
            }

            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.displayMetrics.density.toInt() * 32, // 32dp width
                    0, 
                    mins.toFloat().coerceAtLeast(1f)
                )
                background = barBackground
            }

            // Label (Sun, Mon, Tue)
            val label = TextView(requireContext()).apply {
                text = daysLabels[i]
                textSize = 10f
                setTextColor(if (isToday) ContextCompat.getColor(requireContext(), R.color.text_white) else ContextCompat.getColor(requireContext(), R.color.text_secondary))
                setPadding(0, 8, 0, 0)
                gravity = android.view.Gravity.CENTER
            }

            columnLayout.addView(spacer)
            columnLayout.addView(bar)
            columnLayout.addView(label)
            
            container.addView(columnLayout)
        }
    }
}
