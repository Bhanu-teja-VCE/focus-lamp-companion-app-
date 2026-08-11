package com.focuslamp.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.R
import com.focuslamp.app.data.repository.AiRepository
import kotlinx.coroutines.launch

/**
 * AI Coach Fragment: Interactive Groq AI interface for personal focus coaching,
 * real-time usage pattern analysis, and smart study scheduling.
 */
class AiCoachFragment : Fragment() {

    private val aiRepository = AiRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_coach, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvChatLog = view.findViewById<TextView>(R.id.tvChatLog)
        val etUserPrompt = view.findViewById<EditText>(R.id.etUserPrompt)
        val btnSendPrompt = view.findViewById<Button>(R.id.btnSendPrompt)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbLoading)
        val svChatLog = view.findViewById<ScrollView>(R.id.svChatLog)

        val chipAnalyze = view.findViewById<Button>(R.id.chipAnalyze)
        val chipReduce = view.findViewById<Button>(R.id.chipReduce)
        val chipPlan = view.findViewById<Button>(R.id.chipPlan)

        // Observe chat messages
        lifecycleScope.launch {
            aiRepository.chatHistory.collect { messages ->
                val chatText = messages.joinToString("\n\n") { msg ->
                    val reasoningText = if (msg.reasoning != null) "\n💡 *Reasoning*: ${msg.reasoning}" else ""
                    "**${msg.sender}**:\n${msg.text}$reasoningText"
                }
                tvChatLog.text = chatText
                
                // Auto scroll to bottom
                svChatLog.post {
                    svChatLog.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            aiRepository.isLoading.collect { loading ->
                pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
                btnSendPrompt.isEnabled = !loading
            }
        }

        fun sendPrompt(text: String) {
            if (text.isNotBlank()) {
                etUserPrompt.setText("")
                lifecycleScope.launch {
                    aiRepository.sendMessage(requireContext(), text)
                }
            }
        }

        btnSendPrompt.setOnClickListener {
            val userText = etUserPrompt.text.toString().trim()
            sendPrompt(userText)
        }

        chipAnalyze.setOnClickListener {
            sendPrompt("Analyze today's screen time usage and tell me which apps are distracting me the most.")
        }

        chipReduce.setOnClickListener {
            sendPrompt("What custom distraction limits and Focus Lamp rules do you recommend for me today?")
        }

        chipPlan.setOnClickListener {
            sendPrompt("Create a 2-hour deep work study session plan for me with planned focus blocks and break intervals.")
        }
    }
}
