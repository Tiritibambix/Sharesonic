package com.tiritibambix.sharesonic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tiritibambix.sharesonic.MainActivity
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.widget.WidgetStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Populated in onCreate; kept as fields so Player.Listener callbacks can
    // reach them without holding the whole player.
    private lateinit var widgetState: WidgetStateRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var autoDj: AutoDjOrchestrator

    // Cached vpaths for the Auto-DJ source-folder filter. Refreshed from
    // DataStore in onCreate (the ConnectionTest step of settings populates it).
    private var cachedVpaths: List<String> = emptyList()

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

        widgetState = WidgetStateRepository(applicationContext)
        settingsRepo = SettingsRepository(applicationContext)
        autoDj = AutoDjOrchestrator(
            context = applicationContext,
            settingsRepo = settingsRepo,
            addTrackToQueue = { song ->
                // Marshalling to the service scope keeps ExoPlayer touched on
                // the main thread. Auto-DJ delivers filepath-only EntryDtos —
                // we build the same /media URL the ViewModel uses.
                serviceScope.launch {
                    val settings = settingsRepo.settings.first()
                    val uri = buildStreamUrl(
                        base = settings.serverUrl.trimEnd('/'),
                        filepath = song.path ?: song.id,
                        token = settings.jwtToken,
                    )
                    player.addMediaItem(MediaItem.fromUri(uri))
                }
            },
            // The service doesn't have direct access to the EntryDto queue the
            // ViewModel keeps — but it CAN read the currently playing track's
            // metadata from ExoPlayer. For Auto-DJ seeding (BPM / key / artist)
            // this is a limitation: the fetch will fall through to the
            // no-seed random path when the app UI is dead. Acceptable — the
            // service's job is to keep the queue flowing, not to replicate the
            // full VM state.
            getCurrentTrack = { null },
            getCachedVpaths = { cachedVpaths },
        )

        // Keep vpaths in sync for Auto-DJ source-folder filtering.
        serviceScope.launch {
            settingsRepo.vpaths.collect { cachedVpaths = it }
        }

        // Player events → widget snapshot + Auto-DJ trigger on track end.
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishSnapshot(player)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishSnapshot(player)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    serviceScope.launch {
                        val enabled = settingsRepo.autoDjEnabled.first()
                        if (enabled) autoDj.fetchNext(serviceScope)
                    }
                }
                publishSnapshot(player)
            }
        })

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

    /** Publish an isPlaying-focused snapshot. The ViewModel supplies the richer
     *  fields (title/artist/rating/coverArtUrl) when it's alive — this ensures
     *  isPlaying stays fresh even after the Activity is destroyed. */
    private fun publishSnapshot(player: Player) {
        serviceScope.launch {
            widgetState.update { current ->
                current.copy(isPlaying = player.isPlaying)
            }
        }
    }

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

    /**
     * Mirror [com.tiritibambix.sharesonic.ui.player.PlayerViewModel.streamUrl] for
     * native (filepath) tracks. The service never sees Subsonic numeric-ID tracks —
     * the Auto-DJ endpoint returns filepath tracks only.
     */
    private fun buildStreamUrl(base: String, filepath: String, token: String): String {
        val encoded = filepath.trimStart('/')
            .split('/')
            .joinToString("/") { Uri.encode(it) }
        return "$base/media/$encoded?token=$token"
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
