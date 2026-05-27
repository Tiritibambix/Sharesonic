package com.tiritibambix.sharesonic.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    var shuffleLoading by remember { mutableStateOf(false) }
    var shuffleError by remember { mutableStateOf<String?>(null) }

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
        },
        floatingActionButton = {
            // Shuffle FAB — always visible, shuffles current level
            ExtendedFloatingActionButton(
                onClick = {
                    shuffleLoading = true
                    viewModel.shuffleCurrent(
                        onReady = { songs ->
                            shuffleLoading = false
                            playerViewModel.playQueue(songs)
                            onOpenNowPlaying()
                        },
                        onError = { err ->
                            shuffleLoading = false
                            shuffleError = err
                        }
                    )
                },
                icon = {
                    if (shuffleLoading)
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                },
                text = { Text("Shuffle") }
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp) // clear FAB
                        ) {
                            items(s.entries, key = { it.id }) { entry ->
                                EntryRow(
                                    entry = entry,
                                    coverArtUrl = entry.coverArt?.let { viewModel.coverArtUrl(it) },
                                    onClick = {
                                        if (entry.isDir) onOpenFolder(entry.id, entry.displayName)
                                        else {
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

            if (shareState is ShareState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // ── Context menu (long press) ─────────────────────────────────────────────
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
                        ) { Text("▶  Play") }
                    }
                    if (entry.isDir) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                shuffleLoading = true
                                viewModel.shuffleCurrent(
                                    onReady = { songs ->
                                        shuffleLoading = false
                                        playerViewModel.playQueue(songs)
                                        onOpenNowPlaying()
                                    },
                                    onError = { err ->
                                        shuffleLoading = false
                                        shuffleError = err
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔀  Shuffle folder") }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            viewModel.shareEntry(entry.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🔗  Share link") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) { Text("Cancel") }
            }
        )
    }

    // ── Error snackbar-style dialogs ──────────────────────────────────────────
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

    shuffleError?.let { err ->
        AlertDialog(
            onDismissRequest = { shuffleError = null },
            title = { Text("Shuffle failed") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { shuffleError = null }) { Text("OK") } }
        )
    }
}

// ── Entry row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: EntryDto,
    coverArtUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail: cover art if available, icon fallback
        if (coverArtUrl != null) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (entry.isDir) Icons.Default.Folder else Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = if (entry.isDir)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDir) {
                Text(
                    text = buildString {
                        entry.artist?.let { append(it) }
                        if (!entry.artist.isNullOrBlank() && !entry.album.isNullOrBlank()) append(" · ")
                        entry.album?.let { append(it) }
                    }.ifBlank { "" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration hint for songs
        if (!entry.isDir && entry.duration != null) {
            Text(
                text = formatDuration(entry.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
