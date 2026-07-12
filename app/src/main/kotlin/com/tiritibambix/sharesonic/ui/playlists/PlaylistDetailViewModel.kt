package com.tiritibambix.sharesonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.NativePlaylistEntry
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A playlist song entry bundling the display [dto] with the database [entryId]
 * needed by the remove-song endpoint.
 */
data class PlaylistEntry(
    val entryId: Int,
    val dto: EntryDto
)

sealed interface PlaylistDetailState {
    data object Loading : PlaylistDetailState
    data class Ready(
        val name: String,
        val entries: List<PlaylistEntry>
    ) : PlaylistDetailState
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
    /** The playlist NAME — used as the identifier for all Velvet playlist endpoints. */
    val playlistName: String
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailState>(PlaylistDetailState.Loading)
    val state: StateFlow<PlaylistDetailState> = _state

    private val _addSongsState = MutableStateFlow<AddSongsState>(AddSongsState.Idle)
    val addSongsState: StateFlow<AddSongsState> = _addSongsState

    private var searchJob: Job? = null
    // Debounces the server-side save while the user is still dragging. Cancelled
    // and rescheduled on every reorderMove(); commits after the pause.
    private var reorderCommitJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { PlaylistDetailState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { PlaylistDetailState.Error("Server not configured") }
                return@launch
            }
            val token = settings.jwtToken.ifEmpty {
                _state.update { PlaylistDetailState.Error("Not authenticated — open Settings") }
                return@launch
            }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            when (val r = repo.loadPlaylist(token, playlistName)) {
                is Result.Success -> _state.update {
                    PlaylistDetailState.Ready(
                        name    = playlistName,
                        entries = r.data.map { it.toPlaylistEntry() }
                    )
                }
                is Result.Error -> _state.update { PlaylistDetailState.Error(r.message) }
            }
        }
    }

    fun removeSong(entryId: Int) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            repo.removeSongFromPlaylist(token, entryId, playlistName)
            load()
        }
    }

    fun addSong(filepath: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            repo.addSongToPlaylist(token, filepath, playlistName)
            load()
        }
    }

    /**
     * Move an entry from index [from] to index [to] in the current Ready state.
     * Updates local state immediately (optimistic — the user sees the row jump
     * under their finger) and schedules a debounced save. Call [commitReorder]
     * on drag-end to flush immediately when the user releases; otherwise the
     * debounce fires by itself if they let go without triggering that path.
     */
    fun moveEntry(from: Int, to: Int) {
        val current = _state.value as? PlaylistDetailState.Ready ?: return
        if (from == to || from !in current.entries.indices || to !in current.entries.indices) return
        val reordered = current.entries.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { current.copy(entries = reordered) }
    }

    /**
     * Persist the current local ordering to the server. Called on drag-end
     * (immediate) — the intermediate [moveEntry] calls only mutate local state.
     * A debounce fallback commits 800 ms after the last [moveEntry] in case
     * drag-end never fires (e.g. the user swipes off-screen).
     */
    fun commitReorder(immediate: Boolean = true) {
        reorderCommitJob?.cancel()
        reorderCommitJob = viewModelScope.launch {
            if (!immediate) delay(800)
            val ordered = (_state.value as? PlaylistDetailState.Ready)?.entries?.map { it.dto.id }
                ?: return@launch
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            repo.reorderPlaylist(token, playlistName, ordered)
            // Refresh so entryIds line up with the server's post-save row IDs
            // (savePlaylist re-inserts rows → their DB entry IDs change).
            load()
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = settings.jwtToken.ifEmpty { return@launch }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            repo.renamePlaylist(token, playlistName, newName)
            // After rename the playlist no longer exists under the old name —
            // pop back to the list so the user sees the updated name there.
            _state.update { PlaylistDetailState.Error("Renamed to \"$newName\" — go back to refresh") }
        }
    }

    // ── Search for songs to add (native Velvet search — returns filepaths) ───

    fun searchSongsToAdd(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { _addSongsState.update { AddSongsState.Idle }; return }
        searchJob = viewModelScope.launch {
            delay(350)
            _addSongsState.update { AddSongsState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _addSongsState.update { AddSongsState.Error("Server not configured") }
                return@launch
            }
            val token = settings.jwtToken.ifEmpty {
                _addSongsState.update { AddSongsState.Error("Not authenticated") }
                return@launch
            }
            val repo = VelvetRepository(VelvetClient.build(settings.serverUrl))
            when (val r = repo.search(token, query.trim())) {
                is Result.Success -> _addSongsState.update { AddSongsState.Results(r.data.song) }
                is Result.Error   -> _addSongsState.update { AddSongsState.Error(r.message) }
            }
        }
    }

    fun clearAddSongsSearch() {
        searchJob?.cancel()
        _addSongsState.update { AddSongsState.Idle }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun NativePlaylistEntry.toPlaylistEntry(): PlaylistEntry {
        val meta = metadata
        return PlaylistEntry(
            entryId = id,
            dto = EntryDto(
                id       = filepath,
                title    = meta?.title?.takeIf { it.isNotBlank() }
                    ?: filepath.substringAfterLast('/').substringBeforeLast('.'),
                artist   = meta?.artist,
                album    = meta?.album,
                coverArt = meta?.albumArt,
                duration = meta?.duration?.toInt(),
                isDir    = false,
                path     = filepath
            )
        )
    }
}

class PlaylistDetailViewModelFactory(
    private val settingsRepo: SettingsRepository,
    private val playlistName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlaylistDetailViewModel(settingsRepo, playlistName) as T
    }
}
