package com.tiritibambix.sharesonic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.components.FrostedPlaylistPicker
import com.tiritibambix.sharesonic.ui.components.FrostedShareExpiryDialog
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import kotlin.random.Random
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
    // Current-song share + add-to-playlist overlays. Hoisted to this level (out
    // of NowPlayingPage) so the frosted-glass blur can be applied to the whole
    // Scaffold behind them, exactly like the track-info dialog.
    var showShareExpiryDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()
    // Full-screen zoomable cover viewer, opened by tapping the artwork on the
    // Now Playing page. Shares the same frosted-glass backdrop as the track-info
    // dialog (blur + black scrim), so the two overlays feel visually related.
    var showCoverZoom by remember { mutableStateOf(false) }

    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            onShareCreated(url)
            viewModel.clearShare()
        }
    }

    // Frosted-glass effect: when the track-info modal is open, the whole Now
    // Playing surface (Scaffold + Now Playing page + queue) blurs behind it,
    // exactly like the folder-browser drawer scrim. The modal itself renders
    // OVER this blurred layer as a solid theme-coloured surface — so what looks
    // frosted is the *background*, not the modal.
    val contentBlur by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (
            showFileInfoDialog || showCoverZoom || showShareExpiryDialog ||
            showPlaylistPicker || showShareQueueExpiryDialog
        ) 18.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "info-blur",
    )

    // Queue list state hoisted here so the top bar can react to it: on the queue
    // page, once the list scrolls under the (translucent) bar, the text bleeding
    // through hurts readability — so we fade the bar to a near-opaque surface
    // while scrolled, and keep it translucent on Now Playing (where the ambient
    // gradient is meant to bleed through) and at the top of an unscrolled queue.
    val queueListState = rememberLazyListState()
    val queueScrolled by remember {
        derivedStateOf {
            queueListState.firstVisibleItemIndex > 0 || queueListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pagerState.currentPage == PAGE_QUEUE && queueScrolled) 0.92f else 0.35f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "topBarAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.blur(contentBlur),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                expandedHeight = 40.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha),
                ),
                title = {
                    if (isTV) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(PAGE_NOW_PLAYING)
                                    }
                                }
                            ) {
                                Text(
                                    stringResource(R.string.player_now_playing),
                                    style = MaterialTheme.typography.titleSmall,
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
                                    stringResource(R.string.player_queue_counter, state.queueIndex + 1, state.queue.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (pagerState.currentPage == PAGE_QUEUE)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.textSecondary
                                )
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
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
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            repeat(2) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == i) 7.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == i)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.4f)
                                        )
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (pagerState.currentPage == PAGE_NOW_PLAYING)
                                    stringResource(R.string.player_now_playing)
                                else
                                    stringResource(R.string.player_queue_counter, state.queueIndex + 1, state.queue.size),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconToggleButton(
                        checked = state.autoDjEnabled,
                        onCheckedChange = { viewModel.toggleAutoDj() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = stringResource(if (state.autoDjEnabled) R.string.player_autodj_on else R.string.player_autodj_off),
                            modifier = Modifier.size(20.dp),
                            tint = if (state.autoDjEnabled)
                                       MaterialTheme.colorScheme.primary
                                   else
                                       MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                    when {
                        pagerState.currentPage == PAGE_QUEUE && state.queue.isNotEmpty() -> {
                            if (state.shareLoading) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { showShareQueueExpiryDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.player_share_queue),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        pagerState.currentPage == PAGE_NOW_PLAYING && state.currentSong != null -> {
                            IconButton(
                                onClick = { showMoreSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.player_more),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (state.sleepRemainingMs != null)
                                               MaterialTheme.colorScheme.primary
                                           else
                                               MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        if (state.currentSong == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    stringResource(R.string.player_nothing_playing),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.textSecondary
                )
            }
            return@Scaffold
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                PAGE_NOW_PLAYING -> NowPlayingPage(
                    state = state,
                    viewModel = viewModel,
                    onCoverTap = { showCoverZoom = true },
                    onShare = { showShareExpiryDialog = true },
                    onAddToPlaylist = { showPlaylistPicker = true; viewModel.loadPlaylists() },
                    topPadding = topPadding,
                )
                PAGE_QUEUE       -> QueuePage(state, viewModel, isTV, listState = queueListState, topPadding = topPadding)
            }
        }
    }

    // ── Track info overlay — sits over the blurred Scaffold as a solid, theme-
    // coloured card. The scrim below darkens the frosted layer just enough for
    // the modal to read as the focused surface. Tapping the scrim dismisses. ──
    if (showFileInfoDialog) {
        state.currentSong?.let { song ->
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showFileInfoDialog = false }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Consume clicks on the card so tapping the modal doesn't
                // bubble up to the scrim's dismiss handler.
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { },
                    )
                ) {
                    TrackInfoDialog(
                        song = enriched,
                        bitrateKbps = state.audioBitrateKbps,
                        sampleRateHz = state.audioSampleRateHz,
                        channels = state.audioChannels,
                        onDismiss = { showFileInfoDialog = false }
                    )
                }
            }
        }
    }

    // ── Full-screen zoomable cover viewer — sits over the same blurred
    // Scaffold as the track-info card, with the same scrim, so the two
    // overlays feel like the same visual family. ──
    if (showCoverZoom && state.coverArtUrl != null) {
        CoverZoomOverlay(
            url = state.coverArtUrl!!,
            onDismiss = { showCoverZoom = false }
        )
    }

    // ── Current-song share — frosted-glass, matches the folder browser ──
    if (showShareExpiryDialog) {
        FrostedShareExpiryDialog(
            onConfirm = { expiryDays ->
                showShareExpiryDialog = false
                viewModel.shareCurrentSong(expiryDays)
            },
            onDismiss = { showShareExpiryDialog = false }
        )
    }

    // ── Share queue — frosted-glass ──
    if (showShareQueueExpiryDialog) {
        FrostedShareExpiryDialog(
            onConfirm = { expiryDays ->
                showShareQueueExpiryDialog = false
                viewModel.shareQueue(expiryDays)
            },
            onDismiss = { showShareQueueExpiryDialog = false }
        )
    }

    // ── Add current song to playlist — frosted-glass, with inline create ──
    if (showPlaylistPicker) {
        val song = state.currentSong
        FrostedPlaylistPicker(
            title = stringResource(R.string.player_add_playlist_title),
            subtitle = song?.let { s ->
                s.displayName + (s.artist?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: "")
            },
            playlists = playlists,
            onPick = { name ->
                viewModel.addCurrentSongToPlaylist(name)
                showPlaylistPicker = false
            },
            onCreate = { name ->
                viewModel.createPlaylistAndAddCurrentSong(name)
                showPlaylistPicker = false
            },
            onDismiss = { showPlaylistPicker = false }
        )
    }
    } // outer Box wrapping the Scaffold + blur + overlay

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

}

