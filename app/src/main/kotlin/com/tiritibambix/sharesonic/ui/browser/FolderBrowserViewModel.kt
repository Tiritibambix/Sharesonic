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
 *   "mf_{id}" → parent-discovery via getIndexes sample (level-1 genre folders)
 *   "{id}"    → getMusicDirectory(id)                (direct children of any sub-dir)
 *
 * See loadLibraryRootViaTraversal() for a full explanation of why simple
 * parent-chain traversal fails (Navidrome exposes dirs above the music root)
 * and how path-based parent discovery solves it.
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
                folderId.startsWith("mf_") -> loadLibraryRootViaTraversal(repo, folderId.removePrefix("mf_"))
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
     * Level 2 — display the library's direct first-level folders
     * (e.g. Multitrack, Reggae, Rock, Royalty Free, World).
     *
     * WHY NOT parent-chain traversal:
     * Navidrome exposes directories above the music library root (/mnt, /data…),
     * so walking up until parent==null overshoots and lands on a near-empty dir.
     *
     * APPROACH — parent discovery via path:
     * getIndexes returns artist-level entries (depth 2 in genre/artist/album trees).
     * For one artist per alphabetical group we call getMusicDirectory to get:
     *   • parent ID   → the level-1 folder's directory ID
     *   • path of first child (e.g. "Reggae/Bob Marley/Legend/…") → split on "/"
     *     → first component = level-1 folder name
     * Deduplicating by parent ID gives exactly the genre/top-level folders.
     *
     * Cost: 1 getIndexes + at most 1 getMusicDirectory per alphabet group (≤26).
     * Fallback: show raw getIndexes artist list if path data is unavailable.
     */
    private suspend fun loadLibraryRootViaTraversal(repo: SubsonicRepository, musicFolderId: String) {
        val indexResult = repo.getIndexes(musicFolderId)
        if (indexResult !is Result.Success) {
            _state.update { BrowserState.Error((indexResult as Result.Error).message) }
            return
        }

        val looseFiles = indexResult.data.child
        val groups = indexResult.data.index

        if (groups.isEmpty()) {
            _state.update { BrowserState.Ready(looseFiles) }
            return
        }

        // Sample one artist per alphabetical group; collect unique parent folders.
        val parentFolders = mutableMapOf<String, EntryDto>() // parentId → EntryDto

        for (group in groups) {
            val sampleArtist = group.artist.firstOrNull() ?: continue
            val dirResult = repo.getMusicDirectory(sampleArtist.id) as? Result.Success ?: continue
            val parentId = dirResult.data.parent.takeIf { !it.isNullOrEmpty() } ?: continue

            if (parentId in parentFolders) continue

            // Extract parent name from the path of the first child entry.
            // e.g. child.path = "Reggae/Bob Marley/Legend" → first component = "Reggae"
            val parentName = dirResult.data.child
                .firstOrNull { !it.path.isNullOrEmpty() }
                ?.path
                ?.split("/")
                ?.firstOrNull { it.isNotEmpty() }

            if (parentName != null) {
                parentFolders[parentId] = EntryDto(id = parentId, title = parentName, isDir = true)
            } else {
                // Path not available — fetch the parent directory directly for its name
                val parentDir = repo.getMusicDirectory(parentId) as? Result.Success
                if (parentDir != null) {
                    parentFolders[parentId] = EntryDto(
                        id = parentId, title = parentDir.data.name, isDir = true
                    )
                }
            }
        }

        if (parentFolders.isNotEmpty()) {
            val sorted = parentFolders.values.sortedBy { it.displayName.lowercase() }
            _state.update { BrowserState.Ready(sorted + looseFiles) }
        } else {
            // Fallback: show raw artist list from getIndexes
            val fallback = groups.flatMap { it.artist }
                .map { EntryDto(id = it.id, title = it.name, isDir = true) }
                .sortedBy { it.displayName.lowercase() }
            _state.update { BrowserState.Ready(fallback + looseFiles) }
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
