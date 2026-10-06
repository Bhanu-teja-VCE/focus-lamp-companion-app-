package com.focuslamp.app.data.ai

import com.focuslamp.app.data.local.ChatMessageDao
import com.focuslamp.app.data.local.ChatMessageEntity
import com.focuslamp.app.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Chat history in Room plus Groq completions.
 *
 * Fixed: the old version saved the user's message, then read history (which now included it)
 * and appended the same message again, so the model saw every question twice.
 */
class ChatRepository(
    private val dao: ChatMessageDao,
    private val settings: SettingsManager,
    private val context: CoachContext,
    private val groq: GroqApiClient = GroqApiClient()
) {
    fun messages(): Flow<List<ChatMessageEntity>> = dao.getAllMessagesFlow()

    suspend fun send(text: String, screen: String?): Result<String> = withContext(Dispatchers.IO) {
        dao.insertMessage(ChatMessageEntity(sender = "user", text = text))
        val history = dao.latest(12).reversed().map { (if (it.sender == "user") "user" else "assistant") to it.text }
        val result = groq.complete(settings.groqApiKey, context.systemPrompt(screen), history)
        result.onSuccess { dao.insertMessage(ChatMessageEntity(sender = "ai", text = it)) }
        result
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clearAllMessages() }
}
