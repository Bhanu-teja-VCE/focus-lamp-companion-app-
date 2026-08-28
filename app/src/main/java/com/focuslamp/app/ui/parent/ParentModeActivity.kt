package com.focuslamp.app.ui.parent

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.focuslamp.app.R

/**
 * ParentModeActivity — Dedicated, authenticated container for Parent Mode.
 * Features an isolated navigation graph, Material Deep Indigo theme, 2-minute inactivity auto-exit,
 * and background re-auth security.
 */
class ParentModeActivity : AppCompatActivity() {

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        Toast.makeText(this, "🔒 Parent Mode timed out due to inactivity.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private var pausedTimestampMs: Long = 0L

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 120_000L // 2 minutes
        private const val BACKGROUND_REAUTH_TIMEOUT_MS = 30_000L // 30 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        resetInactivityTimer()

        // Check if backgrounded for too long
        if (pausedTimestampMs > 0L) {
            val bgDuration = System.currentTimeMillis() - pausedTimestampMs
            if (bgDuration > BACKGROUND_REAUTH_TIMEOUT_MS) {
                Toast.makeText(this, "🔒 Session expired while in background.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pausedTimestampMs = System.currentTimeMillis()
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
    }

    private fun exitParentMode() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }
}
