package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.ArtistFolderSongsRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistAddSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistDeleteRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistNewRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRemoveSongRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistRenameRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistEntry
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistLoadRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistSaveRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetArtResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetFileMetaWrapper
import com.tiritibambix.sharesonic.data.api.models.MetadataRequest
import com.tiritibambix.sharesonic.data.api.models.LyricsResponse
import com.tiritibambix.sharesonic.data.api.models.RecursiveScanRequest
import com.tiritibambix.sharesonic.data.api.models.NativeSearchRequest
import com.tiritibambix.sharesonic.data.api.models.NativeSearchResponse
import com.tiritibambix.sharesonic.data.api.models.ScrobbleFilepathRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetLoginRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetLoginResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetRefreshResponse
import com.tiritibambix.sharesonic.data.api.models.VelvetShareListItem
import com.tiritibambix.sharesonic.data.api.models.VelvetShareRequest
import com.tiritibambix.sharesonic.data.api.models.VelvetShareResponse
import com.tiritibambix.sharesonic.data.api.models.SimilarArtistsResponse
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface VelvetApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: VelvetLoginRequest): VelvetLoginResponse

    /** Refresh the JWT — call on boot to get a token signed by the current server secret. */
    @GET("api/v1/auth/refresh")
    suspend fun refreshToken(@Header("x-access-token") token: String): VelvetRefreshResponse

    // ── File explorer ─────────────────────────────────────────────────────────

    @POST("api/v1/file-explorer")
    suspend fun fileExplorer(
        @Header("x-access-token") token: String,
        @Body request: FileExplorerRequest
    ): FileExplorerResponse

    // ── Share ─────────────────────────────────────────────────────────────────

    /** Create a public share link. Returns { playlistId } → URL = <serverUrl>/shared/<playlistId> */
    @POST("api/v1/share")
    suspend fun share(
        @Header("x-access-token") token: String,
        @Body request: VelvetShareRequest
    ): VelvetShareResponse

    /** List all share links created by the authenticated user. */
    @GET("api/v1/share/list")
    suspend fun getShareList(@Header("x-access-token") token: String): List<VelvetShareListItem>

    /** Revoke (delete) a share link by its playlistId. */
    @DELETE("api/v1/share/{playlistId}")
    suspend fun deleteShare(
        @Header("x-access-token") token: String,
        @Path("playlistId") playlistId: String
    ): ResponseBody

    // ── Random songs ──────────────────────────────────────────────────────────

    /**
     * Returns one random song per call. Pass the [VelvetRandomSongsRequest.ignoreList]
     * from the response back on subsequent calls to avoid repeats.
     */
    @POST("api/v1/db/random-songs")
    suspend fun randomSong(
        @Header("x-access-token") token: String,
        @Body request: VelvetRandomSongsRequest
    ): VelvetRandomSongsResponse

    // ── Ratings ───────────────────────────────────────────────────────────────

    /**
     * Rate a track — Velvet's native scale is 0–10 (half-star precision); the
     * Sharesonic UI shows 0–5 stars, so `rating = stars * 2`. Pass `null` to clear.
     *
     * Takes a raw [RequestBody] (built by [VelvetRepository.rateSong] with a Gson
     * instance that serializes nulls) rather than [VelvetRateSongRequest] directly —
     * the shared converter's Gson omits null fields by default, which would drop
     * `rating` entirely from a clear request instead of sending `"rating": null`.
     */
    @POST("api/v1/db/rate-song")
    suspend fun rateSong(
        @Header("x-access-token") token: String,
        @Body body: RequestBody
    ): ResponseBody

    /**
     * Server-side recursive scan: returns every audio filepath under [request].directory
     * as one flat array (e.g. "Music/Artist/Album/track.mp3"). One request instead of the
     * thousands of per-subfolder /file-explorer calls a client-side walk would make —
     * used to gather a whole folder for shuffle at any scale.
     */
    @POST("api/v1/file-explorer/recursive")
    suspend fun recursiveScan(
        @Header("x-access-token") token: String,
        @Body request: RecursiveScanRequest
    ): List<String>

    /**
     * Batch metadata lookup: given a list of filepaths, returns a map keyed by filepath,
     * each value the same [VelvetFileMetaWrapper] shape as pullMetadata=true file-explorer
     * entries. Lets a recursive scan's bare filepaths be resolved to full metadata in one call.
     */
    @POST("api/v1/db/metadata/batch")
    suspend fun metadataBatch(
        @Header("x-access-token") token: String,
        @Body filepaths: List<String>
    ): Map<String, VelvetFileMetaWrapper>

    /**
     * Full, fresh metadata for one track (bpm / musical-key / genres included,
     * unlike search-origin entries). Response = { filepath, metadata: {...}, rg }.
     */
    @POST("api/v1/db/metadata")
    suspend fun trackMetadata(
        @Header("x-access-token") token: String,
        @Body request: MetadataRequest
    ): VelvetFileMetaWrapper

    /**
     * Lyrics for a track. Server matches on artist+title (falling back to parsing
     * "Artist - Title" from a filename-title) and prefers the DB duration for the
     * given filepath. Returns parsed lines or { notFound: true }.
     */
    @GET("api/v1/lyrics")
    suspend fun getLyrics(
        @Header("x-access-token") token: String,
        @Query("artist") artist: String,
        @Query("title") title: String,
        @Query("filepath") filepath: String,
        @Query("duration") duration: Int
    ): LyricsResponse

    // ── On-demand art ─────────────────────────────────────────────────────────

    /** Extract embedded album art from any audio file; returns the cache filename or null. */
    @GET("api/v1/files/art")
    suspend fun getArt(
        @Header("x-access-token") token: String,
        @Query("fp") filepath: String
    ): VelvetArtResponse

    // ── Native search ─────────────────────────────────────────────────────────

    /**
     * Full-text search across songs (title), albums, and artists.
     * Uses FTS5 — returns songs with filepath IDs, same as file-explorer entries.
     */
    @POST("api/v1/db/search")
    suspend fun nativeSearch(
        @Header("x-access-token") token: String,
        @Body request: NativeSearchRequest
    ): NativeSearchResponse

    /**
     * Every song whose artist/album_artist tag exactly matches one of the
     * requested names — see [ArtistFolderSongsRequest]. Response is a bare
     * array (not wrapped in an object), same shape as pullMetadata=true
     * file-explorer entries and [VelvetRandomSongsResponse.songs].
     */
    @POST("api/v1/db/artist-folder-songs")
    suspend fun artistFolderSongs(
        @Header("x-access-token") token: String,
        @Body request: ArtistFolderSongsRequest
    ): List<VelvetFileMetaWrapper>

    // ── Native playlists ──────────────────────────────────────────────────────

    /** List all playlists for the authenticated user (metadata only — no song entries). */
    @GET("api/v1/playlist/getall")
    suspend fun getPlaylists(@Header("x-access-token") token: String): List<NativePlaylist>

    /**
     * Fetch the full song list for a single playlist.
     * Returns one entry per song with its database row ID (for remove-song) and filepath.
     */
    @POST("api/v1/playlist/load")
    suspend fun loadPlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistLoadRequest
    ): List<NativePlaylistEntry>

    /**
     * Same endpoint as [loadPlaylist] but returns the raw HTTP body. Used as a
     * diagnostic fallback when [loadPlaylist] parses to an empty list — dumping
     * the raw JSON on-screen lets the user see whether the server actually
     * returned 0 entries or whether the client's Gson parse silently produced
     * an empty list (e.g. object vs. array mismatch).
     */
    @POST("api/v1/playlist/load")
    suspend fun loadPlaylistRaw(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistLoadRequest
    ): ResponseBody

    /** Create a new empty playlist. Returns {} on success. */
    @POST("api/v1/playlist/new")
    suspend fun createPlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistNewRequest
    ): ResponseBody

    /** Delete a playlist by name. Returns {} on success. */
    @POST("api/v1/playlist/delete")
    suspend fun deletePlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistDeleteRequest
    ): ResponseBody

    /** Rename a playlist. Returns {} on success; 400 if new name already exists. */
    @POST("api/v1/playlist/rename")
    suspend fun renamePlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistRenameRequest
    ): ResponseBody

    /** Append a single song (by filepath) to an existing playlist. */
    @POST("api/v1/playlist/add-song")
    suspend fun addSongToPlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistAddSongRequest
    ): ResponseBody

    /**
     * Remove a single song from a playlist by its database entry ID.
     * The entry ID comes from [NativePlaylistSong.id] in the getall response.
     */
    @POST("api/v1/playlist/remove-song")
    suspend fun removeSongFromPlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistRemoveSongRequest
    ): ResponseBody

    /** Overwrite a playlist's entire track list (for reorder / bulk-replace). */
    @POST("api/v1/playlist/save")
    suspend fun savePlaylist(
        @Header("x-access-token") token: String,
        @Body request: NativePlaylistSaveRequest
    ): ResponseBody

    // ── Last.fm similar artists ───────────────────────────────────────────────

    /**
     * Fetch similar artists from Last.fm via the Velvet server.
     * Used by Auto-DJ to prefer tracks from artists similar to the currently playing one.
     * NOTE: The exact endpoint path should be verified against Velvet's route list.
     */
    @GET("api/v1/lastfm/similar-artists")
    suspend fun getSimilarArtists(
        @Header("x-access-token") token: String,
        @Query("artist") artist: String
    ): SimilarArtistsResponse

    // ── Scrobble ──────────────────────────────────────────────────────────────

    /** Scrobble a track to Last.fm (after 50% played). */
    @POST("api/v1/lastfm/scrobble-by-filepath")
    suspend fun lastfmScrobble(
        @Header("x-access-token") token: String,
        @Body request: ScrobbleFilepathRequest
    ): ResponseBody

    /** Send a "now playing" ping to ListenBrainz (on track start). */
    @POST("api/v1/listenbrainz/playing-now")
    suspend fun listenBrainzNowPlaying(
        @Header("x-access-token") token: String,
        @Body request: ScrobbleFilepathRequest
    ): ResponseBody

    /** Scrobble a track to ListenBrainz (after 50% played). */
    @POST("api/v1/listenbrainz/scrobble-by-filepath")
    suspend fun listenBrainzScrobble(
        @Header("x-access-token") token: String,
        @Body request: ScrobbleFilepathRequest
    ): ResponseBody
}
