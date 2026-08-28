package com.focuslamp.app.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AiChatActivity — Dedicated full-screen AI Chatbot container.
 * Powered by Room DB message persistence, Groq Llama 3.3 LLM completions with live screen-time telemetry,
 * prompt suggestion chips, and clean back navigation to Settings.
 */
class AiChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        adapter = ChatAdapter()

        val btnBack = findViewById<FrameLayout>(R.id.btnBackToSettings)
        val btnClear = findViewById<FrameLayout>(R.id.btnClearChatHistory)
        val rvMessages = findViewById<RecyclerView>(R.id.rvChatMessages)
        val containerTyping = findViewById<LinearLayout>(R.id.containerTypingIndicator)
        val containerChips = findViewById<HorizontalScrollView>(R.id.containerSuggestedChips)
        val etInput = findViewById<EditText>(R.id.etChatMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSendMessage)

        val chip1 = findViewById<TextView>(R.id.chipPrompt1)
        val chip2 = findViewById<TextView>(R.id.chipPrompt2)
        val chip3 = findViewById<TextView>(R.id.chipPrompt3)

        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = adapter

        // System back button returns cleanly to Settings
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        btnBack.setOnClickListener { finish() }

        // Clear Conversation Overflow Option
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🗑️ Clear Conversation History")
                .setMessage("Are you sure you want to delete all messages in this conversation thread?")
                .setPositiveButton("Clear All") { _, _ ->
                    viewModel.clearHistory()
                    Toast.makeText(this, "Chat history cleared.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Disable send button initially if text is empty
        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = s.toString().trim().isNotEmpty()
                btnSend.isEnabled = hasText && !viewModel.isGenerating.value
                btnSend.alpha = if (btnSend.isEnabled) 1.0f else 0.5f
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                etInput.setText("")
            }
        }

        // Prompt Chips Click Listeners
        val chipClickListener = View.OnClickListener { v ->
            if (v is TextView) {
                val promptText = v.text.toString()
                viewModel.sendMessage(promptText)
            }
        }
        chip1.setOnClickListener(chipClickListener)
        chip2.setOnClickListener(chipClickListener)
        chip3.setOnClickListener(chipClickListener)

        // Observe Messages from Room DB
        lifecycleScope.launch {
            viewModel.messages.collectLatest { list ->
                adapter.submitList(list) {
                    if (list.isNotEmpty()) {
                        rvMessages.smoothScrollToPosition(list.size - 1)
                        containerChips.visibility = View.GONE
                    } else {
                        containerChips.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Observe Generating / Typing State
        lifecycleScope.launch {
            viewModel.isGenerating.collectLatest { isGenerating ->
                containerTyping.visibility = if (isGenerating) View.VISIBLE else View.GONE
                val hasText = etInput.text.toString().trim().isNotEmpty()
                btnSend.isEnabled = hasText && !isGenerating
                btnSend.alpha = if (btnSend.isEnabled) 1.0f else 0.5f
            }
        }

        // Observe Error Messages
        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { err ->
                if (err != null) {
                    Toast.makeText(this@AiChatActivity, err, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }
}
