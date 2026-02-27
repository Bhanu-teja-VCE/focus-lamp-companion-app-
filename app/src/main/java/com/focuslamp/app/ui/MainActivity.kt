package com.focuslamp.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
 * Main Activity — handles navigation, permission checks, and hosts all fragments.
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

        // Check permissions on launch
        checkUsagePermission()
        checkNotificationPermission()
    }

    /**
     * Usage Access permission — must be enabled manually in Settings.
     * Shows a dialog explaining why, then opens the Settings screen.
     */
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

    /**
     * Notification permission — required on Android 13+ for foreground service notification.
     */
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
