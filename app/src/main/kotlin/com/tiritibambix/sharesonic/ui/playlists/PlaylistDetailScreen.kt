package com.tiritibambix.sharesonic.ui.playlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    initialName: String,
    viewModel: PlaylistDetailViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val addSongsState by viewModel.addSongsState.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    // Push FABs and list bottom padding up when the mini player bar is visible
    val miniPlayerVisible = playerState.currentSong != null
    val fabBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 68.dp else 0.dp,
        label = "fabBottomPadding"
    )
    val listBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 156.dp else 80.dp,
        label = "listBottomPadding"
    )

    val playlistName = (state as? PlaylistDetailState.Ready)?.name ?: initialName

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addQuery by remember { mutableStateOf("") }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                TextButton(onClick = {
                    if (renameName.isNotBlank()) viewModel.rename(renameName.trim())
                    showRenameDialog = false
                }) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // ── Add songs dialog ──────────────────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false; addQuery = ""; viewModel.clearAddSongsSearch()
            },
            title = { Text(stringResource(R.string.playlist_detail_add_songs)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addQuery,
                        onValueChange = { addQuery = it; viewModel.searchSongsToAdd(it) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (addQuery.isNotEmpty()) {
                                IconButton(onClick = { addQuery = ""; viewModel.clearAddSongsSearch() }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 300.dp)) {
                        when (val s = addSongsState) {
                            is AddSongsState.Idle ->
                                Text(
                                    stringResource(R.string.search_empty),
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            is AddSongsState.Loading ->
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                                    strokeWidth = 2.dp
                                )
                            is AddSongsState.Error ->
                                Text(
                                    s.message,
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            is AddSongsState.Results -> {
                                if (s.songs.isEmpty()) {
                                    Text(
                                        stringResource(R.string.playlist_detail_empty),
                                        modifier = Modifier.align(Alignment.Center),
                                        color = MaterialTheme.colorScheme.textSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    LazyColumn {
                                        items(s.songs, key = { it.id }) { song ->
                                            AddSongRow(song = song, onAdd = {
                                                viewModel.addSong(song.id)
                                                showAddDialog = false
                                                addQuery = ""
                                                viewModel.clearAddSongsSearch()
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false; addQuery = ""; viewModel.clearAddSongsSearch()
                }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                expandedHeight = 40.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { renameName = playlistName; showRenameDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_rename), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { addQuery = ""; showAddDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playlist_detail_add_songs), modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            val entries = (state as? PlaylistDetailState.Ready)?.entries
            if (!entries.isNullOrEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(entries.map { it.dto }.shuffled())
                            onOpenNowPlaying()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.playlist_detail_shuffle))
                    }
                    FloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(entries.map { it.dto })
                            onOpenNowPlaying()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_detail_play_all))
                    }
                    Spacer(modifier = Modifier.height(fabBottomPadding))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is PlaylistDetailState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PlaylistDetailState.Error ->
                    Text(
                        s.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                is PlaylistDetailState.Ready -> {
                    if (s.entries.isEmpty()) {
                        Text(
                            "No songs — tap + to add some",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.textSecondary
                        )
                    } else {
                        val isTV = LocalIsTV.current
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = listBottomPadding)
                        ) {
                            items(s.entries, key = { it.entryId }) { entry ->
                                SwipeToRemoveSongRow(
                                    entry = entry,
                                    isTV = isTV,
                                    onPlay = {
                                        playerViewModel.playSong(entry.dto)
                                        onOpenNowPlaying()
                                    },
                                    onRemove = { viewModel.removeSong(entry.entryId) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemoveSongRow(
    entry: PlaylistEntry,
    isTV: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    if (isTV) {
        // TV: no swipe — show a visible ✕ button at the end of each row
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SongRow(song = entry.dto, onClick = onPlay)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.playlist_detail_remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) { onRemove(); true } else false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.playlist_detail_remove), tint = MaterialTheme.colorScheme.error)
                }
            }
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                SongRow(song = entry.dto, onClick = onPlay)
            }
        }
    }
}

@Composable
private fun SongRow(song: EntryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play), tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                song.artist?.let { append(it) }
                if (!song.artist.isNullOrBlank() && !song.album.isNullOrBlank()) append("  ·  ")
                song.album?.let { append(it) }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        song.duration?.let {
            Text(
                "%d:%02d".format(it / 60, it % 60),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textSecondary
            )
        }
    }
}

@Composable
private fun AddSongRow(song: EntryDto, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            song.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
