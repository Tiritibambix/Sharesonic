package com.tiritibambix.sharesonic.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tiritibambix.sharesonic.ui.player.PlayerViewModel
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

    // Auto-focus the search field on entry. During the very first composition pass
    // the BasicTextField's focus target may not be attached to this FocusRequester
    // yet (it mounts once the Scaffold/Column layout settles) — calling
    // requestFocus() too early throws IllegalStateException("FocusRequester is not
    // initialized"), which was crashing this screen almost every time it opened.
    // A missed auto-focus is harmless; a crash on every search isn't — swallow the race.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            "Type to search your library",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            onOpenNowPlaying = onOpenNowPlaying
                        )
                    }
                }
            }
        }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search songs, albums, artists…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    result: SearchResult3,
    settings: ServerSettings,
    viewModel: SearchViewModel,
    playerViewModel: PlayerViewModel,
    onOpenFolder: (id: String, name: String) -> Unit,
    onOpenArtistResults: (artistName: String) -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val totalCount = result.folder.size + result.song.size + result.album.size + result.artist.size
    if (totalCount == 0) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "No results",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Folders ──────────────────────────────────────────────────────
        // Real on-disk folders whose name matched the query. This is the
        // precise, server-provided navigation path (browse_path) — exactly what
        // the Velvet webapp surfaces as its "Folders" section — so tapping one
        // opens the correct folder with zero client-side path guessing.
        if (result.folder.isNotEmpty()) {
            item { SectionHeader("Folders") }
            itemsIndexed(result.folder, key = { idx, _ -> "folder_$idx" }) { _, folder ->
                FolderRow(
                    name = folder.displayName,
                    onClick = { onOpenFolder(folder.id, folder.displayName) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // ── Artists ──────────────────────────────────────────────────────
        if (result.artist.isNotEmpty()) {
            item {
                SectionHeader("Artists")
            }
            itemsIndexed(result.artist, key = { idx, _ -> "artist_$idx" }) { _, artist ->
                ArtistRow(
                    artist = artist,
                    onClick = {
                        coroutineScope.launch {
                            // Tier 1: server-authoritative resolution — every song the
                            // server has for this artist tag (+ raw tag variants) comes
                            // back with a verified real filepath, so the derived folder
                            // can never be a wrong-depth guess (see SearchViewModel).
                            val resolved = viewModel.resolveArtistFolderAuthoritative(artist.name, artist.variants)
                            if (resolved != null) {
                                onOpenFolder(resolved, artist.name)
                                return@launch
                            }
                            // Tier 2: scan every known vpath for a matching subfolder —
                            // only ever returns paths observed directly via file-explorer.
                            val viaVpathScan = viewModel.resolveArtistFolder(artist.name)
                            if (viaVpathScan != null) {
                                onOpenFolder(viaVpathScan, artist.name)
                                return@launch
                            }
                            // Tier 3: no real on-disk folder found anywhere — show the
                            // dedicated results screen, fed by the same server-verified
                            // song list Tier 1 already fetched (if any).
                            val songs = viewModel.fetchArtistSongsRaw(artist.name, artist.variants).orEmpty()
                            viewModel.setArtistResults(songs)
                            onOpenArtistResults(artist.name)
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // ── Albums ───────────────────────────────────────────────────────
        if (result.album.isNotEmpty()) {
            item { SectionHeader("Albums") }
            itemsIndexed(result.album, key = { idx, _ -> "album_$idx" }) { _, album ->
                EntryRow(
                    entry = album,
                    coverArtUrl = album.coverArt?.let { nativeCoverArtUrl(settings, it) },
                    isAlbum = true,
                    onClick = { onOpenFolder(album.id, album.displayName) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // ── Songs ────────────────────────────────────────────────────────
        if (result.song.isNotEmpty()) {
            item { SectionHeader("Songs") }
            itemsIndexed(result.song, key = { idx, _ -> "song_$idx" }) { _, song ->
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
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
private fun ArtistRow(artist: TopLevelDir, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

@Composable
internal fun EntryRow(
    entry: EntryDto,
    coverArtUrl: String?,
    isAlbum: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
