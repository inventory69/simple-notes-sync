package dev.dettmer.simplenotes.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.SimpleNotesApplication
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
        
        // Collect events from ViewModel (for Activity-level actions)
        collectViewModelEvents()
        
        setContent {
            SimpleNotesSettingsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                
                // Toast handling from ViewModel
                LaunchedEffect(Unit) {
                    viewModel.showToast.collect { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                
                SettingsNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    onFinish = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
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
                    is SettingsViewModel.SettingsEvent.RequestBatteryOptimization -> {
                        checkBatteryOptimization()
                    }
                    is SettingsViewModel.SettingsEvent.RestartNetworkMonitor -> {
                        restartNetworkMonitor()
                    }
                }
            }
        }
    }
    
    /**
     * Check if battery optimization is disabled for this app
     * v1.5.0: Ported from old SettingsActivity
     */
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryOptimizationDialog()
        }
    }
    
    /**
     * Show dialog asking user to disable battery optimization
     * v1.5.0: Ported from old SettingsActivity
     */
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hintergrund-Synchronisation")
            .setMessage(
                "Damit die App im Hintergrund synchronisieren kann, " +
                "muss die Akku-Optimierung deaktiviert werden.\n\n" +
                "Bitte wähle 'Nicht optimieren' für Simple Notes."
            )
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton("Später") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Open system battery optimization settings
     * v1.5.0: Ported from old SettingsActivity
     */
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
}

/**
 * Material 3 Theme with Dynamic Colors support
 */
@Composable
fun SimpleNotesSettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val colorScheme = when {
        // Dynamic colors are available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        // Fallback to static Material 3 colors
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
