package com.tiritibambix.sharesonic.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object VelvetClient {

    /**
     * Snapshot of the last outbound `/api/v1/playlist/load` request body,
     * captured by an interceptor before the bytes leave the phone. Used by the
     * PlaylistDetail diagnostic to show what the app actually sent on the wire
     * (as opposed to what Kotlin thinks the string is), so character-level
     * corruption at any layer between the ViewModel and the socket is visible.
     */
    @Volatile
    var lastPlaylistLoadRequestBody: ByteArray? = null
        private set

    /**
     * Longer read timeout for heavy whole-folder operations (recursive scan +
     * batch metadata on very large folders): the server walks its filesystem
     * for these, which can take well over the default 60 s on big libraries.
     * Use via [buildLongTimeout]; keep normal calls on the shorter default.
     */
    fun buildLongTimeout(serverUrl: String): VelvetApiService =
        build(serverUrl, readTimeoutSeconds = 300)

    fun build(serverUrl: String, readTimeoutSeconds: Long = 60): VelvetApiService {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Mirror x-access-token as Authorization: Bearer for reverse-proxy compatibility
            .addInterceptor { chain ->
                val original = chain.request()
                val token = original.header("x-access-token")
                val request = if (!token.isNullOrEmpty()) {
                    original.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else original
                chain.proceed(request)
            }
            // Diagnostic tap: on `/playlist/load` requests, copy the outbound
            // body bytes into [lastPlaylistLoadRequestBody] so the UI can show
            // exactly what left the phone. Reading the body via Buffer doesn't
            // consume the original request — we clone into a fresh Buffer and
            // extract its bytes.
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath.endsWith("/api/v1/playlist/load")) {
                    val body = request.body
                    if (body != null) {
                        val buf = Buffer()
                        body.writeTo(buf)
                        lastPlaylistLoadRequestBody = buf.readByteArray()
                    }
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val gson = GsonBuilder().setLenient().create()
        val baseUrl = serverUrl.trimEnd('/') + "/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(VelvetApiService::class.java)
    }
}
