package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Immutable "what the widget should show right now" snapshot.
 *
 * Stored in each widget instance's **native Glance state** (a per-widget
 * Preferences store managed by Glance), NOT a separate app DataStore. This is
 * the documented mechanism that reliably re-composes the widget when the state
 * changes — the previous approach (external DataStore + updateAll) did not
 * refresh the display.
 */
data class WidgetSnapshot(
    val title: String? = null,
    val artist: String? = null,
    /** Native Velvet filepath — the id the rate endpoint takes. Null for
     *  Subsonic-numeric-id songs (not ratable natively). */
    val filepath: String? = null,
    /** Cover art URL incl. the JWT query param, resolved to a bitmap in
     *  provideGlance via Coil. */
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    /** UI-scale rating 0..5 (native scale is 0..10, already divided by 2). */
    val rating: Int = 0,
    val autoDjEnabled: Boolean = false,
)

/** Preference keys inside the widget's Glance state. */
object WidgetKeys {
    val TITLE      = stringPreferencesKey("title")
    val ARTIST     = stringPreferencesKey("artist")
    val FILEPATH   = stringPreferencesKey("filepath")
    val COVER_URL  = stringPreferencesKey("cover_url")
    val IS_PLAYING = booleanPreferencesKey("is_playing")
    val RATING     = intPreferencesKey("rating")
    val AUTO_DJ    = booleanPreferencesKey("auto_dj")
}

/** Read a [WidgetSnapshot] out of a Glance state [Preferences] blob. */
fun Preferences.toWidgetSnapshot(): WidgetSnapshot = WidgetSnapshot(
    title         = this[WidgetKeys.TITLE]?.takeIf { it.isNotEmpty() },
    artist        = this[WidgetKeys.ARTIST]?.takeIf { it.isNotEmpty() },
    filepath      = this[WidgetKeys.FILEPATH]?.takeIf { it.isNotEmpty() },
    coverArtUrl   = this[WidgetKeys.COVER_URL]?.takeIf { it.isNotEmpty() },
    isPlaying     = this[WidgetKeys.IS_PLAYING] ?: false,
    rating        = this[WidgetKeys.RATING] ?: 0,
    autoDjEnabled = this[WidgetKeys.AUTO_DJ] ?: false,
)

/**
 * Push [snapshot] into every placed widget instance's Glance state and trigger
 * a guaranteed recomposition. This is the canonical external-update path:
 * [GlanceAppWidgetManager.getGlanceIds] → [updateAppWidgetState] → [update].
 *
 * `suspend`; safe to call off the main thread. Called by the ViewModel (rich
 * snapshot on state change), the service (isPlaying after the Activity dies),
 * and the rate / auto-DJ action callbacks (their own field).
 */
suspend fun pushWidgetState(context: Context, transform: (WidgetSnapshot) -> WidgetSnapshot) {
    val manager = GlanceAppWidgetManager(context)
    val ids = manager.getGlanceIds(SharesonicWidget::class.java)
    val widget = SharesonicWidget()
    ids.forEach { id ->
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            val next = transform(prefs.toWidgetSnapshot())
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.TITLE]      = next.title.orEmpty()
                this[WidgetKeys.ARTIST]     = next.artist.orEmpty()
                this[WidgetKeys.FILEPATH]   = next.filepath.orEmpty()
                this[WidgetKeys.COVER_URL]  = next.coverArtUrl.orEmpty()
                this[WidgetKeys.IS_PLAYING] = next.isPlaying
                this[WidgetKeys.RATING]     = next.rating
                this[WidgetKeys.AUTO_DJ]    = next.autoDjEnabled
            }
        }
        widget.update(context, id)
    }
}

/** Convenience overload: replace the whole snapshot. */
suspend fun pushWidgetState(context: Context, snapshot: WidgetSnapshot) =
    pushWidgetState(context) { snapshot }
