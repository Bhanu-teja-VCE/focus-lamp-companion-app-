package com.focuslamp.app.ui.parent

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.focuslamp.app.R

/**
 * ParentModeActivity — Dedicated, authenticated container for Parent Mode.
 * Hardened with WindowManager FLAG_SECURE, persistent background timeout across process death,
 * 2-minute inactivity auto-exit, and Parent Mode session flag for service overlay suppression.
 */
class ParentModeActivity : AppCompatActivity() {

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        Toast.makeText(this, "🔒 Parent Mode timed out due to inactivity.", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 120_000L // 2 minutes
        private const val BACKGROUND_REAUTH_TIMEOUT_MS = 30_000L // 30 seconds
        private const val PREFS_SESSION = "parent_mode_session_prefs"
        private const val KEY_PAUSED_TIMESTAMP_MS = "paused_timestamp_ms"

        /** Flag read by FocusMonitorService to suppress overlay while parent is active */
        @Volatile
        var isParentModeActive: Boolean = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔒 Security Hardening #3: Prevent screenshots & Recents app switcher thumbnail leaks
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_parent_mode)

        val btnExitIcon = findViewById<FrameLayout>(R.id.btnExitParentMode)
        val btnExitText = findViewById<Button>(R.id.btnExitParentText)

        btnExitIcon.setOnClickListener { exitParentMode() }
        btnExitText.setOnClickListener { exitParentMode() }

        resetInactivityTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetInactivityTimer()
    }

    override fun onResume() {
        super.onResume()
        isParentModeActive = true
        resetInactivityTimer()

        // 🔒 Security Hardening #5: Check disk-persisted background duration (survives process death)
        val prefs = getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
        val pausedAt = prefs.getLong(KEY_PAUSED_TIMESTAMP_MS, 0L)
        if (pausedAt > 0L) {
            val bgDuration = System.currentTimeMillis() - pausedAt
            // Clear saved timestamp
            prefs.edit().remove(KEY_PAUSED_TIMESTAMP_MS).apply()

            if (bgDuration > BACKGROUND_REAUTH_TIMEOUT_MS) {
                Toast.makeText(this, "🔒 Session expired while in background.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isParentModeActive = false
        inactivityHandler.removeCallbacks(inactivityRunnable)

        // Persist pause timestamp to disk to survive process kill
        getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PAUSED_TIMESTAMP_MS, System.currentTimeMillis())
            .commit()
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
    }

    private fun exitParentMode() {
        isParentModeActive = false
        inactivityHandler.removeCallbacks(inactivityRunnable)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        isParentModeActive = false
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }
}
