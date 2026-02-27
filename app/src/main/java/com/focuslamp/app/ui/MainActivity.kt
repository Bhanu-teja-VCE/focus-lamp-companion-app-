package com.focuslamp.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.focuslamp.app.R
import com.focuslamp.app.databinding.ActivityMainBinding

/**
 * Main Activity — handles navigation, header, permission checks, and hosts fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: FocusViewModel

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Continue regardless — notification is nice-to-have, not mandatory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[FocusViewModel::class.java]

        // Set up Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        
        // Custom bottom navigation item handling for the middle lamp button and profile
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_lamp -> {
                    // Start Focus Timer Bottom Sheet directly
                    val bottomSheet = FocusSetupBottomSheet()
                    bottomSheet.show(supportFragmentManager, "FocusSetupBottomSheet")
                    return@setOnItemSelectedListener false // don't check the item
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Profile coming soon!", Toast.LENGTH_SHORT).show()
                    return@setOnItemSelectedListener false
                }
                else -> {
                    // Let navigation controller handle the rest
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                    return@setOnItemSelectedListener handled
                }
            }
        }

        // Header settings button click
        binding.btnHeaderSettings.setOnClickListener {
            if (navController.currentDestination?.id != R.id.settingsFragment) {
                navController.navigate(R.id.settingsFragment)
            }
        }

        // Check permissions on launch
        checkUsagePermission()
        checkNotificationPermission()
    }

    private fun checkUsagePermission() {
        if (!viewModel.hasUsagePermission()) {
            AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage(
                    "Focus Lamp needs to monitor which apps you use to track your screen time.\n\n" +
                    "Please enable \"Usage Access\" for Focus Lamp in the next screen."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
