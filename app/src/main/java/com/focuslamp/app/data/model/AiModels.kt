package com.focuslamp.app.data.model

/**
 * Data models for AI-Powered Personalization & Intelligence Features.
 */

enum class MessageTone {
    CALM, ENCOURAGEMENT, FIRM, MOTIVATIONAL, ACADEMIC, PLAYFUL
}

data class CoachMessage(
    val id: String,
    val sender: String, // "AI Coach" or "User"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reasoning: String? = null
)

data class AdaptiveLimitRecommendation(
    val currentLimitMinutes: Long,
    val recommendedLimitMinutes: Long,
    val percentageReduction: Int = 10,
    val reasoning: String
)

data class DistractionSignal(
    val triggerPattern: String, // e.g. "Frequent app switching", "Late-night usage spike"
    val severity: String,      // "GENTLE", "MODERATE", "HIGH"
    val recommendedLampState: LampState = LampState.SLOW_PULSE
)

data class SessionPlan(
    val goalText: String,
    val focusBlockMinutes: Int,
    val breakMinutes: Int,
    val recommendedLampState: LampState = LampState.PURPLE,
    val structuredTasks: List<String>
)

data class ReflectionPrompt(
    val id: String,
    val question: String,
    val options: List<String>
)

data class NlScheduleParseResult(
    val rawText: String,
    val targetApp: String,
    val startTime: String,
    val endTime: String,
    val isEducationalAllowed: Boolean
)
