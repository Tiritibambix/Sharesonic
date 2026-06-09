package com.tiritibambix.sharesonic.ui.search

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
