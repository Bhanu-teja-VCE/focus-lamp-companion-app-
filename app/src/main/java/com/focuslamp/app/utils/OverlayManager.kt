package com.focuslamp.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.focuslamp.app.R

class OverlayManager(private val context: Context) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var ringtone: Ringtone? = null
    
    var isShowing = false
        private set

    fun showOverlay(appName: String) {
        if (isShowing) {
            overlayView?.findViewById<TextView>(R.id.tvOverlayAppName)?.text = "App Blocked: $appName"
            return
        }

        // Only proceed if we have permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            // FLAG_NOT_FOCUSABLE allows interaction with our buttons, but since layout is match_parent & clickable, it blocks touches to the app behind it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.layout_alert_overlay, null)

        overlayView?.findViewById<TextView>(R.id.tvOverlayAppName)?.text = "App Blocked: $appName"

        overlayView?.findViewById<Button>(R.id.btnOverlayDismiss)?.setOnClickListener {
            // Dismissing sends the user back to their Android Home Screen, hiding the distracting app
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(startMain)
            
            // We hide the overlay now that they left the app
            hideOverlay()
        }

        try {
            windowManager.addView(overlayView, layoutParams)
            isShowing = true
            
            // Play default alarm sound (or notification sound if alarm is unavailable)
            try {
                var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (uri == null) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOverlay() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
            isShowing = false
            
            // Stop sound
            ringtone?.stop()
            ringtone = null
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
