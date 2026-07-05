package com.tiritibambix.sharesonic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.ui.share.ShareExpiryDialog
import com.tiritibambix.sharesonic.ui.theme.textSecondary
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
    var showMoreSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }

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
                                        MaterialTheme.colorScheme.textSecondary
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
                                        MaterialTheme.colorScheme.textSecondary
                                )
                            }
                        }
                    } else {
                        // Phone: tapping the dot indicator + label toggles the pager
                        // between Now Playing and Queue. This is the discoverable way
                        // back to the player from a scrolled-full queue where the
                        // right-swipe gesture is caught by SwipeToDismissBox on the
                        // row and never reaches the pager.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            if (pagerState.currentPage == PAGE_NOW_PLAYING)
                                                PAGE_QUEUE
                                            else
                                                PAGE_NOW_PLAYING
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
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
                                                MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.4f)
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
                    // Track info lives in the "⋮ More" sheet (below), so there's no
                    // separate ⓘ button here — it would duplicate that entry.

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
                                       MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                    // More actions (sleep timer, track info) — Now Playing page only
                    if (pagerState.currentPage == PAGE_NOW_PLAYING && state.currentSong != null) {
                        IconButton(onClick = { showMoreSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = if (state.sleepRemainingMs != null)
                                           MaterialTheme.colorScheme.primary
                                       else
                                           MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.6f)
                            )
                        }
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
                    color = MaterialTheme.colorScheme.textSecondary
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

    // ── Track info dialog — triggered by the ⓘ in the top bar (or the More sheet).
    // Full metadata table; the file path (selectable, copyable) sits last. ──
    if (showFileInfoDialog) {
        state.currentSong?.let { song ->
            // Fetch fresh, full metadata on open so bpm/key/genres/year/track always
            // show — search-origin songs carry none of those on their EntryDto.
            val fresh by produceState<com.tiritibambix.sharesonic.data.api.models.VelvetInnerMetadata?>(
                initialValue = null, key1 = song.id
            ) {
                value = song.path?.let { viewModel.fetchTrackMetadata(it) }
            }
            val enriched = song.copy(
                bpm = fresh?.bpm ?: song.bpm,
                musicalKey = fresh?.musicalKey ?: song.musicalKey,
                genres = fresh?.genres ?: song.genres,
                year = fresh?.year ?: song.year,
                track = fresh?.track ?: song.track,
                rating = fresh?.rating ?: song.rating,
                artist = song.artist ?: fresh?.artist,
                album = song.album ?: fresh?.album
            )
            TrackInfoDialog(
                song = enriched,
                bitrateKbps = state.audioBitrateKbps,
                sampleRateHz = state.audioSampleRateHz,
                channels = state.audioChannels,
                onDismiss = { showFileInfoDialog = false }
            )
        }
    }

    // ── More actions sheet (sleep timer + lyrics + track info) ──
    if (showMoreSheet) {
        MoreActionsSheet(
            sleepRemainingMs = state.sleepRemainingMs,
            onOpenSleepTimer = { showMoreSheet = false; showSleepSheet = true },
            onOpenLyrics = { showMoreSheet = false; showLyricsSheet = true },
            onOpenInfo = { showMoreSheet = false; showFileInfoDialog = true },
            onDismiss = { showMoreSheet = false }
        )
    }

    // ── Lyrics sheet ──
    if (showLyricsSheet) {
        state.currentSong?.let { song ->
            LyricsSheet(
                title = song.displayName,
                fetch = { viewModel.fetchLyrics(song) },
                onDismiss = { showLyricsSheet = false }
            )
        }
    }

    // ── Sleep timer picker sheet ──
    if (showSleepSheet) {
        SleepTimerSheet(
            active = state.sleepRemainingMs != null,
            onPick = { minutes -> viewModel.setSleepTimer(minutes); showSleepSheet = false },
            onCancel = { viewModel.cancelSleepTimer(); showSleepSheet = false },
            onDismiss = { showSleepSheet = false }
        )
    }

    // ── Share queue — ask for expiry before creating the link (Velvet style) ──
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

    val base = MaterialTheme.colorScheme.background
    // Album-art halo — OKLCH radial glow anchored top-centre (mStream parity),
    // seeded from the cover's vibrant swatch. The brush is built at draw time
    // (Modifier.drawWithCache) because Brush.radialGradient's center/radius are
    // in pixels — feeding fractional values or Offset.Unspecified put the seed
    // in the top-left corner or dead-centre (invisible / wrong). Building with
    // `size` in hand lets us point the halo at (width/2, 0) with a radius of
    // 1.25 × the shortest side, which is what mStream's ambientGradient does.
    val ambientSeed = rememberAmbientColor(state.coverArtUrl, vibrant = true)
    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = ambientSeed,
            animationSpec = tween(700),
            label = "ambient",
            modifier = Modifier.fillMaxSize()
        ) { seed ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brush = seed?.let {
                            ambientBrush(
                                seed = it,
                                base = base,
                                vibrant = true,
                                center = Offset(size.width / 2f, 0f),
                                radius = size.minDimension * 1.25f,
                            )
                        } ?: SolidColor(base)
                        onDrawBehind { drawRect(brush = brush) }
                    }
            )
        }
        // Firefly-like particles drifting behind the content, tinted with the
        // same ambient seed so they read as motes of the artwork's own colour.
        FloatingParticles(
            color = ambientSeed,
            seedKey = state.currentSong?.id ?: "none",
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Cover art area — FLEXIBLE (weight 1f): it soaks up whatever vertical space
        // the fixed controls below don't use, so the whole page always fits on screen
        // and NEVER scrolls. The artwork is a centred square sized to fit; the ambient
        // glow (drawn by the parent Box above) fills the area behind it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(12.dp))
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
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    color = MaterialTheme.colorScheme.textSecondary,
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
                        color = MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.65f),
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

        Spacer(Modifier.height(14.dp))

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
            // Subtle theme-accent shadow under the play/pause button, mirroring
            // mStream's `BoxShadow(color: primary.withAlpha(0.4), blurRadius: 16,
            // offset: (0, 6))`. Modifier.shadow uses the platform shadow API —
            // on API 28+ spotColor tints the shadow with primary; on 26-27 it
            // falls back to a plain grey shadow, which still reads as depth.
            FilledIconButton(
                onClick = { viewModel.playPause() },
                modifier = Modifier
                    .size(68.dp)
                    .shadow(
                        // Two successive +10 % bumps: 12 → 14 → ~15.4, rounded up to
                        // 16 dp for a rounder, clearly-perceptible halo.
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary,
                        spotColor = MaterialTheme.colorScheme.primary,
                    )
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

        // ── Seek bar — waveform, tap or drag to seek ─────────────────────────────
        if (state.durationMs > 0L) {
            var scrubFraction by remember { mutableStateOf<Float?>(null) }
            val fraction = (state.currentPositionMs.toFloat() / state.durationMs.toFloat())
                .coerceIn(0f, 1f)
            val shownMs = ((scrubFraction ?: fraction) * state.durationMs).toLong()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                // Subtle accent glow behind the played portion only: vertical
                // taper (transparent → primary alpha → transparent) so the
                // halo blooms softly above and below the played bars and stops
                // at the playhead — the unplayed side stays flat.
                val waveGlow = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .drawBehind {
                            val head = (scrubFraction ?: fraction).coerceIn(0f, 1f)
                            if (head <= 0f) return@drawBehind
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        // Two successive +10 % bumps: 0.22 → 0.242 → 0.266.
                                        waveGlow.copy(alpha = 0.266f),
                                        Color.Transparent,
                                    )
                                ),
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width * head, size.height),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    WaveformSeekBar(
                        fraction = fraction,
                        seedKey = state.currentSong!!.id,
                        onSeek = { f -> viewModel.seekTo((f * state.durationMs).toLong()) },
                        onScrub = { f -> scrubFraction = f },
                        playedColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatMs(shownMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textSecondary
                    )
                    Text(
                        formatMs(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    color = MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        }  // Column (page content)
    }      // Box (ambient wrapper)

    // ── Share — ask for expiry before creating the link (Velvet style) ──
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
                        color = MaterialTheme.colorScheme.textSecondary
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
                           else MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.4f),
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
                tint = if (rating != 0) MaterialTheme.colorScheme.textSecondary
                       else MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.25f),
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
                color = MaterialTheme.colorScheme.textSecondary
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
                    color = MaterialTheme.colorScheme.textSecondary
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
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        song.duration?.let {
            Text(
                formatDuration(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textSecondary
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
                            color = MaterialTheme.colorScheme.textSecondary
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
                            MaterialTheme.colorScheme.textSecondary
                        else
                            MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.38f)
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
                            MaterialTheme.colorScheme.textSecondary
                        else
                            MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.38f)
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
                                   MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.4f)
                    )
                }
            }
            // Reserve space for system navigation bar below the content row
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
