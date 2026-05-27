package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onShareCreated: (url: String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            onShareCreated(url)
            viewModel.clearShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.currentSong == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Nothing playing",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Cover art ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                if (state.coverArtUrl != null) {
                    AsyncImage(
                        model = state.coverArtUrl,
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.padding(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                // Gradient overlay at the bottom of the art
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            // ── Song info + controls ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.currentSong!!.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (!state.currentSong!!.artist.isNullOrBlank()) {
                    Text(
                        text = buildString {
                            append(state.currentSong!!.artist!!)
                            state.currentSong!!.album?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.skipPrev() },
                        enabled = state.queueIndex > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.skipNext() },
                        enabled = state.queueIndex < state.queue.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }
                }

                // Share button
                if (state.shareLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    OutlinedButton(
                        onClick = { viewModel.shareCurrentSong() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share link")
                    }
                }
                state.shareError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Queue ─────────────────────────────────────────────────────────
            if (state.queue.size > 1) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text(
                    "Queue — ${state.queueIndex + 1} / ${state.queue.size}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val listState = rememberLazyListState()
                LaunchedEffect(state.queueIndex) {
                    listState.animateScrollToItem(state.queueIndex)
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.queue) { index, song ->
                        val isCurrent = index == state.queueIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.jumpTo(index) }
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.background
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (!song.artist.isNullOrBlank()) {
                                    Text(
                                        song.artist!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
}
