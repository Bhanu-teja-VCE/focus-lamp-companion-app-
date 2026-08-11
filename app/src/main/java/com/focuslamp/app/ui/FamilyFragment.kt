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
import com.focuslamp.app.utils.ParentPinManager
import com.focuslamp.app.utils.ScheduleManager
import kotlinx.coroutines.launch

/**
 * Family Fragment: Manages Parent-Child family account security (Parent PIN Lock),
 * Scheduled Restriction Windows (School Hours & Bedtime Mode), and Remote Extension Requests.
 */
class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val familyRepository = FamilyRepository()
    private lateinit var pinManager: ParentPinManager
    private lateinit var scheduleManager: ScheduleManager

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

        pinManager = ParentPinManager(requireContext())
        scheduleManager = ScheduleManager(requireContext())

        // 1. Refresh Active Restriction Status Banner
        refreshRestrictionStatus()

        // 2. Setup Schedule Switches
        binding.switchSchoolMode.isChecked = scheduleManager.isSchoolModeEnabled
        binding.switchBedtimeMode.isChecked = scheduleManager.isBedtimeModeEnabled

        binding.switchSchoolMode.setOnCheckedChangeListener { _, isChecked ->
            if (verifyPinAction("toggle School Mode")) {
                scheduleManager.isSchoolModeEnabled = isChecked
                refreshRestrictionStatus()
                Toast.makeText(requireContext(), "School Hours Mode ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchSchoolMode.isChecked = !isChecked // Revert switch if PIN wrong
            }
        }

        binding.switchBedtimeMode.setOnCheckedChangeListener { _, isChecked ->
            if (verifyPinAction("toggle Bedtime Mode")) {
                scheduleManager.isBedtimeModeEnabled = isChecked
                refreshRestrictionStatus()
                Toast.makeText(requireContext(), "Bedtime Mode ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchBedtimeMode.isChecked = !isChecked // Revert switch if PIN wrong
            }
        }

        // 3. Setup Parent PIN Lock UI
        updatePinUiState()

        binding.btnSavePin.setOnClickListener {
            val enteredPin = binding.etParentPin.text.toString().trim()
            if (enteredPin.length != 4) {
                binding.etParentPin.error = "PIN must be 4 digits"
                return@setOnClickListener
            }

            if (pinManager.isPinSet()) {
                // Verify existing PIN before changing
                if (pinManager.verifyPin(enteredPin)) {
                    pinManager.clearPin(enteredPin)
                    binding.etParentPin.setText("")
                    updatePinUiState()
                    Toast.makeText(requireContext(), "Parent PIN cleared. Set a new PIN below.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "❌ Incorrect current PIN!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Set new PIN
                if (pinManager.setPin(enteredPin)) {
                    binding.etParentPin.setText("")
                    updatePinUiState()
                    Toast.makeText(requireContext(), "🔒 Parent PIN set successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Observe Extension Requests
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
            if (verifyPinAction("approve extension")) {
                familyRepository.approveExtension("REQ_101")
                Toast.makeText(requireContext(), "✅ Extension Approved! Lamp switched to Amber state.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRejectExtension.setOnClickListener {
            if (verifyPinAction("reject extension")) {
                familyRepository.rejectExtension("REQ_101")
                Toast.makeText(requireContext(), "Extension Rejected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshRestrictionStatus() {
        val activeReason = scheduleManager.getActiveRestrictionReason()
        if (activeReason != null) {
            binding.tvScheduleStatus.text = activeReason
            binding.tvScheduleStatus.setTextColor(resources.getColor(R.color.danger_red, null))
        } else {
            binding.tvScheduleStatus.text = "🟢 No Active Scheduled Restriction"
            binding.tvScheduleStatus.setTextColor(resources.getColor(R.color.primary_green, null))
        }
    }

    private fun updatePinUiState() {
        if (pinManager.isPinSet()) {
            binding.tvPinHint.text = "🔒 Parent PIN is ACTIVE. Controls are locked."
            binding.btnSavePin.text = "Clear / Change PIN"
        } else {
            binding.tvPinHint.text = "Set a 4-digit PIN to lock parent controls and prevent unauthorized changes."
            binding.btnSavePin.text = "Set PIN"
        }
    }

    private fun verifyPinAction(actionName: String): Boolean {
        if (!pinManager.isPinSet()) return true // Unlocked if no PIN set

        val enteredPin = binding.etParentPin.text.toString().trim()
        if (enteredPin.length != 4) {
            Toast.makeText(requireContext(), "🔒 Please enter your 4-digit Parent PIN to $actionName.", Toast.LENGTH_SHORT).show()
            return false
        }

        val isValid = pinManager.verifyPin(enteredPin)
        if (!isValid) {
            Toast.makeText(requireContext(), "❌ Incorrect Parent PIN!", Toast.LENGTH_SHORT).show()
        }
        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
