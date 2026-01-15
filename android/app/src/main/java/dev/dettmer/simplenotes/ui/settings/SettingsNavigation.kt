package dev.dettmer.simplenotes.ui.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.dettmer.simplenotes.ui.settings.screens.AboutScreen
import dev.dettmer.simplenotes.ui.settings.screens.BackupSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.DebugSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.MarkdownSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.ServerSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.SettingsMainScreen
import dev.dettmer.simplenotes.ui.settings.screens.SyncSettingsScreen

/**
 * Settings navigation host with all routes
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun SettingsNavHost(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    onFinish: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoute.Main.route
    ) {
        // Main Settings Overview
        composable(SettingsRoute.Main.route) {
            SettingsMainScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route.route) },
                onBack = onFinish
            )
        }
        
        // Server Settings
        composable(SettingsRoute.Server.route) {
            ServerSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Sync Settings
        composable(SettingsRoute.Sync.route) {
            SyncSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Markdown Settings
        composable(SettingsRoute.Markdown.route) {
            MarkdownSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Backup Settings
        composable(SettingsRoute.Backup.route) {
            BackupSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // About Screen
        composable(SettingsRoute.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // Debug Settings
        composable(SettingsRoute.Debug.route) {
            DebugSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
