package com.tiritibambix.sharesonic.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val result: SearchResult3) : SearchState
    data class Error(val message: String) : SearchState
}

class SearchViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    private var debounceJob: Job? = null

    /**
     * Best-effort resolution of a folder path for an artist returned by [search].
     *
     * The native search response gives artists only a bare name (no folder
     * path), and for an artist-only query the `title`/`albums` arrays are
     * often empty too (mStream's FTS matches song/album text, not artist
     * names), so [SearchScreen]'s same-response heuristic frequently finds
     * nothing. As a fallback, probe each known library vpath for a
     * "<vpath>/<artistName>" folder via file-explorer — this matches the
     * "<vpath>/<Artist>/<Album>/<track>" layout assumed elsewhere in the app.
     * Returns the first vpath/artist combination that file-explorer accepts
     * (HTTP 200), or null if none of them exist.
     */
    suspend fun resolveArtistFolder(artistName: String): String? {
        val settings = settingsRepo.settings.first()
        if (!settings.isConfigured) return null
        val token = settings.jwtToken.takeIf { it.isNotEmpty() } ?: return null

        val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))

        // Vpaths are normally cached from login/connection-test, but if that
        // never ran (or returned empty), fetch the root listing live — same
        // fallback AutoDjSettingsViewModel uses — and persist it for next time.
        var vpaths = settingsRepo.vpaths.first()
        if (vpaths.isEmpty()) {
            val root = repo.fileExplorer(token, "")
            if (root is Result.Success && root.data.directories.isNotEmpty()) {
                vpaths = root.data.directories.map { it.name }
                settingsRepo.saveVpaths(vpaths)
            }
        }

        Log.d(TAG, "resolveArtistFolder('$artistName'): vpaths=$vpaths")

        for (vpath in vpaths) {
            val candidate = "$vpath/$artistName"
            val result = repo.fileExplorer(token, candidate)
            Log.d(TAG, "  probe '$candidate' -> $result")
            if (result is Result.Success) return candidate
        }
        return null
    }

    private companion object {
        const val TAG = "SharesonicSearch"
    }

    fun onQueryChange(q: String) {
        _query.update { q }
        debounceJob?.cancel()
        if (q.isBlank()) {
            _searchState.update { SearchState.Idle }
            return
        }
        debounceJob = viewModelScope.launch {
            try {
                delay(350) // debounce
                _searchState.update { SearchState.Loading }
                val settings = settingsRepo.settings.first()
                if (!settings.isConfigured) {
                    _searchState.update { SearchState.Error("Server not configured") }
                    return@launch
                }
                val token = settings.jwtToken.ifEmpty {
                    _searchState.update { SearchState.Error("Not authenticated — open Settings") }
                    return@launch
                }
                val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
                when (val r = repo.search(token, q.trim())) {
                    is Result.Success -> _searchState.update { SearchState.Results(r.data) }
                    is Result.Error   -> _searchState.update { SearchState.Error(r.message) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // structured concurrency — must rethrow
            } catch (e: Exception) {
                _searchState.update { SearchState.Error(e.message ?: "Unexpected error") }
            }
        }
    }
}

class SearchViewModelFactory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SearchViewModel(repo) as T
    }
}
