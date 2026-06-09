package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.ui.share.ShareExpiryDialog
import com.tiritibambix.sharesonic.utils.LocalIsTV
import kotlinx.coroutines.launch

private const val PAGE_NOW_PLAYING = 0
private const val PAGE_QUEUE = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onShareCreated: (url: String) -> Unit
) {
    val isTV = LocalIsTV.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = PAGE_NOW_PLAYING) { 2 }
    var showShareQueueExpiryDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            onShareCreated(url)
            viewModel.clearShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isTV) {
                        // TV: explicit tab buttons — D-pad can focus and select them,
                        // replacing the swipe gesture that doesn't work without touch.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(PAGE_NOW_PLAYING)
                                    }
                                }
                            ) {
                                Text(
                                    "Now Playing",
                                    color = if (pagerState.currentPage == PAGE_NOW_PLAYING)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(PAGE_QUEUE)
                                    }
                                }
                            ) {
                                Text(
                                    "Queue (${state.queueIndex + 1}/${state.queue.size})",
                                    color = if (pagerState.currentPage == PAGE_QUEUE)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Phone: dot indicator + label
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(2) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == i)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (pagerState.currentPage == PAGE_NOW_PLAYING) "Now Playing"
                                       else "Queue  (${state.queueIndex + 1}/${state.queue.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // ⓘ — file path, tucked into the top bar so it costs zero
                    // vertical space in the scrollable player body (every inline
                    // placement tried so far either pushed the layout and forced a
                    // scroll, or sat awkwardly over the cover art). Tap reveals the
                    // full path in a small dialog. Only relevant on the player page.
                    if (pagerState.currentPage == PAGE_NOW_PLAYING && state.currentSong?.path != null) {
                        IconButton(onClick = { showFileInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "File path",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    // Share the whole queue as one public playlist link — only
                    // makes sense while looking at the queue, so it only appears there.
                    if (pagerState.currentPage == PAGE_QUEUE && state.queue.isNotEmpty()) {
                        if (state.shareLoading) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { showShareQueueExpiryDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share queue"
                                )
                            }
                        }
                    }
                    // Auto-DJ toggle — headphones icon, lit when enabled
                    IconToggleButton(
                        checked = state.autoDjEnabled,
                        onCheckedChange = { viewModel.toggleAutoDj() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = if (state.autoDjEnabled) "Auto-DJ on" else "Auto-DJ off",
                            tint = if (state.autoDjEnabled)
                                       MaterialTheme.colorScheme.primary
                                   else
                                       MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { page ->
            when (page) {
                PAGE_NOW_PLAYING -> NowPlayingPage(state, viewModel)
                PAGE_QUEUE       -> QueuePage(state, viewModel, isTV)
            }
        }
    }

    // ── File path dialog — triggered by the ⓘ in the top bar. Selectable text so
    // the path can be copied. ──
    if (showFileInfoDialog) {
        state.currentSong?.path?.let { path ->
            AlertDialog(
                onDismissRequest = { showFileInfoDialog = false },
                title = { Text("File path") },
                text = {
                    SelectionContainer {
                        Text(text = path, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFileInfoDialog = false }) { Text("Close") }
                }
            )
        }
    }

    // ── Share queue — ask for expiry before creating the link (mStream Velvet style) ──
    if (showShareQueueExpiryDialog) {
        ShareExpiryDialog(
            onConfirm = { expiryDays ->
                showShareQueueExpiryDialog = false
                viewModel.shareQueue(expiryDays)
            },
            onDismiss = { showShareQueueExpiryDialog = false }
        )
    }
}

// ── Page 0: Now Playing ───────────────────────────────────────────────────────

@Composable
private fun NowPlayingPage(state: PlayerState, viewModel: PlayerViewModel) {
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showShareExpiryDialog by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover art — square, max 300 dp so controls always fit on screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
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
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(72.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            // Bottom gradient so text sits over the art comfortably
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
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

        Spacer(Modifier.height(16.dp))

        // ── Title + subtitle — kept tight, they read as one block ──────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = state.currentSong!!.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            val subtitle = buildString {
                state.currentSong!!.artist?.let { append(it) }
                if (!state.currentSong!!.artist.isNullOrBlank() &&
                    !state.currentSong!!.album.isNullOrBlank()
                ) append("  ·  ")
                state.currentSong!!.album?.let { append(it) }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Secondary info: format/bitrate, then star rating ────────────────────
        // Stacked rather than sharing a row: when both are present, the rating's
        // five stars + clear button were wide enough to squeeze the format/bitrate
        // label into an ellipsis. Stacking gives the label the full row width so
        // it's always completely readable, and centers the rating beneath it.
        val formatLabel = buildString {
            state.currentSong!!.suffix?.takeIf { it.isNotBlank() }?.let { append(it.uppercase()) }
            state.audioBitrateKbps?.let {
                if (isNotEmpty()) append("  ·  ")
                append("$it kbps")
            }
        }
        // Subsonic search-result songs carry a numeric ID with no native filepath,
        // so they can't be rated through the native rate-song endpoint.
        val ratableSong = !state.currentSong!!.id.all { it.isDigit() }
        if (formatLabel.isNotBlank() || ratableSong) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (formatLabel.isNotBlank()) {
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (ratableSong) {
                    RatingStars(
                        rating = (state.currentSong!!.rating ?: 0) / 2,
                        onRate = viewModel::rateCurrentSong
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ── Playback controls — generously spaced, the visual anchor of the page ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.skipPrev() },
                enabled = state.queueIndex > 0
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous",
                    modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { viewModel.playPause() },
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(
                onClick = { viewModel.skipNext() },
                enabled = state.queueIndex < state.queue.lastIndex
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next",
                    modifier = Modifier.size(36.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Seek bar ────────────────────────────────────────────────────────────
        if (state.durationMs > 0L) {
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }

            val sliderValue = if (dragging) dragValue
                else (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { v ->
                        dragging = true
                        dragValue = v
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo((dragValue * state.durationMs).toLong())
                        dragging = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatMs(if (dragging) (dragValue * state.durationMs).toLong() else state.currentPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatMs(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Actions: Share / Add to playlist — own breathing room from the controls ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.shareLoading) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    OutlinedButton(
                        onClick = { showShareExpiryDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                OutlinedButton(
                    onClick = { showPlaylistPicker = true; viewModel.loadPlaylists() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Playlist")
                }
            }

            state.shareError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Playback error (stream failed)
            state.playbackError?.let { err ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠ $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearPlaybackError() }) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Hint: swipe for queue
            if (state.queue.size > 1) {
                Text(
                    "← Swipe for queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    // ── Share — ask for expiry before creating the link (mStream Velvet style) ──
    if (showShareExpiryDialog) {
        ShareExpiryDialog(
            onConfirm = { expiryDays ->
                showShareExpiryDialog = false
                viewModel.shareCurrentSong(expiryDays)
            },
            onDismiss = { showShareExpiryDialog = false }
        )
    }

    // ── Add-to-playlist dialog ─────────────────────────────────────────────────
    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet.\nCreate one in the Playlists screen first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addCurrentSongToPlaylist(playlist.name)
                                    showPlaylistPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${playlist.name}  (${playlist.songCount})",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 0–5 star rating row for the currently playing track. Tapping a star sets the
 * rating to that many stars; tapping the already-active star toggles it back to
 * "unrated" — [PlayerViewModel.rateCurrentSong] handles that toggle.
 *
 * Mirrors the Auto-DJ "Minimum rating" picker ([StarRatingPicker] in
 * AutoDjSettingsScreen): the toggle-the-active-star gesture alone wasn't
 * discoverable enough — once a rating was set, people couldn't find their way
 * back to "unrated" — so an explicit, always-visible clear button is included too.
 */
@Composable
private fun RatingStars(
    rating: Int,
    onRate: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            IconButton(
                onClick = { onRate(star) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$star ${if (star == 1) "star" else "stars"}",
                    tint = if (star <= rating) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        // Explicit, always-visible way back to "unrated" — same affordance as the
        // Auto-DJ minimum-rating picker's clear button (see rationale above).
        IconButton(
            onClick = { onRate(0) },
            enabled = rating != 0,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear rating",
                tint = if (rating != 0) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Page 1: Queue ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuePage(state: PlayerState, viewModel: PlayerViewModel, isTV: Boolean) {
    if (state.queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "Queue is empty",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.queueIndex) {
        listState.animateScrollToItem(state.queueIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(state.queue, key = { _, song -> song.id }) { index, song ->
            val isCurrent = index == state.queueIndex

            if (isCurrent) {
                // Currently playing track — no remove action
                QueueSongRow(index, song, isCurrent, onClick = { viewModel.jumpTo(index) }, onRemove = null)
            } else if (isTV) {
                // TV: swipe not available — show an always-visible ✕ button instead
                QueueSongRow(
                    index, song, isCurrent,
                    onClick  = { viewModel.jumpTo(index) },
                    onRemove = { viewModel.removeFromQueue(index) }
                )
            } else {
                // Phone: swipe left (EndToStart) → remove from queue
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.removeFromQueue(index)
                            true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                ) {
                    QueueSongRow(index, song, isCurrent, onClick = { viewModel.jumpTo(index) }, onRemove = null)
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun QueueSongRow(
    index: Int,
    song: com.tiritibambix.sharesonic.data.api.models.EntryDto,
    isCurrent: Boolean,
    onClick: () -> Unit,
    /** On TV, passed for non-current rows so a ✕ button is always visible. */
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrent)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Playing indicator or track number
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

        song.duration?.let {
            Text(
                formatDuration(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // TV: always-visible remove button (replaces swipe gesture)
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

// ── Mini player bar ───────────────────────────────────────────────────────────
// Persistent bar shown at the bottom of all screens while a song is playing.
// Tapping it navigates to the full NowPlaying screen.

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    onToggleAutoDj: () -> Unit = {}
) {
    val song = state.currentSong ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column {
            // Thin progress strip along the top edge
            if (state.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (state.currentPositionMs.toFloat() / state.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art thumbnail
                if (state.coverArtUrl != null) {
                    AsyncImage(
                        model = state.coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.padding(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Track info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = song.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!song.artist.isNullOrBlank()) {
                        Text(
                            text = song.artist!!,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Controls
                IconButton(
                    onClick = onSkipPrev,
                    enabled = state.queueIndex > 0
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (state.queueIndex > 0)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause
                                      else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    enabled = state.queueIndex < state.queue.lastIndex
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = if (state.queueIndex < state.queue.lastIndex)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
                // Auto-DJ toggle — headphones icon
                IconToggleButton(
                    checked = state.autoDjEnabled,
                    onCheckedChange = { onToggleAutoDj() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = if (state.autoDjEnabled) "Auto-DJ on" else "Auto-DJ off",
                        tint = if (state.autoDjEnabled)
                                   MaterialTheme.colorScheme.primary
                               else
                                   MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
            // Reserve space for system navigation bar below the content row
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
