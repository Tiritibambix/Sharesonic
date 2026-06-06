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

    /**
     * Playlist detail — the playlist NAME is both the identifier and the display label
     * (mStream Velvet uses name as the primary key for all playlist mutations).
     * Encoded with URLEncoder to handle spaces and special chars safely.
     */
    data object PlaylistDetail : Screen("playlist/{playlistName}") {
        fun createRoute(name: String): String = "playlist/${name.urlEncode()}"
        const val ARG_NAME = "playlistName"
    }

    /** Auto-DJ configuration panel, accessible from Settings. */
    data object AutoDjSettings : Screen("autodj-settings")
}

private fun String.urlEncode() = java.net.URLEncoder.encode(this, "UTF-8")
