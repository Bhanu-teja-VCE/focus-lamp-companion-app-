package com.focuslamp.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.data.repository.AiRepository
import com.focuslamp.app.databinding.FragmentAiCoachBinding
import kotlinx.coroutines.launch

/**
 * AI Coach Fragment: Interactive AI interface for personal focus coaching,
 * post-session reflection, and natural language schedule parsing.
 */
class AiCoachFragment : Fragment() {

    private var _binding: FragmentAiCoachBinding? = null
    private val binding get() = _binding!!

    private val aiRepository = AiRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            aiRepository.chatHistory.collect { messages ->
                val chatText = messages.joinToString("\n\n") { msg ->
                    val reasoningText = if (msg.reasoning != null) " (Reasoning: ${msg.reasoning})" else ""
                    "${msg.sender}: ${msg.text}$reasoningText"
                }
                binding.tvChatLog.text = chatText
            }
        }

        binding.btnSendPrompt.setOnClickListener {
            val userText = binding.etUserPrompt.text.toString().trim()
            if (userText.isNotEmpty()) {
                aiRepository.sendMessage(userText)
                binding.etUserPrompt.setText("")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
