package com.tiritibambix.sharesonic.data.api

import com.google.gson.GsonBuilder
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val CLIENT_NAME = "Sharesonic"
private const val API_VERSION = "1.16.1"

object SubsonicClient {

    /**
     * Build a Subsonic API client using plain-text password auth (p= param).
     * mStream's Subsonic endpoint requires p= rather than token/salt auth.
     */
    fun build(serverUrl: String, username: String, password: String): SubsonicApiService {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("u", username)
                    .addQueryParameter("p", password)
                    .addQueryParameter("v", API_VERSION)
                    .addQueryParameter("c", CLIENT_NAME)
                    .addQueryParameter("f", "json")
                    .build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val gson = GsonBuilder().setLenient().create()
        val baseUrl = serverUrl.trimEnd('/') + "/rest/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SubsonicApiService::class.java)
    }

    /** Stable cover-art URL for Coil to cache, using plain-text password auth. */
    fun coverArtUrl(settings: ServerSettings, coverArtId: String, size: Int = 256): String {
        val base = settings.serverUrl.trimEnd('/')
        return "$base/rest/getCoverArt.view" +
            "?id=$coverArtId" +
            "&u=${settings.username}" +
            "&p=${settings.password}" +
            "&v=$API_VERSION" +
            "&c=$CLIENT_NAME" +
            "&size=$size"
    }
}
