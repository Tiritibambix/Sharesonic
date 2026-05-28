package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MStreamApiService {

    @POST("api/v1/login")
    suspend fun login(@Body request: MStreamLoginRequest): MStreamLoginResponse

    @POST("api/v1/file-explorer")
    suspend fun fileExplorer(
        @Header("Authorization") authorization: String,
        @Body request: FileExplorerRequest
    ): FileExplorerResponse
}
