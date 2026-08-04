package com.focuslamp.app.data.model

/**
 * Extended Physical State Lighting Language for Focus Lamp.
 * Maps physical RGB colors and pulsing animations to user focus states.
 */
enum class LampState(
    val key: String,
    val displayName: String,
    val hexColor: String,
    val description: String,
    val endpoint: String
) {
    GREEN("focus", "Focus / Safe", "#4CAF50", "Within 0–80% of daily limit", "focus"),
    WHITE("warning", "Approaching Limit", "#FFFFFF", "Approaching daily limit (80–99%)", "warning"),
    RED("distraction", "Limit Reached", "#F44336", "Screen time limit exceeded", "distraction"),
    BLUE("rest", "Rest & Recovery", "#2196F3", "Break interval active", "blue"),
    PURPLE("deep_work", "Deep-Work Session", "#9C27B0", "Active study ritual in progress", "purple"),
    AMBER("extension", "Extension Granted", "#FF9800", "Parent approved extra time", "amber"),
    SLOW_PULSE("nudge", "Mindful Nudge", "#00BCD4", "Pre-emptive distraction warning", "pulse_slow"),
    FAST_PULSE("alert", "Restricted Alert", "#E91E63", "Distraction detected during focus window", "pulse_fast"),
    IDLE("idle", "Idle / Ready", "#4CAF50", "Lamp connected and ready", "idle");

    companion object {
        fun fromKey(key: String): LampState {
            return values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GREEN
        }
    }
}
