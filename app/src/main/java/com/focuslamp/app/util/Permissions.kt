package com.focuslamp.app.util

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.focuslamp.app.service.BlockAccessibilityService
import com.focuslamp.app.service.ProtectionAdminReceiver

/**
 * One place that knows every permission Focus Lamp relies on, whether it's granted,
 * and how to send the student to the right system screen.
 */
enum class PermissionItem(val title: String, val why: String, val required: Boolean) {
    USAGE("Usage access", "Lets Focus Lamp measure how long each app is open.", true),
    BLOCKER("Instant app blocking", "Accessibility access lets Focus Lamp cover an app the moment it opens.", false),
    OVERLAY("Display over other apps", "Backup way to show the block screen if instant blocking is off.", false),
    NOTIFICATIONS("Notifications", "Shows that monitoring is on and tells you when a parent answers.", false),
    BATTERY("Run in the background", "Stops the phone from pausing Focus Lamp to save battery.", false),
    UNINSTALL("Uninstall protection", "A parent has to turn this off before Focus Lamp can be removed.", false);
}

object Permissions {

    fun isGranted(context: Context, item: PermissionItem): Boolean = when (item) {
        PermissionItem.USAGE -> com.focuslamp.app.Graph.tracker.hasUsagePermission()
        PermissionItem.BLOCKER -> isAccessibilityEnabled(context)
        PermissionItem.OVERLAY -> Settings.canDrawOverlays(context)
        PermissionItem.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        PermissionItem.BATTERY -> (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
        PermissionItem.UNINSTALL -> (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isAdminActive(ComponentName(context, ProtectionAdminReceiver::class.java))
    }

    /** Blocking works if either the accessibility blocker or the overlay fallback is available. */
    fun canBlock(context: Context) =
        isGranted(context, PermissionItem.BLOCKER) || isGranted(context, PermissionItem.OVERLAY)

    fun missing(context: Context, items: List<PermissionItem> = PermissionItem.values().toList()) =
        items.filter { !isGranted(context, it) }

    /** Intent for the system screen that grants [item]. NOTIFICATIONS is requested at runtime instead. */
    fun settingsIntent(context: Context, item: PermissionItem): Intent? = when (item) {
        PermissionItem.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionItem.BLOCKER -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionItem.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        PermissionItem.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 26)
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else null
        PermissionItem.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        PermissionItem.UNINSTALL -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, ProtectionAdminReceiver::class.java))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Focus Lamp can only be removed after a parent turns this off.")
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (s in splitter) {
            if (ComponentName.unflattenFromString(s) == expected) return true
        }
        return false
    }
}
