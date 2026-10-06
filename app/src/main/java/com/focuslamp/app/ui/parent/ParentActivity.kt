package com.focuslamp.app.ui.parent

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.databinding.ActivityParentBinding
import com.focuslamp.app.ui.TabNavigator
import com.focuslamp.app.ui.WelcomeActivity
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Permissions
import com.focuslamp.app.util.Role
import kotlinx.coroutines.launch

/** Parent's phone: Children, Requests, Rules, Settings. */
class ParentActivity : AppCompatActivity() {

    private lateinit var b: ActivityParentBinding
    lateinit var tabs: TabNavigator
        private set

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Graph.settings.role != Role.PARENT) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)
        val nav = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        tabs = TabNavigator(nav, b.bottomNav, R.id.childrenFragment).also { it.attach() }
        nav.addOnDestinationChangedListener { _, dest, _ -> b.screenTitle.text = dest.label }

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            !Permissions.isGranted(this, PermissionItem.NOTIFICATIONS)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // A request notification opens the Requests tab.
        if (intent?.getBooleanExtra(EXTRA_OPEN_REQUESTS, false) == true) tabs.go(R.id.requestsFragment)
    }

    override fun onResume() {
        super.onResume()
        if (!::b.isInitialized) return
        lifecycleScope.launch { Graph.family.registerToken(null) }
    }

    companion object {
        const val EXTRA_OPEN_REQUESTS = "open_requests"
    }
}
