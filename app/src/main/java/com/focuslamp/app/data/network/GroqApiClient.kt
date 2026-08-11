package com.focuslamp.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GroqApiClient — Connects the Focus Lamp AI Coach to the ultra-fast Groq LLM Cloud API
 * using models like llama-3.3-70b-versatile.
 */
class GroqApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL_NAME = "llama-3.3-70b-versatile"
    }

    /**
     * Sends a chat completion request to the Groq API.
     * @param apiKey User-provided Groq API key
     * @param systemPrompt Instructions & live screen time context data for the AI Coach
     * @param conversationHistory List of prior messages (role, content)
     * @param userMessage Latest user query
     */
    suspend fun generateResponse(
        apiKey: String,
        systemPrompt: String,
        conversationHistory: List<Pair<String, String>>, // role ("user" or "assistant") to content
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("No Groq API key configured. Please set your key in Settings."))
            }

            val messagesArray = JSONArray()

            // System prompt with live screen time telemetry
            val systemObj = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            messagesArray.put(systemObj)

            // Conversation history
            for ((role, content) in conversationHistory) {
                val msgObj = JSONObject().apply {
                    put("role", role)
                    put("content", content)
                }
                messagesArray.put(msgObj)
            }

            // Latest user message
            val userObj = JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }
            messagesArray.put(userObj)

            val requestJson = JSONObject().apply {
                put("model", MODEL_NAME)
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_tokens", 512)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
                val errorMsg = errorJson?.optJSONObject("error")?.optString("message") 
                    ?: "HTTP ${response.code}: ${response.message}"
                return@withContext Result.failure(Exception("Groq API Error: $errorMsg"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val text = message.getString("content").trim()
                Result.success(text)
            } else {
                Result.failure(Exception("Groq API returned an empty response."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Quickly validates if a Groq API key works by sending a tiny test request.
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val testResult = generateResponse(
            apiKey = apiKey,
            systemPrompt = "You are a test assistant.",
            conversationHistory = emptyList(),
            userMessage = "Ping"
        )
        if (testResult.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(testResult.exceptionOrNull() ?: Exception("Invalid API key"))
        }
    }
}
