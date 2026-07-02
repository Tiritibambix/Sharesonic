package com.tiritibambix.sharesonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.settings.AppTheme
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data object Success : ConnectionState
    data class Failure(val message: String) : ConnectionState
}

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<ServerSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ServerSettings())

    /** Selected visual theme — Velvet (default), Dark or Light. */
    val appTheme: StateFlow<AppTheme> = repo.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.VELVET)

    init {
        // On boot, refresh the stored JWT so it stays signed by the current server secret.
        // Silently ignored on failure — ensureToken() will re-login if the token is truly expired.
        viewModelScope.launch {
            val s = repo.settings.first()
            if (s.isConfigured && s.jwtToken.isNotEmpty()) {
                val mstr = VelvetRepository(VelvetClient.build(s.serverUrl))
                val result = mstr.refreshToken(s.jwtToken)
                if (result is Result.Success) repo.saveToken(result.data)
            }
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** Last JWT obtained from a successful test — included when saving. */
    private var _pendingToken: String = ""

    /**
     * @param onSaved Invoked once [repo.save] has actually finished writing to
     * DataStore — NOT just once this function returns (it's fire-and-forget by
     * itself, since it launches a coroutine). Callers that need to navigate
     * straight to a screen depending on the freshly-saved settings (e.g. the
     * folder browser, which reads `settingsRepo.settings.first()` on init) MUST
     * use this hook rather than navigating immediately after calling [save]:
     * doing the latter raced the DataStore write and surfaced as a
     * "Server not configured" error on the very first connection (the browser's
     * ViewModel was created and read the *old*, still-unconfigured snapshot).
     */
    fun save(serverUrl: String, username: String, password: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repo.save(
                ServerSettings(
                    serverUrl = serverUrl.trim(),
                    username  = username.trim(),
                    password  = password,
                    jwtToken  = _pendingToken.ifEmpty { settings.value.jwtToken }
                )
            )
            _connectionState.update { ConnectionState.Idle }
            onSaved()
        }
    }

    /** Persists the chosen visual theme — applied immediately app-wide via [appTheme]. */
    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            repo.saveAppTheme(theme)
        }
    }

    /** POST /api/v1/login — stores the token and vpaths on success. */
    fun testConnection(serverUrl: String, username: String, password: String) {
        _connectionState.update { ConnectionState.Testing }
        viewModelScope.launch {
            val api  = VelvetClient.build(serverUrl.trim())
            val mstr = VelvetRepository(api)
            when (val result = mstr.loginFull(username.trim(), password)) {
                is Result.Success -> {
                    val (token, vpaths) = result.data
                    _pendingToken = token
                    repo.saveToken(token)
                    if (vpaths.isNotEmpty()) repo.saveVpaths(vpaths)
                    _connectionState.update { ConnectionState.Success }
                }
                is Result.Error -> {
                    _connectionState.update { ConnectionState.Failure(result.message) }
                }
            }
        }
    }
}

class SettingsViewModelFactory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repo) as T
    }
}
