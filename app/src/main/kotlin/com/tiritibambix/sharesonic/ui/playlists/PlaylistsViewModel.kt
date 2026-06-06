package com.tiritibambix.sharesonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.PlaylistDto
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistsState {
    data object Loading : PlaylistsState
    data class Ready(val playlists: List<PlaylistDto>) : PlaylistsState
    data class Error(val message: String) : PlaylistsState
}

class PlaylistsViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsState>(PlaylistsState.Loading)
    val state: StateFlow<PlaylistsState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { PlaylistsState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { PlaylistsState.Error("Server not configured") }
                return@launch
            }
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            when (val r = repo.getPlaylists()) {
                is Result.Success -> _state.update { PlaylistsState.Ready(r.data) }
                is Result.Error   -> _state.update { PlaylistsState.Error(r.message) }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            repo.createPlaylist(name)
            load()
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
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

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val repo = SubsonicRepository(
                SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
            )
            repo.deletePlaylist(playlistId)
            load()
        }
    }
}

class PlaylistsViewModelFactory(
    private val settingsRepo: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlaylistsViewModel(settingsRepo) as T
    }
}
