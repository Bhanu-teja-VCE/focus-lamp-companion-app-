package com.focuslamp.app.data.repository

import android.content.Context
import com.focuslamp.app.data.model.*
import com.focuslamp.app.data.network.GroqApiClient
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository managing AI Coaching interactions backed by Groq LLM Cloud API (Llama-3.3-70b)
 * and Android UsageStats telemetry.
 */
class AiRepository {

    private val groqApiClient = GroqApiClient()

    private val _chatHistory = MutableStateFlow<List<CoachMessage>>(
        listOf(
            CoachMessage(
                id = "MSG_WELCOME",
                sender = "AI Coach",
                text = "Welcome to Focus Lamp AI! I am your personal focus coach connected to your hardware lamp and screen time telemetry. Ask me anything about your focus patterns or ask for a custom study plan!",
                reasoning = "Focus Lamp AI Engine Ready."
            )
        )
    )
    val chatHistory: StateFlow<List<CoachMessage>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Sends a message to the AI Coach, automatically injecting live screen time context.
     */
    suspend fun sendMessage(context: Context, userText: String) = withContext(Dispatchers.Default) {
        val userMsg = CoachMessage(
            id = "USER_${System.currentTimeMillis()}",
            sender = "You",
            text = userText
        )
        _chatHistory.value = _chatHistory.value + userMsg
        _isLoading.value = true

        val settings = SettingsManager(context)
        val apiKey = settings.groqApiKey

        if (apiKey.isNotBlank()) {
            // Build live telemetry context
            val systemPrompt = buildSystemPromptWithTelemetry(context, settings)

            // Convert conversation history for Groq (last 6 messages max)
            val history = _chatHistory.value.takeLast(6).dropLast(1).map { msg ->
                val role = if (msg.sender == "You") "user" else "assistant"
                Pair(role, msg.text)
            }

            val result = groqApiClient.generateResponse(
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                conversationHistory = history,
                userMessage = userText
            )

            _isLoading.value = false

            result.onSuccess { aiText ->
                val aiMsg = CoachMessage(
                    id = "AI_${System.currentTimeMillis()}",
                    sender = "AI Coach",
                    text = aiText,
                    reasoning = "Generated via Groq Llama-3.3-70b with live screen time context."
                )
                _chatHistory.value = _chatHistory.value + aiMsg
            }.onFailure { error ->
                val errorMsg = CoachMessage(
                    id = "AI_ERR_${System.currentTimeMillis()}",
                    sender = "AI Coach",
                    text = "⚠️ Unable to reach Groq AI API: ${error.localizedMessage}\n\nFalling back to offline mode.",
                    reasoning = "Groq API error. Check API key in Settings."
                )
                _chatHistory.value = _chatHistory.value + errorMsg
            }
        } else {
            _isLoading.value = false
            // Fallback response when no Groq API key is set
            val fallbackMsg = generateFallbackResponse(userText)
            _chatHistory.value = _chatHistory.value + fallbackMsg
        }
    }

    /**
     * Builds a comprehensive system prompt containing real-time screen time telemetry.
     */
    private fun buildSystemPromptWithTelemetry(context: Context, settings: SettingsManager): String {
        val tracker = ScreenTimeTracker(context)
        val distractingManager = DistractingAppsManager(context)

        val totalScreenTime = tracker.getTotalScreenTimeToday()
        val distractingSet = distractingManager.getDistractingApps()
        val distractionTime = tracker.getDistractionTimeOnly(distractingSet)
        val timeLimit = settings.timeLimitMinutes
        val currentApp = tracker.getCurrentForegroundApp() ?: "Home Screen / Launcher"

        val perAppList = tracker.getPerAppUsageToday(distractingSet).take(6)
        val appUsageSummary = if (perAppList.isNotEmpty()) {
            perAppList.joinToString("\n") { app ->
                "  - ${app.appName} (${app.packageName}): ${app.usageMinutes} mins ${if (app.isDistracting) "[DISTRACTING]" else ""}"
            }
        } else {
            "  - No app usage recorded yet today."
        }

        return """
You are Focus Coach, an empathetic, data-driven AI focus assistant integrated into the physical Focus Lamp IoT hardware project.
You have direct, real-time access to the user's phone usage telemetry provided below.

=== REAL-TIME TELEMETRY DATA TODAY ===
- Total Phone Screen Time Today: $totalScreenTime minutes
- Distraction App Screen Time: $distractionTime minutes (Daily Limit: $timeLimit minutes)
- Currently Active App: $currentApp
- ESP32 Focus Lamp IP: ${settings.espIp}
- Top App Usage Today:
$appUsageSummary

=== YOUR INSTRUCTIONS ===
1. Use the real telemetry data above to give specific, accurate advice. Always cite actual app names and minutes when relevant.
2. Be encouraging, warm, and constructive — NEVER shaming or punitive.
3. Keep responses concise (under 150 words) with clear bullet points.
4. When suggesting study blocks or limits, recommend realistic, gradual adjustments (10% stepwise target).
5. If the user asks about the physical lamp, explain how its LEDs signal focus (Green = Focus, White = Warning, Red = Limit Exceeded).
""".trimIndent()
    }

    /**
     * Rule-based fallback when Groq API key is not configured.
     */
    private fun generateFallbackResponse(userText: String): CoachMessage {
        val lower = userText.lowercase()
        val hintText = "\n\n💡 *Tip: Enter your Groq API key in Settings to get real-time AI responses!*"
        
        return when {
            "why" in lower && "focus" in lower -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "Your focus was affected today because phone usage increased during your afternoon focus window.$hintText",
                reasoning = "Offline rule engine."
            )
            "tomorrow" in lower || "limit" in lower -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "I recommend an adaptive screen time limit reduction of 10% for tomorrow for sustainable habit formation.$hintText",
                reasoning = "Offline rule engine."
            )
            else -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "Great goal! Placing your phone face-down will sync your physical Focus Lamp to Green state for deep work.$hintText",
                reasoning = "Offline rule engine."
            )
        }
    }

    fun getAdaptiveLimitRecommendation(currentLimit: Long): AdaptiveLimitRecommendation {
        val target = (currentLimit * 0.9).toLong().coerceAtLeast(15)
        return AdaptiveLimitRecommendation(
            currentLimitMinutes = currentLimit,
            recommendedLimitMinutes = target,
            percentageReduction = 10,
            reasoning = "Gradual 10% weekly reduction promotes sustainable habit formation without sudden withdrawal."
        )
    }

    fun parseNaturalLanguageSchedule(rawText: String): NlScheduleParseResult {
        return NlScheduleParseResult(
            rawText = rawText,
            targetApp = "Instagram & YouTube",
            startTime = "18:00",
            endTime = "20:00",
            isEducationalAllowed = true
        )
    }
}
