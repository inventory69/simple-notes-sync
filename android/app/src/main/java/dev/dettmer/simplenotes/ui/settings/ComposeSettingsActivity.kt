package dev.dettmer.simplenotes.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.SimpleNotesApplication
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.BatteryOptimizationHelper
import dev.dettmer.simplenotes.utils.Logger
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
class ComposeSettingsActivity : AppCompatActivity() {
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

        // v2.0.0: Register both OPEN and CLOSE transitions for consistent
        // Shared Axis X animation on all back paths (arrow button + swipe gesture).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_enter,
                R.anim.shared_axis_x_exit
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit
            )
        }

        // v2.0.0: On API 35+ (mandatory predictive back), overrideActivityTransition(CLOSE)
        // is only respected for explicit finish() calls — the system uses its own animation
        // for gesture-driven back. Routing through OnBackPressedCallback + finish() fixes this.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithTransition()
                }
            }
        )

        // v2.0.0: Default result for Back gesture
        setResult(RESULT_OK)

        // Collect events from ViewModel (for Activity-level actions)
        collectViewModelEvents()

        setContent {
            // Live theme preview: theme state flows from SettingsViewModel so that
            // changes in DisplaySettingsScreen are immediately reflected here.
            val themeMode by viewModel.themeMode.collectAsState()
            val colorTheme by viewModel.colorTheme.collectAsState()
            // v2.1.0: NavController must live ABOVE SimpleNotesTheme so it survives
            // the Crossfade composition recreation on theme changes — otherwise
            // navigation resets to the start destination on every theme switch.
            val navController = rememberNavController()
            SimpleNotesTheme(themeMode = themeMode, colorTheme = colorTheme) {
                val showBatteryDialog by viewModel.showBatteryOptimizationDialog.collectAsState()

                // Battery optimization dialog (state-driven)
                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissBatteryOptimizationDialog() },
                        title = { Text(getString(R.string.battery_optimization_dialog_title)) },
                        text = { Text(getString(R.string.battery_optimization_dialog_full_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.dismissBatteryOptimizationDialog()
                                if (!BatteryOptimizationHelper.openBatteryOptimizationSettings(this)) {
                                    viewModel.showSnackbar(getString(R.string.battery_optimization_open_settings_failed))
                                }
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
                        finishWithTransition()
                    }
                )
            }
        }
    }

    private fun finishWithTransition() {
        finish()
        // API < 34: overrideActivityTransition not available, use deprecated API
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit
            )
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
