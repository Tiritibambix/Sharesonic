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
 * folderId encoding:
 *   "root"    → getMusicFolders           (library list)
 *   "mf_{id}" → getIndexes(musicFolderId) (immediate children of the library root)
 *   "{id}"    → getMusicDirectory(id)     (immediate children of any sub-directory)
 *
 * WHY getIndexes for mf_* and NOT getMusicDirectory:
 * getMusicFolders returns integer IDs that live in a separate Navidrome table
 * (music_folders). Passing one of those IDs to getMusicDirectory returns
 * error 70 "Data not found" because it is not a directory ID.
 * getIndexes is the only endpoint that accepts a musicFolderId and returns
 * the immediate top-level directory entries of that library.
 *
 * Entries are sorted: directories first (A→Z), then files (A→Z).
 */
class FolderBrowserViewModel(
    private val settingsRepo: SettingsRepository,
    val folderId: String
) : ViewModel() {

    private val _state = MutableStateFlow<BrowserState>(BrowserState.Loading)
    val state: StateFlow<BrowserState> = _state

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState

    /** Cover-art URL builder — set once settings are loaded. */
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
            when {
                folderId == "root"         -> loadRootFolders(repo)
                folderId.startsWith("mf_") -> loadLibraryRoot(repo, folderId.removePrefix("mf_"))
                else                       -> loadDirectory(repo, folderId)
            }
        }
    }

    private suspend fun loadRootFolders(repo: SubsonicRepository) {
        when (val r = repo.getMusicFolders()) {
            is Result.Success -> {
                val entries = r.data.map { EntryDto(id = "mf_${it.id}", title = it.name, isDir = true) }
                _state.update { BrowserState.Ready(entries) }
            }
            is Result.Error -> _state.update { BrowserState.Error(r.message) }
        }
    }

    /** Level 2: immediate children of a music library root, via getIndexes. */
    private suspend fun loadLibraryRoot(repo: SubsonicRepository, musicFolderId: String) {
        when (val r = repo.getIndexes(musicFolderId)) {
            is Result.Success -> {
                // Flatten all index groups (A, B, C…) → one sorted list, dirs first
                val dirs = r.data.index
                    .flatMap { it.artist }
                    .map { EntryDto(id = it.id, title = it.name, isDir = true) }
                    .sortedBy { it.displayName.lowercase() }
                val loose = r.data.child
                    .sortedWith(compareByDescending<EntryDto> { it.isDir }
                        .thenBy { it.displayName.lowercase() })
                _state.update { BrowserState.Ready(dirs + loose) }
            }
            is Result.Error -> _state.update { BrowserState.Error(r.message) }
        }
    }

    /** Level 3+: immediate children of any sub-directory. */
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

    /**
     * Shuffle the current level:
     * - root / mf_*  → getRandomSongs (fast, server-side random, large set)
     * - sub-directory → recursive collect + local shuffle
     */
    fun shuffleCurrent(
        onReady: (List<EntryDto>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val repo = buildRepo(settings)

            when {
                folderId == "root" -> {
                    // Shuffle entire library via getRandomSongs
                    when (val r = repo.getRandomSongs(size = 200)) {
                        is Result.Success -> {
                            if (r.data.isEmpty()) onError("No songs found")
                            else onReady(r.data.shuffled())
                        }
                        is Result.Error -> onError(r.message)
                    }
                }
                folderId.startsWith("mf_") -> {
                    // Shuffle a whole library — getRandomSongs filtered by folder
                    val musicFolderId = folderId.removePrefix("mf_")
                    when (val r = repo.getRandomSongs(size = 200, musicFolderId = musicFolderId)) {
                        is Result.Success -> {
                            if (r.data.isEmpty()) onError("No songs found")
                            else onReady(r.data.shuffled())
                        }
                        is Result.Error -> onError(r.message)
                    }
                }
                else -> {
                    // Shuffle a specific sub-directory recursively
                    val songs = repo.collectSongs(folderId)
                    if (songs.isEmpty()) onError("No songs found")
                    else onReady(songs.shuffled())
                }
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    fun shareEntry(entryId: String) {
        val realId = entryId.removePrefix("mf_")
        _shareState.update { ShareState.Loading }
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            when (val r = buildRepo(settings).createShare(realId)) {
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
