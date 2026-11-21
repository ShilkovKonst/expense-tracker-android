package com.spendobserver.ui.tracker

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.animateToWithDecay
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import com.spendobserver.ui.common.SwipeDirection
import com.spendobserver.ui.common.SwipeFlingVelocity
import com.spendobserver.ui.common.rememberBackSwipe
import com.spendobserver.ui.common.swipeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The tracker screen's two panes: its records, and its statistics to their right. */
enum class TrackerPane { Records, Stats }

/** Fast but without overshoot: the pane lands in place rather than bouncing. */
private val PaneSettleSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Which pane a phone shows, kept across rotations; a strip the finger drags between them. */
@Stable
class TrackerPanesState internal constructor(
    internal val anchored: AnchoredDraggableState<TrackerPane>,
    private val scope: CoroutineScope,
) {
    internal var settleJob: Job? = null

    /** Where the strip is headed: the pane showing, or about to. */
    val pane: TrackerPane get() = anchored.targetValue

    /** Slides to [pane], as a swipe would. */
    fun show(pane: TrackerPane) {
        settle { anchored.animateTo(pane, PaneSettleSpec) }
    }

    internal fun settle(block: suspend () -> Unit) {
        settleJob?.cancel()
        settleJob = scope.launch { block() }
    }
}

@Composable
fun rememberTrackerPanesState(): TrackerPanesState {
    val anchored = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(TrackerPane.Records) }
    val scope = rememberCoroutineScope()
    return remember(anchored, scope) { TrackerPanesState(anchored, scope) }
}

/**
 * The tracker's [records] and [stats] side by side. In two columns both show.
 * On a phone one fills the screen and the other waits beside it: a swipe left
 * drags the statistics in under the finger, a swipe right drags the records
 * back, and on the records a swipe right is the back gesture, with the screen
 * underneath following the finger in. Back on the statistics slides to the
 * records, the system's edge swipe likewise.
 *
 * The same layout either way, with the records always its first child, so a
 * fold or unfold keeps the record list's scroll and open months, and which
 * pane was showing.
 */
