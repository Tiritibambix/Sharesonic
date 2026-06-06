package com.tiritibambix.sharesonic.ui.autodj

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDjSettingsScreen(
    viewModel: AutoDjSettingsViewModel,
    onBack: () -> Unit
) {
    val s by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-DJ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── BPM Continuity ────────────────────────────────────────────────
            item {
                SectionHeader("BPM Continuity")
            }
            item {
                SettingRow(
                    label = "Use BPM continuity",
                    description = "Prefer tracks with a similar tempo to the current song"
                ) {
                    Switch(
                        checked = s.useBpm,
                        onCheckedChange = viewModel::setUseBpm
                    )
                }
            }
            item {
                AnimatedVisibility(visible = s.useBpm) {
                    Column {
                        SliderSetting(
                            label = "Tight range  ±${s.bpmTightRange} BPM",
                            value = s.bpmTightRange.toFloat(),
                            onValueChange = { viewModel.setBpmTightRange(it.roundToInt()) },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                        SliderSetting(
                            label = "Wide range  ±${s.bpmWideRange} BPM  (fallback)",
                            value = s.bpmWideRange.toFloat(),
                            onValueChange = { viewModel.setBpmWideRange(it.roundToInt()) },
                            valueRange = 10f..50f,
                            steps = 7
                        )
                        SettingRow(
                            label = "Require BPM tag",
                            description = "Skip tracks that have no BPM metadata"
                        ) {
                            Switch(
                                checked = s.requireBpm,
                                onCheckedChange = viewModel::setRequireBpm
                            )
                        }
                    }
                }
            }

            // ── Harmonic Mixing ───────────────────────────────────────────────
            item { SectionDivider() }
            item { SectionHeader("Harmonic Mixing (Camelot)") }
            item {
                SettingRow(
                    label = "Harmonic mixing",
                    description = "Prefer tracks whose musical key is compatible on the Camelot wheel"
                ) {
                    Switch(
                        checked = s.useHarmonicMixing,
                        onCheckedChange = viewModel::setUseHarmonicMixing
                    )
                }
            }
            item {
                AnimatedVisibility(visible = s.useHarmonicMixing) {
                    SettingRow(
                        label = "Require key tag",
                        description = "Skip tracks that have no musical key metadata"
                    ) {
                        Switch(
                            checked = s.requireKey,
                            onCheckedChange = viewModel::setRequireKey
                        )
                    }
                }
            }

            // ── Similar Artists ───────────────────────────────────────────────
            item { SectionDivider() }
            item { SectionHeader("Similar Artists (Last.fm)") }
            item {
                SettingRow(
                    label = "Similar artists",
                    description = "Fetch similar artists via mStream's Last.fm integration and prefer their tracks"
                ) {
                    Switch(
                        checked = s.useSimilarArtists,
                        onCheckedChange = viewModel::setUseSimilarArtists
                    )
                }
            }
            item {
                StepperSetting(
                    label = "Artist cooldown",
                    description = "Number of recently played artists to exclude",
                    value = s.artistCooldown,
                    onDecrement = { viewModel.setArtistCooldown(s.artistCooldown - 1) },
                    onIncrement = { viewModel.setArtistCooldown(s.artistCooldown + 1) },
                    min = 1,
                    max = 10
                )
            }

            // ── Genre Filter ──────────────────────────────────────────────────
            item { SectionDivider() }
            item { SectionHeader("Genre Filter") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Filter mode",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("off" to "Off", "whitelist" to "Whitelist", "blacklist" to "Blacklist")
                            .forEach { (mode, label) ->
                                FilterChip(
                                    selected = s.genreMode == mode,
                                    onClick = { viewModel.setGenreMode(mode) },
                                    label = { Text(label) }
                                )
                            }
                    }
                }
            }
            item {
                AnimatedVisibility(visible = s.genreMode != "off") {
                    GenresEditor(
                        genres = s.genres,
                        onUpdate = viewModel::setGenres
                    )
                }
            }

            // ── Rating Filter ─────────────────────────────────────────────────
            item { SectionDivider() }
            item { SectionHeader("Minimum Rating") }
            item {
                SliderSetting(
                    label = if (s.minRating == 0) "No minimum rating"
                            else "Minimum rating: ${s.minRating} / 10",
                    value = s.minRating.toFloat(),
                    onValueChange = { viewModel.setMinRating(it.roundToInt()) },
                    valueRange = 0f..10f,
                    steps = 9
                )
            }
        }
    }
}

// ── Genre text editor ─────────────────────────────────────────────────────────

@Composable
private fun GenresEditor(
    genres: List<String>,
    onUpdate: (List<String>) -> Unit
) {
    // Edit as a single comma/newline separated string; commit on any change
    var text by remember(genres) {
        mutableStateOf(genres.joinToString(", "))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Genres (comma-separated)",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v
                onUpdate(v.split(",").map { it.trim() }.filter { it.isNotBlank() })
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rock, Jazz, Classical…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = false,
            minLines = 2
        )
    }
}

// ── Reusable layout helpers ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingRow(
    label: String,
    description: String? = null,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        control()
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepperSetting(
    label: String,
    description: String? = null,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    min: Int,
    max: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                enabled = value > min,
                modifier = Modifier.size(36.dp)
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            FilledTonalIconButton(
                onClick = onIncrement,
                enabled = value < max,
                modifier = Modifier.size(36.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
