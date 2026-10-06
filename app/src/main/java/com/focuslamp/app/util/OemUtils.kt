package com.focuslamp.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Some manufacturers kill background apps aggressively. This opens their own
 * "auto-start" screen when one exists, otherwise the standard app details page.
 */
object OemUtils {

    fun hasAutoStartScreen(): Boolean = autoStartIntents().isNotEmpty()

    fun openAutoStartSettings(context: Context): Boolean {
        val candidates = autoStartIntents() + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun autoStartIntents(): List<Intent> {
        val m = Build.MANUFACTURER.lowercase()
        fun c(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        return when {
            m.contains("vivo") || m.contains("iqoo") -> listOf(
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
            )
            m.contains("samsung") -> listOf(
                // Fixed: the old code pointed at "com.samsung.android.loction" (typo), which never opened.
                c("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            )
            else -> emptyList()
        }
    }
}
