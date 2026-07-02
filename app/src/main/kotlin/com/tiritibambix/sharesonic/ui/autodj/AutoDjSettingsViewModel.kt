package com.tiritibambix.sharesonic.ui.autodj

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.settings.AutoDjSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AutoDjSettingsViewModel(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AutoDjSettings())
    val settings: StateFlow<AutoDjSettings> = _settings

    /**
     * Available library virtual paths (vpaths) from the last successful connection test.
     * Populated immediately from DataStore; falls back to a live file-explorer query
     * if not yet stored.
     */
    private val _availableVpaths = MutableStateFlow<List<String>>(emptyList())
    val availableVpaths: StateFlow<List<String>> = _availableVpaths

    init {
        viewModelScope.launch {
            settingsRepo.autoDjSettings.collect { s -> _settings.update { s } }
        }
        viewModelScope.launch {
            settingsRepo.vpaths.collect { stored ->
                if (stored.isNotEmpty()) {
                    _availableVpaths.update { stored }
                } else {
                    // Vpaths not yet stored — fetch from the server and persist them
                    val s = settingsRepo.settings.first()
                    if (s.isConfigured && s.jwtToken.isNotEmpty()) {
                        val velvet = VelvetRepository(VelvetClient.build(s.serverUrl))
                        val resp = velvet.fileExplorer(s.jwtToken, "")
                        if (resp is Result.Success && resp.data.directories.isNotEmpty()) {
                            val vpaths = resp.data.directories.map { it.name }
                            _availableVpaths.update { vpaths }
                            settingsRepo.saveVpaths(vpaths)
                        }
                    }
                }
            }
        }
    }

    // ── Individual setters — each one persists immediately ────────────────────

    fun setUseBpm(value: Boolean) = update { it.copy(useBpm = value) }
    fun setBpmTightRange(value: Int) = update { it.copy(bpmTightRange = value) }
    fun setBpmWideRange(value: Int) = update { it.copy(bpmWideRange = value) }
    fun setRequireBpm(value: Boolean) = update { it.copy(requireBpm = value) }
    fun setUseHarmonicMixing(value: Boolean) = update { it.copy(useHarmonicMixing = value) }
    fun setRequireKey(value: Boolean) = update { it.copy(requireKey = value) }
    fun setUseSimilarArtists(value: Boolean) = update { it.copy(useSimilarArtists = value) }
    fun setArtistCooldown(value: Int) = update { it.copy(artistCooldown = value.coerceIn(1, 10)) }
    fun setGenreMode(value: String) = update { it.copy(genreMode = value) }
    fun setGenres(value: List<String>) = update { it.copy(genres = value) }
    fun setMinRating(value: Int) = update { it.copy(minRating = value.coerceIn(0, 5)) }
    fun setCrossfadeDuration(value: Int) = update { it.copy(crossfadeDurationSec = value.coerceIn(0, 12)) }
    fun setSourceFolders(value: List<String>) = update { it.copy(sourceFolders = value) }

    private fun update(transform: (AutoDjSettings) -> AutoDjSettings) {
        _settings.update(transform)
        viewModelScope.launch {
            settingsRepo.saveAutoDjSettings(_settings.first())
        }
    }
}

class AutoDjSettingsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    private val settingsRepo = SettingsRepository(context)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AutoDjSettingsViewModel(settingsRepo) as T
    }
}
