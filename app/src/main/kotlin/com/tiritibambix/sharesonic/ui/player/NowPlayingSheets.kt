package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.api.models.EntryDto

/**
 * Enriched track-info dialog: a key/value list of every metadata field the track
 * carries (nulls hidden), with the full file path — selectable for copying — last.
 * Replaces the old path-only dialog with a full metadata view.
 */
@Composable
fun TrackInfoDialog(
    song: EntryDto,
    bitrateKbps: Int?,
    sampleRateHz: Int?,
    channels: Int?,
    onDismiss: () -> Unit
) {
    val rows: List<Pair<String, String>> = buildList {
        (song.title ?: song.name)?.takeIf { it.isNotBlank() }?.let { add("Title" to it) }
        song.artist?.takeIf { it.isNotBlank() }?.let { add("Artist" to it) }
        song.album?.takeIf { it.isNotBlank() }?.let { add("Album" to it) }
        song.year?.let { add("Year" to it.toString()) }
        song.track?.let { add("Track" to it.toString()) }
        song.genres?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?.let { add("Genre" to it.joinToString(", ")) }
        song.bpm?.takeIf { it > 0f }?.let { add("BPM" to formatBpm(it)) }
        song.musicalKey?.takeIf { it.isNotBlank() }?.let { add("Key" to it) }
        song.duration?.takeIf { it > 0 }?.let { add("Duration" to formatSeconds(it)) }
        song.suffix?.takeIf { it.isNotBlank() }?.let { add("Format" to it.uppercase()) }
        bitrateKbps?.let { add("Bitrate" to "$it kbps") }
        sampleRateHz?.let { add("Sample rate" to formatSampleRate(it)) }
        channels?.let { add("Channels" to formatChannels(it)) }
        song.rating?.takeIf { it > 0 }?.let { add("Rating" to "${it / 2} / 5") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp)
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                song.path?.takeIf { it.isNotBlank() }?.let { path ->
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Path",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(path, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
    onOpenInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text("Sleep timer") },
                supportingContent = {
                    Text(
                        if (sleepRemainingMs != null) "Stops in ${formatCountdown(sleepRemainingMs)}"
                        else "Off"
                    )
                },
                leadingContent = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                modifier = Modifier.clickable { onOpenSleepTimer() }
            )
            ListItem(
                headlineContent = { Text("Track info") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable { onOpenInfo() }
            )
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
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    AssistChip(
                        onClick = { onPick(minutes) },
                        label = { Text("$minutes min") }
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
                    label = { Text("Custom (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { custom.toIntOrNull()?.takeIf { it > 0 }?.let { onPick(it) } },
                    enabled = custom.toIntOrNull()?.let { it > 0 } == true
                ) { Text("Set") }
            }
            if (active) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel timer") }
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
