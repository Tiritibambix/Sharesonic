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
    data object ShareConfirm : Screen("shareconfirm/{shareUrl}") {
        fun createRoute(shareUrl: String) = "shareconfirm/${shareUrl.encode()}"
        const val ARG_URL = "shareUrl"
    }
}

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")
