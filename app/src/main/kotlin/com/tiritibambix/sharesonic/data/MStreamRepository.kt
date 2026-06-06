package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.MStreamApiService
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamFile
import com.tiritibambix.sharesonic.data.api.models.MStreamFileMetaWrapper
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistAddSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistDeleteRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistEntry
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistLoadRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistNewRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRemoveSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRenameRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamRandomSongsRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamShareListItem
import com.tiritibambix.sharesonic.data.api.models.MStreamShareRequest
import com.tiritibambix.sharesonic.data.api.models.NativeSearchRequest
import com.tiritibambix.sharesonic.data.api.models.ScrobbleFilepathRequest
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.api.models.TopLevelDir

class MStreamRepository(private val api: MStreamApiService) {

    /** Authenticate and return the JWT token. */
    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val resp = api.login(MStreamLoginRequest(username, password))
            val token = resp.token
            if (!token.isNullOrEmpty()) Result.Success(token)
            else Result.Error(resp.err ?: "Login failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Browse a directory.
     *
     * @param token          JWT bearer token
     * @param path           mStream directory path; empty string for root
     * @param pullMetadata   when true, file entries include filepath + audio tags
     */
    suspend fun fileExplorer(
        token: String,
        path: String,
        pullMetadata: Boolean = false
    ): Result<FileExplorerResponse> {
        return try {
            val resp = api.fileExplorer(
                token,
                FileExplorerRequest(directory = path, sort = true, pullMetadata = pullMetadata)
            )
            Result.Success(resp)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Map a FileExplorerResponse to a list of EntryDto for the browser UI.
     *
     * Directory entry.id = mStream path      (for navigation)
     * File entry.id      = mStream filepath  (for native /media/<filepath>?token= streaming)
     *
     * Only audio files with a known filepath (pullMetadata=true) are included.
     */
    fun toEntries(response: FileExplorerResponse, currentPath: String): List<EntryDto> {
        val dirs: List<EntryDto> = response.directories.map { dir ->
            val dirPath: String = dir.path
                ?: if (currentPath.isEmpty()) "/${dir.name}" else "$currentPath/${dir.name}"
            EntryDto(id = dirPath, title = dir.name, isDir = true, path = dirPath)
        }

        val files: List<EntryDto> = response.files
            .filter { it.isAudio }
            .mapNotNull { file: MStreamFile -> fileToEntryDto(file) }

        return dirs + files
    }

    /**
     * Recursively collect all playable tracks under [path] for shuffle.
     * Uses pullMetadata=true so every file has a filepath for native streaming.
     */
    suspend fun collectSongs(token: String, path: String): List<EntryDto> {
        val result = fileExplorer(token, path, pullMetadata = true)
        if (result !is Result.Success) return emptyList()

        val resp: FileExplorerResponse = result.data
        val files: List<EntryDto> = resp.files
            .filter { it.isAudio }
            .mapNotNull { file: MStreamFile -> fileToEntryDto(file) }

        val nested: List<EntryDto> = resp.directories.flatMap { dir ->
            val dirPath: String = dir.path
                ?: if (path.isEmpty()) "/${dir.name}" else "$path/${dir.name}"
            collectSongs(token, dirPath)
        }

        return files + nested
    }

    /**
     * Create a public share link for a single track.
     *
     * @param token    JWT bearer token
     * @param filepath Full mStream filepath (e.g. "library/Artist/Album/track.mp3")
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun share(token: String, filepath: String): Result<String> =
        shareFilepaths(token, listOf(filepath))

    /**
     * Create a public share link for all audio tracks under [path].
     * Recursively collects every track and creates a single shared playlist
     * with a 14-day expiry.
     *
     * @param token JWT bearer token
     * @param path  mStream directory path (e.g. "/library/Artist")
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun shareFolder(token: String, path: String): Result<String> {
        val songs = collectSongs(token, path)
        if (songs.isEmpty()) return Result.Error("No audio files found in folder")
        return shareFilepaths(token, songs.map { it.id }, expiryDays = 14)
    }

    private suspend fun shareFilepaths(
        token: String,
        filepaths: List<String>,
        expiryDays: Int? = null
    ): Result<String> {
        return try {
            // Velvet interprets `time` as a number of days (expiresIn: '${time}d')
            val resp = api.share(token, MStreamShareRequest(playlist = filepaths, time = expiryDays))
            val shareId = resp.playlistId
            if (!shareId.isNullOrBlank()) Result.Success(shareId)
            else Result.Error("Share failed: no shareId returned")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Native search ─────────────────────────────────────────────────────────

    /**
     * Full-text search using the native Velvet API (JWT auth — no Subsonic password needed).
     * Maps results to [SearchResult3] so the SearchScreen can consume them unchanged.
     *
     * Songs come back with filepath IDs → stream and share via native endpoints.
     * Artists have `id = name` (used to navigate to the artist folder).
     */
    suspend fun search(token: String, query: String): Result<SearchResult3> {
        return try {
            val resp = api.nativeSearch(token, NativeSearchRequest(search = query))

            val songs = resp.title.mapNotNull { item ->
                val fp = item.filepath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // name is "Artist - Title"; split on first " - " if present
                val name     = item.name.orEmpty()
                val dashIdx  = name.indexOf(" - ")
                val title    = if (dashIdx >= 0) name.substring(dashIdx + 3) else name
                val artist   = if (dashIdx >= 0) name.substring(0, dashIdx).takeIf { it.isNotBlank() } else null
                EntryDto(
                    id       = fp,
                    title    = title.takeIf { it.isNotBlank() } ?: fp.substringAfterLast('/').substringBeforeLast('.'),
                    artist   = artist,
                    coverArt = item.albumArtFile,
                    isDir    = false,
                    path     = fp
                )
            }

            val albums = resp.albums.mapNotNull { item ->
                val fp = item.filepath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                EntryDto(
                    id       = fp,
                    title    = item.name?.takeIf { it.isNotBlank() } ?: fp.substringAfterLast('/'),
                    coverArt = item.albumArtFile,
                    isDir    = true,
                    path     = fp
                )
            }

            val artists = resp.artists.mapNotNull { a ->
                val n = a.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TopLevelDir(id = n, name = n)
            }

            Result.Success(SearchResult3(song = songs, album = albums, artist = artists))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Native playlists ──────────────────────────────────────────────────────

    /** List all playlists for the authenticated user (metadata only — no song entries). */
    suspend fun getPlaylists(token: String): Result<List<NativePlaylist>> = try {
        Result.Success(api.getPlaylists(token))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /**
     * Fetch the full song list for [name].
     * Each entry contains the database row ID (for remove-song) and the filepath (for streaming).
     */
    suspend fun loadPlaylist(token: String, name: String): Result<List<NativePlaylistEntry>> = try {
        Result.Success(api.loadPlaylist(token, NativePlaylistLoadRequest(name)))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /** Create a new empty playlist with the given title. Fire-and-forget on success. */
    suspend fun createPlaylist(token: String, title: String): Result<Unit> = try {
        api.createPlaylist(token, NativePlaylistNewRequest(title))
        Result.Success(Unit)
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /** Delete a playlist by name. Fire-and-forget. */
    suspend fun deletePlaylist(token: String, name: String) {
        try { api.deletePlaylist(token, NativePlaylistDeleteRequest(name)) } catch (_: Exception) {}
    }

    /** Rename a playlist. Returns an error if the new name already exists. */
    suspend fun renamePlaylist(token: String, oldName: String, newName: String): Result<Unit> = try {
        api.renamePlaylist(token, NativePlaylistRenameRequest(oldName, newName))
        Result.Success(Unit)
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /** Append a single song (by filepath) to an existing playlist. Fire-and-forget. */
    suspend fun addSongToPlaylist(token: String, filepath: String, playlistName: String) {
        try { api.addSongToPlaylist(token, NativePlaylistAddSongRequest(filepath, playlistName)) }
        catch (_: Exception) {}
    }

    /**
     * Remove a song from a playlist by its database entry ID.
     * The entry ID comes from [NativePlaylistSong.id] in the getall response.
     * Fire-and-forget.
     */
    suspend fun removeSongFromPlaylist(token: String, entryId: Int) {
        try { api.removeSongFromPlaylist(token, NativePlaylistRemoveSongRequest(entryId)) }
        catch (_: Exception) {}
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Refresh the JWT token. Call on app boot when a stored token exists.
     * On success, the caller should persist the new token via SettingsRepository.saveToken().
     */
    suspend fun refreshToken(token: String): Result<String> = try {
        val resp = api.refreshToken(token)
        if (!resp.token.isNullOrEmpty()) Result.Success(resp.token)
        else Result.Error("Token refresh failed")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    // ── Random songs ──────────────────────────────────────────────────────────

    /**
     * Fetch [count] random songs from the library using the native Velvet endpoint.
     * Makes [count] sequential calls, each passing the server's updated ignore list
     * so every song is unique. Replaces Subsonic getRandomSongs for shuffle-all.
     *
     * Performance: ~30 calls on a LAN ≈ 1.5–2 s.
     */
    suspend fun getRandomSongs(token: String, count: Int = 30): Result<List<EntryDto>> {
        val results = mutableListOf<EntryDto>()
        var ignoreList = emptyList<Int>()
        repeat(count) {
            try {
                val resp = api.randomSong(token, MStreamRandomSongsRequest(ignoreList = ignoreList))
                ignoreList = resp.ignoreList
                val wrapper = resp.songs.firstOrNull() ?: return@repeat
                fileMetaWrapperToEntryDto(wrapper)?.let { results.add(it) }
            } catch (_: Exception) { return@repeat }
        }
        return if (results.isNotEmpty()) Result.Success(results)
        else Result.Error("No songs returned")
    }

    // ── Share list / revoke ───────────────────────────────────────────────────

    /** Fetch the authenticated user's share links. */
    suspend fun getShareList(token: String): Result<List<MStreamShareListItem>> = try {
        Result.Success(api.getShareList(token))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /**
     * Delete every share link whose expiry timestamp is in the past.
     * Silently ignores network and parse errors — best-effort cleanup only.
     */
    suspend fun cleanupExpiredShares(token: String) {
        try {
            val shares = api.getShareList(token)
            val nowSeconds = System.currentTimeMillis() / 1000L
            shares.forEach { share ->
                val exp = share.expires ?: return@forEach
                if (exp < nowSeconds) {
                    try { api.deleteShare(token, share.playlistId) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    // ── On-demand art ─────────────────────────────────────────────────────────

    /**
     * Extract embedded album art from any audio file and return the cache filename.
     * Returns null when no embedded art is found or on network error.
     * The art URL is: <serverUrl>/album-art/<aaFile>?token=<jwt>
     */
    suspend fun getArtFilename(token: String, filepath: String): String? = try {
        api.getArt(token, filepath).aaFile
    } catch (_: Exception) { null }

    // ── Scrobble ──────────────────────────────────────────────────────────────

    /**
     * Send a "now playing" ping to ListenBrainz.
     * Fire-and-forget — silently ignored if ListenBrainz is not configured in mStream.
     */
    suspend fun listenBrainzNowPlaying(token: String, filepath: String) {
        try { api.listenBrainzNowPlaying(token, ScrobbleFilepathRequest(filepath)) }
        catch (_: Exception) {}
    }

    /**
     * Scrobble to Last.fm and ListenBrainz by filepath.
     * Fire-and-forget — silently ignored if the services are not configured in mStream.
     * Call after 50% of track duration has elapsed.
     */
    suspend fun scrobble(token: String, filepath: String) {
        try { api.lastfmScrobble(token, ScrobbleFilepathRequest(filepath)) } catch (_: Exception) {}
        try { api.listenBrainzScrobble(token, ScrobbleFilepathRequest(filepath)) } catch (_: Exception) {}
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fileToEntryDto(file: MStreamFile): EntryDto? {
        // The full mStream filepath (library + relative path) is our stable identifier.
        // It is used to build the native stream URL: /media/<filepath>?token=<jwt>
        val filepath = file.mStreamFilepath ?: return null
        val meta = file.metadata?.metadata
        return EntryDto(
            id = filepath,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: file.name,
            artist = meta?.artist,
            album = meta?.album,
            coverArt = meta?.albumArt,
            isDir = false,
            path = filepath
        )
    }

    /**
     * Map a [MStreamFileMetaWrapper] (from /api/v1/db/random-songs) to an [EntryDto].
     * Uses [MStreamFileMetaWrapper.filepath] as the entry ID — same native filepath
     * format as file-explorer pullMetadata=true, so streaming and sharing work identically.
     */
    private fun fileMetaWrapperToEntryDto(wrapper: MStreamFileMetaWrapper): EntryDto? {
        val filepath = wrapper.filepath?.takeIf { it.isNotBlank() } ?: return null
        val meta = wrapper.metadata
        return EntryDto(
            id = filepath,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: filepath.substringAfterLast('/').substringBeforeLast('.'),
            artist = meta?.artist,
            album = meta?.album,
            coverArt = meta?.albumArt,
            isDir = false,
            path = filepath
        )
    }
}
