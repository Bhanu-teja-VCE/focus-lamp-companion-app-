package com.focuslamp.app.data.ai

import android.content.Context
import com.focuslamp.app.data.repository.FamilyRepository
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.utils.ScheduleManager
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ChatToolHandler — Intercepts and injects live screen-time telemetry, active restriction windows,
 * and pending extension requests into the AI LLM context.
 */
class ChatToolHandler(private val context: Context) {

    private val tracker = ScreenTimeTracker(context)
    private val scheduleManager = ScheduleManager(context)
    private val settingsManager = SettingsManager(context)
    private val distractingManager = DistractingAppsManager(context)
    private val familyRepository = FamilyRepository()

    /**
     * Builds a comprehensive System Prompt with live phone usage telemetry and active schedules.
     */
    suspend fun getLiveSystemPrompt(): String = withContext(Dispatchers.IO) {
        val totalMins = tracker.getTotalScreenTimeToday()
        val totalHours = totalMins / 60
        val totalRemMins = totalMins % 60

        val distractingApps = distractingManager.getDistractingApps()
        val distractionMins = tracker.getDistractionTimeOnly(distractingApps)
        val limitMins = settingsManager.timeLimitMinutes

        val topDistractions = tracker.getPerAppUsageToday(distractingApps)
            .sortedByDescending { it.usageTimeMinutes }
            .take(5)
            .joinToString(", ") { "${it.appName}: ${it.usageTimeMinutes}m" }
            .ifEmpty { "None recorded today" }

        val activeSchedule = scheduleManager.getActiveRestrictionReason() ?: "None (All apps permitted)"
        val isSchoolMode = scheduleManager.isSchoolModeEnabled
        val isBedtimeMode = scheduleManager.isBedtimeModeEnabled

        val pendingRequestsCount = familyRepository.extensionRequests.value.count { it.isApproved == null }

        """
        You are the official Focus Lamp AI Assistant & Screen Time Coach.
        Your goal is to help the user manage their phone usage, understand their focus habits, and navigate app features.
        
        [LIVE TELEMETRY & SYSTEM CONTEXT]:
        - Total Screen Time Today: ${totalHours}h ${totalRemMins}m (${totalMins} total minutes)
        - Tracked Distraction Usage: ${distractionMins} minutes (Daily Limit: ${limitMins} minutes)
        - Top Distraction Apps Today: $topDistractions
        - Currently Active Restriction Window: $activeSchedule
        - School Hours Mode Enabled: $isSchoolMode (Mon-Fri 8:00 AM - 3:00 PM)
        - Bedtime Mode Enabled: $isBedtimeMode (Daily 9:00 PM - 6:00 AM)
        - Pending Parent Extension Requests: $pendingRequestsCount pending request(s)
        - ESP32 Focus Lamp Connection IP: ${settingsManager.espIp}
        
        [INSTRUCTIONS]:
        - Be encouraging, empathetic, direct, and concise (2-4 sentences max per response unless requested otherwise).
        - Use the live telemetry data above to answer questions concretely (e.g., if asked "How much time did I use today?", state the exact hours and top distracting apps).
        - Keep answers strictly focused on digital wellbeing, phone limits, schedules, and Focus Lamp hardware features.
        """.trimIndent()
    }
}
