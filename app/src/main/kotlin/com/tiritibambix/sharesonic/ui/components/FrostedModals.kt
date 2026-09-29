package com.tiritibambix.sharesonic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvInitialFocus
import com.tiritibambix.sharesonic.utils.TvPillShape
import com.tiritibambix.sharesonic.utils.tvFocusRing
import com.tiritibambix.sharesonic.utils.tvFocusTrap
import com.tiritibambix.sharesonic.utils.tvKeyboardOptions
import com.tiritibambix.sharesonic.utils.tvTextFieldKeys

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
    val isTV = LocalIsTV.current
    val tvTrap = remember { FocusRequester() }
    // Back closes THIS modal first — without this, on TV the Back key would
    // bubble to a parent (e.g. PlayerPanel) and collapse the whole surface
    // behind an open picker instead of dismissing the picker itself.
    BackHandler(onBack = onDismiss)
    // TV: the scrim and card must NOT be clickable — a clickable is focusable, and
    // D-pad focus search never enters a focused node's children, so everything in
    // the card would be unreachable. Instead the card is a focus trap (arrows stay
    // inside, Back dismisses) that takes the initial focus.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .then(
                if (isTV) Modifier
                else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = if (isTV) Modifier.tvFocusTrap(true, tvTrap)
            else Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
        ) {
            content()
        }
    }
    TvInitialFocus(isTV, tvTrap)
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
    val isTV = LocalIsTV.current
    // TV: the "New playlist" row disappears when activated, which would drop
    // focus — send it to the name field that replaces it.
    val tvNameField = remember { FocusRequester() }
    if (showCreateInput) TvInitialFocus(isTV, tvNameField)

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
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isTV) Modifier.focusRequester(tvNameField) else Modifier)
                            .tvTextFieldKeys(isTV),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = if (isTV) KeyboardOptions(imeAction = ImeAction.Done)
                                          else KeyboardOptions.Default,
                        keyboardActions = if (isTV) KeyboardActions(onDone = {
                            if (newName.isNotBlank()) onCreate(newName.trim())
                        }) else KeyboardActions.Default,
                    )
                    IconButton(
                        onClick = {
                            if (newName.isNotBlank()) onCreate(newName.trim())
                        },
                        modifier = Modifier
                            .tvFocusRing(isTV, TvCircleShape)
                            .size(40.dp),
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
                        .tvFocusRing(isTV, RoundedCornerShape(10.dp), 1.02f)
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
                                .tvFocusRing(isTV, RoundedCornerShape(10.dp), 1.02f)
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
 * Frosted-glass context menu for a song track — Play / Add to queue / Add to
 * playlist. Same visual family as [FrostedPlaylistPicker] and mirrors the
 * long-press menu wired up inline in FolderBrowserScreen. Shared here so
 * SearchScreen / ArtistResultsScreen don't have to reimplement the block.
 */
@Composable
fun FrostedSongContextMenu(
    song: EntryDto,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    FrostedOverlay(onDismiss = onDismiss) {
        FrostedCard {
            Text(
                song.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            song.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ContextRow(
                icon = Icons.Default.PlayArrow,
                label = stringResource(R.string.player_play),
                onClick = { onDismiss(); onPlay() },
            )
            ContextRow(
                icon = Icons.Default.PlaylistAdd,
                label = stringResource(R.string.browser_add_to_queue),
                onClick = { onDismiss(); onAddToQueue() },
            )
            ContextRow(
                icon = Icons.Default.QueueMusic,
                label = stringResource(R.string.browser_add_to_playlist),
                onClick = { onDismiss(); onAddToPlaylist() },
            )
        }
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(LocalIsTV.current, RoundedCornerShape(10.dp), 1.02f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.textSecondary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
    val isTV = LocalIsTV.current

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
                keyboardOptions = if (isTV) KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ).tvKeyboardOptions(true)
                else KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = if (isTV) KeyboardActions(onDone = {
                    if (isValid) onConfirm(parsedDays)
                }) else KeyboardActions.Default,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().tvTextFieldKeys(isTV),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = { onConfirm(parsedDays) },
                    enabled = isValid,
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) { Text(stringResource(R.string.share_expiry_confirm)) }
            }
        }
    }
}

/**
 * Simple frosted-glass confirmation dialog — title, message, Cancel/Confirm.
 * Used for playlist delete + playlist entry remove, so those confirmation
 * modals match the other frosted-glass overlays.
 */
@Composable
fun FrostedConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val isTV = LocalIsTV.current
    FrostedOverlay(onDismiss = onDismiss) {
        FrostedCard {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) {
                    Text(
                        confirmLabel,
                        color = if (destructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Generic frosted-glass single-line text prompt (title + optional hint + one
 * field + Cancel/Confirm). Used for "Save queue as playlist" and reusable for
 * any other name-entry flow, so those all match the share / picker modals.
 */
@Composable
fun FrostedTextPromptDialog(
    title: String,
    label: String,
    confirmLabel: String,
    subtitle: String? = null,
    initialValue: String = "",
    onConfirm: (text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val isTV = LocalIsTV.current

    FrostedOverlay(onDismiss = onDismiss) {
        FrostedCard {
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
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().tvTextFieldKeys(isTV),
                keyboardOptions = if (isTV) KeyboardOptions(imeAction = ImeAction.Done)
                                  else KeyboardOptions.Default,
                keyboardActions = if (isTV) KeyboardActions(onDone = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                }) else KeyboardActions.Default,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape),
                ) { Text(confirmLabel) }
            }
        }
    }
}
