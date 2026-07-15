package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tiritibambix.sharesonic.MainActivity
import com.tiritibambix.sharesonic.R
import kotlinx.coroutines.flow.first

class SharesonicWidget : GlanceAppWidget() {

    // Two responsive buckets — one compact (roughly 2×1), one full (3×2 and up).
    // Below the compact threshold, Glance falls back to the compact bucket.
    companion object {
        private val COMPACT_SIZE = DpSize(180.dp, 90.dp)
        private val FULL_SIZE    = DpSize(260.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, FULL_SIZE))
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WidgetStateRepository(context)
        val snapshot = repo.snapshot.first()
        val bitmap = loadCoverArtBitmap(context, snapshot.coverArtUrl)
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot = snapshot, coverBitmap = bitmap)
            }
        }
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetSnapshot, coverBitmap: android.graphics.Bitmap?) {
        val size = LocalSize.current
        val compact = size.width < FULL_SIZE.width
        val openApp = actionStartActivity<MainActivity>()

        // No outer clickable — Glance's nested-click routing is unreliable, so
        // tap-to-open is restricted to the cover art and the title stack (the
        // areas where a "tap" naturally reads as "open the full player").
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(10.dp)
        ) {
            if (compact) CompactLayout(snapshot, coverBitmap, openApp)
            else         FullLayout(snapshot, coverBitmap, openApp)
        }
    }

    // ── Layouts ────────────────────────────────────────────────────────────

    /** ~2×1: cover + transport + AutoDJ. No rating (not enough room). */
    @Composable
    private fun CompactLayout(
        snapshot: WidgetSnapshot,
        coverBitmap: android.graphics.Bitmap?,
        openApp: Action,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(coverBitmap, sizeDp = 56, onClick = openApp)
            Spacer(GlanceModifier.width(10.dp))
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackText(snapshot, onClick = openApp)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransportRow(isPlaying = snapshot.isPlaying, size = 34)
                    Spacer(GlanceModifier.width(6.dp))
                    AutoDjToggle(enabled = snapshot.autoDjEnabled, size = 34)
                }
            }
        }
    }

    /** ~3×2 and up: cover + full track info + transport + AutoDJ + rating stars. */
    @Composable
    private fun FullLayout(
        snapshot: WidgetSnapshot,
        coverBitmap: android.graphics.Bitmap?,
        openApp: Action,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(coverBitmap, sizeDp = 84, onClick = openApp)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                TrackText(snapshot, onClick = openApp)
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransportRow(isPlaying = snapshot.isPlaying, size = 38)
                    Spacer(GlanceModifier.width(8.dp))
                    AutoDjToggle(enabled = snapshot.autoDjEnabled, size = 38)
                }
                if (snapshot.filepath != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    RatingRow(rating = snapshot.rating, starSize = 26)
                }
            }
        }
    }

    // ── Pieces ─────────────────────────────────────────────────────────────

    @Composable
    private fun TrackText(snapshot: WidgetSnapshot, onClick: Action) {
        Column(modifier = GlanceModifier.fillMaxWidth().clickable(onClick)) {
            Text(
                text = snapshot.title?.takeIf { it.isNotBlank() } ?: "—",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            snapshot.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun TransportRow(isPlaying: Boolean, size: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                iconRes = R.drawable.ic_widget_prev,
                contentDescription = "Previous",
                onClickAction = actionRunCallback<PrevCallback>(),
                size = size,
            )
            Spacer(GlanceModifier.width(2.dp))
            IconButton(
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClickAction = actionRunCallback<PlayPauseCallback>(),
                size = size + 6,
                filled = true,
            )
            Spacer(GlanceModifier.width(2.dp))
            IconButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = "Next",
                onClickAction = actionRunCallback<NextCallback>(),
                size = size,
            )
        }
    }

    @Composable
    private fun RatingRow(rating: Int, starSize: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                val filled = star <= rating
                IconButton(
                    iconRes = if (filled) R.drawable.ic_widget_star_filled else R.drawable.ic_widget_star_outline,
                    contentDescription = "$star star${if (star > 1) "s" else ""}",
                    onClickAction = actionRunCallback<RateCallback>(
                        actionParametersOf(RateCallback.StarsKey to star),
                    ),
                    size = starSize,
                    // Filled stars get a subtle primary tint on top of the same
                    // filled asset so on/off contrast is unmistakable.
                    tintFilled = filled,
                )
            }
        }
    }

    /**
     * Auto-DJ pill. Filled-primary background when ON, hollow border-only when
     * OFF — the visual difference is deliberately loud so a glance tells the
     * user which mode they're in (the previous subtler tint-swap wasn't enough).
     */
    @Composable
    private fun AutoDjToggle(enabled: Boolean, size: Int) {
        val bg = if (enabled) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
        val fg = if (enabled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant
        Box(
            modifier = GlanceModifier
                .size(size.dp)
                .cornerRadius((size / 2).dp)
                .background(bg)
                .clickable(actionRunCallback<ToggleAutoDjCallback>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_autodj),
                contentDescription = if (enabled) "Auto-DJ on" else "Auto-DJ off",
                modifier = GlanceModifier.size((size * 5 / 8).dp),
                colorFilter = ColorFilter.tint(fg),
            )
        }
    }

    @Composable
    private fun CoverThumb(bitmap: android.graphics.Bitmap?, sizeDp: Int, onClick: Action) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius(8.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = null,
                    modifier = GlanceModifier.size((sizeDp / 2).dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    /**
     * Generic icon button. When [filled] is true the button has a primary
     * background — used for the play/pause target so it stands out from
     * prev/next. When [tintFilled] is true the icon is tinted with `primary`
     * (used for the "on" star to make filled vs empty visually loud).
     *
     * Each button gets its OWN background — a Box with a transparent
     * background may not register as a click target in some Glance / launcher
     * combinations, hence the `.background` call even when we want it plain.
     */
    @Composable
    private fun IconButton(
        iconRes: Int,
        contentDescription: String,
        onClickAction: Action,
        size: Int,
        filled: Boolean = false,
        tintFilled: Boolean = false,
    ) {
        val bg = if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
        val fg = when {
            filled     -> GlanceTheme.colors.onPrimary
            tintFilled -> GlanceTheme.colors.primary
            else       -> GlanceTheme.colors.onSurfaceVariant
        }
        Box(
            modifier = GlanceModifier
                .size(size.dp)
                .cornerRadius((size / 2).dp)
                .background(bg)
                .clickable(onClickAction),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size((size * 9 / 16).dp),
                colorFilter = ColorFilter.tint(fg),
            )
        }
    }
}
