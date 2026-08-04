package com.focuslamp.app.data.repository

import com.focuslamp.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing AI Coaching chats, adaptive limit recommendations, and natural language rules.
 */
class AiRepository {

    private val _chatHistory = MutableStateFlow<List<CoachMessage>>(
        listOf(
            CoachMessage(
                id = "MSG_01",
                sender = "AI Coach",
                text = "Welcome to Focus Lamp AI! I am analyzing your study sessions. Your peak focus window is between 6 PM and 8 PM.",
                reasoning = "Based on 14 days of UsageStats telemetry and zero phone pickups between 18:00 and 20:00."
            )
        )
    )
    val chatHistory: StateFlow<List<CoachMessage>> = _chatHistory.asStateFlow()

    fun sendMessage(userText: String) {
        val userMsg = CoachMessage(
            id = "USER_${System.currentTimeMillis()}",
            sender = "You",
            text = userText
        )
        val aiResponse = generateAiResponse(userText)
        _chatHistory.value = _chatHistory.value + userMsg + aiResponse
    }

    private fun generateAiResponse(userText: String): CoachMessage {
        val lower = userText.lowercase()
        return when {
            "why" in lower && "focus" in lower -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "Your focus was lower today because social media pickups increased by 35% around 4:00 PM right after your study session.",
                reasoning = "Telemetry analysis: 12 rapid pickups recorded between 16:00 and 16:45."
            )
            "tomorrow" in lower || "limit" in lower -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "I recommend an adaptive screen time limit of 45 minutes for tomorrow — a gradual 10% reduction from today's 50-minute threshold.",
                reasoning = "Adaptive Limits algorithm: 10% weekly stepwise target."
            )
            "block" in lower || "schedule" in lower -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "I've parsed your rule: Block Instagram & YouTube from 6 PM to 8 PM on weekdays. Educational videos remain allowed.",
                reasoning = "Natural Language Schedule Parser parsed 18:00–20:00 window."
            )
            else -> CoachMessage(
                id = "AI_${System.currentTimeMillis()}",
                sender = "AI Coach",
                text = "Great goal! Setting up your Deep-Work session. I will switch your Focus Lamp to Purple state once you place your phone face down.",
                reasoning = "Ritual Engine activated."
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
