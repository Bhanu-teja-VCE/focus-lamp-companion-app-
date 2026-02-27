package com.focuslamp.app.data.tracking

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages a list of "distracting" app package names stored in SharedPreferences.
 * Users can add/remove apps they consider distracting (e.g., Instagram, YouTube, Twitter).
 */
class DistractingAppsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "focus_lamp_distracting_apps", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_APPS = "distracting_apps"

        // Default distracting apps — common social media
        val DEFAULT_APPS = setOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",   // TikTok
            "com.snapchat.android",
            "com.facebook.katana",
            "com.google.android.youtube"
        )
    }

    /**
     * Get all distracting app package names.
     */
    fun getAll(): Set<String> {
        return prefs.getStringSet(KEY_APPS, DEFAULT_APPS) ?: DEFAULT_APPS
    }

    /**
     * Add a package name to the distracting list.
     */
    fun addApp(packageName: String) {
        val current = getAll().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_APPS, current).apply()
    }

    /**
     * Remove a package name from the distracting list.
     */
    fun removeApp(packageName: String) {
        val current = getAll().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_APPS, current).apply()
    }

    /**
     * Replace the entire list.
     */
    fun setApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_APPS, packages).apply()
    }

    /**
     * Reset to defaults.
     */
    fun resetToDefaults() {
        prefs.edit().putStringSet(KEY_APPS, DEFAULT_APPS).apply()
    }
}
