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
 *   "root"    → getMusicFolders                     (library list)
 *   "mf_{id}" → loadLibraryRoot: getMusicDirectory(id), fallback getIndexes.child
 *   "{id}"    → getMusicDirectory(id)  (direct children of any sub-directory)
 *
 * Entries are sorted: directories first (A→Z), then loose files (A→Z).
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

    /**
     * Level 2 — show the direct contents of a music library root.
     *
     * Attempt 1: getMusicDirectory(musicFolderId).
     *   Works on servers that map the music folder integer ID to a directory.
     *   If it succeeds with a non-empty child list, use that directly.
     *
     * Attempt 2: getIndexes(musicFolderId).child[].
     *   The child array contains files/dirs that live at the library root level.
     *   Used when getMusicDirectory fails or returns nothing.
     *
     * No artist sampling, no path parsing, no parent guessing.
     */
    private suspend fun loadLibraryRoot(repo: SubsonicRepository, musicFolderId: String) {
        // Attempt 1 — direct getMusicDirectory
        val direct = repo.getMusicDirectory(musicFolderId)
        if (direct is Result.Success && direct.data.child.isNotEmpty()) {
            val sorted = direct.data.child
                .sortedWith(compareByDescending<EntryDto> { it.isDir }
                    .thenBy { it.displayName.lowercase() })
            _state.update { BrowserState.Ready(sorted) }
            return
        }

        // Attempt 2 — getIndexes child entries
        val index = repo.getIndexes(musicFolderId)
        if (index is Result.Success) {
            val sorted = index.data.child
                .sortedWith(compareByDescending<EntryDto> { it.isDir }
                    .thenBy { it.displayName.lowercase() })
            _state.update { BrowserState.Ready(sorted) }
            return
        }

        _state.update { BrowserState.Error("Could not load library root") }
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
