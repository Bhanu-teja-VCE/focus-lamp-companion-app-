package com.focuslamp.app.data.repository

import android.content.Context
import com.focuslamp.app.data.ai.ChatToolHandler
import com.focuslamp.app.data.local.AppDatabase
import com.focuslamp.app.data.local.ChatMessageEntity
import com.focuslamp.app.data.network.GroqApiClient
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * ChatRepository — Manages Room DB chat history persistence and Groq API LLM completions with live telemetry.
 */
class ChatRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).chatMessageDao()
    private val groqClient = GroqApiClient()
    private val toolHandler = ChatToolHandler(context)
    private val settingsManager = SettingsManager(context)

    fun getMessagesFlow(): Flow<List<ChatMessageEntity>> = dao.getAllMessagesFlow()

    suspend fun saveUserMessage(text: String): Long = withContext(Dispatchers.IO) {
        dao.insertMessage(ChatMessageEntity(sender = "user", text = text))
    }

    suspend fun saveAiMessage(text: String): Long = withContext(Dispatchers.IO) {
        dao.insertMessage(ChatMessageEntity(sender = "ai", text = text))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAllMessages()
    }

    suspend fun generateAiResponse(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.groqApiKey.ifBlank {
            return@withContext Result.failure(Exception("No Groq API key configured. Please set your key in Settings or enter a valid key."))
        }

        val history = dao.getAllMessages()
            .takeLast(10)
            .map { Pair(if (it.sender == "user") "user" else "assistant", it.text) }

        val systemPrompt = toolHandler.getLiveSystemPrompt()

        val apiResult = groqClient.generateResponse(
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            conversationHistory = history,
            userMessage = userMessage
        )

        if (apiResult.isSuccess) {
            val replyText = apiResult.getOrThrow()
            saveAiMessage(replyText)
            Result.success(replyText)
        } else {
            Result.failure(apiResult.exceptionOrNull() ?: Exception("Failed to generate AI response"))
        }
    }
}
