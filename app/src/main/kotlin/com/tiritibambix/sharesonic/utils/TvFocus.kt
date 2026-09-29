package com.tiritibambix.sharesonic.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * D-pad helpers for Android TV.
 *
 * Every helper takes `isTV` explicitly and returns the receiver untouched when it
 * is false, so the phone modifier chains stay exactly what they were: nothing is
 * added, not even a no-op `composed {}` element.
 *
 * Compose facts these rely on (Compose UI / Foundation 1.7.0, Material3 1.3.0):
 *  - 2D focus search only looks among the focused node's SIBLINGS, never its
 *    children. A focusable nested inside another focusable (an icon button inside
 *    a clickable row) is unreachable with the arrows. See TwoDimensionalFocusSearch.
 *  - requestFocus() on a FocusRequester attached to a focusGroup enters the group
 *    and focuses the matching child (FocusRequester.findFocusTargetNode).
 *  - focusProperties enter / exit returning FocusRequester.Cancel block focus from
 *    entering / leaving a group.
 *  - Material3 1.3.0 Slider is focusable but ignores arrow keys (key support landed
 *    after that release), hence tvSliderKeys.
 */

/**
 * Overscan-safe margins for TV layouts: 5 % of the 960×540 dp TV design size
 * (Android TV layout guidelines — 48 dp left/right, 27 dp top/bottom). Keeps
 * text and controls clear of the screen edges (quality criterion TV-OV).
 */
val TvSafeHorizontal = 48.dp
val TvSafeVertical = 27.dp

/** Shapes used by [tvFocusRing] at the call sites. */
val TvRowShape: Shape = RoundedCornerShape(8.dp)
val TvCircleShape: Shape = CircleShape
val TvPillShape: Shape = RoundedCornerShape(50)

/**
 * Visible focus for a TV remote, following the Google TV focus system (scale +
 * outline + colour): a 2 dp accent outline, a light accent wash and a small zoom.
 * The Material default (a 10 % state layer) can't be seen from the sofa.
 */
fun Modifier.tvFocusRing(
    isTV: Boolean,
    shape: Shape = TvRowShape,
    focusedScale: Float = 1.05f,
): Modifier = if (!isTV) this else this.composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        label = "tvFocusScale",
    )
    val accent = MaterialTheme.colorScheme.primary
    Modifier
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (focused) Modifier
                .border(2.dp, accent, shape)
                .background(accent.copy(alpha = 0.12f), shape)
            else Modifier
        )
}

/**
 * Keeps D-pad focus inside a modal drawn in the same window as the screen behind
 * it (the frosted overlays): arrows can't leave the group, and [requester] can be
 * used to place the initial focus inside it (see [TvInitialFocus]).
 */
fun Modifier.tvFocusTrap(isTV: Boolean, requester: FocusRequester): Modifier =
    if (!isTV) this else this
        .focusRequester(requester)
        .focusProperties { exit = { FocusRequester.Cancel } }
        .focusGroup()

/**
 * Stops D-pad focus from entering this subtree while [blocked] returns true. Used
 * for content that is composed but not visible (the collapsed Now Playing sheet,
 * the screen hidden under the expanded one).
 */
fun Modifier.tvBlockFocusEntry(isTV: Boolean, blocked: () -> Boolean): Modifier =
    if (!isTV) this else this
        .focusProperties {
            enter = { if (blocked()) FocusRequester.Cancel else FocusRequester.Default }
        }
        .focusGroup()

/**
 * Requests focus on [requester] once it is attached. Retries for a few frames
 * because lazy-list items and freshly shown overlays attach a frame or two after
 * the effect starts. Re-runs whenever [key] changes.
 */
@Composable
fun TvInitialFocus(isTV: Boolean, requester: FocusRequester, key: Any? = Unit) {
    if (!isTV) return
    LaunchedEffect(key) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/**
 * Calls [refocus] each time [open] goes from true back to false — used to put
 * focus back where it was once an overlay (menu, picker, dialog) that had taken
 * it closes; otherwise focus would simply be dropped.
 */
@Composable
fun TvRefocusAfter(isTV: Boolean, open: Boolean, refocus: () -> Unit) {
    if (!isTV) return
    val currentRefocus by rememberUpdatedState(refocus)
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            currentRefocus()
        }
    }
}

/**
 * D-pad Left / Right step a Material3 Slider (1.3.0 ignores arrow keys). The value
 * is pushed on every key-down (so holding the key sweeps), and [onValueChangeFinished]
 * fires once on key-up, so a held key persists a single time.
 */
fun Modifier.tvSliderKeys(
    isTV: Boolean,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
): Modifier = if (!isTV) this else this.onPreviewKeyEvent { event ->
    val delta = when (event.key) {
        Key.DirectionLeft -> -step
        Key.DirectionRight -> step
        else -> return@onPreviewKeyEvent false
    }
    when (event.type) {
        KeyEventType.KeyDown -> onValueChange((value + delta).coerceIn(range.start, range.endInclusive))
        KeyEventType.KeyUp -> onValueChangeFinished?.invoke()
    }
    true
}

/**
 * Makes a vertically scrolling text block readable with a remote: the block takes
 * focus and D-pad Up / Down scroll it. At either end the key is not consumed, so
 * focus can move on to the next control.
 */
