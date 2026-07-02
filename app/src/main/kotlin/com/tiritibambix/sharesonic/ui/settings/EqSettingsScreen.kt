package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.playback.EqualizerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EqViewModel(private val repo: SettingsRepository) : ViewModel() {

    val available: Boolean = EqualizerController.available
    val bandCount: Int = EqualizerController.bandCount
    val levelRangeMb: Pair<Short, Short> =
        EqualizerController.levelRangeMb ?: ((-1500).toShort() to 1500.toShort())
    val centerFreqsHz: List<Int> = (0 until bandCount).map { EqualizerController.centerFreqHz(it) }

    private val _enabled = MutableStateFlow(EqualizerController.enabled)
    val enabled: StateFlow<Boolean> = _enabled

    private val _levelsMb = MutableStateFlow((0 until bandCount).map { EqualizerController.getBandLevelMb(it) })
    val levelsMb: StateFlow<List<Short>> = _levelsMb

    fun setEnabled(value: Boolean) {
        EqualizerController.enabled = value
        _enabled.value = value
        persist()
    }

    fun setBand(index: Int, levelMb: Short) {
        EqualizerController.setBandLevelMb(index, levelMb)
        _levelsMb.update { list ->
            list.toMutableList().also { if (index in it.indices) it[index] = levelMb }
        }
        persist()
    }

    fun reset() {
        for (i in 0 until bandCount) EqualizerController.setBandLevelMb(i, 0)
        _levelsMb.value = List(bandCount) { 0.toShort() }
        persist()
    }

    private fun persist() {
        viewModelScope.launch { repo.saveEqSettings(_enabled.value, _levelsMb.value) }
    }
}

class EqViewModelFactory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EqViewModel(repo) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqSettingsScreen(
    viewModel: EqViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!viewModel.available) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Equalizer not available on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val enabled by viewModel.enabled.collectAsState()
        val levels by viewModel.levelsMb.collectAsState()
        val (minMb, maxMb) = viewModel.levelRangeMb

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enabled", style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
            }
            TextButton(onClick = viewModel::reset) { Text("Reset to flat") }
            Spacer(Modifier.height(8.dp))

            viewModel.centerFreqsHz.forEachIndexed { index, freq ->
                val levelMb = levels.getOrElse(index) { 0.toShort() }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatFreq(freq), style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatGain(levelMb),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = levelMb.toFloat(),
                        onValueChange = { v -> viewModel.setBand(index, v.toInt().toShort()) },
                        valueRange = minMb.toFloat()..maxMb.toFloat(),
                        enabled = enabled
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000.0
        if (k % 1.0 == 0.0) "${k.toInt()} kHz" else "%.1f kHz".format(k)
    } else "$hz Hz"

/** Millibels → signed dB, e.g. "+6 dB", "-3 dB", "0 dB". */
private fun formatGain(levelMb: Short): String {
    val db = levelMb / 100
    return if (db > 0) "+$db dB" else "$db dB"
}
