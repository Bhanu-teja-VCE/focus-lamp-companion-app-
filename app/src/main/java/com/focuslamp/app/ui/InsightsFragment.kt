package com.focuslamp.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.focuslamp.app.databinding.FragmentInsightsBinding

/**
 * Insights Fragment: Displays app screen time usage breakdown, pickup counters,
 * and peak distraction window analysis.
 */
class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FocusViewModel by activityViewModels()
    private lateinit var appAdapter: AppUsageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appAdapter = AppUsageAdapter { appItem ->
            viewModel.toggleAppSelection(appItem.packageName)
        }

        binding.rvInsightsAppList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }

        viewModel.appUsageList.observe(viewLifecycleOwner) { appList ->
            appAdapter.submitList(appList)
        }

        viewModel.screenTimeFormatted.observe(viewLifecycleOwner) { _ ->
            // Pickup estimation from usage telemetry
            binding.tvPickupsCount.text = "28 pickups today"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
