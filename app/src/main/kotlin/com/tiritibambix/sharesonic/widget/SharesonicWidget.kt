package com.tiritibambix.sharesonic.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.LocalContext
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
import androidx.glance.appwidget.state.getAppWidgetState
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tiritibambix.sharesonic.MainActivity
import com.tiritibambix.sharesonic.R

class SharesonicWidget : GlanceAppWidget() {

    // Two buckets. COMPACT drops the rating row (no vertical room). FULL shows
    // everything. Glance falls back to COMPACT below the FULL width threshold.
    companion object {
        private val COMPACT_SIZE = DpSize(200.dp, 100.dp)
        private val FULL_SIZE    = DpSize(280.dp, 160.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, FULL_SIZE))

    // Native Glance state: the widget content is driven by a per-instance
    // Preferences store that pushWidgetState() writes to. This is the
    // documented mechanism that reliably re-composes on state change.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the snapshot once here (suspend) so the cover bitmap can be
        // resolved via Coil before composing — RemoteViews can't do async
        // image loads. provideGlance re-runs on every update(id), re-reading
        // fresh state, so this is still fully reactive.
        val snapshot = getAppWidgetState(
            context, PreferencesGlanceStateDefinition, id
        ).toWidgetSnapshot()
        val cover = loadCoverArtBitmap(context, snapshot.coverArtUrl)
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot, cover)
            }
        }
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetSnapshot, cover: android.graphics.Bitmap?) {
        val context = LocalContext.current
        val size = LocalSize.current
        val full = size.height >= FULL_SIZE.height && size.width >= FULL_SIZE.width
        // Same flags as the media notification's PendingIntent (which works):
        // bring the EXISTING MainActivity forward instead of spawning a new
        // instance, so the player ViewModel + queue survive and the mini-player
        // doesn't vanish. MainActivity is launchMode=singleTop in the manifest.
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        // No outer clickable: Glance's nested-click routing swallows child taps
        // when a parent is also clickable, which is why the buttons "did
        // nothing". Tap-to-open lives on the cover and title only.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Big square cover filling the widget height — the visual anchor.
                CoverThumb(cover, onClick = openApp)
                Spacer(GlanceModifier.width(14.dp))
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Spacer(GlanceModifier.defaultWeight())
                    TrackText(snapshot, onClick = openApp)
                    Spacer(GlanceModifier.defaultWeight())
                    // Transport centred, with the Auto-DJ pill pinned to the right.
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TransportRow(isPlaying = snapshot.isPlaying)
                        Spacer(GlanceModifier.defaultWeight())
                        AutoDjToggle(enabled = snapshot.autoDjEnabled)
                    }
                    if (full && snapshot.filepath != null) {
                        Spacer(GlanceModifier.defaultWeight())
                        RatingRow(rating = snapshot.rating)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }

    // ── Pieces ─────────────────────────────────────────────────────────────

    @Composable
    private fun TrackText(snapshot: WidgetSnapshot, onClick: Action) {
        Column(modifier = GlanceModifier.fillMaxWidth().clickable(onClick)) {
            Text(
                text = snapshot.title?.takeIf { it.isNotBlank() } ?: "Nothing playing",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
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
    private fun TransportRow(isPlaying: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleButton(
                iconRes = R.drawable.ic_widget_prev,
                contentDescription = "Previous",
                onClickAction = actionRunCallback<PrevCallback>(),
                diameter = 44,
            )
            Spacer(GlanceModifier.width(6.dp))
            CircleButton(
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClickAction = actionRunCallback<PlayPauseCallback>(),
                diameter = 52,
                accent = true,
            )
            Spacer(GlanceModifier.width(6.dp))
            CircleButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = "Next",
                onClickAction = actionRunCallback<NextCallback>(),
                diameter = 44,
            )
        }
    }

    @Composable
    private fun RatingRow(rating: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                val filled = star <= rating
                Box(
                    modifier = GlanceModifier
                        .size(30.dp)
                        .clickable(
                            actionRunCallback<RateCallback>(
                                actionParametersOf(RateCallback.StarsKey to star),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(
                            if (filled) R.drawable.ic_widget_star_filled else R.drawable.ic_widget_star_outline,
                        ),
                        contentDescription = "$star star${if (star > 1) "s" else ""}",
                        modifier = GlanceModifier.size(22.dp),
                        colorFilter = ColorFilter.tint(
                            if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Auto-DJ pill. Filled-primary when ON, hollow grey when OFF — a loud
     * visual difference so a glance tells the user the mode.
     */
    @Composable
    private fun AutoDjToggle(enabled: Boolean) {
        val bg = if (enabled) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
        val fg = if (enabled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .cornerRadius(22.dp)
                .background(bg)
                .clickable(actionRunCallback<ToggleAutoDjCallback>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_autodj),
                contentDescription = if (enabled) "Auto-DJ on" else "Auto-DJ off",
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(fg),
            )
        }
    }

    @Composable
    private fun CoverThumb(bitmap: android.graphics.Bitmap?, onClick: Action) {
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxHeight().width(96.dp).cornerRadius(12.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Give the placeholder a concrete width so it doesn't collapse.
                Box(
                    modifier = GlanceModifier.fillMaxHeight().width(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_music),
                        contentDescription = null,
                        modifier = GlanceModifier.size(36.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }

    /**
     * A circular icon button. Every button carries its OWN background circle —
     * a transparent Box may fail to register as a click target in some
     * launcher / Glance combinations, and the filled circle also makes the
     * controls read as tappable.
     */
    @Composable
    private fun CircleButton(
        iconRes: Int,
        contentDescription: String,
        onClickAction: Action,
        diameter: Int,
        accent: Boolean = false,
    ) {
        val bg = if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
        val fg = if (accent) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
        Box(
            modifier = GlanceModifier
                .size(diameter.dp)
                .cornerRadius((diameter / 2).dp)
                .background(bg)
                .clickable(onClickAction),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size((diameter * 9 / 16).dp),
                colorFilter = ColorFilter.tint(fg),
            )
        }
    }
}
