package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamArtResponse
import com.tiritibambix.sharesonic.data.api.models.NativeSearchRequest
import com.tiritibambix.sharesonic.data.api.models.NativeSearchResponse
import com.tiritibambix.sharesonic.data.api.models.ScrobbleFilepathRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamRandomSongsRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamRandomSongsResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamRefreshResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamShareListItem
import com.tiritibambix.sharesonic.data.api.models.MStreamShareRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamShareResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface MStreamApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: MStreamLoginRequest): MStreamLoginResponse

    /** Refresh the JWT — call on boot to get a token signed by the current server secret. */
    @GET("api/v1/auth/refresh")
    suspend fun refreshToken(@Header("x-access-token") token: String): MStreamRefreshResponse

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
        @Body request: MStreamShareRequest
    ): MStreamShareResponse

    /** List all share links created by the authenticated user. */
    @GET("api/v1/share/list")
    suspend fun getShareList(@Header("x-access-token") token: String): List<MStreamShareListItem>

    /** Revoke (delete) a share link by its playlistId. */
    @DELETE("api/v1/share/{playlistId}")
    suspend fun deleteShare(
        @Header("x-access-token") token: String,
        @Path("playlistId") playlistId: String
    ): ResponseBody

    // ── Random songs ──────────────────────────────────────────────────────────

    /**
     * Returns one random song per call. Pass the [MStreamRandomSongsRequest.ignoreList]
     * from the response back on subsequent calls to avoid repeats.
     */
    @POST("api/v1/db/random-songs")
    suspend fun randomSong(
        @Header("x-access-token") token: String,
        @Body request: MStreamRandomSongsRequest
    ): MStreamRandomSongsResponse

    // ── On-demand art ─────────────────────────────────────────────────────────

    /** Extract embedded album art from any audio file; returns the cache filename or null. */
    @GET("api/v1/files/art")
    suspend fun getArt(
        @Header("x-access-token") token: String,
        @Query("fp") filepath: String
    ): MStreamArtResponse

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
