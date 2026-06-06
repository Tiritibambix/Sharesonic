package com.tiritibambix.sharesonic.ui.navigation

import java.util.Base64

sealed class Screen(val route: String) {

    data object Settings : Screen("settings")

    data object Browser : Screen("browser/{folderId}?folderName={folderName}") {
        /**
         * mStream paths like "/Reggae/Bob Marley" contain slashes that would
         * break Navigation Compose path-segment matching.
         * Encode with URL-safe Base64 (no padding) so the route stays clean.
         * The special sentinel "root" is passed as-is.
         */
        fun createRoute(folderPath: String, folderName: String): String {
            val encodedId = if (folderPath == ROOT) ROOT
                else Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(folderPath.toByteArray(Charsets.UTF_8))
            return "browser/$encodedId?folderName=${folderName.urlEncode()}"
        }

        fun decodePath(folderId: String): String = if (folderId == ROOT) ROOT
            else String(Base64.getUrlDecoder().decode(folderId), Charsets.UTF_8)

        const val ROOT     = "root"
        const val ARG_ID   = "folderId"
        const val ARG_NAME = "folderName"
    }

    data object NowPlaying : Screen("nowplaying")

    data object Search : Screen("search")

    // Share URL held in AppNavGraph state — no URL embedded in route.
    data object ShareConfirm : Screen("shareconfirm")

    data object Playlists : Screen("playlists")

    data object PlaylistDetail : Screen("playlist/{playlistId}?name={name}") {
        fun createRoute(id: String, name: String): String =
            "playlist/${id.urlEncode()}?name=${name.urlEncode()}"
        const val ARG_ID   = "playlistId"
        const val ARG_NAME = "name"
    }
}

private fun String.urlEncode() = java.net.URLEncoder.encode(this, "UTF-8")
