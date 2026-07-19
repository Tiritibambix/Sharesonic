package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Widget action callbacks. Each runs in a Glance-managed coroutine (a
 * background WorkManager thread) when the user taps a button.
 *
 * Transport does NOT touch a MediaController — that path (building a controller
 * from a background callback) proved unreliable. Instead each transport button
 * writes a command to DataStore that [com.tiritibambix.sharesonic.playback.PlaybackService]
 * observes and executes on its ExoPlayer directly. This reuses the exact
 * DataStore-observed mechanism that already makes Auto-DJ work.
 *
 * Rating is a direct HTTP call (fine off the main thread) plus a Glance-state
 * push. Auto-DJ is a DataStore flag flip plus a Glance-state push.
 */

class PrevCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SettingsRepository(context).sendWidgetCommand("PREV")
    }
}

class PlayPauseCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SettingsRepository(context).sendWidgetCommand("PLAY_PAUSE")
    }
}

class NextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SettingsRepository(context).sendWidgetCommand("NEXT")
    }
}

/**
 * Rate the current track [StarsKey] stars (0..5). Tapping the already-set star
 * clears the rating. Skipped when the current track has no filepath (Subsonic
 * numeric-id song — not ratable natively).
 */
class RateCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val stars = parameters[StarsKey] ?: return
        // Read the current display state straight from this widget's Glance state.
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val snapshot = prefs.toWidgetSnapshot()
        val filepath = snapshot.filepath ?: return
        val newStars = if (snapshot.rating == stars) 0 else stars

        // Optimistic: reflect the new rating in the widget immediately.
        pushWidgetState(context) { it.copy(rating = newStars) }

        val settings = SettingsRepository(context).settings.first()
        val token = settings.jwtToken.ifEmpty { return }
        val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
        val result = repo.rateSong(token, filepath, newStars.takeIf { it > 0 })
        if (result is Result.Error) {
            // Revert on failure so the widget stays truthful.
            pushWidgetState(context) { it.copy(rating = snapshot.rating) }
        }
    }

    companion object {
        val StarsKey = ActionParameters.Key<Int>("widget_rate_stars")
    }
}

/**
 * Flip the persistent Auto-DJ flag AND push the new value into the widget's
 * Glance state so the pill updates immediately. The [PlaybackService] observes
 * the same flag for its own behaviour; when the app is alive the ViewModel
 * bridges it into PlayerState.
 */
class ToggleAutoDjCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settingsRepo = SettingsRepository(context)
        val next = !settingsRepo.autoDjEnabled.first()
        settingsRepo.saveAutoDjEnabled(next)
        pushWidgetState(context) { it.copy(autoDjEnabled = next) }
    }
}
