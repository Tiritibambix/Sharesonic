package com.tiritibambix.sharesonic.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Rounded album card, mirrored from mStream's album_grid.dart:
 *   - 12 dp corner radius on the whole card, art clipped to the top corners
 *   - square art, then a tight 8/6/8 dp text block
 *   - two lines: title (12 sp semibold, primary text) + optional subtitle
 *
 * Meant for the grid on the Search screen's Albums section and any future
 * album/artist landing pages. Not used in the folder browser — that stays
 * list-mode by design.
 */
@Composable
fun AlbumCard(
    title: String,
    subtitle: String?,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                if (coverArtUrl != null) {
                    AsyncImage(
                        model = coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.textSecondary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders [items] as rows of [AlbumCard] fitted to the available width. Column
 * count adapts to width: 2 under 400 dp, 3 under 600 dp, otherwise 4. Meant to
 * be dropped into a `LazyColumn` item — it lays out the whole grid at once, so
 * it's fine for search-result-sized lists (usually < 40 albums) but not for
 * the full library.
 */
@Composable
fun <T> AlbumCardGrid(
    items: List<T>,
    key: (T) -> Any,
    title: (T) -> String,
    subtitle: (T) -> String?,
    coverArtUrl: (T) -> String?,
    onClick: (T) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    spacing: Dp = 12.dp,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
        val cols = when {
            maxWidth > 600.dp -> 4
            maxWidth > 400.dp -> 3
            else -> 2
        }
        val cellWidth = (maxWidth - spacing * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { it ->
                        AlbumCard(
                            title = title(it),
                            subtitle = subtitle(it),
                            coverArtUrl = coverArtUrl(it),
                            onClick = { onClick(it) },
                            modifier = Modifier.width(cellWidth),
                        )
                        // Keeps key wired up for potential future migration to LazyVerticalGrid.
                        @Suppress("UNUSED_EXPRESSION") key(it)
                    }
                    // Pad the last row with empty cells so cards keep their width.
                    val missing = cols - row.size
                    repeat(missing) { Spacer(Modifier.width(cellWidth)) }
                }
            }
        }
    }
}
