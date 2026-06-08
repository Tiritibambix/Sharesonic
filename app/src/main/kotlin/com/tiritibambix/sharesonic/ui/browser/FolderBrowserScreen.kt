package com.tiritibambix.sharesonic.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import com.tiritibambix.sharesonic.ui.share.ShareExpiryDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    folderName: String,
    viewModel: FolderBrowserViewModel,
    playerViewModel: PlayerViewModel,
    onOpenFolder: (id: String, name: String) -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenAutoDjSettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenPublicLinks: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onShareCreated: (url: String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val shareState by viewModel.shareState.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    // Animate FAB and list padding up when the mini player bar is visible
    val miniPlayerVisible = playerState.currentSong != null
    val fabBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 68.dp else 0.dp,
        label = "fabBottomPadding"
    )
    val listBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 156.dp else 88.dp,
        label = "listBottomPadding"
    )

    var contextEntry by remember { mutableStateOf<EntryDto?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    // Entry awaiting an expiry-days choice before its share link is created.
    var shareExpiryTarget by remember { mutableStateOf<EntryDto?>(null) }
    var shuffleLoading by remember { mutableStateOf(false) }
    var shuffleError by remember { mutableStateOf<String?>(null) }

    // ── Swipe-to-add-to-playlist state ────────────────────────────────────────
    var songToAdd by remember { mutableStateOf<EntryDto?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()

    // Load playlists lazily when the picker is first opened
    LaunchedEffect(showPlaylistPicker) {
        if (showPlaylistPicker) viewModel.loadPlaylists()
    }

    // ── Letter strip state ────────────────────────────────────────────────────
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var draggingLetter by remember { mutableStateOf<Char?>(null) }

    // Build letter → first-item-index map whenever the entry list changes.
    // Computed at top level (not inside `when`) to comply with Compose rules.
    val entries = (state as? BrowserState.Ready)?.entries ?: emptyList()
    val letterIndex: Map<Char, Int> = remember(entries) {
        buildMap {
            entries.forEachIndexed { idx, entry ->
                val ch = entry.displayName.firstOrNull() ?: return@forEachIndexed
                val key = if (ch.isLetter()) ch.uppercaseChar() else '#'
                if (!containsKey(key)) put(key, idx)
            }
        }
    }
    val letters: List<Char> = remember(letterIndex) { letterIndex.keys.toList() }

    // Reset drag state when the folder changes
    LaunchedEffect(entries) { draggingLetter = null }

    LaunchedEffect(shareState) {
        if (shareState is ShareState.Done) {
            onShareCreated((shareState as ShareState.Done).url)
            viewModel.clearShareState()
        }
    }

    // ── Navigation drawer ─────────────────────────────────────────────────────
    // Classic hamburger menu, top-left — replaces the old top-right gear icon.
    // ModalNavigationDrawer natively supports the partial fold/unfold drag gesture
    // (swipe from the edge to peek it open, or drag the open drawer to dismiss).
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    fun closeDrawer() = drawerScope.launch { drawerState.close() }

    // Frosted-glass cue: blur the browser behind the drawer while it's open, and
    // size the sheet to ~80% of the screen width so a blurred sliver of the browser
    // stays visible (and tappable-to-dismiss) on the right — making it obvious that
    // there's content behind the drawer and a way back out besides the swipe gesture.
    val contentBlur by animateDpAsState(
        targetValue = if (drawerState.isOpen) 14.dp else 0.dp,
        label = "drawerContentBlur"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BoxWithConstraints {
                ModalDrawerSheet(modifier = Modifier.width(maxWidth * 0.8f)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Sharesonic",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Server") },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawer(); onOpenServerSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Auto-DJ") },
                    icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawer(); onOpenAutoDjSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Theme") },
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawer(); onOpenThemeSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Public Links") },
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawer(); onOpenPublicLinks() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                } // ModalDrawerSheet
            } // BoxWithConstraints
        }
    ) {
    Scaffold(
        modifier = Modifier.blur(contentBlur),
        topBar = {
            TopAppBar(
                title = { Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenPlaylists) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Playlists")
                    }
                }
            )
        },
        floatingActionButton = {
            // Column + Spacer: the Spacer grows when the mini player is visible,
            // pushing the FAB up above it without touching Scaffold's own FAB logic.
            Column(horizontalAlignment = Alignment.End) {
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
                Spacer(modifier = Modifier.height(fabBottomPadding))
            }
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    bottom = listBottomPadding,
                                    end = if (letters.size >= 2) 28.dp else 0.dp
                                )
                            ) {
                                items(s.entries, key = { it.id }) { entry ->
                                    if (!entry.isDir) {
                                        // Songs: swipe right → playlist picker | swipe left → add to queue
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                when (value) {
                                                    SwipeToDismissBoxValue.StartToEnd -> {
                                                        songToAdd = entry
                                                        showPlaylistPicker = true
                                                    }
                                                    SwipeToDismissBoxValue.EndToStart -> {
                                                        playerViewModel.addToQueue(entry)
                                                    }
                                                    else -> {}
                                                }
                                                false // always bounce back
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
                                                    // Right side: add to queue
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
                                                                "Add to queue",
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
                                                    // Left side: add to playlist
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
                                                                "Add to playlist",
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
                                                    entry = entry,
                                                    coverArtUrl = entry.coverArt?.let { viewModel.coverArtUrl(it) },
                                                    onClick = {
                                                        playerViewModel.playSong(entry)
                                                        onOpenNowPlaying()
                                                    },
                                                    onLongClick = {
                                                        contextEntry = entry
                                                        showContextMenu = true
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        // Folders: no swipe, direct row
                                        EntryRow(
                                            entry = entry,
                                            coverArtUrl = entry.coverArt?.let { viewModel.coverArtUrl(it) },
                                            onClick = { onOpenFolder(entry.id, entry.displayName) },
                                            onLongClick = {
                                                contextEntry = entry
                                                showContextMenu = true
                                            }
                                        )
                                    }
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                            }

                            if (letters.size >= 2) {
                                LetterStrip(
                                    letters = letters,
                                    activeLetter = draggingLetter,
                                    letterIndex = letterIndex,
                                    listState = listState,
                                    haptic = haptic,
                                    onDragging = { draggingLetter = it },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            }

            // Floating letter bubble during strip drag
            draggingLetter?.let { LetterBubble(letter = it) }

            if (shareState is ShareState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
    } // ModalNavigationDrawer content

    // ── Playlist picker (swipe-to-add) ───────────────────────────────────────
    if (showPlaylistPicker && songToAdd != null) {
        val song = songToAdd!!
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false; songToAdd = null },
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
                                    viewModel.addToPlaylist(song.id, playlist.name)
                                    showPlaylistPicker = false
                                    songToAdd = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    buildString {
                                        append(playlist.name)
                                        append("  ")
                                        append("(${playlist.songCount})")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false; songToAdd = null }) {
                    Text("Cancel")
                }
            }
        )
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
                            shareExpiryTarget = entry
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

    // ── Share — ask for expiry before creating the link (mStream Velvet style) ──
    shareExpiryTarget?.let { target ->
        ShareExpiryDialog(
            onConfirm = { expiryDays ->
                shareExpiryTarget = null
                viewModel.shareEntry(target, expiryDays)
            },
            onDismiss = { shareExpiryTarget = null }
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

// ── Letter strip ──────────────────────────────────────────────────────────────

/**
 * Vertical alphabetical fast-scroller overlay.
 * Touch or drag to jump the list to the first item starting with that letter.
 * Haptic feedback fires on every letter change.
 */
@Composable
private fun LetterStrip(
    letters: List<Char>,
    activeLetter: Char?,
    letterIndex: Map<Char, Int>,
    listState: LazyListState,
    haptic: HapticFeedback,
    onDragging: (Char?) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .pointerInput(letters, letterIndex) {
                var lastLetter: Char? = null

                fun processY(y: Float) {
                    if (letters.isEmpty() || size.height == 0) return
                    val idx = ((y / size.height) * letters.size).toInt()
                        .coerceIn(0, letters.lastIndex)
                    val letter = letters[idx]
                    if (letter != lastLetter) {
                        lastLetter = letter
                        onDragging(letter)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        letterIndex[letter]?.let { itemIdx ->
                            coroutineScope.launch { listState.scrollToItem(itemIdx) }
                        }
                    }
                }

                detectDragGestures(
                    onDragStart = { offset -> lastLetter = null; processY(offset.y) },
                    onDrag     = { change, _ -> processY(change.position.y) },
                    onDragEnd  = { lastLetter = null; onDragging(null) },
                    onDragCancel = { lastLetter = null; onDragging(null) }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text   = letter.toString(),
                fontSize = 10.sp,
                fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                color  = if (letter == activeLetter)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Letter bubble ─────────────────────────────────────────────────────────────

/**
 * Large centred bubble showing the current letter during a strip drag.
 */
@Composable
private fun LetterBubble(letter: Char) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
