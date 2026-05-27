package com.tiritibambix.sharesonic.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.settings.ServerSettings
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

/**
 * Navigation model (Navidrome Subsonic):
 *
 *   "root"  → getIndexes()            → flat alphabetical artist list
 *   "{id}"  → getMusicDirectory(id)   → albums (if artist) or tracks (if album)
 *
 * getMusicFolders IDs are not navigable. There is no folder layer above artists.
 * isDir=true means album (navigable), isDir=false means track (playable).
 */
class FolderBrowserViewModel(
    private val settingsRepo: SettingsRepository,
    val folderId: String
) : ViewModel() {

    private val _state = MutableStateFlow<BrowserState>(BrowserState.Loading)
    val state: StateFlow<BrowserState> = _state

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState

    private var _settings: ServerSettings? = null

    fun coverArtUrl(coverArtId: String, size: Int = 64): String? {
        val s = _settings ?: return null
        return SubsonicClient.coverArtUrl(s, coverArtId, size)
    }

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { BrowserState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { BrowserState.Error("Server not configured") }
                return@launch
            }
            _settings = settings
            val repo = buildRepo(settings)
            if (folderId == "root") loadArtists(repo)
            else loadDirectory(repo, folderId)
        }
    }

    /** Root level: all artists from getIndexes, sorted A→Z. */
    private suspend fun loadArtists(repo: SubsonicRepository) {
        when (val r = repo.getIndexes()) {
            is Result.Success -> {
                val artists = r.data.index
                    .flatMap { it.artist }
                    .map { EntryDto(id = it.id, title = it.name, isDir = true) }
                    .sortedBy { it.displayName.lowercase() }
                _state.update { BrowserState.Ready(artists) }
            }
            is Result.Error -> _state.update { BrowserState.Error(r.message) }
        }
    }

    /** Artist or album level: direct children via getMusicDirectory. */
    private suspend fun loadDirectory(repo: SubsonicRepository, id: String) {
        when (val r = repo.getMusicDirectory(id)) {
            is Result.Success -> {
                val sorted = r.data.child
                    .sortedWith(compareByDescending<EntryDto> { it.isDir }
                        .thenBy { it.displayName.lowercase() })
                _state.update { BrowserState.Ready(sorted) }
            }
            is Result.Error -> _state.update { BrowserState.Error(r.message) }
        }
    }

    fun refresh() = load()

    // ── Shuffle ───────────────────────────────────────────────────────────────

    fun shuffleCurrent(
        onReady: (List<EntryDto>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val repo = buildRepo(settings)
            if (folderId == "root") {
                when (val r = repo.getRandomSongs(size = 200)) {
                    is Result.Success -> {
                        if (r.data.isEmpty()) onError("No songs found")
                        else onReady(r.data.shuffled())
                    }
                    is Result.Error -> onError(r.message)
                }
            } else {
                val songs = repo.collectSongs(folderId)
                if (songs.isEmpty()) onError("No songs found")
                else onReady(songs.shuffled())
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    fun shareEntry(entryId: String) {
        _shareState.update { ShareState.Loading }
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            when (val r = buildRepo(settings).createShare(entryId)) {
                is Result.Success -> _shareState.update { ShareState.Done(r.data.url) }
                is Result.Error   -> _shareState.update { ShareState.Error(r.message) }
            }
        }
    }

    fun clearShareState() = _shareState.update { ShareState.Idle }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun buildRepo(settings: ServerSettings): SubsonicRepository {
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
