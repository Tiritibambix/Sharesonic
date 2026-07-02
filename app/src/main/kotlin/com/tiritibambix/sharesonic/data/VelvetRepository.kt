package com.tiritibambix.sharesonic.data

import android.util.Log
import com.google.gson.GsonBuilder
import com.tiritibambix.sharesonic.data.api.VelvetApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import com.tiritibambix.sharesonic.data.api.models.ArtistFolderSongsRequest
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetFile
import com.tiritibambix.sharesonic.data.api.models.VelvetFileMetaWrapper
import com.tiritibambix.sharesonic.data.api.models.VelvetLoginRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistAddSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistDeleteRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistEntry
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistLoadRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistNewRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRemoveSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRenameRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistSaveRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetRateSongRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetShareListItem
import com.tiritibambix.sharesonic.data.api.models.VelvetShareRequest
import com.tiritibambix.sharesonic.data.api.models.NativeSearchRequest
import com.tiritibambix.sharesonic.data.api.models.RecursiveScanRequest
import com.tiritibambix.sharesonic.data.api.models.ScrobbleFilepathRequest
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.api.models.TopLevelDir

class VelvetRepository(private val api: VelvetApiService) {

    companion object {
        private const val TAG = "VelvetRepository"

        /** Max tracks materialized for a folder shuffle (see [collectSongsFast]). Bigger
         *  folders are randomly sampled down to this — a 100k-track queue is infeasible. */
        private const val SHUFFLE_MAX = 5000

        /** Filepaths per /db/metadata/batch request, to bound each payload. */
        private const val METADATA_CHUNK = 2000
    }

    /** Used only by [rateSong] — see the comment there for why null serialization is needed. */
    private val rateSongGson = GsonBuilder().serializeNulls().create()

