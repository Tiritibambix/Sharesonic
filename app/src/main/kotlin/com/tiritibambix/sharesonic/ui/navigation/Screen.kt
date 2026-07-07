package com.tiritibambix.sharesonic.ui.navigation

import java.util.Base64

sealed class Screen(val route: String) {

    data object Settings : Screen("settings")

    data object Browser : Screen("browser/{folderId}?folderName={folderName}") {
        /**
         * Velvet paths like "/Reggae/Bob Marley" contain slashes that would
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

    data object Search : Screen("search")

    /**
     * Standalone "folder-like" results page for an artist tapped in [Search] whose
     * tag-derived name has no corresponding on-disk folder. The matching songs
     * themselves travel via the shared SearchViewModel instance (see
     * `SearchViewModel.artistResults`) — only the display name goes through the
     * nav route, URL-encoded since artist names can contain arbitrary characters.
     */
    data object ArtistResults : Screen("artist-results/{artistName}") {
        fun createRoute(artistName: String): String = "artist-results/${artistName.urlEncode()}"
        const val ARG_NAME = "artistName"
    }

    // Share URL held in AppNavGraph state — no URL embedded in route.
    data object ShareConfirm : Screen("shareconfirm")

    data object Playlists : Screen("playlists")

    /**
     * Playlist detail — the playlist NAME is both the identifier and the display label
     * (Velvet uses name as the primary key for all playlist mutations).
     * Encoded with URLEncoder to handle spaces and special chars safely.
     */
    data object PlaylistDetail : Screen("playlist/{playlistName}") {
        fun createRoute(name: String): String = "playlist/${name.urlEncode()}"
        const val ARG_NAME = "playlistName"
    }

    /** Auto-DJ configuration panel, accessible from Settings. */
    data object AutoDjSettings : Screen("autodj-settings")

    /** Velvet server URL / account / connection test, accessible from Settings. */
    data object ServerSettings : Screen("server-settings")

    /** Velvet / Dark / Light visual theme picker, accessible from Settings. */
    data object ThemeSettings : Screen("theme-settings")

    /** Public Links management (list / copy / open / revoke), accessible from Settings. */
    data object PublicLinks : Screen("public-links")

    /** Native Android equalizer (per-band gains), accessible from the drawer. */
    data object EqualizerSettings : Screen("equalizer-settings")

    /** UI language picker (12 languages + system default), accessible from the drawer. */
    data object LanguageSettings : Screen("language-settings")
}

private fun String.urlEncode() = java.net.URLEncoder.encode(this, "UTF-8")
