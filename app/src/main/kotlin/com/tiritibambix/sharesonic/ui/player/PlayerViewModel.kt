package com.tiritibambix.sharesonic.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.playback.PlaybackService
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
    val shareUrl: String? = null,
    val shareLoading: Boolean = false,
    val shareError: String? = null
)

class PlayerViewModel(
    private val context: Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var cachedSettings: ServerSettings? = null

    init {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            cachedSettings = settingsRepo.settings.first()
        }
    }

    fun playSong(song: EntryDto) {
        viewModelScope.launch {
            val settings = settings()
            val url = streamUrl(settings, song.id)
            val coverUrl = song.coverArt?.let { SubsonicClient.coverArtUrl(settings, it, 512) }
            _state.update {
                it.copy(
                    currentSong = song,
                    queue = listOf(song),
                    queueIndex = 0,
                    isPlaying = true,
                    coverArtUrl = coverUrl
                )
            }
            controller?.setMediaItem(MediaItem.fromUri(url))
            controller?.prepare()
            controller?.play()
        }
    }

    fun playQueue(songs: List<EntryDto>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val settings = settings()
            val first = songs[0]
            val coverUrl = first.coverArt?.let { SubsonicClient.coverArtUrl(settings, it, 512) }
            _state.update {
                it.copy(
                    queue = songs,
                    queueIndex = 0,
                    currentSong = first,
                    isPlaying = true,
                    coverArtUrl = coverUrl
                )
            }
            val items = songs.map { MediaItem.fromUri(streamUrl(settings, it.id)) }
            controller?.setMediaItems(items)
            controller?.prepare()
            controller?.play()
        }
    }

    fun jumpTo(index: Int) {
        val q = _state.value
        if (index !in q.queue.indices) return
        val song = q.queue[index]
        viewModelScope.launch {
            val settings = settings()
            val coverUrl = song.coverArt?.let { SubsonicClient.coverArtUrl(settings, it, 512) }
            _state.update {
                it.copy(queueIndex = index, currentSong = song, coverArtUrl = coverUrl)
            }
        }
        controller?.seekTo(index, 0L)
    }

    fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun skipNext() {
        val q = _state.value
        if (q.queueIndex < q.queue.lastIndex) jumpTo(q.queueIndex + 1)
    }

    fun skipPrev() {
        val q = _state.value
        if (q.queueIndex > 0) jumpTo(q.queueIndex - 1)
    }

    fun shareCurrentSong() {
        val song = _state.value.currentSong ?: return
        _state.update { it.copy(shareLoading = true, shareUrl = null, shareError = null) }
        viewModelScope.launch {
            val settings = settings()
            val api = SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            when (val result = SubsonicRepository(api).createShare(song.id)) {
                is Result.Success -> _state.update { it.copy(shareLoading = false, shareUrl = result.data.url) }
                is Result.Error   -> _state.update { it.copy(shareLoading = false, shareError = result.message) }
            }
        }
    }

    fun clearShare() = _state.update { it.copy(shareUrl = null, shareError = null) }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture ?: return)
        super.onCleared()
    }

    private suspend fun settings(): ServerSettings =
        cachedSettings ?: settingsRepo.settings.first().also { cachedSettings = it }

    private fun streamUrl(settings: ServerSettings, id: String): String {
        val salt = (1..12).map { ('a'..'z').random() }.joinToString("")
        val token = SubsonicClient.md5(settings.password + salt)
        val base = settings.serverUrl.trimEnd('/')
        return "$base/rest/stream.view?id=$id&u=${settings.username}&t=$token&s=$salt&v=1.16.1&c=Sharesonic&f=json"
    }
}

class PlayerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    private val settingsRepo = SettingsRepository(context)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(context, settingsRepo) as T
    }
}
