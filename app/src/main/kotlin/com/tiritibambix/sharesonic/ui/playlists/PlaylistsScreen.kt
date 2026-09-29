package com.tiritibambix.sharesonic.ui.playlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.ui.components.FrostedConfirmDialog
import com.tiritibambix.sharesonic.ui.components.FrostedShareExpiryDialog
import com.tiritibambix.sharesonic.ui.components.FrostedTextPromptDialog
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvListFocusEffect
import com.tiritibambix.sharesonic.utils.TvRefocusAfter
import com.tiritibambix.sharesonic.utils.TvRowShape
import com.tiritibambix.sharesonic.utils.rememberTvListFocus
import com.tiritibambix.sharesonic.utils.tvFocusRing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (name: String) -> Unit,
    onShareCreated: (url: String) -> Unit,
    miniPlayerVisible: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val shareState by viewModel.shareState.collectAsState()

    // Push FAB and list bottom padding up when the mini player bar is visible
    val fabBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 68.dp else 0.dp,
        label = "fabBottomPadding"
    )
    val listBottomPadding by animateDpAsState(
        targetValue = if (miniPlayerVisible) 156.dp else 80.dp,
        label = "listBottomPadding"
    )

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NativePlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<NativePlaylist?>(null) }
    var shareTarget by remember { mutableStateOf<NativePlaylist?>(null) }

    // Same frosted-glass pattern as the folder browser: blur the Scaffold
    // whenever any modal is open, so the panel visually sits above the list.
    val anyModalOpen = showCreateDialog || renameTarget != null ||
        deleteTarget != null || shareTarget != null
    val contentBlur by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (anyModalOpen) 18.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "contentBlur"
    )

    LaunchedEffect(shareState) {
        val s = shareState
        if (s is PlaylistShareState.Done) {
            onShareCreated(s.url)
            viewModel.clearShareState()
        }
    }

    // TV: first row focused on entry, the opened playlist again on return, and
    // focus back on its row after a rename / share / delete prompt closes.
    val isTV = LocalIsTV.current
    val listState = rememberLazyListState()
    val listFocus = rememberTvListFocus(isTV)
    val playlistKeys = (state as? PlaylistsState.Ready)?.playlists?.map { it.name }.orEmpty()
    TvListFocusEffect(listFocus, listState, keys = playlistKeys)
    TvRefocusAfter(isTV, anyModalOpen) { listFocus.pendingKey = listFocus.last }

    Scaffold(
        modifier = Modifier.blur(contentBlur),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusRing(isTV, TvCircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // TV: the create FAB is only reachable after scrolling past the
                    // whole list, so "New playlist" lives in the top bar instead.
                    if (isTV) {
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.tvFocusRing(true, TvCircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playlists_new_title))
                        }
                    }
                    IconButton(onClick = { viewModel.load() }, modifier = Modifier.tvFocusRing(isTV, TvCircleShape)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isTV) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playlists_new_title))
                    }
                    Spacer(modifier = Modifier.height(fabBottomPadding))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is PlaylistsState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PlaylistsState.Error -> {
                    Text(
                        s.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is PlaylistsState.Ready -> {
                    if (s.playlists.isEmpty()) {
                        Text(
                            stringResource(R.string.playlists_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.textSecondary
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(listFocus.listModifier { playlistKeys }),
                            contentPadding = PaddingValues(bottom = listBottomPadding)
                        ) {
                            itemsIndexed(s.playlists, key = { _, p -> p.name }) { idx, playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.name) },
                                    onRename = { renameTarget = playlist },
                                    onDelete = { deleteTarget = playlist },
                                    onShare  = { shareTarget = playlist },
                                    tvFocusModifier = listFocus.itemModifier(playlist.name, idx)
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Create dialog (frosted) ────────────────────────────────────────────
    if (showCreateDialog) {
        FrostedTextPromptDialog(
            title = stringResource(R.string.playlists_new_title),
            label = stringResource(R.string.playlists_name_label),
            confirmLabel = stringResource(R.string.playlists_create),
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // ── Rename dialog (frosted) ────────────────────────────────────────────
    renameTarget?.let { target ->
        FrostedTextPromptDialog(
            title = stringResource(R.string.playlists_rename_title),
            label = stringResource(R.string.playlists_new_name),
            confirmLabel = stringResource(R.string.common_rename),
            initialValue = target.name,
            onConfirm = { name ->
                viewModel.renamePlaylist(target.name, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // ── Delete confirmation (frosted) ──────────────────────────────────────
    deleteTarget?.let { target ->
        FrostedConfirmDialog(
            title = stringResource(R.string.playlists_delete_title),
            message = stringResource(R.string.playlists_delete_message, target.name),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                viewModel.deletePlaylist(target.name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    // ── Share expiry prompt (frosted, matches folder / queue shares) ──────
    shareTarget?.let { target ->
        FrostedShareExpiryDialog(
            onConfirm = { expiryDays ->
                viewModel.sharePlaylist(target.name, expiryDays)
                shareTarget = null
            },
            onDismiss = { shareTarget = null }
        )
    }

    // Errors on share are surfaced via a simple dialog for now — success
    // routes through onShareCreated → ShareConfirm.
    (shareState as? PlaylistShareState.Error)?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearShareState() },
            title = { Text(stringResource(R.string.common_share)) },
            text = { Text(err.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearShareState() }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: NativePlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    /** TV: focus bookkeeping for the row's main area (see TvListFocus). */
    tvFocusModifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isTV = LocalIsTV.current

    if (isTV) {
        // TV: the row itself must not be clickable — D-pad focus never enters a
        // focused node's children, so the "⋮" would be unreachable. The main
        // area opens the playlist, the "⋮" (its sibling) opens the menu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(tvFocusModifier)
                    .tvFocusRing(true, TvRowShape, 1.02f)
                    .clip(TvRowShape)
                    .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaylistRowContent(playlist)
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.tvFocusRing(true, TvCircleShape).size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.common_more),
                    modifier = Modifier.size(20.dp)
                )
            }
            PlaylistRowMenu(showMenu, { showMenu = false }, onRename, onShare, onDelete)
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaylistRowContent(playlist)
        PlaylistRowMenu(showMenu, { showMenu = false }, onRename, onShare, onDelete)
    }
}

/** Icon + name/count + chevron: the content shared by phone and TV rows. */
@Composable
private fun RowScope.PlaylistRowContent(playlist: NativePlaylist) {
    Icon(
        Icons.Default.QueueMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp).padding(4.dp)
    )
    Column(modifier = Modifier.weight(1f)) {
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.playlists_song_count, playlist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.textSecondary
        )
    }
    Icon(
        Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.textSecondary
    )
}

/** Rename / Share / Delete menu, opened by long-press (phone) or "⋮" (TV). */
@Composable
private fun PlaylistRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val isTV = LocalIsTV.current
    val itemModifier = Modifier.tvFocusRing(isTV, TvRowShape, 1f)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_rename)) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = { onDismiss(); onRename() },
            modifier = itemModifier
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_share)) },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare() },
            modifier = itemModifier
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = { onDismiss(); onDelete() },
            modifier = itemModifier
        )
    }
}
