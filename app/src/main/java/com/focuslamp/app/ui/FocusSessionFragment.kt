package com.focuslamp.app.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.focuslamp.app.data.model.LampState
import com.focuslamp.app.databinding.FragmentFocusSessionBinding

/**
 * Focus Rituals Fragment: Manages Deep Work sessions, phone face-down triggers,
 * purple lighting state, and victory animations.
 */
class FocusSessionFragment : Fragment() {

    private var _binding: FragmentFocusSessionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FocusViewModel by activityViewModels()
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartRitual.setOnClickListener {
            if (isTimerRunning) {
                stopRitual()
            } else {
                startRitual()
            }
        }
    }

    private fun startRitual() {
        isTimerRunning = true
        binding.btnStartRitual.text = "End Deep-Work Session"
        binding.tvRitualStatus.text = "Ritual Active: Purple State (Phone Face-Down)"
        
        // Sync ESP32 Lamp to PURPLE state
        viewModel.setTargetState(LampState.PURPLE)

        timer = object : CountDownTimer(25 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 1000 / 60
                val secs = (millisUntilFinished / 1000) % 60
                binding.tvTimerDisplay.text = String.format("%02d:%02d", mins, secs)
            }

            override fun onFinish() {
                binding.tvTimerDisplay.text = "25:00"
                binding.tvRitualStatus.text = "Session Complete! Victory Light Triggered 🎉"
                viewModel.setTargetState(LampState.GREEN)
                Toast.makeText(requireContext(), "Deep Work Session Complete!", Toast.LENGTH_LONG).show()
                stopRitual()
            }
        }.start()
    }

    private fun stopRitual() {
        timer?.cancel()
        isTimerRunning = false
        binding.btnStartRitual.text = "Start Deep-Work Session"
        binding.tvRitualStatus.text = "Ritual: Ready for Deep Work"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }
}
