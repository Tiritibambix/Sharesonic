package com.tiritibambix.sharesonic.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "server_settings")

data class ServerSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val jwtToken: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL  = stringPreferencesKey("server_url")
        val USERNAME    = stringPreferencesKey("username")
        val PASSWORD    = stringPreferencesKey("password")
        val JWT_TOKEN   = stringPreferencesKey("jwt_token")
    }

    val settings: Flow<ServerSettings> = context.dataStore.data.map { prefs ->
        ServerSettings(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username  = prefs[Keys.USERNAME]   ?: "",
            password  = prefs[Keys.PASSWORD]   ?: "",
            jwtToken  = prefs[Keys.JWT_TOKEN]  ?: ""
        )
    }

    suspend fun save(settings: ServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = settings.serverUrl
            prefs[Keys.USERNAME]   = settings.username
            prefs[Keys.PASSWORD]   = settings.password
            prefs[Keys.JWT_TOKEN]  = settings.jwtToken
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.JWT_TOKEN] = token
        }
    }
}
