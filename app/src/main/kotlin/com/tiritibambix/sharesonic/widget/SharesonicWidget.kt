package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.background
import androidx.glance.currentState
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
        // CRITICAL — do NOT read state out here. From the Glance source:
        // "update and updateAll do not restart provideGlance if it is already
        // running" (GlanceAppWidget.kt). Anything read before provideContent is
        // captured ONCE and frozen for the life of the session, so the widget
        // could never reflect a change. AppWidgetSession.kt shows the only path
        // a refresh takes: updateGlance() re-reads the stateDefinition into
        // `glanceState`, which is exposed as LocalState — i.e. via currentState
        // INSIDE the composition. That is why everything below reads there.
        provideContent {
            GlanceTheme {
                val snapshot = currentState<Preferences>().toWidgetSnapshot()
                val ctx = LocalContext.current
                // Decode the cached cover only when the artwork actually
                // changed (URL or file version), not on every recomposition.
                val cover = remember(snapshot.coverArtUrl, snapshot.coverVersion) {
                    decodeWidgetCover(ctx)
                }
                WidgetContent(snapshot, cover)
            }
        }
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetSnapshot, cover: android.graphics.Bitmap?) {
        val size = LocalSize.current
        val full = size.height >= FULL_SIZE.height && size.width >= FULL_SIZE.width
        // MainActivity is launchMode=singleTop (manifest): this reified launch
        // brings the EXISTING instance forward (NEW_TASK reuses the running
        // task) rather than spawning a fresh one, so the player ViewModel +
        // queue survive and the mini-player doesn't vanish. Glance 1.1.x has no
        // Intent overload, so flags come from the manifest launch mode.
        val openApp = actionStartActivity<MainActivity>()

        // Square cover sized from the widget height (minus the 14dp top+bottom
        // padding), clamped so it stays reasonable at any size. Using a fixed
        // square avoids the previous fillMaxHeight×fixed-width rectangle.
        val coverSize = (size.height.value.toInt() - 28).coerceIn(56, 120)

        // No outer clickable: Glance's nested-click routing swallows child taps
        // when a parent is also clickable, which is why the buttons "did
        // nothing". Tap-to-open lives on the cover and title only.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Square cover — the visual anchor, centred vertically.
                CoverThumb(cover, sizeDp = coverSize, onClick = openApp)
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
    private fun CoverThumb(bitmap: android.graphics.Bitmap?, sizeDp: Int, onClick: Action) {
        // Square box: width == height == sizeDp. Crop the (usually already
        // square) artwork to fill it.
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius(10.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(sizeDp.dp).cornerRadius(10.dp),
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
