package com.tiritibambix.sharesonic.ui.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.ui.components.FrostedPlaylistPicker
import com.tiritibambix.sharesonic.ui.components.FrostedSongContextMenu
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV

/**
 * Standalone "folder-like" results page for an artist whose tag-derived name has
 * no corresponding on-disk folder anywhere in the library (its tracks are
 * scattered across compilation/vinyl-rip folders, or share no common ancestor
 * that's actually named after the artist). Reached by tapping such an artist in
 * [SearchScreen] — shows every song the server returned for that artist tag via
 * [SearchViewModel.fetchArtistSongsRaw], with Play all / Shuffle FABs just like
 * a real folder.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistResultsScreen(
    artistName: String,
    songs: List<EntryDto>,
    settings: ServerSettings,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val playerState by playerViewModel.state.collectAsState()
    val isTV = LocalIsTV.current
    val miniPlayerVisible = playerState.currentSong != null

    // Same context-menu + playlist-picker pattern as SearchScreen — reuses
    // PlayerViewModel's cached playlists and add methods.
    var contextEntry by remember { mutableStateOf<EntryDto?>(null) }
    var playlistTarget by remember { mutableStateOf<EntryDto?>(null) }
    val playlists by playerViewModel.playlists.collectAsState()
    LaunchedEffect(playlistTarget) {
        if (playlistTarget != null) playerViewModel.loadPlaylists()
    }
    val fabBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 68.dp else 0.dp,
        label = "fabBottomPadding"
    )
    val listBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 156.dp else 80.dp,
        label = "listBottomPadding"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 40.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
                title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    // TV: FABs aren't reliably D-pad reachable — expose Play All /
                    // Shuffle in the TopAppBar so the remote can trigger them.
                    if (isTV && songs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                playerViewModel.playQueue(songs)
                                onOpenNowPlaying()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_detail_play_all), modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                playerViewModel.playQueue(songs.shuffled())
                                onOpenNowPlaying()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.playlist_detail_shuffle), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isTV && songs.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(songs.shuffled())
                            onOpenNowPlaying()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.playlist_detail_shuffle))
                    }
                    FloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(songs)
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
            if (songs.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_results, artistName),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.textSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottomPadding)
                ) {
                    itemsIndexed(songs, key = { idx, _ -> "artistresult_$idx" }) { _, song ->
                        val play = {
                            playerViewModel.playSong(song)
                            onOpenNowPlaying()
                        }
                        if (isTV) {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                EntryRow(
                                    entry = song,
                                    coverArtUrl = song.coverArt?.let { nativeCoverArtUrl(settings, it) },
                                    isAlbum = false,
                                    onClick = play,
                                    onLongClick = { contextEntry = song },
                                    onShowMenu = { contextEntry = song }
                                )
                            }
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> { contextEntry = song }
                                        SwipeToDismissBoxValue.EndToStart -> playerViewModel.addToQueue(song)
                                        else -> {}
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val isEndToStart = dismissState.targetValue ==
                                        SwipeToDismissBoxValue.EndToStart
                                    if (isEndToStart) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .padding(end = 16.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    stringResource(R.string.browser_add_to_queue),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Icon(
                                                    Icons.Default.AddToQueue,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(start = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.QueueMusic,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    stringResource(R.string.browser_add_to_playlist),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                Surface(color = MaterialTheme.colorScheme.surface) {
                                    EntryRow(
                                        entry = song,
                                        coverArtUrl = song.coverArt?.let { nativeCoverArtUrl(settings, it) },
                                        isAlbum = false,
                                        onClick = play,
                                        onLongClick = { contextEntry = song },
                                        onShowMenu = null
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    // ── Song context menu (long-press on phone, "⋮" on TV) ─────────────────
    contextEntry?.let { entry ->
        FrostedSongContextMenu(
            song = entry,
            onPlay = {
                playerViewModel.playSong(entry)
                onOpenNowPlaying()
            },
            onAddToQueue = { playerViewModel.addToQueue(entry) },
            onAddToPlaylist = { playlistTarget = entry },
            onDismiss = { contextEntry = null }
        )
    }

    // ── Playlist picker — same as SearchScreen / Now Playing / FolderBrowser ─
    playlistTarget?.let { target ->
        FrostedPlaylistPicker(
            title = stringResource(R.string.player_add_playlist_title),
            subtitle = target.displayName +
                (target.artist?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""),
            playlists = playlists,
            onPick = { name ->
                playerViewModel.addSongToPlaylist(target, name)
                playlistTarget = null
            },
            onCreate = { name ->
                playerViewModel.createPlaylistAndAddSong(target, name)
                playlistTarget = null
            },
            onDismiss = { playlistTarget = null }
        )
    }
}
