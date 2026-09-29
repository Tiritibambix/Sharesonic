package com.tiritibambix.sharesonic.ui.publiclinks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.api.models.VelvetShareListItem
import com.tiritibambix.sharesonic.ui.components.QrCode
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvListFocusEffect
import com.tiritibambix.sharesonic.utils.TvPillShape
import com.tiritibambix.sharesonic.utils.rememberTvListFocus
import com.tiritibambix.sharesonic.utils.tvFocusRing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Public Links management — mirrors Velvet's share-management panel:
 * lists every link created via the native share API, with copy / open / revoke actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicLinksScreen(
    viewModel: PublicLinksViewModel,
    onBack: () -> Unit,
    miniPlayerVisible: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<VelvetShareListItem?>(null) }
    // TV: no browser to open a link in (and TV apps must not launch one —
    // quality criterion TV-WB), so a row offers its QR code instead.
    val isTV = LocalIsTV.current
    var qrUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val listFocus = rememberTvListFocus(isTV)
    val linkKeys = (state as? PublicLinksState.Ready)?.links?.map { it.playlistId }.orEmpty()
    TvListFocusEffect(listFocus, listState, keys = linkKeys)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.links_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusRing(isTV, TvCircleShape)) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_menu))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val s = state) {
                is PublicLinksState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PublicLinksState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.links_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { viewModel.load() },
                            modifier = Modifier.tvFocusRing(isTV, TvPillShape)
                        ) { Text(stringResource(R.string.common_more)) }
                    }
                }
                is PublicLinksState.Ready -> {
                    if (s.links.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                stringResource(R.string.links_empty),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(listFocus.listModifier { linkKeys }),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = if (miniPlayerVisible) 88.dp else 8.dp,
                            )
                        ) {
                            itemsIndexed(s.links, key = { _, l -> l.playlistId }) { idx, link ->
                                PublicLinkRow(
                                    link = link,
                                    url = viewModel.shareUrl(link.playlistId),
                                    onCopy = {
                                        copyToClipboard(context, viewModel.shareUrl(link.playlistId))
                                        if (isTV) Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
                                    },
                                    onOpen = { openInBrowser(context, viewModel.shareUrl(link.playlistId)) },
                                    onDelete = { pendingDelete = link },
                                    onShowQr = if (isTV) ({ qrUrl = viewModel.shareUrl(link.playlistId) }) else null,
                                    tvFocusModifier = listFocus.itemModifier(link.playlistId, idx)
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    // TV: the link's QR code, to open it on a phone.
    qrUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { qrUrl = null },
            title = { Text(stringResource(R.string.links_qr)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QrCode(url, size = 220.dp)
                    Text(
                        stringResource(R.string.share_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.textSecondary
                    )
                    Text(url, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { qrUrl = null },
                    modifier = Modifier.tvFocusRing(true, TvPillShape)
                ) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    pendingDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.links_revoke)) },
            text = {
                Text(stringResource(R.string.playlists_delete_message, link.playlistId))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(link.playlistId)
                        pendingDelete = null
                    },
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape)
                ) { Text(stringResource(R.string.links_revoke)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.tvFocusRing(isTV, TvPillShape)
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun PublicLinkRow(
    link: VelvetShareListItem,
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    /** TV: replaces Open — shows the link's QR code. */
    onShowQr: (() -> Unit)? = null,
    tvFocusModifier: Modifier = Modifier,
) {
    val isTV = onShowQr != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${link.songCount} ${if (link.songCount == 1) "track" else "tracks"} · ${formatExpiry(link.expires)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.textSecondary
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier.then(tvFocusModifier).tvFocusRing(isTV, TvCircleShape)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.links_copy))
        }
        if (onShowQr != null) {
            IconButton(onClick = onShowQr, modifier = Modifier.tvFocusRing(true, TvCircleShape)) {
                Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.links_qr))
            }
        } else {
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.links_open))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.tvFocusRing(isTV, TvCircleShape)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.links_revoke),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Formats a Unix-seconds expiry timestamp as Velvet does — date or "Permanent"/"Expired". */
private fun formatExpiry(expiresEpochSeconds: Long?): String {
    if (expiresEpochSeconds == null) return "Permanent"
    val instant = Instant.ofEpochSecond(expiresEpochSeconds)
    if (instant.isBefore(Instant.now())) return "Expired"
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return "Expires ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("share_url", text))
}

private fun openInBrowser(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
