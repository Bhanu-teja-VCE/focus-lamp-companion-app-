package com.focuslamp.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.focuslamp.app.R

/**
 * Active focus session screen — shows the countdown timer and stop button.
 * Background color changes based on session state.
 */
class SessionFragment : Fragment(R.layout.fragment_session) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        val tvTimer = view.findViewById<TextView>(R.id.tvTimer)
        val btnStop = view.findViewById<Button>(R.id.btnStopSession)
        val rootLayout = view.findViewById<View>(R.id.layoutSessionRoot)
        val tvSessionLabel = view.findViewById<TextView>(R.id.tvSessionLabel)

        // Observe timer
        viewModel.timerText.observe(viewLifecycleOwner) { time ->
            tvTimer.text = time
        }

        // Observe session state
        viewModel.isSessionActive.observe(viewLifecycleOwner) { isActive ->
            if (isActive) {
                rootLayout.setBackgroundColor(resources.getColor(R.color.focus_green, null))
                tvSessionLabel.text = "Stay Focused! 🧘"
                btnStop.text = "Stop Session"
            } else {
                rootLayout.setBackgroundColor(resources.getColor(R.color.focus_ended, null))
                tvSessionLabel.text = "Session Complete! 🎉"
                btnStop.text = "Back to Home"
            }
        }

        btnStop.setOnClickListener {
            if (viewModel.isSessionActive.value == true) {
                viewModel.stopSession()
            }
            findNavController().navigate(R.id.action_sessionFragment_to_homeFragment)
        }
    }
}
