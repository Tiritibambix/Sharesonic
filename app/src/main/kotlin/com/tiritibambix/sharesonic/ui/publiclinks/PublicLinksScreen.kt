package com.tiritibambix.sharesonic.ui.publiclinks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.api.models.VelvetShareListItem
import com.tiritibambix.sharesonic.ui.theme.textSecondary
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
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<VelvetShareListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Public Links") },
                navigationIcon = {
                    // Keep the hamburger glyph here too — reached via the drawer,
                    // tapping it again exits this screen back to the browser.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                            "Couldn't load public links",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { viewModel.load() }) { Text("Retry") }
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
                                "No public links yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Share a track or folder to create one.",
                                color = MaterialTheme.colorScheme.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(s.links, key = { it.playlistId }) { link ->
                                PublicLinkRow(
                                    link = link,
                                    url = viewModel.shareUrl(link.playlistId),
                                    onCopy = { copyToClipboard(context, viewModel.shareUrl(link.playlistId)) },
                                    onOpen = { openInBrowser(context, viewModel.shareUrl(link.playlistId)) },
                                    onDelete = { pendingDelete = link }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Revoke this link?") },
            text = {
                Text("Anyone with the link will lose access immediately. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(link.playlistId)
                    pendingDelete = null
                }) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
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
    onDelete: () -> Unit
) {
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
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Default.OpenInNew, contentDescription = "Open link")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Revoke link",
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
