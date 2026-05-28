package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.SubsonicEnvelope
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Subsonic API — used only for playback, sharing, and random-song shuffle.
 * Folder browsing is handled by the mStream native API (MStreamApiService).
 */
interface SubsonicApiService {

    @GET("ping.view")
    suspend fun ping(): SubsonicEnvelope

    @GET("getRandomSongs.view")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 200,
        @Query("musicFolderId") musicFolderId: String? = null
    ): SubsonicEnvelope

    @GET("search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("songCount") songCount: Int = 50,
        @Query("albumCount") albumCount: Int = 20,
        @Query("artistCount") artistCount: Int = 10
    ): SubsonicEnvelope

    @Streaming
    @GET("getCoverArt.view")
    suspend fun getCoverArt(
        @Query("id") id: String,
        @Query("size") size: Int = 256
    ): ResponseBody

    @GET("createShare.view")
    suspend fun createShare(
        @Query("id") id: String,
        @Query("description") description: String? = null,
        @Query("expires") expires: Long? = null
    ): SubsonicEnvelope

    @GET("getShares.view")
    suspend fun getShares(): SubsonicEnvelope

    @GET("deleteShare.view")
    suspend fun deleteShare(@Query("id") id: String): SubsonicEnvelope
}
