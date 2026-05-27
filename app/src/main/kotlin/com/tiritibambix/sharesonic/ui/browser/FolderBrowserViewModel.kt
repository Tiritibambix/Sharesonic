package com.tiritibambix.sharesonic.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.MusicFolderDto
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BrowserState {
    data object Loading : BrowserState
    data class Ready(val entries: List<EntryDto>) : BrowserState
    data class Error(val message: String) : BrowserState
}

sealed interface ShareState {
    data object Idle : ShareState
    data object Loading : ShareState
    data class Done(val url: String) : ShareState
    data class Error(val message: String) : ShareState
}

class FolderBrowserViewModel(
    private val settingsRepo: SettingsRepository,
    private val folderId: String
) : ViewModel() {

    private val _state = MutableStateFlow<BrowserState>(BrowserState.Loading)
    val state: StateFlow<BrowserState> = _state

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { BrowserState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { BrowserState.Error("Server not configured") }
                return@launch
            }
            val repo = buildRepo(settings)
            if (folderId == "root") {
                loadRootFolders(repo)
            } else {
                loadDirectory(repo, folderId)
            }
        }
    }

    private suspend fun loadRootFolders(repo: SubsonicRepository) {
        when (val result = repo.getMusicFolders()) {
            is Result.Success -> {
                // Each music folder is presented as a virtual directory entry
                val entries = result.data.map { folder ->
                    EntryDto(id = folder.id, title = folder.name, isDir = true)
                }
                _state.update { BrowserState.Ready(entries) }
            }
            is Result.Error -> _state.update { BrowserState.Error(result.message) }
        }
    }

    private suspend fun loadDirectory(repo: SubsonicRepository, id: String) {
        when (val result = repo.getMusicDirectory(id)) {
            is Result.Success -> {
                _state.update { BrowserState.Ready(result.data.child) }
            }
            is Result.Error -> _state.update { BrowserState.Error(result.message) }
        }
    }

    fun refresh() = load()

    fun shareEntry(entryId: String) {
        _shareState.update { ShareState.Loading }
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val repo = buildRepo(settings)
            when (val result = repo.createShare(entryId)) {
                is Result.Success -> _shareState.update { ShareState.Done(result.data.url) }
                is Result.Error -> _shareState.update { ShareState.Error(result.message) }
            }
        }
    }

    fun clearShareState() = _shareState.update { ShareState.Idle }

    /** Collect all songs from a directory and return them shuffled. */
    fun collectShuffled(
        directoryId: String,
        onReady: (List<EntryDto>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val repo = buildRepo(settings)
            val songs = repo.collectSongs(directoryId)
            if (songs.isEmpty()) onError("No songs found")
            else onReady(songs.shuffled())
        }
    }

    private suspend fun buildRepo(settings: com.tiritibambix.sharesonic.data.settings.ServerSettings): SubsonicRepository {
        val api = SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
        return SubsonicRepository(api)
    }
}

class FolderBrowserViewModelFactory(
    private val repo: SettingsRepository,
    private val folderId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FolderBrowserViewModel(repo, folderId) as T
    }
}
