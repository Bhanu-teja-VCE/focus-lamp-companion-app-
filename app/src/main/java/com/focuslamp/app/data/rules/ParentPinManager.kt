package com.focuslamp.app.data.rules

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The parent PIN used on the student's phone (offline unlock and local parent controls).
 *
 * Changes from the old version:
 *  - Removed "forgot PIN -> fingerprint reset". On a student's phone that fingerprint is the
 *    student's, so it let the student reset the parent PIN.
 *  - Removed the 24-hour automatic reset for the same reason (waiting a day was a bypass).
 *  - Removed the recovery code, which could never verify (saved with its dash, checked without).
 *  - The PIN can now be set from the parent's own phone; only its salted hash is synced.
 */
class ParentPinManager(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    fun isPinSet(): Boolean = !prefs.getString(KEY_HASH, null).isNullOrEmpty()

    /** Sets the PIN on this phone. Returns false if the PIN isn't 4–6 digits. */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = newSalt()
        prefs.edit().putString(KEY_HASH, hash(pin, salt)).putString(KEY_SALT, salt)
            .putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT, 0).commit()
        return true
    }

    /** Stores a PIN hash that the parent set on their own phone. */
    fun setFromRemote(hash: String?, salt: String?) {
        if (hash.isNullOrEmpty() || salt.isNullOrEmpty()) return
        if (hash == prefs.getString(KEY_HASH, null)) return
        prefs.edit().putString(KEY_HASH, hash).putString(KEY_SALT, salt).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT, 0).commit()
    }

    /** Checks the PIN. Wrong attempts lock entry for 30 s, then 1 min, then 5 min. */
    fun verify(pin: String): Boolean {
        if (!isPinSet() || lockoutSecondsRemaining() > 0) return false
        val ok = hash(pin, prefs.getString(KEY_SALT, "") ?: "") == prefs.getString(KEY_HASH, null)
        if (ok) {
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT, 0).commit()
        } else {
            val failed = prefs.getInt(KEY_FAILED, 0) + 1
            val lockMs = when {
                failed >= 8 -> 5 * 60_000L
                failed >= 5 -> 60_000L
                failed >= 3 -> 30_000L
                else -> 0L
            }
            prefs.edit().putInt(KEY_FAILED, failed)
                .putLong(KEY_LOCKOUT, if (lockMs > 0) System.currentTimeMillis() + lockMs else 0L).commit()
        }
        return ok
    }

    fun lockoutSecondsRemaining(): Int {
        val until = prefs.getLong(KEY_LOCKOUT, 0L)
        val left = until - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    companion object {
        private const val FILE = "parent_pin_secure_encrypted_prefs"
        private const val KEY_HASH = "pin_hash_v2"
        private const val KEY_SALT = "pin_salt_v2"
        private const val KEY_FAILED = "failed_attempts_v2"
        private const val KEY_LOCKOUT = "lockout_until_v2"

        fun isValidPin(pin: String) = pin.length in 4..6 && pin.all { it.isDigit() }

        fun newSalt(): String {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }

        fun hash(pin: String, salt: String): String {
            // Stretched so a synced hash can't be brute-forced instantly.
            var d = "$salt:$pin".toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            repeat(10_000) { d = md.digest(d + salt.toByteArray()) }
            return d.joinToString("") { "%02x".format(it) }
        }

        private fun createPrefs(context: Context): SharedPreferences = try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("${FILE}_plain", Context.MODE_PRIVATE)
        }
    }
}
