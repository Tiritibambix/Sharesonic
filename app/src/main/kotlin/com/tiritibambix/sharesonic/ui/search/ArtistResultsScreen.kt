package com.tiritibambix.sharesonic.ui.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel

/**
 * Standalone "folder-like" results page for an artist whose tag-derived name has
 * no corresponding on-disk folder anywhere in the library (its tracks are
 * scattered across compilation/vinyl-rip folders). Reached by tapping such an
 * artist in [SearchScreen] — shows every song from that search response whose
 * path mentions the artist (see [matchesArtist]), with Play all / Shuffle FABs
 * just like a real folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val miniPlayerVisible = playerState.currentSong != null
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
                title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (songs.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(songs.shuffled())
                            onOpenNowPlaying()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            playerViewModel.playQueue(songs)
                            onOpenNowPlaying()
                        },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        text = { Text("Play all") }
                    )
                    Spacer(modifier = Modifier.height(fabBottomPadding))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (songs.isEmpty()) {
                Text(
                    "No tracks found for $artistName",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottomPadding)
                ) {
                    itemsIndexed(songs, key = { idx, _ -> "artistresult_$idx" }) { _, song ->
                        EntryRow(
                            entry = song,
                            coverArtUrl = song.coverArt?.let { nativeCoverArtUrl(settings, it) },
                            isAlbum = false,
                            onClick = {
                                playerViewModel.playSong(song)
                                onOpenNowPlaying()
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
