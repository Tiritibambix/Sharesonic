package com.tiritibambix.sharesonic.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.BpmRange
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsRequest
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.settings.AutoDjSettings
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.utils.CamelotWheel
import com.tiritibambix.sharesonic.playback.PlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val playbackError: String? = null,
    /** True while Auto-DJ is running — queue auto-extends as tracks are consumed. */
    val autoDjEnabled: Boolean = false,
    /** Live audio bitrate of the currently playing track, in kbps (null if unknown). Shown on Now Playing only. */
    val audioBitrateKbps: Int? = null
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

    // ── Playlist state (for "Add to playlist" in NowPlaying) ─────────────────
    private val _playlists = MutableStateFlow<List<NativePlaylist>>(emptyList())
    val playlists: StateFlow<List<NativePlaylist>> = _playlists

    // ── Auto-DJ internal state ────────────────────────────────────────────────
    /** Songs already returned this session — passed back to avoid repeats. */
    private var autoDjIgnoreList: List<Int> = emptyList()
    /** Ring buffer of recently played artist names for cooldown tracking. */
    private val recentlyPlayedArtists = ArrayDeque<String>()
    /** Cache of Last.fm similar-artist lookups (artist → list of similar artists). */
    private val similarArtistsCache = mutableMapOf<String, List<String>>()
    /** Latest snapshot of the user's Auto-DJ configuration. */
    private var autoDjSettings: AutoDjSettings = AutoDjSettings()
    /** Available library vpaths from the last successful connection test. */
    private var cachedVpaths: List<String> = emptyList()

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
                        ctrl.volume = 1f   // restore from any crossfade fade-out
                        val idx = ctrl.currentMediaItemIndex
                        val queue = _state.value.queue
                        if (idx in queue.indices) {
                            val song = queue[idx]
                            // jumpTo() already sets coverArtUrl for manual skips/queue
                            // taps, but auto-advance (track end, Auto-DJ enqueue, or a
                            // notification "skip") reaches this listener directly —
                            // without recomputing it here, the artwork (or lack of it)
                            // from the previous track stuck around indefinitely.
                            val coverUrl = cachedSettings?.let { coverArtUrl(it, song) }
                            _state.update { it.copy(queueIndex = idx, currentSong = song, coverArtUrl = coverUrl) }
                            // Reset 50% threshold for new track and fire "now playing"
                            scrobbleFiredFor = null
                            if (song.id != scrobbleNowPlayingFiredFor) {
                                scrobbleNowPlayingFiredFor = song.id
                                fireNowPlaying(song)
                            }
                            // Auto-DJ: track artist for cooldown
                            val artist = song.artist
                            if (!artist.isNullOrBlank()) {
                                recentlyPlayedArtists.remove(artist)   // deduplicate
                                recentlyPlayedArtists.addLast(artist)
                                while (recentlyPlayedArtists.size > autoDjSettings.artistCooldown) {
                                    recentlyPlayedArtists.removeFirst()
                                }
                            }
                            // Auto-DJ: when we reach the last queued track, pre-fetch the next one
                            if (_state.value.autoDjEnabled && idx == queue.lastIndex) {
                                fetchAndEnqueueAutoDjSong()
                            }
                        }
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        // Pull the live bitrate from the currently selected audio track's
                        // Format — this reflects the actual stream, not just file metadata.
                        val audioFormat: Format? = tracks.groups
                            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                            ?.let { group ->
                                (0 until group.length)
                                    .firstOrNull { i -> group.isTrackSelected(i) }
                                    ?.let { i -> group.getTrackFormat(i) }
                            }
                        val bitrateKbps = audioFormat?.bitrate
                            ?.takeIf { it != Format.NO_VALUE && it > 0 }
                            ?.let { it / 1000 }
                        _state.update { it.copy(audioBitrateKbps = bitrateKbps) }
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

        // Keep server settings live so vpaths (and future token refreshes) are visible
        viewModelScope.launch {
            settingsRepo.settings.collect { cachedSettings = it }
        }
        // Keep Auto-DJ settings in sync with DataStore (live — not first-only)
        viewModelScope.launch {
            settingsRepo.autoDjSettings.collect { settings ->
                autoDjSettings = settings
            }
        }
        // Keep vpaths in sync for Auto-DJ source-folder filtering
        viewModelScope.launch {
            settingsRepo.vpaths.collect { cachedVpaths = it }
        }
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
                // Auto-DJ crossfade: fade the current track out in the last N seconds
                if (_state.value.autoDjEnabled) {
                    val crossSec = autoDjSettings.crossfadeDurationSec
                    if (crossSec > 0 && dur > crossSec * 1000L) {
                        val fadeStartMs = dur - crossSec * 1000L
                        if (pos >= fadeStartMs) {
                            val fadeProgress = ((dur - pos).toFloat() / (crossSec * 1000f))
                                .coerceIn(0f, 1f)
                            ctrl.volume = fadeProgress
                        } else if (ctrl.volume < 1f) {
                            ctrl.volume = 1f   // restore if user seeked back into the track
                        }
                    }
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

    /**
     * Remove the track at [index] from the queue.
     * Removing the currently playing track is a no-op (use skip instead).
     * Tracks before the current index shift the index down by one.
     */
    fun removeFromQueue(index: Int) {
        val q = _state.value
        if (index !in q.queue.indices || index == q.queueIndex) return
        val newQueue = q.queue.toMutableList().apply { removeAt(index) }
        val newIndex = if (index < q.queueIndex) q.queueIndex - 1 else q.queueIndex
        _state.update { it.copy(queue = newQueue, queueIndex = newIndex) }
        viewModelScope.launch { controllerDeferred.await().removeMediaItem(index) }
    }

    /**
     * Append [song] to the end of the current queue without interrupting playback.
     * If nothing is playing, falls back to [playSong] and starts playback immediately.
     */
    fun addToQueue(song: EntryDto) {
        viewModelScope.launch {
            val settings = settings()
            val currentQueue = _state.value.queue
            if (currentQueue.isEmpty()) {
                playSong(song)
                return@launch
            }
            val newQueue = currentQueue + song
            _state.update { it.copy(queue = newQueue) }
            val ctrl = controllerDeferred.await()
            ctrl.addMediaItem(MediaItem.fromUri(streamUrl(settings, song)))
        }
    }

    // ── Playlist management ───────────────────────────────────────────────────

    /**
     * Load the user's playlists for the "Add to playlist" picker.
     * Cached after first load; pass [forceRefresh] = true to bypass.
     */
    fun loadPlaylists(forceRefresh: Boolean = false) {
        if (!forceRefresh && _playlists.value.isNotEmpty()) return
        viewModelScope.launch {
            val settings = settings()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val velvet = VelvetRepository(VelvetClient.build(settings.serverUrl))
            when (val r = velvet.getPlaylists(token)) {
                is Result.Error -> {}
                is Result.Success -> {
                    _playlists.update { r.data }
                    // Refresh real song counts in parallel (getall count is denormalized)
                    val refreshed = r.data.map { playlist ->
                        async {
                            val loaded = velvet.loadPlaylist(token, playlist.name)
                            if (loaded is Result.Success) playlist.copy(songCount = loaded.data.size)
                            else playlist
                        }
                    }.awaitAll()
                    _playlists.update { refreshed }
                }
            }
        }
    }

    /** Append the currently playing track to [playlistName]. Fire-and-forget. */
    fun addCurrentSongToPlaylist(playlistName: String) {
        val song = _state.value.currentSong ?: return
        viewModelScope.launch {
            val settings = settings()
            val token = settings.jwtToken.ifEmpty { return@launch }
            VelvetRepository(VelvetClient.build(settings.serverUrl))
                .addSongToPlaylist(token, song.id, playlistName)
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

    // ── Auto-DJ ───────────────────────────────────────────────────────────────

    /**
     * Toggle the Auto-DJ feature on or off.
     * When turned on while at the last track of the queue, fetches the next song immediately.
     * When turned off, the session state (ignoreList, artist cache) is reset.
     */
    fun toggleAutoDj() {
        val wasEnabled = _state.value.autoDjEnabled
        _state.update { it.copy(autoDjEnabled = !wasEnabled) }
        if (wasEnabled) {
            // Turning off — reset session state
            autoDjIgnoreList = emptyList()
            recentlyPlayedArtists.clear()
            similarArtistsCache.clear()
        } else {
            // Turning on — if already at the last track, fetch immediately
            val s = _state.value
            if (s.queue.isNotEmpty() && s.queueIndex == s.queue.lastIndex) {
                fetchAndEnqueueAutoDjSong()
            }
        }
    }

    /**
     * Fetch one new song using the Auto-DJ algorithm and append it to the queue.
     *
     * Priority order:
     * 1. Similar artists (from Last.fm cache) + BPM (tight) + Camelot key
     * 2. BPM (wide) + Camelot key  (if tight fails)
     * 3. Plain random  (fallback — no constraints)
     *
     * The ignoreList is persisted across calls so no song repeats during the session.
     */
    private fun fetchAndEnqueueAutoDjSong() {
        viewModelScope.launch {
            val serverSettings = settings()
            val token = serverSettings.jwtToken.ifEmpty { return@launch }
            val current = _state.value.currentSong
            val velvet = VelvetRepository(VelvetClient.build(serverSettings.serverUrl))

            // 1. Similar artists (cached per artist)
            val similarArtists: List<String>? = if (autoDjSettings.useSimilarArtists
                && !current?.artist.isNullOrBlank()
            ) {
                similarArtistsCache.getOrPut(current!!.artist!!) {
                    when (val r = velvet.getSimilarArtists(token, current.artist!!)) {
                        is Result.Success -> r.data
                        else              -> emptyList()
                    }
                }.takeIf { it.isNotEmpty() }
            } else null

            // 2. BPM ranges from current track
            val bpmTight: List<BpmRange>? = if (autoDjSettings.useBpm && current?.bpm != null) {
                listOf(BpmRange(
                    current.bpm!! - autoDjSettings.bpmTightRange,
                    current.bpm!! + autoDjSettings.bpmTightRange
                ))
            } else null
            val bpmWide: List<BpmRange>? = if (autoDjSettings.useBpm && current?.bpm != null) {
                listOf(BpmRange(
                    current.bpm!! - autoDjSettings.bpmWideRange,
                    current.bpm!! + autoDjSettings.bpmWideRange
                ))
            } else null

            // 3. Compatible Camelot keys
            val keys: List<String>? = if (autoDjSettings.useHarmonicMixing
                && !current?.musicalKey.isNullOrBlank()
            ) {
                CamelotWheel.compatibleKeys(current!!.musicalKey!!)
            } else null

            // 4. Artist cooldown
            val cooldownArtists: List<String>? = recentlyPlayedArtists.toList().takeIf { it.isNotEmpty() }

            // 5. Genre filter
            val genreList: List<String>? = if (autoDjSettings.genreMode != "off") autoDjSettings.genres.takeIf { it.isNotEmpty() } else null
            val genreMode: String?       = if (autoDjSettings.genreMode != "off") autoDjSettings.genreMode else null
            val minRating: Int?          = if (autoDjSettings.minRating > 0) autoDjSettings.minRating else null

            // Source-folder filter — compute the vpaths to exclude from random selection
            val sourceFolders = autoDjSettings.sourceFolders
            val ignoreVPaths: List<String>? = if (sourceFolders.isNotEmpty() && cachedVpaths.isNotEmpty()) {
                cachedVpaths.filter { it !in sourceFolders }.takeIf { it.isNotEmpty() }
            } else null

            // Build primary request (tight BPM + similar artists + Camelot)
            val primaryRequest = VelvetRandomSongsRequest(
                ignoreList          = autoDjIgnoreList,
                ignoreVPaths        = ignoreVPaths,
                bpmRanges           = bpmTight,
                bpmRangesWide       = bpmWide,
                requireBpm          = if (autoDjSettings.requireBpm) true else null,
                musicalKeys         = keys,
                requireMusicalKey   = if (autoDjSettings.requireKey) true else null,
                artists             = similarArtists,
                ignoreArtists       = cooldownArtists,
                genres              = genreList,
                genreMode           = genreMode,
                minRating           = minRating
            )

            when (val r = velvet.fetchAutoDjSong(token, primaryRequest)) {
                is Result.Success -> {
                    autoDjIgnoreList = r.data.second
                    addToQueue(r.data.first)
                    return@launch
                }
                is Result.Error -> Unit   // fall through to fallback
            }

            // Fallback: plain random (no constraints except ignoreList + source filter)
            when (val fallback = velvet.fetchAutoDjSong(
                token,
                VelvetRandomSongsRequest(ignoreList = autoDjIgnoreList, ignoreVPaths = ignoreVPaths)
            )) {
                is Result.Success -> {
                    autoDjIgnoreList = fallback.data.second
                    addToQueue(fallback.data.first)
                }
                is Result.Error -> Unit   // silently give up — will retry on next transition
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    /**
     * @param expiryDays Number of days until the link expires, as entered in the
     *                   share dialog's "days until expiration" field (mirrors Velvet
     *                   Velvet); null → permanent link.
     */
    fun shareCurrentSong(expiryDays: Int? = null) {
        val song = _state.value.currentSong ?: return
        _state.update { it.copy(shareLoading = true, shareUrl = null, shareError = null) }
        viewModelScope.launch {
            val settings = settings()
            val shareUrl = if (song.id.isSubsonicNumericId()) {
                // Song from getRandomSongs — has a real Subsonic integer ID
                val api = SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
                when (val r = SubsonicRepository(api).createShare(song.id, expiryDays)) {
                    is Result.Success -> r.data.url
                    is Result.Error   -> { _state.update { it.copy(shareLoading = false, shareError = r.message) }; return@launch }
                }
            } else {
                // Velvet native song — use filepath with native share endpoint
                val token = ensureToken(settings) ?: run {
                    _state.update { it.copy(shareLoading = false, shareError = "Authentication failed") }
                    return@launch
                }
                val velvet = VelvetRepository(VelvetClient.build(settings.serverUrl))
                when (val r = velvet.share(token, song.id, expiryDays)) {
                    is Result.Success -> settings.serverUrl.trimEnd('/') + "/shared/${r.data}"
                    is Result.Error   -> { _state.update { it.copy(shareLoading = false, shareError = r.message) }; return@launch }
                }
            }
            _state.update { it.copy(shareLoading = false, shareUrl = shareUrl) }
        }
    }

    /**
     * Create a public share link for the *entire current queue* as a single shared
     * playlist (mirrors Velvet's "share queue" behaviour). Only native
     * filepath-identified songs can be shared this way — Subsonic search-result
     * songs (numeric IDs) are silently skipped from the shared playlist.
     *
     * @param expiryDays Number of days until the link expires; null → permanent link.
     */
    fun shareQueue(expiryDays: Int? = null) {
        val filepaths = _state.value.queue.map { it.id }.filterNot { it.isSubsonicNumericId() }
        if (filepaths.isEmpty()) {
            _state.update { it.copy(shareError = "Nothing shareable in the queue") }
            return
        }
        _state.update { it.copy(shareLoading = true, shareUrl = null, shareError = null) }
        viewModelScope.launch {
            val settings = settings()
            val token = ensureToken(settings) ?: run {
                _state.update { it.copy(shareLoading = false, shareError = "Authentication failed") }
                return@launch
            }
            val velvet = VelvetRepository(VelvetClient.build(settings.serverUrl))
            when (val r = velvet.shareQueue(token, filepaths, expiryDays)) {
                is Result.Success -> {
                    val url = settings.serverUrl.trimEnd('/') + "/shared/${r.data}"
                    _state.update { it.copy(shareLoading = false, shareUrl = url) }
                }
                is Result.Error -> _state.update { it.copy(shareLoading = false, shareError = r.message) }
            }
        }
    }

    /**
     * Rate the currently playing track — Now Playing screen only (mini player has no room).
     * Sharesonic shows 0–5 stars; Velvet's native scale is 0–10 (half-star precision),
     * so [stars] is doubled before being sent. Tapping the already-set star clears the rating.
     *
     * Native ratings need a filepath identifier — only available for songs browsed via
     * file-explorer / shuffle / Auto-DJ (their `id` IS the filepath). Subsonic search-result
     * songs carry a numeric ID instead and aren't ratable through this native endpoint.
     */
    fun rateCurrentSong(stars: Int) {
        val song = _state.value.currentSong ?: return
        if (song.id.isSubsonicNumericId()) return

        // `song.rating` is stored on the **native 0–10** scale (half-star precision);
        // [stars] arrives on the **UI 0–5** scale (the tapped star's index). The two
        // must be converted to the same scale before comparing — comparing them
        // directly was the bug that made tapping a star behave erratically (it would
        // "toggle off" stars that weren't actually active, or leave the wrong rating
        // stored on the EntryDto, which the UI then divided by 2 a second time).
        val currentStars = (song.rating ?: 0) / 2
        val newStars = if (currentStars == stars) 0 else stars
        val newRatingNative = newStars.takeIf { it > 0 }?.times(2)

        // Optimistic update — reflect immediately in currentSong and the queue entry.
        // EntryDto.rating always holds the native 0–10 value, so the UI's `/ 2` stays correct.
        fun applyRating(s: EntryDto) = if (s.id == song.id) s.copy(rating = newRatingNative) else s
        _state.update {
            it.copy(
                currentSong = it.currentSong?.let(::applyRating),
                queue = it.queue.map(::applyRating)
            )
        }

        viewModelScope.launch {
            val settings = settings()
            val token = ensureToken(settings) ?: return@launch
            val velvet = VelvetRepository(VelvetClient.build(settings.serverUrl))
            // rateSong() expects the UI 0–5 scale and doubles it internally — pass `newStars`, not the native value.
            val result = velvet.rateSong(token, song.id, newStars.takeIf { it > 0 })
            if (result is Result.Error) {
                // Revert on failure — keep state truthful to what the server holds.
                fun revert(s: EntryDto) = if (s.id == song.id) s.copy(rating = song.rating) else s
                _state.update {
                    it.copy(
                        currentSong = it.currentSong?.let(::revert),
                        queue = it.queue.map(::revert)
                    )
                }
            }
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
     * - Filepath song → ListenBrainz playing-now via native Velvet API
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
                val token = ensureToken(settings) ?: return@launch
                VelvetRepository(VelvetClient.build(settings.serverUrl))
                    .listenBrainzNowPlaying(token, song.id)
            }
        }
    }

    /**
     * Submit a scrobble when 50% of a track has been played.
     * - Filepath song → Last.fm + ListenBrainz via native Velvet API
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
                val token = ensureToken(settings) ?: return@launch
                VelvetRepository(VelvetClient.build(settings.serverUrl))
                    .scrobble(token, song.id)
            }
        }
    }

    /**
     * Return the stored JWT if present, or attempt a fresh login and persist the new token.
     * Returns null only if login fails (server unreachable or wrong credentials).
     */
    private suspend fun ensureToken(settings: ServerSettings): String? {
        if (settings.jwtToken.isNotEmpty()) return settings.jwtToken
        val velvet = VelvetRepository(VelvetClient.build(settings.serverUrl))
        val result = velvet.login(settings.username, settings.password)
        if (result is Result.Success) {
            settingsRepo.saveToken(result.data)
            return result.data
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun settings(): ServerSettings =
        cachedSettings ?: settingsRepo.settings.first().also { cachedSettings = it }

    /**
     * Build the audio stream URL for a song.
     *
     * Velvet Subsonic IDs are bare integers (e.g. "42") — songs from getRandomSongs.
     * Velvet native songs have a filepath as ID (e.g. "library/Artist/Album/track.mp3").
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
            // Percent-encode each path segment (keeping '/' separators) exactly like
            // the Velvet webapp's encodeFp(). The previous version only escaped
            // %, # and ? — it left spaces, '&', '+' and accented characters (é, è, à,
            // ç…) literal, which 404'd the /media/ request for any such path. On a
            // French library those are everywhere, so playback failed constantly.
            val encoded = filepath.trimStart('/')
                .split('/')
                .joinToString("/") { Uri.encode(it) }
            "$base/media/$encoded?token=${settings.jwtToken}"
        }
    }

    /**
     * Build the cover art URL for a song.
     *
     * - Subsonic cover art IDs start with "al-" / "ar-" or are numeric → Subsonic getCoverArt
     * - Velvet native album art IDs are filenames (e.g. "abc123.jpg") → /album-art/<file>?token=<jwt>
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

/** Returns true if this string is a Velvet Subsonic numeric track ID (bare integer). */
private fun String.isSubsonicNumericId(): Boolean = isNotBlank() && all { it.isDigit() }

class PlayerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    private val settingsRepo = SettingsRepository(context)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(context, settingsRepo) as T
    }
}
