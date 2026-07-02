package com.tiritibambix.sharesonic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tiritibambix.sharesonic.MainActivity
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()

        // Bind a stable audio session id to the player, then attach a platform
        // Equalizer to it so the EQ settings screen can control playback EQ.
        val audioSessionId =
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        player.setAudioSessionId(audioSessionId)
        EqualizerController.attach(audioSessionId)
        applySavedEqualizer()

        // Tapping the media notification / lock-screen / quick-settings "now playing"
        // overlay should bring Sharesonic to the foreground — Media3 wires this up
        // automatically via the session's PendingIntent (setSessionActivity). Without
        // it, the overlay has no launch target and taps on it do nothing.
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Re-apply the user's saved equalizer gains + on/off once the effect is attached. */
    private fun applySavedEqualizer() {
        if (!EqualizerController.available) return
        serviceScope.launch {
            val eq = SettingsRepository(applicationContext).eqSettings.first()
            eq.bandsMb.forEachIndexed { index, level ->
                if (index < EqualizerController.bandCount) {
                    EqualizerController.setBandLevelMb(index, level)
                }
            }
            EqualizerController.enabled = eq.enabled
        }
    }

    override fun onDestroy() {
        EqualizerController.release()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
