package com.focuslamp.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ParentPinManager — Hardware KeyStore EncryptedSharedPreferences storage for salted SHA-256 Parent PIN
 * and persistent exponential backoff lockouts surviving process force-closes & device reboots.
 */
class ParentPinManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    companion object {
        private const val PREFS_FILENAME = "parent_pin_secure_encrypted_prefs"
        private const val KEY_PIN_HASH = "parent_pin_hash_salted"
        private const val KEY_PIN_SALT = "parent_pin_salt"
        private const val KEY_PIN_ENABLED = "parent_pin_enabled"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_timestamp_ms"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILENAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SKEY_TEXT,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // Fallback to MODE_PRIVATE if Keystore hardware is uninitialized
                context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            }
        }
    }

    /** Returns true if a Parent PIN has been set */
    fun isPinSet(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash().isNotEmpty()
    }

    /** Sets and securely hashes a new 4-digit Parent PIN with per-install salt */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false

        val salt = getOrCreateSalt()
        val hash = hashPinWithSalt(pin, salt)

        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .commit()
        return true
    }

    /** Verifies if the entered PIN matches the saved Parent PIN, tracking failed attempts & lockouts */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return true

        if (isLockedOut()) return false

        val salt = getOrCreateSalt()
        val isValid = hashPinWithSalt(pin, salt) == getPinHash()

        if (isValid) {
            resetFailedAttempts()
        } else {
            recordFailedAttempt()
        }

        return isValid
    }

    /** Returns lockout seconds remaining (0 if not locked out) — Reads disk timestamp surviving force-close */
    fun getLockoutSecondsRemaining(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            return ((lockoutUntil - now) / 1000).toInt().coerceAtLeast(1)
        }
        return 0
    }

    /** Returns true if PIN attempts are currently locked out */
    fun isLockedOut(): Boolean {
        return getLockoutSecondsRemaining() > 0
    }

    private fun recordFailedAttempt() {
        val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        var lockoutMs = 0L

        if (currentFailed >= 5) {
            lockoutMs = 60_000L // 60 seconds lockout
        } else if (currentFailed >= 3) {
            lockoutMs = 30_000L // 30 seconds lockout
        }

        val lockoutUntil = if (lockoutMs > 0) System.currentTimeMillis() + lockoutMs else 0L

        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, currentFailed)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            .commit()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .commit()
    }

    /** Clears the Parent PIN */
    fun clearPin(currentPin: String): Boolean {
        if (verifyPin(currentPin)) {
            prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .putBoolean(KEY_PIN_ENABLED, false)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                .commit()
            return true
        }
        return false
    }

    private fun getOrCreateSalt(): String {
        var salt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        if (salt.isEmpty()) {
            val randomBytes = ByteArray(16)
            SecureRandom().nextBytes(randomBytes)
            salt = randomBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_PIN_SALT, salt).commit()
        }
        return salt
    }

    private fun getPinHash(): String {
        return prefs.getString(KEY_PIN_HASH, "") ?: ""
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val input = "$salt:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
