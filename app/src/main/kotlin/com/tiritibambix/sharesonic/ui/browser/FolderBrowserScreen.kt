package com.tiritibambix.sharesonic.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    folderName: String,
    viewModel: FolderBrowserViewModel,
    playerViewModel: PlayerViewModel,
    onOpenFolder: (id: String, name: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onShareCreated: (url: String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val shareState by viewModel.shareState.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    var contextEntry by remember { mutableStateOf<EntryDto?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    // Navigate to share confirm when share URL is ready
    LaunchedEffect(shareState) {
        if (shareState is ShareState.Done) {
            onShareCreated((shareState as ShareState.Done).url)
            viewModel.clearShareState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (playerState.currentSong != null) {
                        IconButton(onClick = onOpenNowPlaying) {
                            Icon(Icons.Default.MusicNote, contentDescription = "Now Playing")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is BrowserState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is BrowserState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }

                is BrowserState.Ready -> {
                    if (s.entries.isEmpty()) {
                        Text(
                            "Empty folder",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.entries, key = { it.id }) { entry ->
                                EntryRow(
                                    entry = entry,
                                    onClick = {
                                        if (entry.isDir) {
                                            onOpenFolder(entry.id, entry.displayName)
                                        } else {
                                            playerViewModel.playSong(entry)
                                            onOpenNowPlaying()
                                        }
                                    },
                                    onLongClick = {
                                        contextEntry = entry
                                        showContextMenu = true
                                    }
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Share loading overlay
            if (shareState is ShareState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Context menu (long press)
    if (showContextMenu && contextEntry != null) {
        val entry = contextEntry!!
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (!entry.isDir) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                playerViewModel.playSong(entry)
                                onOpenNowPlaying()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Play") }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            viewModel.collectShuffled(
                                directoryId = if (entry.isDir) entry.id else entry.id,
                                onReady = { songs ->
                                    playerViewModel.playQueue(songs)
                                    onOpenNowPlaying()
                                },
                                onError = { /* TODO snackbar */ }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (entry.isDir) "Shuffle folder" else "Shuffle from here") }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            viewModel.shareEntry(entry.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Share link") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) { Text("Cancel") }
            }
        )
    }

    // Share error snackbar-like dialog
    if (shareState is ShareState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.clearShareState() },
            title = { Text("Share failed") },
            text = { Text((shareState as ShareState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearShareState() }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: EntryDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Default.Folder else Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (entry.isDir)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDir && entry.artist != null) {
                Text(
                    text = entry.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