// ── Page 0: Now Playing ───────────────────────────────────────────────────────

@Composable
private fun NowPlayingPage(
    state: PlayerState,
    viewModel: PlayerViewModel,
    onCoverTap: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
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
        // same ambient seed. On grayscale artwork (seed = null) they fall back
        // to the current theme's primary so the field is never invisible.
        FloatingParticles(
            color = ambientSeed ?: MaterialTheme.colorScheme.primary,
            seedKey = state.currentSong?.id ?: "none",
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(12.dp))
                        // Tap → full-screen zoomable viewer (only meaningful when
                        // artwork exists). indication = null keeps it silent over
                        // the album art. clickable doesn't consume drag events, so
                        // the HorizontalPager swipe to the Queue page still works.
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            enabled = state.coverArtUrl != null,
                            onClick = onCoverTap
                        )
                ) {
                    if (state.coverArtUrl != null) {
                        AsyncImage(
                            model = state.coverArtUrl,
                            contentDescription = stringResource(R.string.player_album_art),
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
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous),
                    modifier = Modifier.size(36.dp))
            }
            // Theme-accent halo behind the play/pause button — an explicit
            // drawBehind radial glow rather than Modifier.shadow, because a
            // Material shadow at any reasonable elevation reads as a subtle
            // drop and the coloured-shadow spotColor requires API 28+ anyway.
            // The button is 68 dp, the wrapper 128 dp — enough room for a
            // visible ring of primary alpha bleeding out past the button edge.
            val playGlow = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    // Button occupies ~ r * 0.53. Peak alpha lands
                                    // just past the button edge so the halo reads
                                    // as light escaping around it.
                                    0.45f to playGlow.copy(alpha = 0.55f),
                                    0.72f to playGlow.copy(alpha = 0.22f),
                                    1.0f to Color.Transparent,
                                ),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
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
            }
            IconButton(
                onClick = { viewModel.skipNext() },
                enabled = state.queueIndex < state.queue.lastIndex
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next),
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
                val waveGlow = MaterialTheme.colorScheme.primary
                val glowBarCount = 112
                val glowHeights = remember(state.currentSong!!.id, glowBarCount) {
                    val rng = Random(state.currentSong!!.id.hashCode())
                    FloatArray(glowBarCount) { 0.18f + rng.nextFloat() * 0.82f }
                }
                Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp)
                    ) {
                        val head = (scrubFraction ?: fraction).coerceIn(0f, 1f)
                        if (head <= 0f) return@Canvas
                        val n = glowHeights.size
                        val gap = 3f
                        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                        val waveH = 38.dp.toPx()
                        val centerY = size.height / 2f
                        val playedBars = (head * n).toInt()
                        val cr = CornerRadius(barWidth / 2f)
                        for (i in 0 until playedBars.coerceAtMost(n)) {
                            val h = (glowHeights[i] * waveH).coerceAtLeast(barWidth)
                            val x = i * (barWidth + gap)
                            drawRoundRect(
                                color = waveGlow.copy(alpha = 0.85f),
                                topLeft = Offset(x, centerY - h / 2),
                                size = Size(barWidth, h),
                                cornerRadius = cr,
                            )
                        }
                    }
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
                            .align(Alignment.Center)
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
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.player_share))
                    }
                }
                OutlinedButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_playlist))
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
                        Text(stringResource(R.string.common_dismiss), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Hint: swipe for queue
            if (state.queue.size > 1) {
                Text(
                    stringResource(R.string.player_swipe_hint),
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
                    contentDescription = stringResource(
                        if (star == 1) R.string.player_rating_star else R.string.player_rating_stars,
                        star
                    ),
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
                contentDescription = stringResource(R.string.player_clear_rating),
                tint = if (rating != 0) MaterialTheme.colorScheme.textSecondary
                       else MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Full-screen cover viewer ──────────────────────────────────────────────────

/**
 * Fullscreen zoomable album art. Pinch to zoom (1×–5×), pan to move; single tap
 * at rest dismisses, double-tap toggles between 1× and 2.5× centered on the tap
 * point. System Back closes the overlay without collapsing the [PlayerPanel]
 * (this BackHandler is composed on top of PlayerPanel's, so Compose dispatches
 * to this one while the overlay is visible).
 *
 * The `scale`/`offset` state is `remember`ed inside this composable, so it
 * leaves the composition on dismiss and comes back fresh on the next open.
 */
@Composable
private fun CoverZoomOverlay(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Clamp offset to the scaled overflow: at scale 1 the pan budget is 0, so
    // the image is pinned centered — a stray one-finger drag on `transformable`
    // becomes a visual no-op instead of flying the image off-screen.
    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = containerSize.width * (s - 1f) / 2f
        val maxY = containerSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        offset = clampOffset(offset + pan, newScale)
        scale = newScale
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .onSizeChanged { containerSize = it }
            // Tap detector goes BEFORE .transformable so motionless taps aren't
            // eaten by the pinch/pan gesture recognizer. `onDoubleTap` costs a
            // ~300 ms delay on single-tap dismiss — acceptable, matches every
            // standard photo viewer.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1.01f) onDismiss() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(
                                containerSize.width / 2f,
                                containerSize.height / 2f
                            )
                            offset = clampOffset((center - tap) * (target - 1f), target)
                            scale = target
                        }
                    }
                )
            }
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Album art",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

// ── Page 1: Queue ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuePage(
    state: PlayerState,
    viewModel: PlayerViewModel,
    isTV: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (state.queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.queue_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.textSecondary
            )
        }
        return
    }

    LaunchedEffect(state.queueIndex) {
        listState.animateScrollToItem(state.queueIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding),
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
                                contentDescription = stringResource(R.string.queue_remove),
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
                    contentDescription = stringResource(R.string.queue_remove),
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
                        contentDescription = stringResource(R.string.player_previous),
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
                        contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play)
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    enabled = state.queueIndex < state.queue.lastIndex
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
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
                        contentDescription = stringResource(if (state.autoDjEnabled) R.string.player_autodj_on else R.string.player_autodj_off),
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
