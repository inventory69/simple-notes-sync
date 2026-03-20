package dev.dettmer.simplenotes.ui.settings

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

// v2.0.0: Smooth fade transitions for Settings sub-screens
private const val NAV_ANIM_DURATION_MS = 500

/**
 * Settings navigation host with all routes
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun SettingsNavHost(navController: NavHostController, viewModel: SettingsViewModel, onFinish: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Snackbar-Collector — ersetzt den Toast-LaunchedEffect aus ComposeSettingsActivity.
    // scope.launch macht collect nicht-blockierend: neue Messages können sofort empfangen
    // werden, ohne auf das Ende der aktuell angezeigten Snackbar zu warten.
    // dismiss() entfernt die vorherige Snackbar sofort, damit der Countdown sichtbar bleibt.
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Opaque background prevents the translucent Activity window from
    // showing the note list through during internal NavHost fade transitions.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        NavHost(
            navController = navController,
            startDestination = SettingsRoute.Main.route,
            // v2.0.0: Smooth fade transitions for Settings sub-screens
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

        // Snackbar über NavHost rendern (sichtbar über NavHost-Transitions).
        // imePadding() stellt sicher, dass die Snackbar oberhalb der Tastatur erscheint.
        // navigationBarsPadding() + padding(bottom) kompensiert enableEdgeToEdge()
        // in ComposeSettingsActivity, damit die Snackbar oberhalb der Navigationsleiste
        // mit ausreichend Abstand erscheint.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        )
    }
}
