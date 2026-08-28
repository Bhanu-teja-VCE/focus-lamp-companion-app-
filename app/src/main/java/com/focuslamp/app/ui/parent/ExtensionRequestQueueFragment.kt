package com.focuslamp.app.ui.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.R
import com.focuslamp.app.data.repository.FamilyRepository
import kotlinx.coroutines.launch

/**
 * ExtensionRequestQueueFragment — Dedicated Parent Mode screen for reviewing and approving/rejecting
 * child time extension requests.
 */
class ExtensionRequestQueueFragment : Fragment() {

    private val familyRepository = FamilyRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_extension_request_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvQueueDetails = view.findViewById<TextView>(R.id.tvQueueDetails)
        val btnApprove = view.findViewById<Button>(R.id.btnQueueApprove)
        val btnReject = view.findViewById<Button>(R.id.btnQueueReject)

        lifecycleScope.launch {
            familyRepository.extensionRequests.collect { requests ->
                val pending = requests.firstOrNull { it.isApproved == null }
                if (pending != null) {
                    tvQueueDetails.text = "${pending.childName}: Requesting +${pending.requestedMinutes} mins (${pending.reason})"
                    btnApprove.isEnabled = true
                    btnReject.isEnabled = true
                } else {
                    tvQueueDetails.text = "No pending extension requests at this time."
                    btnApprove.isEnabled = false
                    btnReject.isEnabled = false
                }
            }
        }

        btnApprove.setOnClickListener {
            familyRepository.approveExtension("REQ_101")
            Toast.makeText(requireContext(), "✅ Extension Approved! Lamp switched to Amber state.", Toast.LENGTH_SHORT).show()
        }

        btnReject.setOnClickListener {
            familyRepository.rejectExtension("REQ_101")
            Toast.makeText(requireContext(), "Extension Rejected.", Toast.LENGTH_SHORT).show()
        }
    }
}
