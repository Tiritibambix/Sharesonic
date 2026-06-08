package com.tiritibambix.sharesonic.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.tiritibambix.sharesonic.ui.autodj.AutoDjSettingsScreen
import com.tiritibambix.sharesonic.ui.autodj.AutoDjSettingsViewModel
import com.tiritibambix.sharesonic.ui.autodj.AutoDjSettingsViewModelFactory
import com.tiritibambix.sharesonic.ui.settings.ServerSettingsScreen
import com.tiritibambix.sharesonic.ui.settings.SettingsScreen
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModel
import com.tiritibambix.sharesonic.ui.settings.SettingsViewModelFactory
import com.tiritibambix.sharesonic.ui.settings.ThemeSettingsScreen
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksScreen
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksViewModel
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksViewModelFactory
import com.tiritibambix.sharesonic.ui.player.MiniPlayerBar
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

    val playerState by playerVm.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showMiniPlayer = playerState.currentSong != null &&
        navBackStackEntry?.destination?.route != Screen.NowPlaying.route

    // Paint the Velvet background behind everything so the slide/fold transitions
    // never reveal a flash of the window's default background through the gaps
    // between the outgoing and incoming screens.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    NavHost(
        navController = navController,
        startDestination = Screen.Settings.route,
        // Replace the default cross-fade with a directional push/pop slide —
        // new screens slide in from the right and the outgoing screen slides
        // partway out to the left (with a subtle parallax), and it reverses on back.
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        }
    ) {

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToServer = { navController.navigate(Screen.ServerSettings.route) },
                onNavigateToAutoDj = { navController.navigate(Screen.AutoDjSettings.route) },
                onNavigateToTheme = { navController.navigate(Screen.ThemeSettings.route) },
                onNavigateToPublicLinks = { navController.navigate(Screen.PublicLinks.route) }
            )
        }

        composable(Screen.ServerSettings.route) {
            ServerSettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() },
                onNavigateToBrowser = {
                    if (settingsVm.settings.value.isConfigured) {
                        navController.navigate(
                            Screen.Browser.createRoute(Screen.Browser.ROOT, "Library")
                        )
                    }
                }
            )
        }

        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PublicLinks.route) {
            val publicLinksVm: PublicLinksViewModel =
                viewModel(factory = PublicLinksViewModelFactory(settingsRepo))
            PublicLinksScreen(
                viewModel = publicLinksVm,
                onBack = { navController.popBackStack() }
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
                onOpenServerSettings = { navController.navigate(Screen.ServerSettings.route) },
                onOpenAutoDjSettings = { navController.navigate(Screen.AutoDjSettings.route) },
                onOpenThemeSettings = { navController.navigate(Screen.ThemeSettings.route) },
                onOpenPublicLinks = { navController.navigate(Screen.PublicLinks.route) },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenPlaylists = { navController.navigate(Screen.Playlists.route) },
                onShareCreated = ::onShareCreated
            )
        }

        composable(
            route = Screen.NowPlaying.route,
            // Override the global horizontal push with a vertical fold: the player
            // should feel like it unfolds upward out of the mini player bar, and
            // folds back down into it — never sideways.
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
            }
        ) {
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
                },
                miniPlayerVisible = playerState.currentSong != null
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
        composable(Screen.AutoDjSettings.route) {
            val autoDjVm: AutoDjSettingsViewModel =
                viewModel(factory = AutoDjSettingsViewModelFactory(context))
            AutoDjSettingsScreen(
                viewModel = autoDjVm,
                onBack = { navController.popBackStack() },
                miniPlayerVisible = playerState.currentSong != null
            )
        }

    } // NavHost

    // ── Mini player overlay ───────────────────────────────────────────────────
    AnimatedVisibility(
        visible = showMiniPlayer,
        // Same feel as the Now Playing ↔ Queue pager transition: a clean directional
        // slide with no fade — rises up from the bottom edge when it appears, sinks
        // back down below it when it disappears.
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MiniPlayerBar(
            state = playerState,
            onPlayPause = playerVm::playPause,
            onSkipPrev = playerVm::skipPrev,
            onSkipNext = playerVm::skipNext,
            onClick = { navController.navigate(Screen.NowPlaying.route) },
            onToggleAutoDj = playerVm::toggleAutoDj
        )
    }
    } // Box

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
