package com.focuslamp.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized settings manager using SharedPreferences.
 * Stores ESP32 IP, screen time limit, and monitoring state.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "focus_lamp_settings", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_ESP_IP = "esp32_ip"
        private const val KEY_ESP_PORT = "esp32_port"
        private const val KEY_TIME_LIMIT = "time_limit_minutes"
        private const val KEY_USE_HTTP = "use_http_mode"
        private const val KEY_MONITORING_ACTIVE = "monitoring_active"

        const val DEFAULT_IP = "192.168.4.1"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_TIME_LIMIT = 30 // 30 minutes
    }

    /** The ESP32's IP address */
    var espIp: String
        get() = prefs.getString(KEY_ESP_IP, DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString(KEY_ESP_IP, value).apply()

    /** The ESP32's TCP port (for socket mode) */
    var espPort: Int
        get() = prefs.getInt(KEY_ESP_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_ESP_PORT, value).apply()

    /** Daily screen time limit for distracting apps, in minutes */
    var timeLimitMinutes: Int
        get() = prefs.getInt(KEY_TIME_LIMIT, DEFAULT_TIME_LIMIT)
        set(value) = prefs.edit().putInt(KEY_TIME_LIMIT, value).apply()

    /** Whether to use HTTP mode (true) or TCP socket mode (false) */
    var useHttpMode: Boolean
        get() = prefs.getBoolean(KEY_USE_HTTP, true) // HTTP by default
        set(value) = prefs.edit().putBoolean(KEY_USE_HTTP, value).apply()

    /** Whether the background monitoring service is active */
    var isMonitoringActive: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ACTIVE, value).apply()
}
