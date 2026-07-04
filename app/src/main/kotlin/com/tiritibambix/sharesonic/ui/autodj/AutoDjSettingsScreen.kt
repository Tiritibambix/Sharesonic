package com.tiritibambix.sharesonic.ui.autodj

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import kotlin.math.roundToInt

/**
 * Embeddable Auto-DJ settings content (no Scaffold / TopAppBar).
 * Used both by [AutoDjSettingsScreen] (standalone) and by the
 * "Auto-DJ" tab inside SettingsScreen.
 */
@Composable
fun AutoDjSettingsContent(
    viewModel: AutoDjSettingsViewModel,
    modifier: Modifier = Modifier,
    miniPlayerVisible: Boolean = false
) {
    val s by viewModel.settings.collectAsState()
    val vpaths by viewModel.availableVpaths.collectAsState()

    // Leave room for the mini player bar so the last rows (genres, source folders…)
    // aren't hidden behind it.
    val bottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 108.dp else 32.dp,
        label = "autoDjBottomPadding"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
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
                description = "Fetch similar artists via Velvet's Last.fm integration and prefer their tracks"
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

        // ── Crossfade ─────────────────────────────────────────────────────
        item { SectionDivider() }
        item { SectionHeader("Crossfade") }
        item {
            SliderSetting(
                label = if (s.crossfadeDurationSec == 0) "Crossfade: Off"
                        else "Crossfade: ${s.crossfadeDurationSec}s",
                value = s.crossfadeDurationSec.toFloat(),
                onValueChange = { viewModel.setCrossfadeDuration(it.roundToInt()) },
                // 0–12 in 1-second steps: 11 intermediate positions → 13 total
                valueRange = 0f..12f,
                steps = 11
            )
        }

        // ── Minimum Rating ────────────────────────────────────────────────
        item { SectionDivider() }
        item { SectionHeader("Minimum Rating") }
        item {
            StarRatingPicker(
                rating = s.minRating,
                onRatingChange = viewModel::setMinRating
            )
        }

        // ── Source Library ────────────────────────────────────────────────
        item { SectionDivider() }
        item { SectionHeader("Source Library") }
        item {
            SourceFoldersSelector(
                vpaths = vpaths,
                selected = s.sourceFolders,
                onSelectionChange = viewModel::setSourceFolders
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDjSettingsScreen(
    viewModel: AutoDjSettingsViewModel,
    onBack: () -> Unit,
    miniPlayerVisible: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-DJ") },
                navigationIcon = {
                    // Keep the hamburger glyph here too — reached via the drawer,
                    // tapping it again exits this screen back to the browser.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        AutoDjSettingsContent(
            viewModel,
            modifier = Modifier.padding(padding),
            miniPlayerVisible = miniPlayerVisible
        )
    }
}

// ── Star rating picker ────────────────────────────────────────────────────────

/**
 * Horizontal row of 5 tappable stars plus an explicit reset control.
 * [rating] = 0 means "any rating" (no minimum); 1–5 = star minimum.
 *
 * Tapping a star sets the minimum to that star's value; tapping the
 * already-active star, OR the dedicated "clear" button, resets to 0
 * ("Any rating") — the clear button makes the reset path discoverable
 * instead of relying on users guessing they must re-tap the lit star.
 */
@Composable
private fun StarRatingPicker(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (rating == 0) "Any rating"
                   else "$rating ${if (rating == 1) "star" else "stars"} minimum",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = {
                        // Tap the active star again → reset to 0 (any)
                        onRatingChange(if (rating == star) 0 else star)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (star <= rating) Icons.Filled.Star
                                      else Icons.Filled.StarBorder,
                        contentDescription = "$star star",
                        tint = if (star <= rating) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // Explicit, always-visible way back to "Any rating" — avoids the
            // dead end where users couldn't figure out how to clear the minimum.
            IconButton(
                onClick = { onRatingChange(0) },
                enabled = rating != 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reset to any rating",
                    tint = if (rating != 0) MaterialTheme.colorScheme.textSecondary
                           else MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Source folder checkboxes ──────────────────────────────────────────────────

/**
 * Checkbox list of available library vpaths.
 * Empty [selected] means all folders are included (shown as all checked).
 * Checking all folders back → resets to empty (= all, no explicit list).
 */
@Composable
private fun SourceFoldersSelector(
    vpaths: List<String>,
    selected: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "Which library folders Auto-DJ draws songs from",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.textSecondary
        )
        Spacer(Modifier.height(4.dp))

        if (vpaths.isEmpty()) {
            Text(
                "Library folders not yet loaded — test your server connection in Settings to populate this list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            return@Column
        }

        // Treat empty `selected` as "all folders selected"
        val effectiveSelected = if (selected.isEmpty()) vpaths.toSet() else selected.toSet()

        vpaths.forEach { vpath ->
            val isChecked = vpath in effectiveSelected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newSelected = if (isChecked) effectiveSelected - vpath
                                          else effectiveSelected + vpath
                        // All selected or none selected → store empty (= "all library")
                        val toStore = if (newSelected.isEmpty() || newSelected.size == vpaths.size)
                            emptyList()
                        else
                            newSelected.toList().sorted()
                        onSelectionChange(toStore)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = null   // click handled by Row
                )
                Spacer(Modifier.width(8.dp))
                Text(vpath, style = MaterialTheme.typography.bodyMedium)
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
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
                    color = MaterialTheme.colorScheme.textSecondary
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
                    color = MaterialTheme.colorScheme.textSecondary
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
                textAlign = TextAlign.Center
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
