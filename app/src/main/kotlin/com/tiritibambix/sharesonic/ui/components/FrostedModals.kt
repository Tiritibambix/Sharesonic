package com.tiritibambix.sharesonic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Full-screen frosted-glass overlay: a dark scrim over the (parent-blurred)
 * content, with a centred card. Tapping the scrim dismisses; taps on the card
 * are consumed so they don't bubble up.
 *
 * The *blur* itself is applied by the caller on the content behind (via a
 * `Modifier.blur` driven by the same visibility state) — this matches the
 * track-info dialog pattern, where what looks frosted is the background, not
 * the card. This component only draws the scrim + card.
 */
@Composable
fun FrostedOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
        ) {
            content()
        }
    }
}

/** The solid, theme-coloured card that sits over the frosted backdrop. */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * Frosted-glass playlist picker with an inline "New playlist" row (name it and
 * add in one step) plus the list of existing playlists. Shared by the folder
 * browser (swipe-to-add) and Now Playing so the two behave identically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostedPlaylistPicker(
    title: String,
    subtitle: String?,
    playlists: List<NativePlaylist>,
    onPick: (playlistName: String) -> Unit,
    onCreate: (playlistName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    FrostedOverlay(onDismiss = onDismiss) {
        FrostedCard(modifier = Modifier.heightIn(max = 480.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // Create new playlist inline
            if (showCreateInput) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(stringResource(R.string.playlists_name_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = {
                            if (newName.isNotBlank()) onCreate(newName.trim())
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.playlists_create),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showCreateInput = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.playlists_new_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Existing playlists
            if (playlists.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(playlist.name) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.textSecondary, modifier = Modifier.size(22.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("${playlist.songCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.textSecondary)
                        }
                    }
                }
            } else if (!showCreateInput) {
                Text(
                    stringResource(R.string.playlists_empty),
                    color = MaterialTheme.colorScheme.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Frosted-glass version of the "how long should the share link live?" prompt.
 * Same behaviour as the old AlertDialog: empty / 0 → permanent link.
 */
@Composable
fun FrostedShareExpiryDialog(
    onConfirm: (expiryDays: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var daysText by remember { mutableStateOf("") }
    val parsedDays: Int? = daysText.trim().toIntOrNull()?.takeIf { it > 0 }
    val isValid = daysText.isBlank() || parsedDays != null

    FrostedOverlay(onDismiss = onDismiss) {
        FrostedCard {
            Text(
                stringResource(R.string.share_expiry_confirm),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.share_expiry_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.share_expiry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.textSecondary,
            )
            OutlinedTextField(
                value = daysText,
                onValueChange = { input -> daysText = input.filter { it.isDigit() } },
                placeholder = { Text(stringResource(R.string.share_expiry_permanent)) },
                singleLine = true,
                isError = !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = { onConfirm(parsedDays) },
                    enabled = isValid,
                ) { Text(stringResource(R.string.share_expiry_confirm)) }
            }
        }
    }
}
