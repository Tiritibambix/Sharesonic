package com.tiritibambix.sharesonic.ui.publiclinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.MStreamShareListItem
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PublicLinksState {
    data object Loading : PublicLinksState
    data class Ready(val links: List<MStreamShareListItem>) : PublicLinksState
    data class Error(val message: String) : PublicLinksState
}

/**
 * Manages mStream Velvet's "Public Links" — the list of shares created via
 * POST /api/v1/share, mirrored by GET /api/v1/share/list and DELETE /api/v1/share/:id.
 */
class PublicLinksViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow<PublicLinksState>(PublicLinksState.Loading)
    val state: StateFlow<PublicLinksState> = _state

    /** Cached so [shareUrl] can build full links without re-reading settings. */
    private var serverUrl: String = ""

    init {
        load()
    }

    fun load() {
        _state.update { PublicLinksState.Loading }
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            serverUrl = settings.serverUrl
            val token = ensureToken(settings) ?: run {
                _state.update { PublicLinksState.Error("Authentication failed") }
                return@launch
            }
            val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
            when (val r = mStream.getShareList(token)) {
                is Result.Success -> _state.update {
                    // Soonest-expiring (and permanent-last) links surface first.
                    PublicLinksState.Ready(r.data.sortedBy { it.expires ?: Long.MAX_VALUE })
                }
                is Result.Error -> _state.update { PublicLinksState.Error(r.message) }
            }
        }
    }

    /** Full public URL for a share — <serverUrl>/shared/<playlistId>. */
    fun shareUrl(playlistId: String): String =
        serverUrl.trimEnd('/') + "/shared/$playlistId"

    /** Revoke a share link and optimistically drop it from the displayed list. */
    fun delete(playlistId: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val token = ensureToken(settings) ?: return@launch
            val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
            if (mStream.deleteShare(token, playlistId) is Result.Success) {
                val current = _state.value
                if (current is PublicLinksState.Ready) {
                    _state.update {
                        PublicLinksState.Ready(current.links.filterNot { it.playlistId == playlistId })
                    }
                }
            }
        }
    }

    /**
     * Return the stored JWT if valid, or attempt a fresh login.
     * Persists the new token to DataStore on success.
     */
    private suspend fun ensureToken(settings: ServerSettings): String? {
        if (settings.jwtToken.isNotEmpty()) return settings.jwtToken
        val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
        val result = mStream.login(settings.username, settings.password)
        if (result is Result.Success) {
            settingsRepo.saveToken(result.data)
            return result.data
        }
        return null
    }
}

class PublicLinksViewModelFactory(
    private val repo: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PublicLinksViewModel(repo) as T
    }
}
