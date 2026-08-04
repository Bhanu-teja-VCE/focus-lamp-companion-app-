package com.focuslamp.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.data.repository.FamilyRepository
import com.focuslamp.app.databinding.FragmentFamilyBinding
import kotlinx.coroutines.launch

/**
 * Family Fragment: Manages Parent-Child family account pairing, paired devices grid,
 * remote extension request queue, and family agreement rules.
 */
class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val familyRepository = FamilyRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            familyRepository.extensionRequests.collect { requests ->
                val pending = requests.firstOrNull { it.isApproved == null }
                if (pending != null) {
                    binding.tvRequestDetails.text = "${pending.childName}: Requesting +${pending.requestedMinutes} mins (${pending.reason})"
                    binding.btnApproveExtension.isEnabled = true
                    binding.btnRejectExtension.isEnabled = true
                } else {
                    binding.tvRequestDetails.text = "No pending extension requests."
                    binding.btnApproveExtension.isEnabled = false
                    binding.btnRejectExtension.isEnabled = false
                }
            }
        }

        binding.btnApproveExtension.setOnClickListener {
            familyRepository.approveExtension("REQ_101")
            Toast.makeText(requireContext(), "Extension Approved! Lamp switched to Amber state.", Toast.LENGTH_SHORT).show()
        }

        binding.btnRejectExtension.setOnClickListener {
            familyRepository.rejectExtension("REQ_101")
            Toast.makeText(requireContext(), "Extension Rejected.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
