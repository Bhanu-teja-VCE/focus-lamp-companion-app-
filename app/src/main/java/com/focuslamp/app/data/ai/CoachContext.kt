package com.focuslamp.app.data.ai

import com.focuslamp.app.data.focus.FocusTimer
import com.focuslamp.app.data.lamp.LampRepository
import com.focuslamp.app.data.rules.RulesStore
import com.focuslamp.app.data.tracking.AppInfoCache
import com.focuslamp.app.data.tracking.UsageRepository
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Format
import com.focuslamp.app.util.SettingsManager

/**
 * Builds the coach's system prompt from the same snapshot the rest of the app shows,
 * so the coach never quotes different numbers than the Today screen.
 */
class CoachContext(
    private val usage: UsageRepository,
    private val rules: RulesStore,
    private val settings: SettingsManager,
    private val lamp: LampRepository,
    private val focus: FocusTimer,
    private val appInfo: AppInfoCache
) {
    suspend fun systemPrompt(screen: String?): String {
        val s = usage.refresh()
        val budget = settings.timeLimitMinutes
        val top = s.apps.take(8).joinToString("\n") {
            "  - ${it.label}: ${Format.durationWords(it.ms)}${if (it.distracting) " (marked distracting)" else ""}"
        }.ifEmpty { "  - Nothing yet today" }
        val selfLimits = rules.selfLimits().entries.joinToString(", ") { "${appInfo.label(it.key)} ${it.value} min" }.ifEmpty { "none" }
        val parentLimits = rules.parentLimits().entries.joinToString(", ") { "${appInfo.label(it.key)} ${it.value} min" }.ifEmpty { "none" }
        val schedules = rules.schedules().filter { it.enabled }.joinToString(", ") {
            "${it.name} ${Format.clockFromMinute(it.startMinute)}–${Format.clockFromMinute(it.endMinute)}"
        }.ifEmpty { "none" }
        val f = focus.state.value
        val lampState = lamp.status.value.state?.label ?: "unknown"

        return """
You are the Focus Lamp coach, inside a student's screen-time app that is connected to a desk lamp.
The lamp is green under 80% of the student's daily budget for distracting apps, warm white from 80%, and red at 100%.

Live data (${Format.dayAndTime(System.currentTimeMillis())}):
- Total screen time today: ${Format.durationWords(s.totalMs)}, ${s.pickups} pickups
- Distracting apps today: ${Format.durationWords(s.distractionMs)} of a $budget min daily budget
- Lamp: $lampState
- Focus session: ${if (f.running) "running, ${Format.durationWords(f.remainingMs)} left" else "not running"}
- Student's own app limits: $selfLimits
- Parent limits: $parentLimits
- Active schedules: $schedules
- Top apps today:
$top
${if (screen != null) "The student opened you from the $screen screen." else ""}

How to answer:
- Be warm and direct. Never shame. Quote the real numbers above when they help.
- Keep it short: 2-5 sentences or a few bullets, unless asked for a plan.
- Suggest concrete actions the app supports: tick an app as distracting, set an app limit on the Usage tab,
  start a focus session on the Focus tab, lower the daily budget in Settings, or ask a parent for time.
- You can't change settings yourself; tell the student where to do it.
- Use **bold** sparingly and "- " for bullets. No headings.
- Day starts ${Format.clock(DayClock.startOfDay())}; limits reset at midnight, except parent blocks, which a parent lifts.
""".trim()
    }
}
