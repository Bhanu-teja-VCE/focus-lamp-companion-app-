package com.focuslamp.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslamp.app.data.local.ChatMessageEntity
import com.focuslamp.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ChatViewModel — Manages chat state, Room message flows, Groq streaming/generation, and configuration changes.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application.applicationContext)

    val messages = repository.getMessagesFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            // 1. Save user message to Room DB
            repository.saveUserMessage(trimmed)

            // 2. Generate AI response with live telemetry context
            val result = repository.generateAiResponse(trimmed)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to generate AI response"
            }

            _isGenerating.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
