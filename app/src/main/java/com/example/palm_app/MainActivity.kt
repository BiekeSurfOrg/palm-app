package com.example.palm_app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
// Removed Snackbar import as it's not used directly in the provided MainActivity snippet for this change
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
// Removed: import androidx.navigation.ui.setupWithNavController // Will be handled by custom listener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.palm_app.databinding.ActivityMainBinding
import androidx.core.view.WindowCompat
import com.example.palm_app.ui.ble_advertising.BleAdvertisingFragment // Import for KEY_PALM_HASH
import com.example.palm_app.ui.home.HomeFragment // Ensure this import is present

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Conditional navigation based on KEY_PALM_HASH
        val sharedPreferences = getSharedPreferences(BleAdvertisingFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val palmHash = sharedPreferences.getString(BleAdvertisingFragment.KEY_PALM_HASH, null)
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        if (!palmHash.isNullOrEmpty()) {
            // Navigate to BleAdvertisingFragment.
            // We need to ensure the NavController is ready and the graph is set.
            // Navigating here ensures it happens after the NavHostFragment is properly set up.
            navController.navigate(R.id.nav_ble_advertising)
        }
        // If palmHash is null or empty, the app will proceed to the startDestination (nav_home) as usual.


        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        // NavController already initialized above for conditional navigation

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_register, R.id.nav_confirm_registration,
                R.id.nav_ble_advertising
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        // navView.setupWithNavController(navController) // IMPORTANT: This line should be commented out or removed

        navView.setNavigationItemSelectedListener { menuItem ->
            var itemHandled = true // Assume we handle it

            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                    if (navController.currentDestination?.id == R.id.nav_home) {
                        attemptCallRestartActionsOnHomeFragment()
                    } else {
                        navController.navigate(R.id.nav_home)
                        // Post action to the NavHostFragment's view
                        navHostFragment?.view?.post {
                            attemptCallRestartActionsOnHomeFragment()
                        }
                    }
                }
                else -> {
                    // For other menu items, directly navigate.
                    navController.navigate(menuItem.itemId)
                    // If you need more complex handling for other items like the default NavUI behavior:
                    // itemHandled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                }
            }
            binding.drawerLayout.closeDrawers()
            itemHandled
        }
    }

    private fun attemptCallRestartActionsOnHomeFragment() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        val homeFragment = navHostFragment?.childFragmentManager?.fragments?.find { it is HomeFragment } as? HomeFragment

        if (homeFragment != null && homeFragment.isAdded && homeFragment.view != null) {
            Log.d("MainActivity", "HomeFragment found. Calling performRestartActions.")
            homeFragment.performRestartActions()
        } else {
            Log.w("MainActivity", "HomeFragment not found, not added, or view is null when trying to call performRestartActions.")
            // Consider more robust solutions like SharedViewModel if this log appears frequently.
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
