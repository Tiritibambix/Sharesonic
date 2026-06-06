package com.tiritibambix.sharesonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistsState {
    data object Loading : PlaylistsState
    data class Ready(val playlists: List<NativePlaylist>) : PlaylistsState
    data class Error(val message: String) : PlaylistsState
}

class PlaylistsViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsState>(PlaylistsState.Loading)
    val state: StateFlow<PlaylistsState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { PlaylistsState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { PlaylistsState.Error("Server not configured") }
                return@launch
            }
            val token = settings.jwtToken.ifEmpty {
                _state.update { PlaylistsState.Error("Not authenticated — open Settings") }
                return@launch
            }
            val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
            when (val r = repo.getPlaylists(token)) {
                is Result.Error -> _state.update { PlaylistsState.Error(r.message) }
                is Result.Success -> {
                    // Show list immediately with whatever getall returns
                    _state.update { PlaylistsState.Ready(r.data) }
                    // mStream Velvet's getall stores a denormalized songCount that is NOT updated
                    // by add-song / remove-song — load each playlist in parallel to get the real count.
                    val refreshed = r.data.map { playlist ->
                        async {
                            val loaded = repo.loadPlaylist(token, playlist.name)
                            if (loaded is Result.Success)
                                playlist.copy(songCount = loaded.data.size)
                            else
                                playlist
                        }
                    }.awaitAll()
                    _state.update { PlaylistsState.Ready(refreshed) }
                }
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
            repo.createPlaylist(token, title)
            load()
        }
    }

    fun renamePlaylist(oldName: String, newName: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
            repo.renamePlaylist(token, oldName, newName)
            load()
        }
    }

    fun deletePlaylist(name: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
            repo.deletePlaylist(token, name)
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
