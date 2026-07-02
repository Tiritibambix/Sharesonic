package com.tiritibambix.sharesonic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
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

    // Songs fetched from the server (see fetchArtistSongsRaw()) for the artist
    // the user tapped in search results. Stashed here so the dedicated
    // ArtistResultsScreen — which shares this ViewModel instance via the
    // Search back stack entry — can display them as a standalone track list.
    private val _artistResults = MutableStateFlow<List<EntryDto>>(emptyList())
    val artistResults: StateFlow<List<EntryDto>> = _artistResults

    fun setArtistResults(songs: List<EntryDto>) {
        _artistResults.update { songs }
    }

    private var debounceJob: Job? = null

    /**
     * Raw song list for an artist via `/api/v1/db/artist-folder-songs` — the server
     * matches every song whose artist/album_artist tag exactly equals [artistName]
     * or one of [variants] (raw tag values, see
     * [com.tiritibambix.sharesonic.data.api.models.NativeSearchArtist]). Feeds
     * `ArtistResultsScreen`, which lists these tracks directly (mirroring the Velvet
     * webapp's viewArtistProfile) so the exact featuring/variant track is one tap
     * away and plays by its own server-verified filepath — no folder guessing.
     */
    suspend fun fetchArtistSongsRaw(artistName: String, variants: List<String>): List<EntryDto>? {
        val settings = settingsRepo.settings.first()
        if (!settings.isConfigured) return null
        val token = settings.jwtToken.takeIf { it.isNotEmpty() } ?: return null
        val repo = MStreamRepository(MStreamClient.build(settings.serverUrl))
        val names = (variants + artistName).distinct()
        return when (val r = repo.artistFolderSongs(token, names)) {
            is Result.Success -> r.data
            is Result.Error -> null
        }
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
