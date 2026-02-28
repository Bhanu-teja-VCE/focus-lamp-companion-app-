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
        
        // Hide the app list since the OS block prevents granular data
        rvAppsUsage.visibility = View.GONE

        // Hide the chart since weekly tracking requires app-specific data we no longer collect
        chartContainer.visibility = View.GONE

        // Request data load
        viewModel.loadDetailedStats()
    }

}
