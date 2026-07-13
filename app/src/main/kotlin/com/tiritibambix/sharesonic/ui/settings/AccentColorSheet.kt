package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.theme.borderSoft
import com.tiritibambix.sharesonic.ui.theme.borderStrong
import com.tiritibambix.sharesonic.ui.theme.onAccent
import com.tiritibambix.sharesonic.ui.theme.textSecondary
import com.tiritibambix.sharesonic.utils.LocalIsTV

/** Curated presets — subset the user asked for, plus the current Velvet primary. */
private val AccentPresets = listOf(
    Color(0xFF8B5CF6), // Velvet purple
    Color(0xFFFFAB00), // amber / gold
    Color(0xFF9E9E9E), // neutral grey
    Color(0xFFFFFFFF), // white
    Color(0xFF000000), // black
)

/** Full hue spectrum for the hue slider track. */
private val HueTrack = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

private fun Color.toArgb(): Int {
    fun ch(v: Float) = (v * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
}

/** HSV → RGB conversion (mirrors Flutter's HSVColor.toColor). */
private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(r1 + m, g1 + m, b1 + m, 1f)
}

private data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun withHue(h: Float) = copy(h = h.coerceIn(0f, 360f))
    fun withSat(s: Float) = copy(s = s.coerceIn(0f, 1f))
    fun withVal(v: Float) = copy(v = v.coerceIn(0f, 1f))
    fun toColor() = hsvToColor(h, s, v)
}

private fun Color.toHsv(): Hsv {
    val r = red; val g = green; val b = blue
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * (((b - r) / d) + 2f)
        else    -> 60f * (((r - g) / d) + 4f)
    }
    val s = if (mx == 0f) 0f else d / mx
    return Hsv((if (h < 0f) h + 360f else h), s, mx)
}

/**
 * Accent picker bottom sheet: 5 curated preset swatches + a "Theme default"
 * reset chip + a custom HSV picker (hue / saturation / value gradient bars).
 *
 * Live-writes through [onPick] — the parent (typically wired to
 * `SettingsViewModel.setAccentColor`) rebuilds the ColorScheme so the picked
 * colour previews on the real UI behind the sheet. Preset taps also dismiss
 * the sheet; HSV drags commit on release only, not per tick.
 *
 * Presets are deliberately tight — purple / gold / grey / white / black.
 * The broader amber/orange/red/… catalogue would only duplicate what the HSV
 * picker below already covers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccentColorSheet(
    currentArgb: Int?,
    themeDefault: Color,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local HSV state driven by the custom picker sliders. Seeded from the
    // current accent (or the theme default when nothing is picked). Kept in a
    // mutableState rather than committed per drag tick so a drag doesn't beat
    // on DataStore.
    var hsv by remember(currentArgb) {
        val seed = currentArgb?.let { Color(it) } ?: themeDefault
        mutableStateOf(seed.toHsv())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.theme_accent_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Swatch(
                    color = themeDefault,
                    selected = currentArgb == null,
                    icon = Icons.Default.FormatColorReset,
                    onTap = { onPick(null); onDismiss() },
                )
                AccentPresets.forEach { c ->
                    Swatch(
                        color = c,
                        selected = currentArgb == c.toArgb(),
                        onTap = { onPick(c.toArgb()); onDismiss() },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.borderSoft)
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.theme_accent_custom).uppercase(),
                color = MaterialTheme.colorScheme.textSecondary,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val custom = hsv.toColor()
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(custom)
                        .border(1.dp, MaterialTheme.colorScheme.borderStrong, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentArgb == custom.toArgb()) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = onAccent(custom),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GradientBar(
                        colors = HueTrack,
                        t = hsv.h / 360f,
                        onChanged = { hsv = hsv.withHue(it * 360f) },
                        onEnd = { onPick(hsv.toColor().toArgb()) },
                    )
                    GradientBar(
                        colors = listOf(
                            hsvToColor(hsv.h, 0f, hsv.v),
                            hsvToColor(hsv.h, 1f, hsv.v),
                        ),
                        t = hsv.s,
                        onChanged = { hsv = hsv.withSat(it) },
                        onEnd = { onPick(hsv.toColor().toArgb()) },
                    )
                    GradientBar(
                        colors = listOf(
                            Color.Black,
                            hsvToColor(hsv.h, hsv.s, 1f),
                        ),
                        t = hsv.v,
                        onChanged = { hsv = hsv.withVal(it) },
                        onEnd = { onPick(hsv.toColor().toArgb()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onTap: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.24f),
                shape = CircleShape,
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val ink = onAccent(color)
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = ink)
            selected     -> Icon(Icons.Default.Check, contentDescription = null, tint = ink)
        }
    }
}

/**
 * Horizontal gradient track with a draggable white thumb. [t] is the
 * fractional position 0..1; [onChanged] fires continuously during a drag /
 * tap for a live preview; [onEnd] fires once on release / tap-up to commit.
 */
@Composable
private fun GradientBar(
    colors: List<Color>,
    t: Float,
    onChanged: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val h = 26.dp
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val isTV = LocalIsTV.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(h)
            .onSizeChanged { boxSize = it }
            .clip(RoundedCornerShape(percent = 50))
            .background(Brush.horizontalGradient(colors))
            .border(1.dp, MaterialTheme.colorScheme.borderSoft, RoundedCornerShape(percent = 50))
            // TV: focusable + D-pad left/right nudges the value by 4 % per press
            // (25 presses to sweep the whole bar — comfortable on a remote).
            // Commits (onEnd) on each press so previews and DataStore stay in sync.
            .then(
                if (isTV) Modifier
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft  -> { onChanged((t - 0.04f).coerceIn(0f, 1f)); onEnd(); true }
                                Key.DirectionRight -> { onChanged((t + 0.04f).coerceIn(0f, 1f)); onEnd(); true }
                                else -> false
                            }
                        } else false
                    }
                else Modifier
            )
            .pointerInput(boxSize) {
                if (boxSize.width == 0) return@pointerInput
                detectTapGestures(onTap = { pos ->
                    onChanged((pos.x / boxSize.width).coerceIn(0f, 1f))
                    onEnd()
                })
            }
            .pointerInput(boxSize) {
                if (boxSize.width == 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                ) { change, _ ->
                    onChanged((change.position.x / boxSize.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val w = boxSize.width.toFloat().coerceAtLeast(1f)
        val hPx = boxSize.height.toFloat().coerceAtLeast(1f)
        val thumbFraction = t.coerceIn(0f, 1f)
        val thumbLeft = ((thumbFraction * w - hPx / 2f).coerceIn(0f, w - hPx))
        Box(
            modifier = Modifier
                .padding(start = with(androidx.compose.ui.platform.LocalDensity.current) { thumbLeft.toDp() })
                .size(h)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.55f), CircleShape),
        )
    }
}

