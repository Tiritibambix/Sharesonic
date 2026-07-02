package com.tiritibambix.sharesonic.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object MStreamClient {

    /**
     * Longer read timeout for heavy whole-folder operations (recursive scan +
     * batch metadata on very large folders): the server walks its filesystem
     * for these, which can take well over the default 60 s on big libraries.
     * Use via [buildLongTimeout]; keep normal calls on the shorter default.
     */
    fun buildLongTimeout(serverUrl: String): MStreamApiService =
        build(serverUrl, readTimeoutSeconds = 300)

    fun build(serverUrl: String, readTimeoutSeconds: Long = 60): MStreamApiService {
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
            .create(MStreamApiService::class.java)
    }
}
