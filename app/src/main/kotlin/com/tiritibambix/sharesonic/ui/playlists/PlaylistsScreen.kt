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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (name: String) -> Unit,
    miniPlayerVisible: Boolean = false
) {
    val state by viewModel.state.collectAsState()

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
    var createName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<NativePlaylist?>(null) }
    var renameName by remember { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<NativePlaylist?>(null) }

    // ── Create dialog ─────────────────────────────────────────────────────────
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; createName = "" },
            title = { Text(stringResource(R.string.playlists_new_title)) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text(stringResource(R.string.playlists_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createName.isNotBlank()) viewModel.createPlaylist(createName.trim())
                        showCreateDialog = false; createName = ""
                    }
                ) { Text(stringResource(R.string.playlists_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; createName = "" }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.playlists_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text(stringResource(R.string.playlists_new_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameName.isNotBlank())
                            viewModel.renamePlaylist(target.name, renameName.trim())
                        renameTarget = null
                    }
                ) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.playlists_delete_title)) },
            text = { Text(stringResource(R.string.playlists_delete_message, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.name)
                    deleteTarget = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
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
                FloatingActionButton(onClick = { createName = ""; showCreateDialog = true }) {
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
                                    onRename = { renameName = playlist.name; renameTarget = playlist },
                                    onDelete = { deleteTarget = playlist }
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: NativePlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
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

        // TV: ⋮ button replaces long-press to access Rename / Delete
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
