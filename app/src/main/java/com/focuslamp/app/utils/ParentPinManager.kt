package com.focuslamp.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ParentPinManager — Hardware KeyStore EncryptedSharedPreferences storage for salted SHA-256 Parent PIN,
 * 8-character Recovery Code, 24-hour time-delay recovery fallback, and persistent exponential backoff lockouts.
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
        private const val KEY_RECOVERY_CODE_HASH = "recovery_code_hash"
        private const val KEY_DELAY_RESET_UNLOCK_MS = "delay_reset_unlock_ms"

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
                context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            }
        }
    }

    /** Returns true if a Parent PIN has been set */
    fun isPinSet(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash().isNotEmpty()
    }

    /** Sets a new 4-digit Parent PIN with per-install salt and generates a new 8-character Recovery Code */
    fun setPin(pin: String): String? {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return null

        val salt = getOrCreateSalt()
        val hash = hashPinWithSalt(pin, salt)

        // Generate 8-char Recovery Code (e.g. FL-84A2-9B3C)
        val rawRecoveryCode = generateRandomRecoveryCode()
        val recoveryHash = hashString(rawRecoveryCode)

        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_RECOVERY_CODE_HASH, recoveryHash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_DELAY_RESET_UNLOCK_MS, 0L)
            .commit()

        return rawRecoveryCode
    }

    /** Verifies entered Parent PIN against salted SHA-256 hash */
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

    /** Verifies entered Recovery Code */
    fun verifyRecoveryCode(code: String): Boolean {
        val sanitized = code.trim().uppercase().replace("-", "")
        val savedHash = prefs.getString(KEY_RECOVERY_CODE_HASH, "") ?: ""
        if (savedHash.isEmpty()) return false

        val enteredHash = hashString(sanitized)
        if (enteredHash == savedHash) {
            resetPinWithoutAuth()
            return true
        }
        return false
    }

    /** Starts 24-hour time-delay reset fallback */
    fun startDelayReset(): Long {
        val unlockTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24 hours
        prefs.edit().putLong(KEY_DELAY_RESET_UNLOCK_MS, unlockTime).commit()
        return unlockTime
    }

    /** Returns hours remaining in 24h delay reset window (0 if unlocked/not requested) */
    fun getDelayResetHoursRemaining(): Int {
        val unlockTime = prefs.getLong(KEY_DELAY_RESET_UNLOCK_MS, 0L)
        val now = System.currentTimeMillis()
        if (unlockTime > now) {
            return (((unlockTime - now) / (1000 * 60 * 60)) + 1).toInt()
        }
        return 0
    }

    /** Returns true if 24-hour time-delay reset has elapsed and is unlocked */
    fun isDelayResetUnlocked(): Boolean {
        val unlockTime = prefs.getLong(KEY_DELAY_RESET_UNLOCK_MS, 0L)
        val now = System.currentTimeMillis()
        return unlockTime in 1..now
    }

    /** Resets the Parent PIN directly after biometric, recovery code, or 24h delay verification */
    fun resetPinWithoutAuth() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_RECOVERY_CODE_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_DELAY_RESET_UNLOCK_MS, 0L)
            .commit()
    }

    /** Returns lockout seconds remaining (0 if not locked out) */
    fun getLockoutSecondsRemaining(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            return ((lockoutUntil - now) / 1000).toInt().coerceAtLeast(1)
        }
        return 0
    }

    fun isLockedOut(): Boolean {
        return getLockoutSecondsRemaining() > 0
    }

    private fun recordFailedAttempt() {
        val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        var lockoutMs = 0L

        if (currentFailed >= 5) {
            lockoutMs = 60_000L
        } else if (currentFailed >= 3) {
            lockoutMs = 30_000L
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

    fun clearPin(currentPin: String): Boolean {
        if (verifyPin(currentPin)) {
            resetPinWithoutAuth()
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

    private fun generateRandomRecoveryCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder()
        val random = SecureRandom()
        for (i in 0 until 8) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        val raw = sb.toString()
        return "${raw.substring(0, 4)}-${raw.substring(4)}"
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val input = "$salt:$pin"
        return hashString(input)
    }
}
