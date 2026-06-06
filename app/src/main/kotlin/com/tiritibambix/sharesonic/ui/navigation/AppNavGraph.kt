package com.tiritibambix.sharesonic.ui.navigation

import androidx.compose.runtime.*
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
import com.tiritibambix.sharesonic.ui.search.SearchScreen
import com.tiritibambix.sharesonic.ui.search.SearchViewModel
import com.tiritibambix.sharesonic.ui.search.SearchViewModelFactory
import com.tiritibambix.sharesonic.ui.playlists.PlaylistDetailScreen
import com.tiritibambix.sharesonic.ui.playlists.PlaylistDetailViewModel
import com.tiritibambix.sharesonic.ui.playlists.PlaylistDetailViewModelFactory
import com.tiritibambix.sharesonic.ui.playlists.PlaylistsScreen
import com.tiritibambix.sharesonic.ui.playlists.PlaylistsViewModel
import com.tiritibambix.sharesonic.ui.playlists.PlaylistsViewModelFactory
import com.tiritibambix.sharesonic.ui.settings.SettingsScreen
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModel
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModelFactory
import com.tiritibambix.sharesonic.ui.share.ShareConfirmScreen

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val settingsRepo = SettingsRepository(context)

    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo))
    val settings by settingsVm.settings.collectAsState()

    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModelFactory(context))

    var pendingShareUrl by remember { mutableStateOf("") }
    fun onShareCreated(url: String) {
        pendingShareUrl = url
        navController.navigate(Screen.ShareConfirm.route)
    }

    NavHost(navController = navController, startDestination = Screen.Settings.route) {

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsVm,
                onNavigateToBrowser = {
                    if (settingsVm.settings.value.isConfigured) {
                        navController.navigate(
                            Screen.Browser.createRoute(Screen.Browser.ROOT, "Library")
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
            val rawId = backStackEntry.arguments?.getString(Screen.Browser.ARG_ID)
                ?: Screen.Browser.ROOT
            // Decode Base64-encoded mStream path; "root" passes through unchanged.
            val folderPath = Screen.Browser.decodePath(rawId)
            val folderName = backStackEntry.arguments?.getString(Screen.Browser.ARG_NAME)
                ?: "Library"

            val browserVm: FolderBrowserViewModel = viewModel(
                key = "browser_$rawId",
                factory = FolderBrowserViewModelFactory(settingsRepo, folderPath)
            )

            FolderBrowserScreen(
                folderName = folderName,
                viewModel = browserVm,
                playerViewModel = playerVm,
                onOpenFolder = { path, name ->
                    navController.navigate(Screen.Browser.createRoute(path, name))
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenPlaylists = { navController.navigate(Screen.Playlists.route) },
                onShareCreated = ::onShareCreated
            )
        }

        composable(Screen.NowPlaying.route) {
            NowPlayingScreen(
                viewModel = playerVm,
                onBack = { navController.popBackStack() },
                onShareCreated = ::onShareCreated
            )
        }

        composable(Screen.Search.route) {
            val searchVm: SearchViewModel = viewModel(factory = SearchViewModelFactory(settingsRepo))
            SearchScreen(
                viewModel = searchVm,
                playerViewModel = playerVm,
                settings = settings,
                onBack = { navController.popBackStack() },
                onOpenFolder = { path, name ->
                    navController.navigate(Screen.Browser.createRoute(path, name))
                },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
                onShareCreated = ::onShareCreated
            )
        }

        composable(Screen.ShareConfirm.route) {
            ShareConfirmScreen(
                shareUrl = pendingShareUrl,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Playlists.route) {
            val playlistsVm: PlaylistsViewModel =
                viewModel(factory = PlaylistsViewModelFactory(settingsRepo))
            PlaylistsScreen(
                viewModel = playlistsVm,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { name ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(name))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument(Screen.PlaylistDetail.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments
                ?.getString(Screen.PlaylistDetail.ARG_NAME) ?: return@composable
            // URL-decode the name (encoded by createRoute)
            val playlistName = java.net.URLDecoder.decode(rawName, "UTF-8")
            val detailVm: PlaylistDetailViewModel = viewModel(
                key = "playlist_$playlistName",
                factory = PlaylistDetailViewModelFactory(settingsRepo, playlistName)
            )
            PlaylistDetailScreen(
                initialName = playlistName,
                viewModel = detailVm,
                playerViewModel = playerVm,
                onBack = { navController.popBackStack() },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) }
            )
        }
    }

    // Auto-navigate to browser when already configured
    LaunchedEffect(settings.isConfigured) {
        if (settings.isConfigured &&
            navController.currentDestination?.route == Screen.Settings.route
        ) {
            navController.navigate(
                Screen.Browser.createRoute(Screen.Browser.ROOT, "Library")
            ) {
                popUpTo(Screen.Settings.route) { inclusive = false }
            }
        }
    }
}
