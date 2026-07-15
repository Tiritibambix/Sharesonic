package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Widget action callbacks. Each one runs in a WorkManager coroutine that Glance
 * spins up when the user taps the corresponding button — the app process is
 * started if necessary but the Activity never has to exist.
 *
 * Transport actions ([PrevCallback], [PlayPauseCallback], [NextCallback]) go
 * through a fresh [MediaController] which connects to the always-alive
 * [com.tiritibambix.sharesonic.playback.PlaybackService]. Rating and Auto-DJ
 * bypass the media session — rating is an HTTP call to Velvet, and Auto-DJ
 * toggle is just a boolean flip in DataStore that the service observes.
 */

class PrevCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { it.seekToPreviousMediaItem() }
    }
}

class PlayPauseCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }
}

class NextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { it.seekToNextMediaItem() }
    }
}

/**
 * Rate the current track [StarsKey] stars (0..5). Tapping the already-set star
 * clears the rating (mirrors the in-app Now Playing behaviour). Skipped
 * silently when the current track has no filepath (Subsonic numeric-ID song
 * from a legacy search — not ratable through the native endpoint).
 */
class RateCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val stars = parameters[StarsKey] ?: return
        val widgetState = WidgetStateRepository(context)
        val snapshot = widgetState.snapshot.first()
        val filepath = snapshot.filepath ?: return
        // Tap the already-set star → clear back to unrated (same UX as NowPlaying).
        val newStars = if (snapshot.rating == stars) 0 else stars
        // Optimistic snapshot update so the widget reflects the star tap
        // immediately, even before the HTTP call returns.
        widgetState.update { it.copy(rating = newStars) }
        val settings = SettingsRepository(context).settings.first()
        val token = settings.jwtToken.ifEmpty { return }
        val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
        val result = repo.rateSong(token, filepath, newStars.takeIf { it > 0 })
        if (result is Result.Error) {
            // Revert on failure so the widget stays truthful.
            widgetState.update { it.copy(rating = snapshot.rating) }
        }
    }

    companion object {
        val StarsKey = ActionParameters.Key<Int>("widget_rate_stars")
    }
}

/**
 * Flip the persistent Auto-DJ flag. The [PlaybackService] observes this same
 * key and picks up the change on its next `STATE_ENDED`; when the app is
 * alive, [com.tiritibambix.sharesonic.ui.player.PlayerViewModel]'s DataStore
 * observer bridges it into [com.tiritibambix.sharesonic.ui.player.PlayerState].
 */
class ToggleAutoDjCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settingsRepo = SettingsRepository(context)
        val current = settingsRepo.autoDjEnabled.first()
        settingsRepo.saveAutoDjEnabled(!current)
        // Reflect in the widget snapshot immediately (the VM observer would too
        // if the app is alive, but from a cold widget tap this is what makes
        // the tile update on the next frame).
        WidgetStateRepository(context).update { it.copy(autoDjEnabled = !current) }
    }
}
