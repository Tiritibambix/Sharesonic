package com.tiritibambix.sharesonic.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.playback.PlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val currentSong: EntryDto? = null,
    val queue: List<EntryDto> = emptyList(),
    val queueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val coverArtUrl: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shareUrl: String? = null,
    val shareLoading: Boolean = false,
    val shareError: String? = null,
    val playbackError: String? = null
)

class PlayerViewModel(
    private val context: Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var controllerFuture: ListenableFuture<MediaController>? = null

    // Resolved once the MediaController is ready — all play commands await this.
    private val controllerDeferred = CompletableDeferred<MediaController>()

    private var cachedSettings: ServerSettings? = null

    // ── Scrobble tracking ─────────────────────────────────────────────────────
    /** ID of the last song for which a "now playing" ping was sent. */
    private var scrobbleNowPlayingFiredFor: String? = null
    /** ID of the last song for which a 50% scrobble was sent. */
    private var scrobbleFiredFor: String? = null

    init {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val ctrl = try { controllerFuture?.get() } catch (e: Exception) { null }
            if (ctrl != null) {
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val idx = ctrl.currentMediaItemIndex
                        val queue = _state.value.queue
                        if (idx in queue.indices) {
                            val song = queue[idx]
                            _state.update { it.copy(queueIndex = idx, currentSong = song) }
                            // Reset 50% threshold for new track and fire "now playing"
                            scrobbleFiredFor = null
                            if (song.id != scrobbleNowPlayingFiredFor) {
                                scrobbleNowPlayingFiredFor = song.id
                                fireNowPlaying(song)
                            }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        val msg = error.cause?.message?.takeIf { it.isNotBlank() }
                            ?: error.message?.takeIf { it.isNotBlank() }
                            ?: "Playback error (${error.errorCode})"
                        _state.update { it.copy(playbackError = msg, isPlaying = false) }
                    }
                })
                controllerDeferred.complete(ctrl)
            }
        }, MoreExecutors.directExecutor())

        viewModelScope.launch { cachedSettings = settingsRepo.settings.first() }
        startPositionPolling()
    }

    // ── Position polling ──────────────────────────────────────────────────────

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (!controllerDeferred.isCompleted) continue
                val ctrl = controllerDeferred.getCompleted()
                val pos = ctrl.currentPosition.coerceAtLeast(0L)
                val dur = if (ctrl.duration != C.TIME_UNSET && ctrl.duration > 0L)
                    ctrl.duration else 0L
                // Only update position/duration — isPlaying is managed by Player.Listener
                _state.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                // 50% scrobble threshold
                val song = _state.value.currentSong
                if (song != null && dur > 0L && pos >= dur / 2L && scrobbleFiredFor != song.id) {
                    scrobbleFiredFor = song.id
                    fireScrobble(song)
                }
            }
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun playSong(song: EntryDto) {
        viewModelScope.launch {
            val settings = settings()
            val coverUrl = coverArtUrl(settings, song)
            _state.update {
                it.copy(currentSong = song, queue = listOf(song), queueIndex = 0, coverArtUrl = coverUrl)
            }
            val url = streamUrl(settings, song)
            val ctrl = controllerDeferred.await()
            ctrl.setMediaItem(MediaItem.fromUri(url))
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun playQueue(songs: List<EntryDto>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val settings = settings()
            val first = songs[0]
            val coverUrl = coverArtUrl(settings, first)
            _state.update {
                it.copy(queue = songs, queueIndex = 0, currentSong = first, coverArtUrl = coverUrl)
            }
            val items = songs.map { MediaItem.fromUri(streamUrl(settings, it)) }
            val ctrl = controllerDeferred.await()
            ctrl.setMediaItems(items)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun jumpTo(index: Int) {
        val q = _state.value
        if (index !in q.queue.indices) return
        viewModelScope.launch {
            val settings = settings()
            val song = q.queue[index]
            val coverUrl = coverArtUrl(settings, song)
            _state.update { it.copy(queueIndex = index, currentSong = song, coverArtUrl = coverUrl) }
            controllerDeferred.await().seekTo(index, 0L)
        }
    }

    fun playPause() {
        viewModelScope.launch {
            val ctrl = controllerDeferred.await()
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun skipNext() {
        val q = _state.value
        if (q.queueIndex < q.queue.lastIndex) jumpTo(q.queueIndex + 1)
    }

    fun skipPrev() {
        val q = _state.value
        if (q.queueIndex > 0) jumpTo(q.queueIndex - 1)
    }

    fun seekTo(positionMs: Long) {
        _state.update { it.copy(currentPositionMs = positionMs) }
        viewModelScope.launch { controllerDeferred.await().seekTo(positionMs) }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    fun shareCurrentSong() {
        val song = _state.value.currentSong ?: return
        _state.update { it.copy(shareLoading = true, shareUrl = null, shareError = null) }
        viewModelScope.launch {
            val settings = settings()
            val shareUrl = if (song.id.isSubsonicNumericId()) {
                // Song from getRandomSongs — has a real Subsonic integer ID
                val api = SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
                when (val r = SubsonicRepository(api).createShare(song.id)) {
                    is Result.Success -> r.data.url
                    is Result.Error   -> { _state.update { it.copy(shareLoading = false, shareError = r.message) }; return@launch }
                }
            } else {
                // mStream native song — use filepath with native share endpoint
                val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
                when (val r = mStream.share(settings.jwtToken, song.id)) {
                    is Result.Success -> settings.serverUrl.trimEnd('/') + "/shared/${r.data}"
                    is Result.Error   -> { _state.update { it.copy(shareLoading = false, shareError = r.message) }; return@launch }
                }
            }
            _state.update { it.copy(shareLoading = false, shareUrl = shareUrl) }
        }
    }

    fun clearShare() = _state.update { it.copy(shareUrl = null, shareError = null) }

    fun clearPlaybackError() = _state.update { it.copy(playbackError = null) }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        if (controllerDeferred.isCompleted) {
            controllerDeferred.getCompleted().release()
        }
        MediaController.releaseFuture(controllerFuture ?: return)
        super.onCleared()
    }

    // ── Scrobble ──────────────────────────────────────────────────────────────

    /**
     * Send a "now playing" notification when a track starts.
     * - Filepath song → ListenBrainz playing-now via native mStream API
     * - Integer-ID song (search3) → Subsonic scrobble.view submission=false
     * Fire-and-forget — silently ignored if services are not configured.
     */
    private fun fireNowPlaying(song: EntryDto) {
        viewModelScope.launch {
            val settings = settings()
            if (!settings.isConfigured) return@launch
            if (song.id.isSubsonicNumericId()) {
                SubsonicRepository(
                    SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
                ).scrobble(song.id, submission = false)
            } else {
                val token = settings.jwtToken.ifEmpty { return@launch }
                MStreamRepository(MStreamClient.build(settings.serverUrl))
                    .listenBrainzNowPlaying(token, song.id)
            }
        }
    }

    /**
     * Submit a scrobble when 50% of a track has been played.
     * - Filepath song → Last.fm + ListenBrainz via native mStream API
     * - Integer-ID song (search3) → Subsonic scrobble.view submission=true
     * Fire-and-forget — silently ignored if services are not configured.
     */
    private fun fireScrobble(song: EntryDto) {
        viewModelScope.launch {
            val settings = settings()
            if (!settings.isConfigured) return@launch
            if (song.id.isSubsonicNumericId()) {
                SubsonicRepository(
                    SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
                ).scrobble(song.id, submission = true)
            } else {
                val token = settings.jwtToken.ifEmpty { return@launch }
                MStreamRepository(MStreamClient.build(settings.serverUrl))
                    .scrobble(token, song.id)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun settings(): ServerSettings =
        cachedSettings ?: settingsRepo.settings.first().also { cachedSettings = it }

    /**
     * Build the audio stream URL for a song.
     *
     * mStream Subsonic IDs are bare integers (e.g. "42") — songs from getRandomSongs.
     * mStream native songs have a filepath as ID (e.g. "library/Artist/Album/track.mp3").
     *
     * - Numeric ID → Subsonic /rest/stream.view (the integer IS the correct DB row ID)
     * - Filepath ID → native /media/<filepath>?token=<jwt>
     */
    private fun streamUrl(settings: ServerSettings, song: EntryDto): String {
        val base = settings.serverUrl.trimEnd('/')
        return if (song.id.isSubsonicNumericId()) {
            "$base/rest/stream.view?id=${song.id}&u=${settings.username}&p=${settings.password}&v=1.16.1&c=Sharesonic&f=json"
        } else {
            val filepath = song.path ?: song.id
            val encoded = filepath.trimStart('/')
                .replace("%", "%25")
                .replace("#", "%23")
                .replace("?", "%3F")
            "$base/media/$encoded?token=${settings.jwtToken}"
        }
    }

    /**
     * Build the cover art URL for a song.
     *
     * - Subsonic cover art IDs start with "al-" / "ar-" or are numeric → Subsonic getCoverArt
     * - mStream native album art IDs are filenames (e.g. "abc123.jpg") → /album-art/<file>?token=<jwt>
     */
    private fun coverArtUrl(settings: ServerSettings, song: EntryDto): String? {
        val id = song.coverArt ?: return null
        val base = settings.serverUrl.trimEnd('/')
        return if (id.startsWith("al-") || id.startsWith("ar-") || id.isSubsonicNumericId()) {
            SubsonicClient.coverArtUrl(settings, id, 512)
        } else {
            "$base/album-art/$id?token=${settings.jwtToken}"
        }
    }
}

/** Returns true if this string is a mStream Subsonic numeric track ID (bare integer). */
private fun String.isSubsonicNumericId(): Boolean = isNotBlank() && all { it.isDigit() }

class PlayerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    private val settingsRepo = SettingsRepository(context)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(context, settingsRepo) as T
    }
}
