package com.focuslamp.app.data.tracking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.LruCache
import android.view.inputmethod.InputMethodManager

/**
 * Real app names and icons, plus the sets of packages that should never count as screen time.
 *
 * Names come from the launcher entry (what the student sees under the icon), not from the
 * package name, which is what produced "katana", "musically" and "android" before.
 */
class AppInfoCache(private val context: Context) {

    data class AppEntry(val packageName: String, val label: String)

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, Drawable>(120)

    @Volatile private var launchable: Map<String, AppEntry> = emptyMap()
    @Volatile private var launchableLoadedAt = 0L
    @Volatile private var excluded: Set<String> = emptySet()
    @Volatile private var excludedLoadedAt = 0L
    @Volatile private var keyboards: Set<String> = emptySet()

    /** Every app with a launcher icon, keyed by package. Refreshed every 10 minutes. */
    fun launchableApps(): Map<String, AppEntry> {
        val now = System.currentTimeMillis()
        if (launchable.isEmpty() || now - launchableLoadedAt > 10 * 60_000L) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val result = HashMap<String, AppEntry>()
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg in result) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                result[pkg] = AppEntry(pkg, label.ifEmpty { humanize(pkg) })
            }
            launchable = result
            launchableLoadedAt = now
        }
        return launchable
    }

    fun label(packageName: String): String {
        launchableApps()[packageName]?.let { return it.label }
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            humanize(packageName)
        }
    }

    fun icon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val icon = try {
            pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (icon != null) iconCache.put(packageName, icon)
        return icon
    }

    /**
     * Packages whose foreground time is not "screen time": home-screen launchers, keyboards,
     * system UI and Focus Lamp itself. Matches how Digital Wellbeing counts.
     */
    fun excludedPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (excluded.isEmpty() || now - excludedLoadedAt > 10 * 60_000L) {
            val set = HashSet<String>()
            set += context.packageName
            set += "android"
            set += "com.android.systemui"
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(home, 0).mapNotNullTo(set) { it.activityInfo?.packageName }
            val ime = HashSet<String>()
            try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.enabledInputMethodList.mapTo(ime) { it.packageName }
            } catch (_: Exception) {
            }
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { if (it.isNotBlank()) ime += it }
            keyboards = ime
            set += ime
            excluded = set
            excludedLoadedAt = now
        }
        return excluded
    }

    /** Keyboards pop up over the app being used, so they never count as switching apps. */
    fun isKeyboard(packageName: String): Boolean {
        excludedPackages()
        return packageName in keyboards
    }

    companion object {
        /** Last-resort name: "com.example.my_app" -> "My app". Never the bare last segment. */
        fun humanize(packageName: String): String {
            val parts = packageName.split('.').filter { it.isNotBlank() }
            val generic = setOf("android", "app", "apps", "mobile", "client", "main", "com", "org", "net")
            val pick = parts.lastOrNull { it.lowercase() !in generic } ?: parts.lastOrNull() ?: packageName
            val words = pick.replace('_', ' ').replace('-', ' ').trim()
            return words.replaceFirstChar { it.uppercase() }
        }
    }
}
