package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Immutable snapshot of the "what should the widget show right now" state.
 *
 * Populated by the two writers of playback truth:
 *  - [com.tiritibambix.sharesonic.playback.PlaybackService] — every Player.Listener
 *    event (isPlaying / mediaItem transition), always alive while the service is up.
 *  - [com.tiritibambix.sharesonic.ui.player.PlayerViewModel] — rating changes and
 *    the seed data at song-start (filepath, cover URL, artist name — data the
 *    service doesn't see because ExoPlayer only knows the MediaItem URI).
 *
 * Consumed by:
 *  - [SharesonicWidget.provideGlance] — the rendered widget.
 *  - Every [androidx.glance.appwidget.action.ActionCallback] that needs the current
 *    filepath (rate) or the auto-DJ flag (toggle).
 */
data class WidgetSnapshot(
    val title: String? = null,
    val artist: String? = null,
    /** Native Velvet filepath — the identifier the rate endpoint takes. Null when
     *  the current track is a Subsonic-numeric-id song (search legacy path, can't
     *  be rated through the native endpoint). */
    val filepath: String? = null,
    /** Full cover art URL including the JWT query parameter. Coil resolves it to
     *  a Bitmap inside the widget provider. */
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    /** UI-scale rating 0..5 (the native scale is 0..10, already divided by 2 here). */
    val rating: Int = 0,
    val autoDjEnabled: Boolean = false,
)

private val Context.widgetDataStore by preferencesDataStore(name = "widget_snapshot")

class WidgetStateRepository(private val context: Context) {

    private object Keys {
        val TITLE          = stringPreferencesKey("title")
        val ARTIST         = stringPreferencesKey("artist")
        val FILEPATH       = stringPreferencesKey("filepath")
        val COVER_ART_URL  = stringPreferencesKey("cover_art_url")
        val IS_PLAYING     = booleanPreferencesKey("is_playing")
        val RATING         = intPreferencesKey("rating")
        val AUTO_DJ        = booleanPreferencesKey("auto_dj")
    }

    val snapshot: Flow<WidgetSnapshot> = context.widgetDataStore.data.map { prefs ->
        WidgetSnapshot(
            title         = prefs[Keys.TITLE]?.takeIf { it.isNotEmpty() },
            artist        = prefs[Keys.ARTIST]?.takeIf { it.isNotEmpty() },
            filepath      = prefs[Keys.FILEPATH]?.takeIf { it.isNotEmpty() },
            coverArtUrl   = prefs[Keys.COVER_ART_URL]?.takeIf { it.isNotEmpty() },
            isPlaying     = prefs[Keys.IS_PLAYING]     ?: false,
            rating        = prefs[Keys.RATING]         ?: 0,
            autoDjEnabled = prefs[Keys.AUTO_DJ]        ?: false,
        )
    }

    /**
     * Apply [transform] to the current snapshot and persist the result. Also
     * pings the AppWidgetManager so every placed instance of [SharesonicWidget]
     * re-invokes provideGlance and picks up the new state on the next frame.
     */
    suspend fun update(transform: (WidgetSnapshot) -> WidgetSnapshot) {
        context.widgetDataStore.edit { prefs ->
            val current = WidgetSnapshot(
                title         = prefs[Keys.TITLE]?.takeIf { it.isNotEmpty() },
                artist        = prefs[Keys.ARTIST]?.takeIf { it.isNotEmpty() },
                filepath      = prefs[Keys.FILEPATH]?.takeIf { it.isNotEmpty() },
                coverArtUrl   = prefs[Keys.COVER_ART_URL]?.takeIf { it.isNotEmpty() },
                isPlaying     = prefs[Keys.IS_PLAYING]     ?: false,
                rating        = prefs[Keys.RATING]         ?: 0,
                autoDjEnabled = prefs[Keys.AUTO_DJ]        ?: false,
            )
            val next = transform(current)
            prefs[Keys.TITLE]         = next.title.orEmpty()
            prefs[Keys.ARTIST]        = next.artist.orEmpty()
            prefs[Keys.FILEPATH]      = next.filepath.orEmpty()
            prefs[Keys.COVER_ART_URL] = next.coverArtUrl.orEmpty()
            prefs[Keys.IS_PLAYING]    = next.isPlaying
            prefs[Keys.RATING]        = next.rating
            prefs[Keys.AUTO_DJ]       = next.autoDjEnabled
        }
        SharesonicWidget().updateAll(context)
    }
}
