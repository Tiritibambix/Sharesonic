package com.tiritibambix.sharesonic.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.api.models.TopLevelDir
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.ui.common.AlbumCardGrid
import com.tiritibambix.sharesonic.ui.components.FrostedPlaylistPicker
import com.tiritibambix.sharesonic.ui.components.FrostedSongContextMenu
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
import androidx.compose.ui.res.stringResource
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.ui.theme.textTertiary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvListFocus
import com.tiritibambix.sharesonic.utils.TvRefocusAfter
import com.tiritibambix.sharesonic.utils.TvRowShape
import com.tiritibambix.sharesonic.utils.rememberTvListFocus
import com.tiritibambix.sharesonic.utils.tvFocusRing
import com.tiritibambix.sharesonic.utils.tvTextFieldKeys
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playerViewModel: PlayerViewModel,
    settings: ServerSettings,
    onBack: () -> Unit,
    onOpenFolder: (id: String, name: String) -> Unit,
    onOpenArtistResults: (artistName: String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onShareCreated: (url: String) -> Unit
) {
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val isTV = LocalIsTV.current
    // TV: the row that had focus, restored when coming back from an artist or a
    // folder opened from the results (instead of jumping back to the field).
    val listFocus = rememberTvListFocus(isTV)
    val tvFieldFocused = rememberSaveable { mutableStateOf(false) }
    val tvRestorePending = remember { mutableStateOf(true) }

    // Hoisted overlays — a song row's "⋮" (TV) or swipe (phone) surfaces the
    // context menu; picking "Add to playlist" then hands off to the frosted
    // picker. Uses PlayerViewModel's playlists cache and add methods, matching
    // Now Playing's flow — so the same modals appear across every screen.
    var contextEntry by remember { mutableStateOf<EntryDto?>(null) }
    var playlistTarget by remember { mutableStateOf<EntryDto?>(null) }
    val playlists by playerViewModel.playlists.collectAsState()
    LaunchedEffect(playlistTarget) {
        if (playlistTarget != null) playerViewModel.loadPlaylists()
    }

    // Auto-focus the search field on entry. During the very first composition pass
    // the BasicTextField's focus target may not be attached to this FocusRequester
    // yet (it mounts once the Scaffold/Column layout settles) — calling
    // requestFocus() too early throws IllegalStateException("FocusRequester is not
    // initialized"), which was crashing this screen almost every time it opened.
    // A missed auto-focus is harmless; a crash on every search isn't — swallow the race.
    // TV: only on the first entry — returning from a result keeps focus on the
    // result row (restored in SearchResults), not the field + keyboard.
    LaunchedEffect(Unit) {
        if (!isTV || !tvFieldFocused.value) {
            tvFieldFocused.value = true
            runCatching { focusRequester.requestFocus() }
        }
    }
    // TV: put focus back on the row once its context menu / picker closes.
    TvRefocusAfter(isTV, contextEntry != null || playlistTarget != null) {
        listFocus.pendingKey = listFocus.last
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusRing(isTV, TvCircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = searchState) {
                    is SearchState.Idle -> {
                        Text(
                            stringResource(R.string.search_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.textTertiary
                        )
                    }
                    is SearchState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is SearchState.Error -> {
                        Text(
                            s.message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is SearchState.Results -> {
                        SearchResults(
                            result = s.result,
                            settings = settings,
                            viewModel = viewModel,
                            playerViewModel = playerViewModel,
                            onOpenFolder = onOpenFolder,
                            onOpenArtistResults = onOpenArtistResults,
                            onOpenNowPlaying = onOpenNowPlaying,
                            onShowContextMenu = { contextEntry = it },
                            listFocus = listFocus,
                            tvRestorePending = tvRestorePending
                        )
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

    // ── Playlist picker — mirrors the Now Playing / FolderBrowser flow ────
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

/**
 * A compact, pill-shaped search field — Material You "search bar" styling rather
 * than a generic [OutlinedTextField] crammed into the TopAppBar's title slot
 * (which clipped/overflowed because the field's intrinsic height exceeds the
 * app bar's content area). Living in the screen body, it has room to breathe.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val isTV = LocalIsTV.current
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.textSecondary
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .tvTextFieldKeys(isTV)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        // TV: the button disappears with the text — hand focus to
                        // the field instead of dropping it.
                        if (isTV) runCatching { focusRequester.requestFocus() }
                    },
                    modifier = Modifier.tvFocusRing(isTV, TvCircleShape).size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.common_close),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.textSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResults(
    result: SearchResult3,
    settings: ServerSettings,
    viewModel: SearchViewModel,
    playerViewModel: PlayerViewModel,
    onOpenFolder: (id: String, name: String) -> Unit,
    onOpenArtistResults: (artistName: String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onShowContextMenu: (EntryDto) -> Unit,
    listFocus: TvListFocus,
    tvRestorePending: MutableState<Boolean>,
) {
    val isTV = LocalIsTV.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // TV: ignore repeated OK presses while an artist's tracks are loading (each
    // press would otherwise open the artist screen once more).
    var artistLoading by remember { mutableStateOf(false) }
    val totalCount = result.folder.size + result.song.size + result.album.size + result.artist.size
    if (totalCount == 0) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "No results",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.textTertiary
            )
        }
        return
    }

    // ── Category tabs — mirrors the Velvet webapp's search tab bar ─────────
    // "All" + one chip per category that has results, with counts, Tracks first.
    // Shown only when at least two categories matched; a remembered tab that has
    // nothing in this result set falls back to All.
    val selectedTab by viewModel.tab.collectAsState()
    val categories = buildList {
        if (result.song.isNotEmpty())   add(Triple(SearchTab.TRACKS,  R.string.search_section_songs,   result.song.size))
        if (result.folder.isNotEmpty()) add(Triple(SearchTab.FOLDERS, R.string.search_section_folders, result.folder.size))
        if (result.artist.isNotEmpty()) add(Triple(SearchTab.ARTISTS, R.string.search_section_artists, result.artist.size))
        if (result.album.isNotEmpty())  add(Triple(SearchTab.ALBUMS,  R.string.search_section_albums,  result.album.size))
    }
    val showTabs = categories.size > 1
    val tab = if (showTabs && categories.any { it.first == selectedTab }) selectedTab else SearchTab.ALL
    fun shows(t: SearchTab) = tab == SearchTab.ALL || tab == t

    if (isTV) {
        // LazyColumn item keys in display order (headers and the album grid
        // included) so a row key maps to its item index for scroll + focus.
        val lazyKeys = buildList {
            if (result.folder.isNotEmpty() && shows(SearchTab.FOLDERS)) {
                add("hdr_folders"); result.folder.indices.forEach { add("folder_$it") }
            }
            if (result.artist.isNotEmpty() && shows(SearchTab.ARTISTS)) {
                add("hdr_artists"); result.artist.indices.forEach { add("artist_$it") }
            }
            if (result.album.isNotEmpty() && shows(SearchTab.ALBUMS)) {
                add("hdr_albums"); add("albums_grid")
            }
            if (result.song.isNotEmpty() && shows(SearchTab.TRACKS)) {
                add("hdr_songs"); result.song.indices.forEach { add("song_$it") }
            }
        }
        // Back from an artist / folder: focus the row it was opened from.
        LaunchedEffect(Unit) {
            if (tvRestorePending.value) {
                tvRestorePending.value = false
                listFocus.last?.let { key -> listFocus.focus(key, listState) { lazyKeys.indexOf(it) } }
            }
        }
        // A menu or picker closed: focus the row again.
        LaunchedEffect(listFocus.pendingKey) {
            val key = listFocus.pendingKey ?: return@LaunchedEffect
            listFocus.focus(key, listState) { lazyKeys.indexOf(it) }
            listFocus.pendingKey = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
    if (showTabs) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = tab == SearchTab.ALL,
                onClick = { viewModel.selectTab(SearchTab.ALL) },
                label = { Text(stringResource(R.string.search_tab_all)) },
                modifier = Modifier.tvFocusRing(isTV, TvRowShape)
            )
            categories.forEach { (t, labelRes, count) ->
                FilterChip(
                    selected = tab == t,
                    onClick = { viewModel.selectTab(t) },
                    label = { Text("${stringResource(labelRes)} ($count)") },
                    modifier = Modifier.tvFocusRing(isTV, TvRowShape)
                )
            }
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {

        // ── Folders ──────────────────────────────────────────────────────
        // Real on-disk folders whose name matched the query. This is the
        // precise, server-provided navigation path (browse_path) — exactly what
        // the Velvet webapp surfaces as its "Folders" section — so tapping one
        // opens the correct folder with zero client-side path guessing.
        if (result.folder.isNotEmpty() && shows(SearchTab.FOLDERS)) {
            item { SectionHeader(stringResource(R.string.search_section_folders)) }
            itemsIndexed(result.folder, key = { idx, _ -> "folder_$idx" }) { idx, folder ->
                FolderRow(
                    name = folder.displayName,
                    onClick = { onOpenFolder(folder.id, folder.displayName) },
                    tvFocusModifier = listFocus.itemModifier("folder_$idx")
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // ── Artists ──────────────────────────────────────────────────────
        if (result.artist.isNotEmpty() && shows(SearchTab.ARTISTS)) {
            item {
                SectionHeader(stringResource(R.string.search_section_artists))
            }
            itemsIndexed(result.artist, key = { idx, _ -> "artist_$idx" }) { idx, artist ->
                ArtistRow(
                    artist = artist,
                    tvFocusModifier = listFocus.itemModifier("artist_$idx"),
                    onClick = click@{
                        if (isTV && artistLoading) return@click
                        artistLoading = isTV
                        // Match the Velvet webapp's viewArtistProfile: tapping an artist
                        // shows that artist's own tracks (exact tag match via
                        // artist-folder-songs, including featuring/variant tags), never a
                        // guessed folder. Folder navigation lives in the Folders section
                        // above. This also fixes the "featuring artist opens a huge shared
                        // folder where you can't find the track" problem — the track is
                        // listed directly and plays on tap.
                        coroutineScope.launch {
                            try {
                                val songs = viewModel.fetchArtistSongsRaw(artist.name, artist.variants).orEmpty()
                                viewModel.setArtistResults(songs)
                                onOpenArtistResults(artist.name)
                            } finally {
                                artistLoading = false
                            }
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // ── Albums ───────────────────────────────────────────────────────
        // Grid of rounded cards — same layout as the Velvet webapp's album
        // grid — rather than the flat list the other sections use. Albums are
        // a visual result set (cover art is the primary signifier), the
        // others are text-first.
        if (result.album.isNotEmpty() && shows(SearchTab.ALBUMS)) {
            item { SectionHeader(stringResource(R.string.search_section_albums)) }
            item {
                AlbumCardGrid(
                    items = result.album,
                    key = { it.id },
                    title = { it.displayName },
                    subtitle = { it.artist },
                    coverArtUrl = { it.coverArt?.let { c -> nativeCoverArtUrl(settings, c) } },
                    onClick = { onOpenFolder(it.id, it.displayName) },
                )
            }
        }

        // ── Songs ────────────────────────────────────────────────────────
        // Phone: swipe right → add to playlist, swipe left → add to queue
        //       (mirrors FolderBrowser). Long-press → context menu.
        // TV:    ⋮ button → context menu with Play / Add to queue / Add to playlist.
        if (result.song.isNotEmpty() && shows(SearchTab.TRACKS)) {
            item { SectionHeader(stringResource(R.string.search_section_songs)) }
            itemsIndexed(result.song, key = { idx, _ -> "song_$idx" }) { idx, song ->
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
                            onLongClick = { onShowContextMenu(song) },
                            onShowMenu = { onShowContextMenu(song) },
                            tvFocusModifier = listFocus.itemModifier("song_$idx")
                        )
                    }
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> onShowContextMenu(song)
                                SwipeToDismissBoxValue.EndToStart -> playerViewModel.addToQueue(song)
                                else -> {}
                            }
                            false // bounce back — trigger only
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
                                onLongClick = { onShowContextMenu(song) },
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit, tvFocusModifier: Modifier = Modifier) {
    val isTV = LocalIsTV.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tvFocusModifier)
            .tvFocusRing(isTV, TvRowShape, 1.02f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(4.dp)
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistRow(artist: TopLevelDir, onClick: () -> Unit, tvFocusModifier: Modifier = Modifier) {
    val isTV = LocalIsTV.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tvFocusModifier)
            .tvFocusRing(isTV, TvRowShape, 1.02f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(4.dp)
        )
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryRow(
    entry: EntryDto,
    coverArtUrl: String?,
    isAlbum: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** On TV, tapping this shows the context menu (replaces swipe / long-press). */
    onShowMenu: (() -> Unit)? = null,
    /** TV: focus bookkeeping for the row's main area (see TvListFocus). */
    tvFocusModifier: Modifier = Modifier,
) {
    if (LocalIsTV.current) {
        // TV: the row itself must not be clickable — D-pad focus never enters a
        // focused node's children, so the "⋮" would be unreachable. The main
        // area is the clickable and the "⋮" its sibling (Right reaches it).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val main = Modifier
                .weight(1f)
                .then(tvFocusModifier)
                .tvFocusRing(true, TvRowShape, 1.02f)
                .clip(TvRowShape)
            Row(
                modifier = (if (onLongClick != null) main.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                            else main.clickable(onClick = onClick))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntryRowContent(entry, coverArtUrl, isAlbum)
            }
            if (onShowMenu != null) {
                IconButton(
                    onClick = onShowMenu,
                    modifier = Modifier.tvFocusRing(true, TvCircleShape).size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.common_more),
                        tint = MaterialTheme.colorScheme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        return
    }
    val rowModifier = if (onLongClick != null) {
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    }
    Row(
        modifier = rowModifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntryRowContent(entry, coverArtUrl, isAlbum)
        // "⋮" button opens the context menu — replaces swipe / long-press.
        if (onShowMenu != null) {
            IconButton(onClick = onShowMenu, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.common_more),
                    tint = MaterialTheme.colorScheme.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Art + title/subtitle + duration: the content shared by phone and TV rows. */
@Composable
private fun RowScope.EntryRowContent(entry: EntryDto, coverArtUrl: String?, isAlbum: Boolean) {
    if (coverArtUrl != null) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
        )
    } else {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                imageVector = if (isAlbum) Icons.Default.Album else Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.textSecondary
            )
        }
    }
    Column(modifier = Modifier.weight(1f)) {
        Text(
            entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val sub = buildString {
            entry.artist?.let { append(it) }
            if (!entry.artist.isNullOrBlank() && !entry.album.isNullOrBlank()) append("  ·  ")
            if (!isAlbum) entry.album?.let { append(it) }
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
    if (!isAlbum) {
        entry.duration?.let {
            Text(
                formatDuration(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textTertiary
            )
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/** Build a native album-art URL. Search results come from the native API so
 *  coverArt is always a cache filename (e.g. "abc123.jpg"), never a Subsonic ID. */
internal fun nativeCoverArtUrl(settings: ServerSettings, filename: String): String =
    "${settings.serverUrl.trimEnd('/')}/album-art/$filename?token=${settings.jwtToken}"
