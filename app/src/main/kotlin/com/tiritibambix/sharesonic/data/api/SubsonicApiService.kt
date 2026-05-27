package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.SubsonicEnvelope
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApiService {

    @GET("ping.view")
    suspend fun ping(): SubsonicEnvelope

    @GET("getMusicFolders.view")
    suspend fun getMusicFolders(): SubsonicEnvelope

    @GET("getMusicDirectory.view")
    suspend fun getMusicDirectory(@Query("id") id: String): SubsonicEnvelope

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
