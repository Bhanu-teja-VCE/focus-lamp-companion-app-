package com.focuslamp.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.lamp.LampLink
import com.focuslamp.app.databinding.ActivityMainBinding
import com.focuslamp.app.service.FocusMonitorService
import com.focuslamp.app.ui.coach.CoachSheet
import com.focuslamp.app.ui.common.setLampDot
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Permissions
import com.focuslamp.app.util.Role
import kotlinx.coroutines.launch

/**
 * Student home. Four tabs (Today, Usage, Focus, Family) plus, from anywhere:
 * the lamp chip (top), Settings (top), and the coach button (floating).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    lateinit var tabs: TabNavigator
        private set

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Graph.settings.role != Role.STUDENT) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        val nav = navHost.navController
        tabs = TabNavigator(nav, b.bottomNav, R.id.todayFragment).also { it.attach() }

        nav.addOnDestinationChangedListener { _, dest, _ ->
            b.screenTitle.text = dest.label
            val isTab = tabs.isTab(dest.id)
            b.backButton.visible(!isTab)
            b.coachFab.visible(dest.id != R.id.rulesEditorFragment)
            b.lampChip.visible(dest.id != R.id.lampFragment)
            b.settingsButton.visible(dest.id != R.id.settingsFragment)
        }
        b.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        b.settingsButton.setOnClickListener { tabs.open(R.id.settingsFragment) }
        b.lampChip.setOnClickListener { tabs.open(R.id.lampFragment) }
        b.coachFab.setOnClickListener {
            val screen = nav.currentDestination?.label?.toString()
            CoachSheet.newInstance(screen).show(supportFragmentManager, CoachSheet.TAG)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Graph.lamp.status.collect { s ->
                    val (color, text) = when (s.link) {
                        LampLink.ONLINE -> (s.state?.colorRes ?: R.color.lamp_green) to (s.state?.label ?: "Lamp")
                        LampLink.OFFLINE -> R.color.lamp_off to getString(R.string.lamp_offline)
                        LampLink.CONNECTING -> R.color.lamp_off to getString(R.string.lamp_connecting)
                        LampLink.UNKNOWN -> R.color.lamp_off to getString(R.string.lamp)
                    }
                    b.lampChip.text = text
                    b.lampChip.setLampDot(color)
                    b.lampChip.contentDescription = getString(R.string.lamp_chip_description, text)
                }
            }
        }

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            !Permissions.isGranted(this, PermissionItem.NOTIFICATIONS)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::b.isInitialized) return
        FocusMonitorService.startIfAllowed(this)
        lifecycleScope.launch {
            Graph.usage.refresh()
            if (Graph.lamp.status.value.link == LampLink.UNKNOWN) Graph.lamp.check()
        }
    }
}
