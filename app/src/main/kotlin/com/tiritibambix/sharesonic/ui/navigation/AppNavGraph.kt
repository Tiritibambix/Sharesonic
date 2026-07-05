package com.tiritibambix.sharesonic.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import com.tiritibambix.sharesonic.ui.player.PlayerViewModelFactory
import com.tiritibambix.sharesonic.ui.search.ArtistResultsScreen
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
import com.tiritibambix.sharesonic.ui.settings.EqSettingsScreen
import com.tiritibambix.sharesonic.ui.settings.EqViewModel
import com.tiritibambix.sharesonic.ui.settings.EqViewModelFactory
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksScreen
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksViewModel
import com.tiritibambix.sharesonic.ui.publiclinks.PublicLinksViewModelFactory
import com.tiritibambix.sharesonic.ui.player.PlayerPanel
import com.tiritibambix.sharesonic.ui.player.rememberPlayerPanelState
import com.tiritibambix.sharesonic.ui.share.ShareConfirmScreen

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val settingsRepo = SettingsRepository(context)

    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo))
    val settings by settingsVm.settings.collectAsState()

    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModelFactory(context))

    val playerState by playerVm.state.collectAsState()
    val panelState = rememberPlayerPanelState()
    val showMiniPlayer = playerState.currentSong != null

    var pendingShareUrl by remember { mutableStateOf("") }
    fun onShareCreated(url: String) {
        pendingShareUrl = url
        // Collapse the player sheet so the ShareConfirm route isn't hidden
        // under it (the sheet at t=1 covers the whole screen, and users can
        // trigger a share from Now Playing).
        panelState.collapse()
        navController.navigate(Screen.ShareConfirm.route)
    }

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
        // Material "shared axis X" — both screens travel a short, equal distance in
        // the same direction while cross-fading. The previous full-width slide-over
        // (new screen sliding the full width while the old one only nudged a quarter
        // of the way out) left both screens visibly overlapping mid-transition,
        // which read as messy/cluttered. Shorter travel + fade removes that overlap
        // entirely and feels far more cohesive — push right→left, pop left→right.
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
        }
    ) {

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToServer = { navController.navigate(Screen.ServerSettings.route) },
                onNavigateToAutoDj = { navController.navigate(Screen.AutoDjSettings.route) },
                onNavigateToEqualizer = { navController.navigate(Screen.EqualizerSettings.route) },
                onNavigateToTheme = { navController.navigate(Screen.ThemeSettings.route) },
                onNavigateToPublicLinks = { navController.navigate(Screen.PublicLinks.route) }
            )
        }

        composable(Screen.ServerSettings.route) {
            ServerSettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() },
                onNavigateToBrowser = {
                    // ServerSettingsScreen now invokes this only from save()'s
                    // onSaved completion hook — i.e. after the new settings have
                    // actually landed in DataStore and the fields looked valid.
                    // Re-checking settingsVm.settings.value here (the original
                    // code) was itself a stale-snapshot trap and the root of both
                    // the double-tap bug and the "Server not configured" race.
                    navController.navigate(
                        Screen.Browser.createRoute(Screen.Browser.ROOT, "Library")
                    )
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

        composable(Screen.EqualizerSettings.route) {
            val eqVm: EqViewModel = viewModel(factory = EqViewModelFactory(settingsRepo))
            EqSettingsScreen(
                viewModel = eqVm,
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
            // Decode Base64-encoded Velvet path; "root" passes through unchanged.
            val folderPath = Screen.Browser.decodePath(rawId)
            // createRoute() URL-encodes the display name (URLEncoder — form-urlencoded
            // semantics, spaces become '+'); it must be decoded back symmetrically or
            // the top bar literally shows '+' wherever the folder name had a space.
            val rawName = backStackEntry.arguments?.getString(Screen.Browser.ARG_NAME)
                ?: "Library"
            val folderName = runCatching { java.net.URLDecoder.decode(rawName, "UTF-8") }
                .getOrDefault(rawName)

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
                onOpenEqualizer = { navController.navigate(Screen.EqualizerSettings.route) },
                onOpenNowPlaying = { /* mini-bar stays collapsed; user expands manually */ },
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenPlaylists = { navController.navigate(Screen.Playlists.route) },
                // Home icon in the top bar: pop every browser frame in one go
                // and land on a fresh root browser. Settings stays as the
                // start destination so system Back still exits the app.
                onGoRoot = {
                    navController.navigate(
                        Screen.Browser.createRoute(Screen.Browser.ROOT, "Library")
                    ) {
                        popUpTo(Screen.Settings.route) {
                            inclusive = false
                            saveState = false
                        }
                        launchSingleTop = true
                    }
                },
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
                onOpenArtistResults = { artistName ->
                    navController.navigate(Screen.ArtistResults.createRoute(artistName))
                },
                onOpenNowPlaying = { /* mini-bar stays collapsed; user expands manually */ },
                onShareCreated = ::onShareCreated
            )
        }

        composable(
            route = Screen.ArtistResults.route,
            arguments = listOf(
                navArgument(Screen.ArtistResults.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments?.getString(Screen.ArtistResults.ARG_NAME) ?: ""
            val artistName = runCatching { java.net.URLDecoder.decode(rawName, "UTF-8") }
                .getOrDefault(rawName)

            // Share the SearchViewModel instance with the Search screen still on the
            // back stack below us — the matching songs were stashed there via
            // SearchViewModel.setArtistResults() before navigating here.
            val searchEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Search.route)
            }
            val searchVm: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(settingsRepo),
                viewModelStoreOwner = searchEntry
            )
            val songs by searchVm.artistResults.collectAsState()

            ArtistResultsScreen(
                artistName = artistName,
                songs = songs,
                settings = settings,
                playerViewModel = playerVm,
                onBack = { navController.popBackStack() },
                onOpenNowPlaying = { /* mini-bar stays collapsed; user expands manually */ }
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
                onOpenNowPlaying = { /* mini-bar stays collapsed; user expands manually */ }
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

    // ── Drag-up player panel ──────────────────────────────────────────────────
    // Unified mini + Now Playing surface: drag the mini bar (or tap it) to
    // expand to full screen, drag or swipe down to collapse, system Back
    // collapses when expanded. Replaces the old separate Screen.NowPlaying
    // route + AnimatedVisibility mini bar.
    PlayerPanel(
        state = panelState,
        playerState = playerState,
        visible = showMiniPlayer,
        onPlayPause = playerVm::playPause,
        onSkipPrev = playerVm::skipPrev,
        onSkipNext = playerVm::skipNext,
        onToggleAutoDj = playerVm::toggleAutoDj,
        onShareCreated = ::onShareCreated,
        viewModel = playerVm,
    )
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
