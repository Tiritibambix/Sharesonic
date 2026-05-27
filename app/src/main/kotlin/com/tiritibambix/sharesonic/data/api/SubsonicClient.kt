package com.tiritibambix.sharesonic.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest

private const val CLIENT_NAME = "Sharesonic"
private const val API_VERSION = "1.16.1"

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

    private fun randomSalt(): String =
        (1..12).map { ('a'..'z').random() }.joinToString("")

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
