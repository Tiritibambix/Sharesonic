package com.tiritibambix.sharesonic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.api.models.TopLevelDir
import com.tiritibambix.sharesonic.data.settings.ServerSettings
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

    fun onQueryChange(q: String) {
        _query.update { q }
        debounceJob?.cancel()
        if (q.isBlank()) {
            _searchState.update { SearchState.Idle }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(350) // debounce
            _searchState.update { SearchState.Loading }
            val settings = settingsRepo.settings.first()
            if (settings.subsonicPassword.isBlank()) {
                _searchState.update {
                    SearchState.Error("Subsonic password not configured — set it in Settings")
                }
                return@launch
            }
            val repo = buildRepo(settings)
            when (val r = repo.search(q.trim())) {
                is Result.Success -> _searchState.update { SearchState.Results(r.data) }
                is Result.Error   -> _searchState.update { SearchState.Error(r.message) }
            }
        }
    }

    fun coverArtUrl(coverArtId: String): String? {
        // Built synchronously from cached settings — fine since settings are loaded at app start
        return null // Resolved lazily in screen via remember
    }

    private fun buildRepo(settings: ServerSettings): SubsonicRepository {
        val api = SubsonicClient.build(settings.serverUrl, settings.username, settings.subsonicPassword)
        return SubsonicRepository(api)
    }
}

class SearchViewModelFactory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SearchViewModel(repo) as T
    }
}
