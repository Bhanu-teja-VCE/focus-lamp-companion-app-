package com.focuslamp.app.data.lamp

import com.focuslamp.app.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LampLink { UNKNOWN, CONNECTING, ONLINE, OFFLINE }

data class LampStatus(
    val link: LampLink = LampLink.UNKNOWN,
    /** What we last asked the lamp to show (or what it reported). */
    val state: LampState? = null,
    val host: String = "",
    val checkedAt: Long = 0L
)

/**
 * Owns the connection to the lamp. The monitor service calls [apply] every tick.
 *
 * Fixes the old "send once, never retry" behaviour: a command is re-sent if the last one
 * failed, and the current state is re-sent every minute so a lamp that rebooted catches up.
 */
class LampRepository(private val settings: SettingsManager) {

    private val http = HttpLampController()
    private val mutex = Mutex()

    private val _status = MutableStateFlow(LampStatus(host = settings.espIp))
    val status: StateFlow<LampStatus> = _status.asStateFlow()

    private var lastSent: LampState? = null
    private var lastSentOk = false
    private var lastSentAt = 0L

    val host: String get() = settings.espIp

    fun setHost(host: String) {
        settings.espIp = host
        lastSent = null
        _status.value = LampStatus(LampLink.UNKNOWN, null, host)
    }

    /** Checks /status. Used by "Test connection" and the lamp screen. */
    suspend fun check(): Boolean = mutex.withLock {
        _status.value = _status.value.copy(link = LampLink.CONNECTING, host = host)
        val s = http.status(host)
        _status.value = LampStatus(
            link = if (s != null) LampLink.ONLINE else LampLink.OFFLINE,
            state = s?.mode ?: _status.value.state,
            host = host,
            checkedAt = System.currentTimeMillis()
        )
        s != null
    }

    /** Sends [state] if it changed, if the last send failed, or once a minute as a heartbeat. */
    suspend fun apply(state: LampState, force: Boolean = false) = mutex.withLock {
        val now = System.currentTimeMillis()
        val due = force || state != lastSent || !lastSentOk || now - lastSentAt > HEARTBEAT_MS
        // When the lamp is unreachable, back off to one try every 30 s instead of every tick.
        if (!due || (!force && !lastSentOk && state == lastSent && now - lastSentAt < RETRY_MS)) return@withLock
        val ok = http.send(host, state)
        lastSent = state
        lastSentOk = ok
        lastSentAt = now
        _status.value = LampStatus(
            link = if (ok) LampLink.ONLINE else LampLink.OFFLINE,
            state = state,
            host = host,
            checkedAt = now
        )
    }

    companion object {
        private const val HEARTBEAT_MS = 60_000L
        private const val RETRY_MS = 30_000L
    }
}
