package dev.dettmer.simplenotes.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.SimpleNotesApplication
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.Logger
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Settings Activity with Jetpack Compose UI
 * v1.5.0: Complete Settings Redesign with grouped screens
 * 
 * Replaces the old 1147-line SettingsActivity.kt with a modern
 * Compose-based implementation featuring:
 * - 6 logical settings groups as separate screens
 * - Material 3 Design with Dynamic Colors (Material You)
 * - Navigation with back button in each screen
 * - Clean separation of concerns with SettingsViewModel
 */
class ComposeSettingsActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "ComposeSettingsActivity"
    }
    
    private val viewModel: SettingsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Material You (Android 12+)
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Handle back button with slide animation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK)
                finishWithSlideAnimation()
            }
        })
        
        // Collect events from ViewModel (for Activity-level actions)
        collectViewModelEvents()
        
        setContent {
            SimpleNotesTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val showBatteryDialog by viewModel.showBatteryOptimizationDialog.collectAsState()

                // Toast handling from ViewModel
                LaunchedEffect(Unit) {
                    viewModel.showToast.collect { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                // Battery optimization dialog (state-driven)
                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissBatteryOptimizationDialog() },
                        title = { Text(getString(R.string.battery_optimization_dialog_title)) },
                        text = { Text(getString(R.string.battery_optimization_dialog_full_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.dismissBatteryOptimizationDialog()
                                openBatteryOptimizationSettings()
                            }) {
                                Text(getString(R.string.battery_optimization_open_settings))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissBatteryOptimizationDialog() }) {
                                Text(getString(R.string.battery_optimization_later))
                            }
                        }
                    )
                }

                SettingsNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    onFinish = {
                        setResult(RESULT_OK)
                        finishWithSlideAnimation()
                    }
                )
            }
        }
    }
    
    private fun finishWithSlideAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    /**
     * Collect events from ViewModel for Activity-level actions
     * v1.5.0: Ported from old SettingsActivity
     */
    private fun collectViewModelEvents() {
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is SettingsViewModel.SettingsEvent.RestartNetworkMonitor -> {
                        restartNetworkMonitor()
                    }
                    else -> { /* handled via state */ }
                }
            }
        }
    }
    
    /**
     * Open system battery optimization settings
     * v1.5.0: Ported from old SettingsActivity
     * 
     * Note: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is acceptable for F-Droid builds.
     * For Play Store builds, this would need to be changed to
     * ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (shows list, doesn't request directly).
     */
    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to open battery optimization settings: ${e.message}")
            // Fallback: Open general battery settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Logger.w(TAG, "Failed to open fallback battery settings: ${e2.message}")
                Toast.makeText(this, "Bitte Akku-Optimierung manuell deaktivieren", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Restart the network monitor after sync settings change
     * v1.5.0: Ported from old SettingsActivity
     */
    private fun restartNetworkMonitor() {
        try {
            val app = application as SimpleNotesApplication
            Logger.d(TAG, "🔄 Restarting NetworkMonitor with new settings")
            app.networkMonitor.stopMonitoring()
            app.networkMonitor.startMonitoring()
            Logger.d(TAG, "✅ NetworkMonitor restarted successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Failed to restart NetworkMonitor: ${e.message}")
        }
    }
    
    /**
     * Handle configuration changes (e.g., locale) without recreating activity
     * v1.8.0: Prevents flickering during language changes by avoiding full recreate
     * Compose automatically recomposes when configuration changes
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Logger.d(TAG, "📱 Configuration changed (likely locale switch) - Compose will recompose")
        // Compose handles UI updates automatically via recomposition
        // No manual action needed - stringResource() etc. will pick up new locale
    }
}
