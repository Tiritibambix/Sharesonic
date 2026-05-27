package com.tiritibambix.sharesonic.data.api

import com.google.gson.GsonBuilder
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

private const val CLIENT_NAME = "Sharesonic"
private const val API_VERSION = "1.16.1"

// Fixed salt for cover-art URLs so Coil can cache them by URL.
// Slightly less random than per-request salts but fine for a local client.
private const val COVER_ART_SALT = "sharesonic"

object SubsonicClient {

    fun build(serverUrl: String, username: String, password: String): SubsonicApiService {
        val salt = randomSalt()
        val token = md5(password + salt)

        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("u", username)
                    .addQueryParameter("t", token)
                    .addQueryParameter("s", salt)
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

    /**
     * Returns a stable URL for [coverArtId] that Coil can load and cache.
     * Uses a fixed salt so the URL doesn't change on every call.
     */
    fun coverArtUrl(settings: ServerSettings, coverArtId: String, size: Int = 256): String {
        val token = md5(settings.password + COVER_ART_SALT)
        val base = settings.serverUrl.trimEnd('/')
        return "$base/rest/getCoverArt.view" +
            "?id=$coverArtId" +
            "&u=${settings.username}" +
            "&t=$token" +
            "&s=$COVER_ART_SALT" +
            "&v=$API_VERSION" +
            "&c=$CLIENT_NAME" +
            "&size=$size"
    }

    private fun randomSalt(): String =
        (1..12).map { ('a'..'z').random() }.joinToString("")

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
