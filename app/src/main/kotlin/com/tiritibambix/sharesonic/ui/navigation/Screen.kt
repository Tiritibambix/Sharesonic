package com.tiritibambix.sharesonic.ui.navigation

sealed class Screen(val route: String) {
    data object Settings : Screen("settings")
    data object Browser : Screen("browser/{folderId}?folderName={folderName}") {
        fun createRoute(folderId: String, folderName: String) =
            "browser/$folderId?folderName=$folderName"
        const val ARG_ID = "folderId"
        const val ARG_NAME = "folderName"
    }
    data object NowPlaying : Screen("nowplaying")
    data object Search : Screen("search")
    // No URL in the route — share URL is held in AppNavGraph state to avoid
    // Navigation Compose mishandling encoded slashes (%2F) in path segments.
    data object ShareConfirm : Screen("shareconfirm")
}
