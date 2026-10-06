package com.focuslamp.app.util

import android.content.Context
import android.content.SharedPreferences

enum class Role { STUDENT, PARENT }

/**
 * Plain settings. Keys for IP, limit and Groq key are unchanged so existing installs keep them.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_lamp_settings", Context.MODE_PRIVATE)

    var role: Role?
        get() = prefs.getString(KEY_ROLE, null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_ROLE, value?.name).apply()

    var displayName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    var espIp: String
        get() = prefs.getString(KEY_ESP_IP, DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString(KEY_ESP_IP, value.trim()).apply()

    /** Daily budget for all distracting apps together, in minutes. Drives the lamp. */
    var timeLimitMinutes: Int
        get() = prefs.getInt(KEY_TIME_LIMIT, DEFAULT_TIME_LIMIT)
        set(value) = prefs.edit().putInt(KEY_TIME_LIMIT, value.coerceIn(5, 600)).apply()

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GROQ_API_KEY, value.trim()).apply()

    val isAiEnabled: Boolean get() = groqApiKey.isNotBlank()

    // Family pairing
    var familyId: String?
        get() = prefs.getString(KEY_FAMILY_ID, null)
        set(value) = prefs.edit().putString(KEY_FAMILY_ID, value).apply()

    var parentName: String
        get() = prefs.getString(KEY_PARENT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PARENT_NAME, value).apply()

    var lastStatusPublish: Long
        get() = prefs.getLong(KEY_LAST_PUBLISH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PUBLISH, value).apply()

    var lastInstalledPublishDay: String
        get() = prefs.getString(KEY_LAST_INSTALLED, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_INSTALLED, value).apply()

    // Focus session (persisted so it survives the app being closed)
    var focusEndAt: Long
        get() = prefs.getLong(KEY_FOCUS_END, 0L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_END, value).apply()

    var focusStartAt: Long
        get() = prefs.getLong(KEY_FOCUS_START, 0L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_START, value).apply()

    var focusMinutes: Int
        get() = prefs.getInt(KEY_FOCUS_MIN, 25)
        set(value) = prefs.edit().putInt(KEY_FOCUS_MIN, value).apply()

    /** Block distracting apps while a focus session runs. */
    var strictFocus: Boolean
        get() = prefs.getBoolean(KEY_STRICT_FOCUS, true)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_FOCUS, value).apply()

    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    companion object {
        private const val KEY_ROLE = "role"
        private const val KEY_NAME = "display_name"
        private const val KEY_ESP_IP = "esp32_ip"
        private const val KEY_TIME_LIMIT = "time_limit_minutes"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_FAMILY_ID = "family_id"
        private const val KEY_PARENT_NAME = "parent_name"
        private const val KEY_LAST_PUBLISH = "last_status_publish"
        private const val KEY_LAST_INSTALLED = "last_installed_publish_day"
        private const val KEY_FOCUS_END = "focus_end_at"
        private const val KEY_FOCUS_START = "focus_start_at"
        private const val KEY_FOCUS_MIN = "focus_minutes"
        private const val KEY_STRICT_FOCUS = "strict_focus"
        private const val KEY_ONBOARDING = "onboarding_seen"

        const val DEFAULT_IP = "192.168.4.1"
        const val DEFAULT_TIME_LIMIT = 30
    }
}
