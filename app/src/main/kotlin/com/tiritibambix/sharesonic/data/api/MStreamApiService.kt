package com.tiritibambix.sharesonic.data.api

import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamShareRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamShareResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MStreamApiService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: MStreamLoginRequest): MStreamLoginResponse

    @POST("api/v1/file-explorer")
    suspend fun fileExplorer(
        @Header("x-access-token") token: String,
        @Body request: FileExplorerRequest
    ): FileExplorerResponse

    /** Create a public share link. Returns { shareId } → URL = <serverUrl>/shared/<shareId> */
    @POST("api/v1/share")
    suspend fun share(
        @Header("x-access-token") token: String,
        @Body request: MStreamShareRequest
    ): MStreamShareResponse
}
