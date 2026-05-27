package com.tiritibambix.sharesonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.tiritibambix.sharesonic.data.Result

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data object Success : ConnectionState
    data class Failure(val message: String) : ConnectionState
}

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<ServerSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ServerSettings())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun save(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            repo.save(ServerSettings(serverUrl.trim(), username.trim(), password))
            _connectionState.update { ConnectionState.Idle }
        }
    }

    fun testConnection(serverUrl: String, username: String, password: String) {
        _connectionState.update { ConnectionState.Testing }
        viewModelScope.launch {
            val api = SubsonicClient.build(serverUrl.trim(), username.trim(), password)
            val result = SubsonicRepository(api).ping()
            _connectionState.update {
                when (result) {
                    is Result.Success -> ConnectionState.Success
                    is Result.Error -> ConnectionState.Failure(result.message)
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
