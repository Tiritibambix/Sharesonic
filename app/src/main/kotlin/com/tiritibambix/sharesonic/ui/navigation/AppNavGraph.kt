package com.tiritibambix.sharesonic.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.ui.browser.FolderBrowserScreen
import com.tiritibambix.sharesonic.ui.browser.FolderBrowserViewModel
import com.tiritibambix.sharesonic.ui.browser.FolderBrowserViewModelFactory
import com.tiritibambix.sharesonic.ui.player.NowPlayingScreen
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import com.tiritibambix.sharesonic.ui.player.PlayerViewModelFactory
import com.tiritibambix.sharesonic.ui.settings.SettingsScreen
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModel
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModelFactory
import com.tiritibambix.sharesonic.ui.share.ShareConfirmScreen
import java.net.URLDecoder

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val settingsRepo = SettingsRepository(context)

    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo))
    val settings by settingsVm.settings.collectAsState()

    // Shared PlayerViewModel — lives for the whole nav graph
    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModelFactory(context))

    val startDestination = Screen.Settings.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsVm,
                onNavigateToBrowser = {
                    val s = settingsVm.settings.value
                    if (s.isConfigured) {
                        navController.navigate(
                            Screen.Browser.createRoute("root", "Library")
                        )
                    }
                }
            )
        }

        composable(
            route = Screen.Browser.route,
            arguments = listOf(
                navArgument(Screen.Browser.ARG_ID) { type = NavType.StringType },
                navArgument(Screen.Browser.ARG_NAME) {
                    type = NavType.StringType
                    defaultValue = "Library"
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString(Screen.Browser.ARG_ID) ?: "root"
            val folderName = backStackEntry.arguments?.getString(Screen.Browser.ARG_NAME) ?: "Library"

            val browserVm: FolderBrowserViewModel = viewModel(
                key = "browser_$folderId",
                factory = FolderBrowserViewModelFactory(settingsRepo, folderId)
            )

            FolderBrowserScreen(
                folderName = folderName,
                viewModel = browserVm,
                playerViewModel = playerVm,
                onOpenFolder = { id, name ->
                    navController.navigate(Screen.Browser.createRoute(id, name))
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
                onShareCreated = { url ->
                    navController.navigate(Screen.ShareConfirm.createRoute(url))
                }
            )
        }

        composable(Screen.NowPlaying.route) {
            NowPlayingScreen(
                viewModel = playerVm,
                onBack = { navController.popBackStack() },
                onShareCreated = { url ->
                    navController.navigate(Screen.ShareConfirm.createRoute(url))
                }
            )
        }

        composable(
            route = Screen.ShareConfirm.route,
            arguments = listOf(
                navArgument(Screen.ShareConfirm.ARG_URL) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString(Screen.ShareConfirm.ARG_URL) ?: ""
            val shareUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            ShareConfirmScreen(
                shareUrl = shareUrl,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // Auto-navigate to browser if already configured
    LaunchedEffect(settings.isConfigured) {
        if (settings.isConfigured &&
            navController.currentDestination?.route == Screen.Settings.route
        ) {
            navController.navigate(Screen.Browser.createRoute("root", "Library")) {
                popUpTo(Screen.Settings.route) { inclusive = false }
            }
        }
    }
}
