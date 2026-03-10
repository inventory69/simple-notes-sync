package dev.dettmer.simplenotes.ui.settings

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.dettmer.simplenotes.ui.settings.screens.AboutScreen
import dev.dettmer.simplenotes.ui.settings.screens.BackupSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.DebugSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.DisplaySettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.ImportSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.LanguageSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.MarkdownSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.ServerSettingsScreen
import dev.dettmer.simplenotes.ui.settings.screens.SettingsMainScreen
import dev.dettmer.simplenotes.ui.settings.screens.SyncSettingsScreen

// 🆕 v1.11.0: Smooth Fade-Transitions für Settings-Navigation
private const val NAV_ANIM_DURATION_MS = 700

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
        startDestination = SettingsRoute.Main.route,
        // 🆕 v1.11.0: Globale Fade-Transitions für alle Settings-Routen
        enterTransition = {
            fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))
        }
    ) {
        // Main Settings Overview
        composable(SettingsRoute.Main.route) {
            SettingsMainScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route.route) },
                onBack = onFinish
            )
        }
        
        // Language Settings
        composable(SettingsRoute.Language.route) {
            LanguageSettingsScreen(
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() },
                onNavigateToServerSettings = {
                    navController.navigate(SettingsRoute.Server.route) {
                        // Avoid multiple copies of server settings in back stack
                        launchSingleTop = true
                    }
                }
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
        
        // About Screen — 🔧 v1.11.0: viewModel für Easter-Egg Entwickleroptionen
        composable(SettingsRoute.About.route) {
            AboutScreen(
                viewModel = viewModel,
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
        
        // 🎨 v1.7.0: Display Settings
        composable(SettingsRoute.Display.route) {
            DisplaySettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // 🆕 Issue #21: Import Notes
        composable(SettingsRoute.Import.route) {
            ImportSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
