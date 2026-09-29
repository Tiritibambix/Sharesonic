package com.tiritibambix.sharesonic.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.components.QrCode
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvInitialFocus
import com.tiritibambix.sharesonic.utils.TvPillShape
import com.tiritibambix.sharesonic.utils.tvFocusRing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareConfirmScreen(
    shareUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    if (LocalIsTV.current) {
        ShareConfirmTv(shareUrl, onBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_confirm_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            Text(
                stringResource(R.string.share_confirm_title),
                style = MaterialTheme.typography.titleLarge
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = shareUrl,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { copyToClipboard(context, shareUrl) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_copy))
                }

                Button(
                    onClick = { shareViaAndroid(context, shareUrl) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_send))
                }
            }
        }
    }
}

/**
 * TV version: a TV usually has no app to share text with (and must not open a
 * browser — TV quality criterion TV-WB), so the link is shown large with a QR
 * code to scan from a phone. "Send" only appears when something can actually
 * receive the link; "Copy" confirms with a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareConfirmTv(shareUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val canSend = remember { hasShareTarget(context) }
    val firstButton = remember { FocusRequester() }
    TvInitialFocus(true, firstButton)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_confirm_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusRing(true, TvCircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrCode(shareUrl, size = 240.dp)
            Column(
                modifier = Modifier.weight(1f, fill = false).widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    stringResource(R.string.share_scan_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.textSecondary
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = shareUrl,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, shareUrl)
                            Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.focusRequester(firstButton).tvFocusRing(true, TvPillShape)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_copy))
                    }
                    if (canSend) {
                        Button(
                            onClick = { shareViaAndroid(context, shareUrl) },
                            modifier = Modifier.tvFocusRing(true, TvPillShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.share_send))
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("share_url", text))
}

private fun shareIntent(url: String) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, url)
}

/** Whether any installed app can receive shared text (often none on a TV). */
private fun hasShareTarget(context: Context): Boolean =
    context.packageManager.queryIntentActivities(shareIntent(""), 0).isNotEmpty()

private fun shareViaAndroid(context: Context, url: String) {
    try {
        context.startActivity(Intent.createChooser(shareIntent(url), "Share via"))
    } catch (_: ActivityNotFoundException) {
        // No chooser / target on this device (typical on TV): nothing to do —
        // the link is on screen and can be copied or scanned.
    }
}
