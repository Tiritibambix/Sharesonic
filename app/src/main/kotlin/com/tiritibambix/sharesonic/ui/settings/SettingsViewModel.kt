package com.tiritibambix.sharesonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.MStreamClient
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

    init {
        // On boot, refresh the stored JWT so it stays signed by the current server secret.
        // Silently ignored on failure — ensureToken() will re-login if the token is truly expired.
        viewModelScope.launch {
            val s = repo.settings.first()
            if (s.isConfigured && s.jwtToken.isNotEmpty()) {
                val mstr = MStreamRepository(MStreamClient.build(s.serverUrl))
                val result = mstr.refreshToken(s.jwtToken)
                if (result is Result.Success) repo.saveToken(result.data)
            }
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** Last JWT obtained from a successful test — included when saving. */
    private var _pendingToken: String = ""

    fun save(serverUrl: String, username: String, password: String) {
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
        }
    }

    /** POST /api/v1/login — stores the token and vpaths on success. */
    fun testConnection(serverUrl: String, username: String, password: String) {
        _connectionState.update { ConnectionState.Testing }
        viewModelScope.launch {
            val api  = MStreamClient.build(serverUrl.trim())
            val mstr = MStreamRepository(api)
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
