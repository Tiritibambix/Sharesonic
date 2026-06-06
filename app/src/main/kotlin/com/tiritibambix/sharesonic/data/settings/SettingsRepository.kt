package com.tiritibambix.sharesonic.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME   = stringPreferencesKey("username")
        val PASSWORD   = stringPreferencesKey("password")
        val JWT_TOKEN  = stringPreferencesKey("jwt_token")
        /** Library vpaths stored after the last successful connection test. */
        val VPATHS     = stringPreferencesKey("vpaths")
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

    /** Library virtual paths returned by the last successful connection test. */
    val vpaths: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.VPATHS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveVpaths(vpaths: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VPATHS] = vpaths.joinToString("|")
        }
    }

    // ── Auto-DJ settings ──────────────────────────────────────────────────────

    private object AutoDjKeys {
        val USE_BPM              = booleanPreferencesKey("autodj_use_bpm")
        val BPM_TIGHT_RANGE      = intPreferencesKey("autodj_bpm_tight")
        val BPM_WIDE_RANGE       = intPreferencesKey("autodj_bpm_wide")
        val REQUIRE_BPM          = booleanPreferencesKey("autodj_require_bpm")
        val USE_HARMONIC         = booleanPreferencesKey("autodj_use_harmonic")
        val REQUIRE_KEY          = booleanPreferencesKey("autodj_require_key")
        val USE_SIMILAR_ARTISTS  = booleanPreferencesKey("autodj_use_similar")
        val ARTIST_COOLDOWN      = intPreferencesKey("autodj_artist_cooldown")
        val GENRE_MODE             = stringPreferencesKey("autodj_genre_mode")
        val GENRES                 = stringPreferencesKey("autodj_genres")
        val MIN_RATING             = intPreferencesKey("autodj_min_rating")
        val CROSSFADE_DURATION_SEC = intPreferencesKey("autodj_crossfade_sec")
        val SOURCE_FOLDERS         = stringPreferencesKey("autodj_source_folders")
    }

    val autoDjSettings: Flow<AutoDjSettings> = context.dataStore.data.map { prefs ->
        AutoDjSettings(
            useBpm             = prefs[AutoDjKeys.USE_BPM]              ?: true,
            bpmTightRange      = prefs[AutoDjKeys.BPM_TIGHT_RANGE]      ?: 10,
            bpmWideRange       = prefs[AutoDjKeys.BPM_WIDE_RANGE]       ?: 20,
            requireBpm         = prefs[AutoDjKeys.REQUIRE_BPM]          ?: false,
            useHarmonicMixing  = prefs[AutoDjKeys.USE_HARMONIC]         ?: true,
            requireKey         = prefs[AutoDjKeys.REQUIRE_KEY]          ?: false,
            useSimilarArtists  = prefs[AutoDjKeys.USE_SIMILAR_ARTISTS]  ?: true,
            artistCooldown     = prefs[AutoDjKeys.ARTIST_COOLDOWN]      ?: 5,
            genreMode          = prefs[AutoDjKeys.GENRE_MODE]           ?: "off",
            genres             = prefs[AutoDjKeys.GENRES]
                                     ?.split("|")
                                     ?.filter { it.isNotBlank() }
                                 ?: emptyList(),
            minRating          = (prefs[AutoDjKeys.MIN_RATING] ?: 0).coerceIn(0, 5),
            crossfadeDurationSec = (prefs[AutoDjKeys.CROSSFADE_DURATION_SEC] ?: 0).coerceIn(0, 12),
            sourceFolders      = prefs[AutoDjKeys.SOURCE_FOLDERS]
                                     ?.split("|")
                                     ?.filter { it.isNotBlank() }
                                 ?: emptyList()
        )
    }

    suspend fun saveAutoDjSettings(settings: AutoDjSettings) {
        context.dataStore.edit { prefs ->
            prefs[AutoDjKeys.USE_BPM]             = settings.useBpm
            prefs[AutoDjKeys.BPM_TIGHT_RANGE]     = settings.bpmTightRange
            prefs[AutoDjKeys.BPM_WIDE_RANGE]      = settings.bpmWideRange
            prefs[AutoDjKeys.REQUIRE_BPM]         = settings.requireBpm
            prefs[AutoDjKeys.USE_HARMONIC]        = settings.useHarmonicMixing
            prefs[AutoDjKeys.REQUIRE_KEY]         = settings.requireKey
            prefs[AutoDjKeys.USE_SIMILAR_ARTISTS] = settings.useSimilarArtists
            prefs[AutoDjKeys.ARTIST_COOLDOWN]     = settings.artistCooldown
            prefs[AutoDjKeys.GENRE_MODE]              = settings.genreMode
            prefs[AutoDjKeys.GENRES]                  = settings.genres.joinToString("|")
            prefs[AutoDjKeys.MIN_RATING]              = settings.minRating
            prefs[AutoDjKeys.CROSSFADE_DURATION_SEC]  = settings.crossfadeDurationSec
            prefs[AutoDjKeys.SOURCE_FOLDERS]          = settings.sourceFolders.joinToString("|")
        }
    }
}
