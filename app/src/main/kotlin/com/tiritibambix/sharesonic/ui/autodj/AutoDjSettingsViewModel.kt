package com.tiritibambix.sharesonic.ui.autodj

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    init {
        viewModelScope.launch {
            settingsRepo.autoDjSettings.collect { s -> _settings.update { s } }
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
    fun setMinRating(value: Int) = update { it.copy(minRating = value.coerceIn(0, 10)) }

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
