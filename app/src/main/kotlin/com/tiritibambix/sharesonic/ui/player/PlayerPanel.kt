package com.tiritibambix.sharesonic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.utils.LocalIsTV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drag-up player sheet — one component that unifies the persistent mini-player
 * with the full Now Playing screen, mirroring mStream's `player_panel.dart`.
 *
 *  - At progress 0f, the collapsed mini-player is fully visible at the bottom
 *    edge; the expanded sheet is off-screen below.
 *  - Dragging up or tapping the mini bar animates progress toward 1f; the sheet
 *    slides in from the bottom, the mini bar fades out (front-loaded, gone by
 *    progress 0.4).
 *  - Dragging down past the middle, or fling velocity > 300 px/s, collapses.
 *  - System back collapses when expanded (mStream parity).
 *
 * Sharesonic previously routed to a separate `NowPlaying` screen; this makes
 * the transition a native gesture instead of a nav-graph animation, and gives
 * the panel the "premium sheet" feel of Spotify / Apple Music.
 */

const val PLAYER_PANEL_COLLAPSED_HEIGHT_DP = 66

@Stable
class PlayerPanelState internal constructor(
    internal val progress: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
) {
    val isExpanded: Boolean get() = progress.value > 0.5f

    fun expand() { scope.launch { progress.animateTo(1f, tween(320)) } }
    fun collapse() { scope.launch { progress.animateTo(0f, tween(320)) } }
}

@Composable
fun rememberPlayerPanelState(): PlayerPanelState {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return remember(progress, scope) { PlayerPanelState(progress, scope) }
}

/**
 * Draws the panel over the full parent area. The parent decides positioning by
 * passing an appropriate [modifier]; the panel itself owns:
 *   - the drag gesture (from anywhere inside the sheet or mini bar),
 *   - the fade between mini and expanded views,
 *   - the slide-up transform,
 *   - the back-handler.
 *
 * @param visible whether the panel should appear at all (typically = there is
 *   a current song). Folds away entirely when false.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerPanel(
    state: PlayerPanelState,
    playerState: PlayerState,
    visible: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleAutoDj: () -> Unit,
    onShareCreated: (url: String) -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    // The Now Playing ⇆ Queue pager lives here (not inside NowPlayingScreen) so the
    // single BackHandler below can be page-aware: from the Queue page, Back steps
    // back to Now Playing; from Now Playing, it collapses the whole panel. Page 0 =
    // Now Playing, page 1 = Queue.
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val panelScope = rememberCoroutineScope()
    val isTV = LocalIsTV.current
    val miniFocusRequester = remember { FocusRequester() }

    // On TV, move focus to the mini player whenever it becomes visible (new song
    // starts or player expands then collapses). OK key on the focused mini bar
    // expands the panel to the full Now Playing screen.
    LaunchedEffect(visible) {
        if (isTV && visible && !state.isExpanded) {
            runCatching { miniFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val collapsedPx = with(density) { PLAYER_PANEL_COLLAPSED_HEIGHT_DP.dp.toPx() }
        // How much the sheet has to travel from off-screen-bottom to fully-open.
        // Guard against zero-height parents (very short-lived during layout).
        val dragExtentPx = (maxHeightPx - collapsedPx).coerceAtLeast(1f)
        val scope = rememberCoroutineScope()
        val t = state.progress.value

        // Common fling / snap-to-anchor behaviour when the drag lifts / cancels.
        val snapToNearest = {
            scope.launch {
                val target = if (state.progress.value > 0.5f) 1f else 0f
                state.progress.animateTo(target, tween(320))
            }
        }

        // ── Full-screen sheet: pinned to top-start, height = full parent.
        // At t=0 it is offset down by dragExtent (so only its top edge peeks at
        // the mini-bar position); at t=1 the offset is 0 (fully on screen).
        // Fades in from t=0.1 upward so it doesn't muddily overlap the mini bar.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .align(Alignment.TopStart)
                .offset { IntOffset(0, ((1f - t) * dragExtentPx).roundToInt()) }
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .graphicsLayer { alpha = ((t - 0.1f) / 0.9f).coerceIn(0f, 1f) }
                .pointerInput(dragExtentPx) {
                    detectVerticalDragGestures(
                        onDragEnd = { snapToNearest() },
                        onDragCancel = { snapToNearest() },
                    ) { _, dragAmount ->
                        scope.launch {
                            val next = (state.progress.value - dragAmount / dragExtentPx)
                                .coerceIn(0f, 1f)
                            state.progress.snapTo(next)
                        }
                    }
                },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBack = { state.collapse() },
                onShareCreated = onShareCreated,
                pagerState = pagerState,
            )
        }

        // ── Collapsed mini bar: pinned to the bottom edge, fades out by t=0.4.
        // Once faded out it's non-interactive (alpha == 0, no clicks land).
        // Its own drag gesture forwards to the same progress so the user can
        // start dragging up straight from the mini-player.
        val miniAlpha = (1f - t / 0.4f).coerceIn(0f, 1f)
        if (miniAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLAYER_PANEL_COLLAPSED_HEIGHT_DP.dp)
                    .align(Alignment.BottomStart)
                    .graphicsLayer { alpha = miniAlpha }
                    .then(if (isTV) Modifier.focusRequester(miniFocusRequester) else Modifier)
                    .pointerInput(dragExtentPx) {
                        detectVerticalDragGestures(
                            onDragEnd = { snapToNearest() },
                            onDragCancel = { snapToNearest() },
                        ) { _, dragAmount ->
                            scope.launch {
                                val next = (state.progress.value - dragAmount / dragExtentPx)
                                    .coerceIn(0f, 1f)
                                state.progress.snapTo(next)
                            }
                        }
                    },
            ) {
                MiniPlayerBar(
                    state = playerState,
                    onPlayPause = onPlayPause,
                    onSkipPrev = onSkipPrev,
                    onSkipNext = onSkipNext,
                    onClick = { state.expand() },
                    onToggleAutoDj = onToggleAutoDj,
                )
            }
        }
    }

    // Hierarchical back: on the Queue page, step back to Now Playing first;
    // only collapse the whole panel once already on Now Playing.
    BackHandler(enabled = state.isExpanded) {
        if (pagerState.currentPage != 0) {
            panelScope.launch { pagerState.animateScrollToPage(0) }
        } else {
            state.collapse()
        }
    }
}