    /** Authenticate and return the JWT token. */
    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val resp = api.login(VelvetLoginRequest(username, password))
            val token = resp.token
            if (!token.isNullOrEmpty()) Result.Success(token)
            else Result.Error(resp.err ?: "Login failed")
        } catch (e: Exception) {
            Result.Error(friendlyNetworkErrorMessage(e))
        }
    }

    /**
     * Authenticate and return both the JWT token and the server's vpaths.
     * Used by [com.tiritibambix.sharesonic.ui.settings.SettingsViewModel.testConnection]
     * to persist vpaths for Auto-DJ source-folder selection.
     */
    suspend fun loginFull(username: String, password: String): Result<Pair<String, List<String>>> {
        return try {
            val resp = api.login(VelvetLoginRequest(username, password))
            val token = resp.token
            if (!token.isNullOrEmpty()) Result.Success(Pair(token, resp.vpaths))
            else Result.Error(resp.err ?: "Login failed")
        } catch (e: Exception) {
            Result.Error(friendlyNetworkErrorMessage(e))
        }
    }

    /**
     * Browse a directory.
     *
     * @param token          JWT bearer token
     * @param path           Velvet directory path; empty string for root
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow cancellation — structured concurrency depends on it
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Map a FileExplorerResponse to a list of EntryDto for the browser UI.
     *
     * Directory entry.id = Velvet path      (for navigation)
     * File entry.id      = Velvet filepath  (for native /media/<filepath>?token= streaming)
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
            .mapNotNull { file: VelvetFile -> fileToEntryDto(file) }

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
            .mapNotNull { file: VelvetFile -> fileToEntryDto(file) }

        val nested: List<EntryDto> = resp.directories.flatMap { dir ->
            val dirPath: String = dir.path
                ?: if (path.isEmpty()) "/${dir.name}" else "$path/${dir.name}"
            collectSongs(token, dirPath)
        }

        return files + nested
    }

    /**
     * Playable tracks under [path] for shuffle, gathered with server-side requests instead
     * of [collectSongs]' recursive per-subfolder client walk — so it scales to very large
     * folders (tens of thousands of files) that would otherwise hang the client:
     *
     *  1. `/api/v1/file-explorer/recursive` → every filepath in the subtree (one request),
     *  2. `/api/v1/db/metadata/batch`       → their metadata (chunked into a few requests).
     *
     * A folder can hold 100k+ files (e.g. a whole-genre library); materializing that many
     * queue entries + their metadata is infeasible on-device, so the result is capped at
     * [SHUFFLE_MAX], sampled randomly across the *whole* folder (shuffle filepaths, then
     * take the cap). Folders under the cap are returned in full. Unindexed files fall back
     * to a minimal EntryDto (filepath only) so they stay playable.
     *
     * Build the repo with [com.tiritibambix.sharesonic.data.api.VelvetClient.buildLongTimeout]
     * when calling this — the server-side recursive walk can exceed the normal read timeout.
     */
    suspend fun collectSongsFast(token: String, path: String): List<EntryDto> {
        val filepaths = try {
            api.recursiveScan(token, RecursiveScanRequest(path))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        if (filepaths.isEmpty()) return emptyList()

        val selected = if (filepaths.size > SHUFFLE_MAX) filepaths.shuffled().take(SHUFFLE_MAX) else filepaths

        val meta = HashMap<String, VelvetFileMetaWrapper>(selected.size)
        for (chunk in selected.chunked(METADATA_CHUNK)) {
            val part: Map<String, VelvetFileMetaWrapper> = try {
                api.metadataBatch(token, chunk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            meta.putAll(part)
        }

        return selected.map { fp ->
            meta[fp]?.let { fileMetaWrapperToEntryDto(it) }
                ?: EntryDto(id = fp, title = fp.substringAfterLast('/'), isDir = false, path = fp)
        }
    }

    /**
     * Cover art for a "leaf" folder — i.e. one containing only audio files, no
     * subdirectories. Used by the browser to show album art instead of a generic
     * folder icon for album-like folders.
     *
     * Returns the album-art cache filename of the first track that has one, or
     * null if [path] has subdirectories, has no files, or none of its files carry
     * embedded art.
     */
    suspend fun leafFolderArt(token: String, path: String): String? {
        val result = fileExplorer(token, path, pullMetadata = true)
        if (result !is Result.Success) return null

        val resp = result.data
        if (resp.directories.isNotEmpty()) return null

        return resp.files.firstNotNullOfOrNull { it.metadata?.metadata?.albumArt }
    }

    /**
     * Create a public share link for a single track.
     *
     * @param token      JWT bearer token
     * @param filepath   Full Velvet filepath (e.g. "library/Artist/Album/track.mp3")
     * @param expiryDays Number of days until the link expires; null/omit → permanent link
     *                   (mirrors the "days until expiration" field in Velvet's share UI)
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun share(token: String, filepath: String, expiryDays: Int? = null): Result<String> =
        shareFilepaths(token, listOf(filepath), expiryDays)

    /**
     * Create a public share link for all audio tracks under [path].
     * Recursively collects every track and creates a single shared playlist.
     *
     * @param token      JWT bearer token
     * @param path       Velvet directory path (e.g. "/library/Artist")
     * @param expiryDays Number of days until the link expires; null/omit → permanent link
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun shareFolder(token: String, path: String, expiryDays: Int? = null): Result<String> {
        val songs = collectSongs(token, path)
        if (songs.isEmpty()) return Result.Error("No audio files found in folder")
        return shareFilepaths(token, songs.map { it.id }, expiryDays)
    }

    /**
     * Create a public share link for an explicit list of filepaths — used to share
     * the current playback queue as a single shared playlist (mirrors Velvet
     * Velvet's "share queue" behaviour). Pass only native filepath IDs; Subsonic
     * numeric-ID songs aren't shareable through this endpoint (filter them out first).
     *
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun shareQueue(token: String, filepaths: List<String>, expiryDays: Int? = null): Result<String> {
        if (filepaths.isEmpty()) return Result.Error("Queue is empty")
        return shareFilepaths(token, filepaths, expiryDays)
    }

    private suspend fun shareFilepaths(
        token: String,
        filepaths: List<String>,
        expiryDays: Int? = null
    ): Result<String> {
        return try {
            // Velvet interprets `time` as a number of days (expiresIn: '${time}d')
            val resp = api.share(token, VelvetShareRequest(playlist = filepaths, time = expiryDays))
            val shareId = resp.playlistId
            if (!shareId.isNullOrBlank()) Result.Success(shareId)
            else Result.Error("Share failed: no shareId returned")
        } catch (e: HttpException) {
            // Extract the server error body so the user sees the actual Velvet error message
            // (e.g. {"error":"Server Error"}) rather than the generic HTTP status line.
            val body = try { e.response()?.errorBody()?.string()?.take(300) } catch (_: Exception) { null }
            val detail = if (!body.isNullOrBlank()) " — $body" else ""
            Log.e(TAG, "share HTTP ${e.code()}$detail")
            Result.Error("HTTP ${e.code()}$detail")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Native search ─────────────────────────────────────────────────────────

    /**
     * Full-text search using the native Velvet API (JWT auth — no Subsonic password needed).
     * Maps results to [SearchResult3] so the SearchScreen can consume them unchanged.
     *
     * `noFolders = false`: with the default `true`, Velvet omits real folder
     * paths from `albums` results (filepath comes back as the JSON boolean
     * `false`, coerced by Gson to the string `"false"`), which made the Albums
     * section of search results always empty AND made it impossible to derive
     * a folder path for an artist-only query. Passing `false` makes Velvet
     * include real album filepaths.
     *
     * Songs come back with filepath IDs → stream and share via native endpoints.
     * Artists have no folder path of their own in the native search response
     * (`NativeSearchArtist` only carries a `name`) — `id = name` is kept as a
     * stable row identifier. SearchScreen derives a navigable folder path (if
     * any) from the song/album results in this same response when an artist
     * row is tapped; if none can be derived, the tap is a no-op rather than
     * sending an invalid `directory` to file-explorer (which used to 500).
     */
    suspend fun search(token: String, query: String): Result<SearchResult3> {
        return try {
            val resp = api.nativeSearch(token, NativeSearchRequest(search = query, noFolders = false))

            // Temporary diagnostics for the artist-tap folder resolution issue —
            // grep logcat for "SharesonicSearch" to see what Velvet actually
            // returns for an artist-name query.
            android.util.Log.d(
                "SharesonicSearch",
                "search('$query'): title=${resp.title} albums=${resp.albums} artists=${resp.artists}"
            )

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
                // With noFolders=false Velvet should send a real folder filepath for
                // albums, but keep the defensive guard: if it ever sends the JSON
                // boolean `false` (Gson-coerced to the string "false"), that's
                // non-blank but not a valid path. A real Velvet filepath always
                // contains at least one '/' (e.g. "library/Artist/Album").
                val fp = item.filepath?.takeIf { it.isNotBlank() && it.contains('/') }
                    ?: return@mapNotNull null
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
                TopLevelDir(id = n, name = n, variants = a.variants.orEmpty())
            }

            // Real folders matched by name — the precise, server-provided way to
            // reach an artist/album folder (browsePath is a valid file-explorer
            // directory), no client-side path guessing needed.
            val folders = resp.folders.mapNotNull { f ->
                val bp = f.browsePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                EntryDto(
                    id    = bp,
                    title = f.folderName?.takeIf { it.isNotBlank() } ?: bp.trimEnd('/').substringAfterLast('/'),
                    isDir = true,
                    path  = bp
                )
            }

            Result.Success(SearchResult3(song = songs, album = albums, artist = artists, folder = folders))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow cancellation — structured concurrency depends on it
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Every song whose artist/album_artist tag exactly matches one of [artistNames] —
     * pass a search-result artist's normalized name PLUS all of its raw tag
     * [com.tiritibambix.sharesonic.data.api.models.NativeSearchArtist.variants], since
     * the server match is exact-string against the raw tag, not the normalized name.
     *
     * Used to resolve an artist tapped in search to its real on-disk folder: every
     * returned entry's [EntryDto.path] is a server-verified filepath, so the caller
     * can derive a real containing folder without any client-side path guessing.
     */
    suspend fun artistFolderSongs(token: String, artistNames: List<String>): Result<List<EntryDto>> {
        return try {
            val rows = api.artistFolderSongs(token, ArtistFolderSongsRequest(artists = artistNames))
            Result.Success(rows.mapNotNull { fileMetaWrapperToEntryDto(it) })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    /**
     * Append a single song (by filepath) to an existing playlist.
     * After adding, calls [syncPlaylistMeta] so [getall]'s songCount stays accurate.
     * Fire-and-forget.
     */
    suspend fun addSongToPlaylist(token: String, filepath: String, playlistName: String) {
        try {
            api.addSongToPlaylist(token, NativePlaylistAddSongRequest(filepath, playlistName))
            syncPlaylistMeta(token, playlistName)
        } catch (_: Exception) {}
    }

    /**
     * Remove a song from a playlist by its database entry ID.
     * After removing, calls [syncPlaylistMeta] so [getall]'s songCount stays accurate.
     * Fire-and-forget.
     */
    suspend fun removeSongFromPlaylist(token: String, entryId: Int, playlistName: String) {
        try {
            api.removeSongFromPlaylist(token, NativePlaylistRemoveSongRequest(entryId))
            syncPlaylistMeta(token, playlistName)
        } catch (_: Exception) {}
    }

    /**
     * Re-save the playlist so the server updates its stored [songCount] and [totalDuration].
     * Velvet's `add-song` / `remove-song` endpoints mutate `playlist_songs` but do NOT
     * update the denormalized metadata in the `playlists` table — only `save` does.
     */
    private suspend fun syncPlaylistMeta(token: String, playlistName: String) {
        try {
            val entries = api.loadPlaylist(token, NativePlaylistLoadRequest(playlistName))
            api.savePlaylist(token, NativePlaylistSaveRequest(
                title = playlistName,
                songs  = entries.map { it.filepath }
            ))
        } catch (_: Exception) {}
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
                val resp = api.randomSong(token, VelvetRandomSongsRequest(ignoreList = ignoreList))
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
    suspend fun getShareList(token: String): Result<List<VelvetShareListItem>> = try {
        Result.Success(api.getShareList(token))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    // ── Ratings ───────────────────────────────────────────────────────────────

    /**
     * Rate a track via POST /api/v1/db/rate-song.
     * @param stars 0–5 (Sharesonic UI scale); converted to Velvet's native 0–10
     *              half-star scale (`stars * 2`) before sending. Pass `null` to clear the rating.
     */
    suspend fun rateSong(token: String, filepath: String, stars: Int?): Result<Unit> = try {
        // The shared Retrofit Gson omits null fields by default (other endpoints rely on
        // that to mean "omit this optional field"), so a clear request needs its own
        // Gson with serializeNulls() to actually send "rating": null instead of dropping
        // the key — the server treats a missing key as a no-op, not as "clear".
        val json = rateSongGson.toJson(VelvetRateSongRequest(filepath = filepath, rating = stars?.times(2)))
        api.rateSong(token, json.toRequestBody("application/json".toMediaType()))
        Result.Success(Unit)
    } catch (e: Exception) { Result.Error(friendlyNetworkErrorMessage(e)) }

    /** Revoke (delete) a public share link by its playlistId. */
    suspend fun deleteShare(token: String, playlistId: String): Result<Unit> = try {
        api.deleteShare(token, playlistId)
        Result.Success(Unit)
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
     * Fire-and-forget — silently ignored if ListenBrainz is not configured in Velvet.
     */
    suspend fun listenBrainzNowPlaying(token: String, filepath: String) {
        try { api.listenBrainzNowPlaying(token, ScrobbleFilepathRequest(filepath)) }
        catch (e: Exception) { Log.w(TAG, "listenBrainzNowPlaying failed: ${e.message}") }
    }

    /**
     * Scrobble to Last.fm and ListenBrainz by filepath.
     * Fire-and-forget — silently ignored if the services are not configured in Velvet.
     * Call after 50% of track duration has elapsed.
     */
    suspend fun scrobble(token: String, filepath: String) {
        try { api.lastfmScrobble(token, ScrobbleFilepathRequest(filepath)) }
        catch (e: Exception) { Log.w(TAG, "lastfm scrobble failed: ${e.message}") }
        try { api.listenBrainzScrobble(token, ScrobbleFilepathRequest(filepath)) }
        catch (e: Exception) { Log.w(TAG, "listenbrainz scrobble failed: ${e.message}") }
    }

    // ── Auto-DJ helpers ───────────────────────────────────────────────────────

    /**
     * Fetch similar artists for [artist] from Last.fm via Velvet.
     * Returns a list of artist names, or an empty list on failure.
     */
    suspend fun getSimilarArtists(token: String, artist: String): Result<List<String>> = try {
        val resp = api.getSimilarArtists(token, artist)
        Result.Success(resp.artists)
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /**
     * Fetch exactly one random song for Auto-DJ.
     * Returns the [EntryDto] together with the updated ignoreList (to pass on the next call).
     * Uses the full [request] with all Auto-DJ filters applied by the caller.
     */
    suspend fun fetchAutoDjSong(
        token: String,
        request: VelvetRandomSongsRequest
    ): Result<Pair<EntryDto, List<Int>>> {
        return try {
            val resp = api.randomSong(token, request)
            val wrapper = resp.songs.firstOrNull()
                ?: return Result.Error("No song returned")
            val entry = fileMetaWrapperToEntryDto(wrapper)
                ?: return Result.Error("Could not parse song")
            Result.Success(Pair(entry, resp.ignoreList))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fileToEntryDto(file: VelvetFile): EntryDto? {
        // The full Velvet filepath (library + relative path) is our stable identifier.
        // It is used to build the native stream URL: /media/<filepath>?token=<jwt>
        val filepath = file.velvetFilepath ?: return null
        val meta = file.metadata?.metadata
        return EntryDto(
            id = filepath,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: file.name,
            artist = meta?.artist,
            album = meta?.album,
            coverArt = meta?.albumArt,
            isDir = false,
            path = filepath,
            // File format (mp3, flac, ogg…) — derived from the filename extension and
            // shown on the Now Playing screen alongside the live audio bitrate.
            suffix = filepath.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase(),
            bpm = meta?.bpm,
            musicalKey = meta?.musicalKey,
            genres = meta?.genres,
            rating = meta?.rating
        )
    }

    /**
     * Map a [VelvetFileMetaWrapper] (from /api/v1/db/random-songs) to an [EntryDto].
     * Uses [VelvetFileMetaWrapper.filepath] as the entry ID — same native filepath
     * format as file-explorer pullMetadata=true, so streaming and sharing work identically.
     * BPM, musical key and genres are propagated for Auto-DJ use.
     */
    private fun fileMetaWrapperToEntryDto(wrapper: VelvetFileMetaWrapper): EntryDto? {
        val filepath = wrapper.filepath?.takeIf { it.isNotBlank() } ?: return null
        val meta = wrapper.metadata
        return EntryDto(
            id = filepath,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: filepath.substringAfterLast('/').substringBeforeLast('.'),
            artist = meta?.artist,
            album = meta?.album,
            coverArt = meta?.albumArt,
            isDir = false,
            path = filepath,
            suffix = filepath.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase(),
            bpm = meta?.bpm,
            musicalKey = meta?.musicalKey,
            genres = meta?.genres,
            rating = meta?.rating
        )
    }
}
