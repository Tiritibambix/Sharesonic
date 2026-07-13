package com.tiritibambix.sharesonic.ui.playlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.ui.components.FrostedConfirmDialog
import com.tiritibambix.sharesonic.ui.components.FrostedShareExpiryDialog
import com.tiritibambix.sharesonic.ui.components.FrostedTextPromptDialog
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (name: String) -> Unit,
    onShareCreated: (url: String) -> Unit,
    miniPlayerVisible: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val shareState by viewModel.shareState.collectAsState()

    // Push FAB and list bottom padding up when the mini player bar is visible
    val fabBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 68.dp else 0.dp,
        label = "fabBottomPadding"
    )
    val listBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 156.dp else 80.dp,
        label = "listBottomPadding"
    )

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NativePlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<NativePlaylist?>(null) }
    var shareTarget by remember { mutableStateOf<NativePlaylist?>(null) }

    // Same frosted-glass pattern as the folder browser: blur the Scaffold
    // whenever any modal is open, so the panel visually sits above the list.
    val anyModalOpen = showCreateDialog || renameTarget != null ||
        deleteTarget != null || shareTarget != null
    val contentBlur by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (anyModalOpen) 18.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "contentBlur"
    )

    LaunchedEffect(shareState) {
        val s = shareState
        if (s is PlaylistShareState.Done) {
            onShareCreated(s.url)
            viewModel.clearShareState()
        }
    }

    Scaffold(
        modifier = Modifier.blur(contentBlur),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playlists_new_title))
                }
                Spacer(modifier = Modifier.height(fabBottomPadding))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is PlaylistsState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PlaylistsState.Error -> {
                    Text(
                        s.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is PlaylistsState.Ready -> {
                    if (s.playlists.isEmpty()) {
                        Text(
                            stringResource(R.string.playlists_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = listBottomPadding)
                        ) {
                            items(s.playlists, key = { it.name }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.name) },
                                    onRename = { renameTarget = playlist },
                                    onDelete = { deleteTarget = playlist },
                                    onShare  = { shareTarget = playlist }
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Create dialog (frosted) ────────────────────────────────────────────
    if (showCreateDialog) {
        FrostedTextPromptDialog(
            title = stringResource(R.string.playlists_new_title),
            label = stringResource(R.string.playlists_name_label),
            confirmLabel = stringResource(R.string.playlists_create),
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // ── Rename dialog (frosted) ────────────────────────────────────────────
    renameTarget?.let { target ->
        FrostedTextPromptDialog(
            title = stringResource(R.string.playlists_rename_title),
            label = stringResource(R.string.playlists_new_name),
            confirmLabel = stringResource(R.string.common_rename),
            initialValue = target.name,
            onConfirm = { name ->
                viewModel.renamePlaylist(target.name, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // ── Delete confirmation (frosted) ──────────────────────────────────────
    deleteTarget?.let { target ->
        FrostedConfirmDialog(
            title = stringResource(R.string.playlists_delete_title),
            message = stringResource(R.string.playlists_delete_message, target.name),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                viewModel.deletePlaylist(target.name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    // ── Share expiry prompt (frosted, matches folder / queue shares) ──────
    shareTarget?.let { target ->
        FrostedShareExpiryDialog(
            onConfirm = { expiryDays ->
                viewModel.sharePlaylist(target.name, expiryDays)
                shareTarget = null
            },
            onDismiss = { shareTarget = null }
        )
    }

    // Errors on share are surfaced via a simple dialog for now — success
    // routes through onShareCreated → ShareConfirm.
    (shareState as? PlaylistShareState.Error)?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearShareState() },
            title = { Text(stringResource(R.string.common_share)) },
            text = { Text(err.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearShareState() }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: NativePlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isTV = LocalIsTV.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(4.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.playlists_song_count, playlist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.textSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.textSecondary
        )

        // TV: ⋮ button replaces long-press to access Rename / Delete / Share
        if (isTV) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.common_more),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { showMenu = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_share)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { showMenu = false; onShare() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}
