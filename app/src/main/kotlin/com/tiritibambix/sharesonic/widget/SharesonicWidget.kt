package com.tiritibambix.sharesonic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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

    // Three responsive buckets so the widget stays useful from 2x1 up to 4x2.
    // Glance picks the closest fit for the placed size at runtime; we branch on
    // LocalSize inside provideContent to render the matching layout.
    companion object {
        private val SMALL_SIZE  = DpSize(180.dp, 90.dp)   // ~2x1
        private val MEDIUM_SIZE = DpSize(260.dp, 130.dp)  // ~3x2
        private val LARGE_SIZE  = DpSize(340.dp, 180.dp)  // ~4x2
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE))

    // Reads the snapshot fresh on every provideContent invocation — no need
    // for a Glance StateDefinition (we own the DataStore ourselves).
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
        val bg = GlanceTheme.colors.surface
        // Tap anywhere outside the buttons opens MainActivity — feels natural
        // and gives quick access to the full player when the widget is only a
        // snapshot.
        val openApp = actionStartActivity<MainActivity>()

        val bucket = when {
            size.width >= LARGE_SIZE.width  -> Bucket.LARGE
            size.width >= MEDIUM_SIZE.width -> Bucket.MEDIUM
            else                            -> Bucket.SMALL
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(16.dp)
                .clickable(openApp)
                .padding(10.dp)
        ) {
            when (bucket) {
                Bucket.SMALL  -> SmallLayout(snapshot)
                Bucket.MEDIUM -> MediumLayout(snapshot, coverBitmap)
                Bucket.LARGE  -> LargeLayout(snapshot, coverBitmap)
            }
        }
    }

    @Composable
    private fun SmallLayout(snapshot: WidgetSnapshot) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TransportRow(isPlaying = snapshot.isPlaying)
            Spacer(GlanceModifier.width(12.dp))
            AutoDjBadge(enabled = snapshot.autoDjEnabled)
        }
    }

    @Composable
    private fun MediumLayout(snapshot: WidgetSnapshot, coverBitmap: android.graphics.Bitmap?) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(coverBitmap, sizeDp = 48)
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    TrackText(snapshot)
                }
                Spacer(GlanceModifier.width(6.dp))
                AutoDjBadge(enabled = snapshot.autoDjEnabled)
            }
            Spacer(GlanceModifier.size(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TransportRow(isPlaying = snapshot.isPlaying)
                if (snapshot.filepath != null) {
                    Spacer(GlanceModifier.width(10.dp))
                    RatingRow(rating = snapshot.rating, starSize = 22)
                }
            }
        }
    }

    @Composable
    private fun LargeLayout(snapshot: WidgetSnapshot, coverBitmap: android.graphics.Bitmap?) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(coverBitmap, sizeDp = 64)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    TrackText(snapshot)
                }
                AutoDjBadge(enabled = snapshot.autoDjEnabled)
            }
            Spacer(GlanceModifier.size(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TransportRow(isPlaying = snapshot.isPlaying)
            }
            Spacer(GlanceModifier.size(6.dp))
            if (snapshot.filepath != null) {
                RatingRow(rating = snapshot.rating)
            }
        }
    }

    @Composable
    private fun TrackText(snapshot: WidgetSnapshot) {
        Text(
            text = snapshot.title ?: "—",
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

    @Composable
    private fun TransportRow(isPlaying: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                iconRes = R.drawable.ic_widget_prev,
                contentDescription = "Previous",
                onClickAction = actionRunCallback<PrevCallback>(),
            )
            Spacer(GlanceModifier.width(6.dp))
            IconButton(
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClickAction = actionRunCallback<PlayPauseCallback>(),
                size = 44,
            )
            Spacer(GlanceModifier.width(6.dp))
            IconButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = "Next",
                onClickAction = actionRunCallback<NextCallback>(),
            )
        }
    }

    @Composable
    private fun RatingRow(rating: Int, starSize: Int = 28) {
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
                )
                if (star != 5) Spacer(GlanceModifier.width(2.dp))
            }
        }
    }

    @Composable
    private fun AutoDjBadge(enabled: Boolean) {
        val tint = if (enabled) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
        Box(
            modifier = GlanceModifier
                .size(40.dp)
                .cornerRadius(20.dp)
                .background(if (enabled) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                .clickable(actionRunCallback<ToggleAutoDjCallback>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_autodj),
                contentDescription = "Auto-DJ",
                modifier = GlanceModifier.size(22.dp),
                colorFilter = androidx.glance.ColorFilter.tint(tint),
            )
        }
    }

    @Composable
    private fun CoverThumb(bitmap: android.graphics.Bitmap?, sizeDp: Int) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius(6.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(6.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = null,
                    modifier = GlanceModifier.size((sizeDp / 2).dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    @Composable
    private fun IconButton(
        iconRes: Int,
        contentDescription: String,
        onClickAction: androidx.glance.action.Action,
        size: Int = 36,
    ) {
        Box(
            modifier = GlanceModifier
                .size(size.dp)
                .cornerRadius((size / 2).dp)
                .clickable(onClickAction),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size((size * 5 / 8).dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
            )
        }
    }

    private enum class Bucket { SMALL, MEDIUM, LARGE }
}