@Composable
fun TrackerPanes(
    state: TrackerPanesState,
    twoPane: Boolean,
    /** False holds off building [stats] while they're off-screen anyway; a swipe builds them at once. */
    statsReady: Boolean,
    records: @Composable () -> Unit,
    stats: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchored = state.anchored
    val backSwipe = rememberBackSwipe()
    val flingVelocityPx = with(LocalDensity.current) { SwipeFlingVelocity.toPx() }
    // Per drag: whether it's moving the strip or playing the back gesture, and where the strip started.
    val drag = remember { DragTracking() }
    val dragState = rememberDraggableState { delta ->
        if (drag.mode == DragMode.Undecided) {
            drag.mode = if (delta > 0 && anchored.offsetOrZero() <= 0.5f) {
                backSwipe.start()
                DragMode.Back
            } else {
                DragMode.Strip
            }
        }
        when (drag.mode) {
            DragMode.Back -> backSwipe.drag(delta, drag.widthPx)
            // The strip moves against the finger's x: dragging left brings the statistics in.
            else -> anchored.dispatchRawDelta(-delta)
        }
    }

    // On the statistics, back slides to the records, following the system's edge swipe as it goes.
    PredictiveBackHandler(enabled = !twoPane && anchored.targetValue == TrackerPane.Stats) { events ->
        state.settleJob?.cancel()
        val widthPx = drag.widthPx
        try {
            events.collect { event -> anchored.anchoredDrag { dragTo(widthPx * (1 - event.progress)) } }
            state.show(TrackerPane.Records)
        } catch (e: CancellationException) {
            state.show(TrackerPane.Stats)
            throw e
        }
    }

    val onePane = !twoPane
    // A pane wholly off-screen is hidden from screen readers too (it isn't placed either; see below).
    val recordsHidden by remember(onePane) {
        derivedStateOf { onePane && anchored.offsetOrZero() >= anchored.anchors.positionOf(TrackerPane.Stats) }
    }
    val statsHidden by remember(onePane) {
        derivedStateOf { onePane && anchored.offsetOrZero() <= 0f }
    }

    Layout(
        content = {
            Box(if (recordsHidden) Modifier.clearAndSetSemantics {} else Modifier) { records() }
            VerticalDivider()
            Box(if (statsHidden) Modifier.clearAndSetSemantics {} else Modifier) {
                if (statsReady || !statsHidden) stats()
            }
        },
        modifier = modifier
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = onePane,
                onDragStarted = { position ->
                    state.settleJob?.cancel()
                    backSwipe.onDragStarted(position)
                    drag.mode = DragMode.Undecided
                    drag.startOffset = anchored.offsetOrZero()
                    drag.startPane = anchored.settledValue
                },
                onDragStopped = { velocity ->
                    when (drag.mode) {
                        DragMode.Back -> backSwipe.finish(velocity, drag.widthPx)
                        DragMode.Strip -> {
                            val moved = -(anchored.offsetOrZero() - drag.startOffset)
                            val target = when (swipeOutcome(moved, velocity, drag.widthPx, flingVelocityPx)) {
                                SwipeDirection.Left -> TrackerPane.Stats
                                SwipeDirection.Right -> TrackerPane.Records
                                null -> drag.startPane
                            }
                            state.settle {
                                anchored.animateToWithDecay(target, -velocity, PaneSettleSpec, exponentialDecay())
                            }
                        }
                        DragMode.Undecided -> Unit
                    }
                    drag.mode = DragMode.Undecided
                },
            ),
    ) { measurables, constraints ->
        val (recordsPane, divider, statsPane) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        drag.widthPx = width.toFloat()
        val newAnchors = DraggableAnchors {
            TrackerPane.Records at 0f
            TrackerPane.Stats at width.toFloat()
        }
        if (anchored.anchors != newAnchors) anchored.updateAnchors(newAnchors, anchored.targetValue)
        val dividerPlaceable = divider.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
        if (twoPane) {
            val recordsWidth = (width - dividerPlaceable.width) / 2
            val statsWidth = width - dividerPlaceable.width - recordsWidth
            val recordsPlaceable = recordsPane.measure(Constraints.fixed(recordsWidth, height))
            val statsPlaceable = statsPane.measure(Constraints.fixed(statsWidth, height))
            layout(width, height) {
                recordsPlaceable.place(0, 0)
                dividerPlaceable.place(recordsWidth, 0)
                statsPlaceable.place(recordsWidth + dividerPlaceable.width, 0)
            }
        } else {
            val recordsPlaceable = recordsPane.measure(Constraints.fixed(width, height))
            val statsPlaceable = statsPane.measure(Constraints.fixed(width, height))
            layout(width, height) {
                // Read here, at placement, so the strip follows the finger without recomposing.
                val offset = anchored.offsetOrZero().roundToInt()
                // A pane wholly off-screen isn't placed: not drawn, and hidden from screen
                // readers, yet measured and ready to slide straight in.
                if (offset < width) recordsPlaceable.place(-offset, 0)
                if (offset > 0) {
                    // The divider rides on the statistics' leading edge, setting the two apart mid-swipe.
                    dividerPlaceable.place(width - offset - dividerPlaceable.width, 0)
                    statsPlaceable.place(width - offset, 0)
                }
            }
        }
    }
}

private enum class DragMode { Undecided, Strip, Back }

private class DragTracking {
    var mode = DragMode.Undecided
    var startOffset = 0f
    var startPane = TrackerPane.Records
    var widthPx = 0f
}

/** The strip's offset, 0 until its anchors are first laid out. */
private fun AnchoredDraggableState<TrackerPane>.offsetOrZero() = offset.takeUnless { it.isNaN() } ?: 0f

