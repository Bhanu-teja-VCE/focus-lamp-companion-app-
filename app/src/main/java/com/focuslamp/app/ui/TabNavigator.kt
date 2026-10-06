package com.focuslamp.app.ui

import androidx.navigation.NavController
import androidx.navigation.navOptions
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom-bar navigation without the "stuck on Home" bug.
 *
 * The old app used setupWithNavController, which saves and restores a separate back stack per
 * tab. Screens opened from Home (AI coach, Insights, Family) weren't tabs, so they were saved
 * inside Home's stack; tapping Home then counted as "reselect" and did nothing, and switching
 * tabs restored the screen you were trying to leave.
 *
 * Here every tap on a tab, including the one that's already highlighted, shows that tab's own
 * first screen. Back from any tab returns to the first tab, then leaves the app.
 */
class TabNavigator(
    private val nav: NavController,
    private val bar: BottomNavigationView,
    private val startTab: Int
) {
    private val tabs = (0 until bar.menu.size()).map { bar.menu.getItem(it).itemId }.toSet()

    fun attach() {
        bar.setOnItemSelectedListener { item -> go(item.itemId); true }
        bar.setOnItemReselectedListener { item -> go(item.itemId) }
        nav.addOnDestinationChangedListener { _, dest, _ ->
            if (dest.id in tabs) bar.menu.findItem(dest.id)?.isChecked = true
        }
    }

    fun isTab(id: Int) = id in tabs

    fun go(tabId: Int) {
        if (nav.currentDestination?.id == tabId) return
        nav.navigate(tabId, null, navOptions {
            popUpTo(startTab) { inclusive = tabId == startTab }
            launchSingleTop = true
        })
    }

    /** Opens a non-tab screen (Lamp, Settings) on top of the current tab. */
    fun open(destinationId: Int) {
        if (nav.currentDestination?.id == destinationId) return
        nav.navigate(destinationId, null, navOptions { launchSingleTop = true })
    }
}
