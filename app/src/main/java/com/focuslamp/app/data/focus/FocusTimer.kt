package com.focuslamp.app.data.focus

import android.content.Context
import com.focuslamp.app.data.local.SessionDao
import com.focuslamp.app.data.local.SessionEntity
import com.focuslamp.app.util.Notifications
import com.focuslamp.app.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocusState(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val minutes: Int = 25,
    val remainingMs: Long = 0L
) {
    val progress: Float
        get() = if (!running || endsAt <= startedAt) 0f
        else 1f - remainingMs.toFloat() / (endsAt - startedAt).toFloat()
}

/**
 * Focus sessions. The end time is saved, so a session keeps running (and is recorded)
 * even if the app is closed. The old timer lived in a ViewModel and was lost on exit.
 */
class FocusTimer(
    private val context: Context,
    private val settings: SettingsManager,
    private val dao: SessionDao,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FocusState(minutes = settings.focusMinutes))
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        restore()
    }

    fun start(minutes: Int) {
        val now = System.currentTimeMillis()
        settings.focusMinutes = minutes
        settings.focusStartAt = now
        settings.focusEndAt = now + minutes * 60_000L
        restore()
    }

    fun stop() {
        val s = _state.value
        if (!s.running) return
        val doneMin = ((System.currentTimeMillis() - s.startedAt) / 60_000L).toInt()
        clear()
        if (doneMin >= 1) {
            scope.launch {
                dao.insertSession(SessionEntity(timestamp = s.startedAt, durationMinutes = doneMin, isCompleted = false))
            }
        }
    }

    /** Active end time if a strict focus session is running (used by the blocker). */
    fun strictEndsAt(): Long? {
        val s = _state.value
        return if (s.running && settings.strictFocus) s.endsAt else null
    }

    private fun restore() {
        val end = settings.focusEndAt
        val start = settings.focusStartAt
        val now = System.currentTimeMillis()
        if (end <= 0L) {
            _state.value = FocusState(minutes = settings.focusMinutes)
            return
        }
        if (now >= end) {
            complete(start, end)
            return
        }
        _state.value = FocusState(true, start, end, settings.focusMinutes, end - now)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) {
                    complete(start, end)
                    break
                }
                _state.value = _state.value.copy(remainingMs = left)
                delay(1_000L - (left % 1_000L).coerceAtMost(999L))
            }
        }
    }

    private fun complete(start: Long, end: Long) {
        val minutes = ((end - start) / 60_000L).toInt().coerceAtLeast(1)
        clear()
        scope.launch {
            dao.insertSession(SessionEntity(timestamp = start, durationMinutes = minutes, isCompleted = true))
        }
        Notifications.post(context, Notifications.ID_FOCUS_DONE, Notifications.CHANNEL_FOCUS,
            "Focus session done", "$minutes minutes of focus. Nice work.")
    }

    private fun clear() {
        ticker?.cancel()
        settings.focusEndAt = 0L
        settings.focusStartAt = 0L
        _state.value = FocusState(minutes = settings.focusMinutes)
    }
}
