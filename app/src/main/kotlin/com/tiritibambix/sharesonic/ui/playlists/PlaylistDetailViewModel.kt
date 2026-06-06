package com.tiritibambix.sharesonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.PlaylistDetailDto
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistDetailState {
    data object Loading : PlaylistDetailState
    data class Ready(val detail: PlaylistDetailDto) : PlaylistDetailState
    data class Error(val message: String) : PlaylistDetailState
}

sealed interface AddSongsState {
    data object Idle : AddSongsState
    data object Loading : AddSongsState
    data class Results(val songs: List<EntryDto>) : AddSongsState
    data class Error(val message: String) : AddSongsState
}

class PlaylistDetailViewModel(
    private val settingsRepo: SettingsRepository,
    val playlistId: String
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailState>(PlaylistDetailState.Loading)
    val state: StateFlow<PlaylistDetailState> = _state

    // ── Add-songs search state ────────────────────────────────────────────────
    private val _addSongsState = MutableStateFlow<AddSongsState>(AddSongsState.Idle)
    val addSongsState: StateFlow<AddSongsState> = _addSongsState

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { PlaylistDetailState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { PlaylistDetailState.Error("Server not configured") }
                return@launch
            }
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            when (val r = repo.getPlaylist(playlistId)) {
                is Result.Success -> _state.update { PlaylistDetailState.Ready(r.data) }
                is Result.Error   -> _state.update { PlaylistDetailState.Error(r.message) }
            }
        }
    }

    fun removeSong(index: Int) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            repo.removeSongFromPlaylist(playlistId, index)
            load()
        }
    }

    fun addSongs(songIds: List<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            repo.addSongsToPlaylist(playlistId, songIds)
            load()
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            repo.renamePlaylist(playlistId, name)
            load()
        }
    }

    // ── Search for songs to add (uses Subsonic search3 for integer IDs) ───────

    fun searchSongsToAdd(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _addSongsState.update { AddSongsState.Idle }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _addSongsState.update { AddSongsState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _addSongsState.update { AddSongsState.Error("Server not configured") }
                return@launch
            }
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            when (val r = repo.search(query.trim())) {
                is Result.Success ->
                    _addSongsState.update { AddSongsState.Results(r.data.song) }
                is Result.Error   ->
                    _addSongsState.update { AddSongsState.Error(r.message) }
            }
        }
    }

    fun clearAddSongsSearch() {
        searchJob?.cancel()
        _addSongsState.update { AddSongsState.Idle }
    }
}

class PlaylistDetailViewModelFactory(
    private val settingsRepo: SettingsRepository,
    private val playlistId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlaylistDetailViewModel(settingsRepo, playlistId) as T
    }
}
