package com.focuslamp.app.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * ParentPinManager — Manages SHA-256 hashed 4-digit Parent PIN protection for Focus Lamp.
 * Prevents children from modifying focus limits, schedule windows, or turning off monitoring.
 */
class ParentPinManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "parent_pin_prefs", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_PIN_HASH = "parent_pin_hash"
        private const val KEY_PIN_ENABLED = "parent_pin_enabled"
    }

    /** Returns true if a Parent PIN has been set and is active */
    fun isPinSet(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash().isNotEmpty()
    }

    /** Sets and securely hashes a new 4-digit Parent PIN */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false

        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
        return true
    }

    /** Verifies if the entered PIN matches the saved Parent PIN */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return true // Default unlocked if no PIN configured
        return hashPin(pin) == getPinHash()
    }

    /** Clears the Parent PIN (requires current PIN verification) */
    fun clearPin(currentPin: String): Boolean {
        if (verifyPin(currentPin)) {
            prefs.edit()
                .remove(KEY_PIN_HASH)
                .putBoolean(KEY_PIN_ENABLED, false)
                .apply()
            return true
        }
        return false
    }

    private fun getPinHash(): String {
        return prefs.getString(KEY_PIN_HASH, "") ?: ""
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