fun Modifier.tvScrollKeys(isTV: Boolean, state: ScrollState): Modifier =
    if (!isTV) this else this.composed {
        val scope = rememberCoroutineScope()
        val stepPx = with(LocalDensity.current) { 120.dp.toPx() }
        Modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown ->
                        if (state.canScrollForward) {
                            scope.launch { state.animateScrollBy(stepPx) }; true
                        } else false
                    Key.DirectionUp ->
                        if (state.canScrollBackward) {
                            scope.launch { state.animateScrollBy(-stepPx) }; true
                        } else false
                    else -> false
                }
            }
            .focusable()
    }

/**
 * Text fields on TV: Up / Down always leave the field (Compose only does that for
 * non-virtual D-pads, so HDMI-CEC remotes would otherwise stay stuck in it), and
 * the centre key opens the on-screen keyboard. Pair with [tvKeyboardOptions] so
 * merely moving across a field doesn't pop the keyboard.
 */
fun Modifier.tvTextFieldKeys(isTV: Boolean): Modifier =
    if (!isTV) this else this.composed {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                Key.DirectionCenter -> {
                    keyboard?.show(); true
                }
                else -> false
            }
        }
    }

/** On TV, don't open the keyboard just because a field received focus. */
fun KeyboardOptions.tvKeyboardOptions(isTV: Boolean): KeyboardOptions =
    if (isTV) copy(showKeyboardOnFocus = false) else this

/**
 * Remembers which list row had focus (by a stable string key, saved across
 * navigation) so a list can put focus back on it when the screen is shown again,
 * or on a row that was re-created by an edit (reorder, removal).
 */
@Stable
class TvListFocus internal constructor(
    val isTV: Boolean,
    private val lastKey: MutableState<String?>,
) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /** A focus target that should receive focus once its row is composed. */
    var pendingKey by mutableStateOf<String?>(null)

    /** Position of the row that last had focus: the fallback when its key is gone. */
    private var lastIndex = -1

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * Attach to a row's focusable element (or the group wrapping its controls).
     * [index] is the row's position among the focusable rows, used to land on
     * its neighbour when the row itself was removed or renamed.
     */
    fun itemModifier(key: String, index: Int = -1): Modifier =
        if (!isTV) Modifier else Modifier
            .focusRequester(requester(key))
            .onFocusChanged {
                if (it.hasFocus) {
                    lastKey.value = key
                    lastIndex = index
                }
            }

    private var listHadFocus = false

    /**
     * Attach to the LazyColumn. When the focused row is removed (delete, rename,
     * a row re-created under a new key) the list loses focus with nowhere to go;
     * this notices it and queues a refocus on the row now at that position.
     * [keys] must return the rows' current keys.
     */
    fun listModifier(keys: () -> List<String>): Modifier =
        if (!isTV) Modifier else Modifier.onFocusChanged {
            if (listHadFocus && !it.hasFocus) {
                val key = lastKey.value
                if (key != null && key !in keys()) pendingKey = key
            }
            listHadFocus = it.hasFocus
        }

    /** Key of the row that last had focus, if any. */
    val last: String? get() = lastKey.value

    /** [key] if still present, else the row now at the last focused position. */
    internal fun resolve(key: String, keys: List<String>): String? = when {
        key in keys -> key
        keys.isEmpty() -> null
        lastIndex >= 0 -> keys[lastIndex.coerceAtMost(keys.lastIndex)]
        else -> keys.last()
    }

    /**
     * Scrolls [listState] so the row [key] is composed, then focuses it. [indexOf]
     * maps a key to its LazyColumn item index (-1 = not present).
     */
    suspend fun focus(key: String, listState: LazyListState, indexOf: (String) -> Int): Boolean {
        val index = indexOf(key)
        if (index < 0) return false
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!visible) listState.scrollToItem(index)
        repeat(10) {
            withFrameNanos { }
            if (runCatching { requester(key).requestFocus() }.isSuccess) return true
        }
        return false
    }
}

@Composable
fun rememberTvListFocus(isTV: Boolean, saveKey: String = "tvListFocus"): TvListFocus {
    val last = rememberSaveable(saveKey) { mutableStateOf<String?>(null) }
    return remember(isTV, last) { TvListFocus(isTV, last) }
}

/**
 * On TV, when a list first has content: focus the row that had focus before (after
 * returning from another screen), else the first row. [keys] are the focusable
 * rows' keys in display order; [indexOf] maps a key to its LazyColumn item index.
 * Also serves [TvListFocus.pendingKey] whenever the content changes (a row that
 * was re-created by an edit gets its focus back).
 */
@Composable
fun TvListFocusEffect(
    focus: TvListFocus,
    listState: LazyListState,
    keys: List<String>,
    indexOf: (String) -> Int = { keys.indexOf(it) },
    enabled: Boolean = true,
) {
    if (!focus.isTV || !enabled) return
    val hasContent = keys.isNotEmpty()
    LaunchedEffect(hasContent) {
        if (!hasContent) return@LaunchedEffect
        val target = focus.last?.takeIf { indexOf(it) >= 0 } ?: keys.first()
        focus.focus(target, listState, indexOf)
    }
    LaunchedEffect(keys, focus.pendingKey) {
        val pending = focus.pendingKey ?: return@LaunchedEffect
        val target = if (indexOf(pending) >= 0) pending else focus.resolve(pending, keys)
        if (target != null) focus.focus(target, listState, indexOf)
        focus.pendingKey = null
    }
}
