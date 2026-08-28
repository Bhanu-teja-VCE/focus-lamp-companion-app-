package com.focuslamp.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/**
 * OemUtils — Deep-links users on OEM devices (Vivo, Xiaomi, Oppo, Samsung) to manufacturer-specific
 * AutoStart & Battery Optimization settings so background UsageStats tracking runs without OEM throttling.
 */
object OemUtils {

    /**
     * Checks if the device manufacturer is Vivo.
     */
    fun isVivo(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("vivo") || manufacturer.contains("iqoo")
    }

    /**
     * Checks if battery optimization is disabled for this app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    /**
     * Opens manufacturer-specific AutoStart or Battery Optimization settings page.
     * Especially handles Vivo iManager / Funtouch OS / OriginOS settings.
     */
    fun openOemAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                // Vivo iManager / AutoStart intents
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                })
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                })
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                })
            }
            manufacturer.contains("samsung") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.loction", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
            }
        }

        // Generic battery optimization intent fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intents.add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next intent in priority order
            }
        }

        Toast.makeText(context, "Please enable AutoStart in system settings.", Toast.LENGTH_LONG).show()
    }
}
