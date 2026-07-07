package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Track-info panel — a "frosted" card floating over the darkened Now Playing
 * background, inspired by Velvet's `.np-info` / `.np-meta` layout: title +
 * artist header on top, a two-column key/value metadata grid in the middle,
 * and the full filepath in a monospace card at the bottom.
 *
 * Rendered inside a Dialog with `usePlatformDefaultWidth = false` so the panel
 * can be a wide rounded surface (not the narrow AlertDialog rail), on a
 * translucent scrim. A tint of the current theme's `surface` at reduced alpha
 * over the scrim reads as frosted glass without needing the RenderScript /
 * `haze` library — the darkened content behind still bleeds through visibly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoDialog(
    song: EntryDto,
    bitrateKbps: Int?,
    sampleRateHz: Int?,
    channels: Int?,
    onDismiss: () -> Unit
) {
    val rows: List<Pair<String, String>> = buildList {
        val yearLabel = stringResource(R.string.info_year)
        val trackLabel = stringResource(R.string.info_track)
        val genreLabel = stringResource(R.string.info_genre)
        val bpmLabel = stringResource(R.string.info_bpm)
        val keyLabel = stringResource(R.string.info_key)
        val durationLabel = stringResource(R.string.info_duration)
        val formatLabel = stringResource(R.string.info_format)
        val bitrateLabel = stringResource(R.string.info_bitrate)
        val sampleRateLabel = stringResource(R.string.info_sample_rate)
        val channelsLabel = stringResource(R.string.info_channels)
        val ratingLabel = stringResource(R.string.info_rating)
        song.year?.let { add(yearLabel to it.toString()) }
        song.track?.let { add(trackLabel to it.toString()) }
        song.genres?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?.let { add(genreLabel to it.joinToString(", ")) }
        song.bpm?.takeIf { it > 0f }?.let { add(bpmLabel to formatBpm(it)) }
        song.musicalKey?.takeIf { it.isNotBlank() }?.let { add(keyLabel to it) }
        song.duration?.takeIf { it > 0 }?.let { add(durationLabel to formatSeconds(it)) }
        song.suffix?.takeIf { it.isNotBlank() }?.let { add(formatLabel to it.uppercase()) }
        val kbpsFmt = bitrateKbps?.let { stringResource(R.string.player_kbps, it) }
        val ratingFmt = song.rating?.takeIf { it > 0 }?.let { stringResource(R.string.info_rating_value, it / 2) }
        kbpsFmt?.let { add(bitrateLabel to it) }
        sampleRateHz?.let { add(sampleRateLabel to formatSampleRate(it)) }
        channels?.let { add(channelsLabel to formatChannels(it)) }
        ratingFmt?.let { add(ratingLabel to it) }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(max = 620.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        // Solid theme-coloured surface — the frosted-glass effect is the blurred
        // Now Playing content BEHIND this modal (applied by the parent), not the
        // modal itself. A soft primary-tinted border lifts it off the scrim.
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Header: title + artist + album ──
                Text(
                    text = (song.title ?: song.name).orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                song.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                song.album?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (rows.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    // ── Two-column metadata grid: 10sp uppercase label / 13sp value.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { (label, value) ->
                            MetadataRow(label = label, value = value)
                        }
                    }
                }

                song.path?.takeIf { it.isNotBlank() }?.let { path ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    Text(
                        stringResource(R.string.info_path),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textSecondary,
                        letterSpacing = 0.7.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    // Monospace card, mirrors Velvet's `.np-fp-path`.
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        ),
                    ) {
                        SelectionContainer {
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 18.sp,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textSecondary,
            letterSpacing = 0.7.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.width(108.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

/**
 * "More actions" bottom sheet opened from the Now Playing top bar — the app's first
 * Material 3 [ModalBottomSheet] (drag handle + rounded corners by default). Houses
 * the sleep timer and track info; more entries (e.g. lyrics) can be added later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsSheet(
    sleepRemainingMs: Long?,
    onOpenSleepTimer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_sleep_timer)) },
                supportingContent = {
                    Text(
                        if (sleepRemainingMs != null)
                            stringResource(R.string.more_sleep_stops_in, formatCountdown(sleepRemainingMs))
                        else
                            stringResource(R.string.more_sleep_off)
                    )
                },
                leadingContent = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                modifier = Modifier.clickable { onOpenSleepTimer() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_lyrics)) },
                leadingContent = { Icon(Icons.Default.Lyrics, contentDescription = null) },
                modifier = Modifier.clickable { onOpenLyrics() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_track_info)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable { onOpenInfo() }
            )
        }
    }
}

/**
 * Full-width lyrics bottom sheet. Fetches on open via [fetch] (server-parsed lines,
 * synced or plain) and shows loading / lyrics / "none" / error states. Scrollable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    title: String,
    fetch: suspend () -> Result<List<String>>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val result by produceState<Result<List<String>>?>(initialValue = null) {
            value = fetch()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            when (val r = result) {
                null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is Result.Success -> {
                    if (r.data.isEmpty()) {
                        Text(
                            stringResource(R.string.lyrics_none),
                            color = MaterialTheme.colorScheme.textSecondary
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            r.data.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                is Result.Error -> Text(
                    r.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Sleep-timer picker bottom sheet: preset durations, a custom minutes field, and a
 * cancel action when a timer is already armed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    active: Boolean,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sleep_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    AssistChip(
                        onClick = { onPick(minutes) },
                        label = { Text(stringResource(R.string.sleep_preset_min, minutes)) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { s -> custom = s.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.sleep_custom_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { custom.toIntOrNull()?.takeIf { it > 0 }?.let { onPick(it) } },
                    enabled = custom.toIntOrNull()?.let { it > 0 } == true
                ) { Text(stringResource(R.string.common_set)) }
            }
            if (active) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.sleep_cancel)) }
            }
        }
    }
}

// ── formatting helpers ─────────────────────────────────────────────────────────

private fun formatBpm(bpm: Float): String =
    if (bpm % 1f == 0f) bpm.toInt().toString() else "%.1f".format(bpm)

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
}

private fun formatChannels(ch: Int): String = when (ch) {
    1 -> "Mono"
    2 -> "Stereo"
    else -> "$ch channels"
}

/** mm:ss (or h:mm:ss) countdown for the sleep-timer remaining time. */
private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
