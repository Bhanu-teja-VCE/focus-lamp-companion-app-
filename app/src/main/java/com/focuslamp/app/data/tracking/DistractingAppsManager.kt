package com.focuslamp.app.data.tracking

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The apps the student has ticked as distracting. Only these count toward the daily
 * distraction budget that drives the lamp.
 */
class DistractingAppsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_lamp_distracting_apps", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow(read())
    val apps: StateFlow<Set<String>> = _apps.asStateFlow()

    fun getAll(): Set<String> = _apps.value

    fun isDistracting(packageName: String) = packageName in _apps.value

    fun setDistracting(packageName: String, distracting: Boolean) {
        val next = _apps.value.toMutableSet()
        if (distracting) next += packageName else next -= packageName
        write(next)
    }

    fun setApps(packages: Set<String>) = write(packages)

    private fun read(): Set<String> =
        prefs.getStringSet(KEY_APPS, null)?.toSet() ?: DEFAULT_APPS

    private fun write(set: Set<String>) {
        // Always store a fresh copy: SharedPreferences must not be handed a set it already owns.
        prefs.edit().putStringSet(KEY_APPS, HashSet(set)).apply()
        _apps.value = set.toSet()
    }

    companion object {
        private const val KEY_APPS = "distracting_apps"

        val DEFAULT_APPS = setOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.snapchat.android",
            "com.facebook.katana",
            "com.google.android.youtube",
            "com.reddit.frontpage"
        )
    }
}
