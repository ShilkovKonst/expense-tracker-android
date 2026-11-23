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

/**
 * Where the tracker screen's strip stands. On a phone: its records, or its
 * statistics. In two columns: the tracker list beside the records, or the
 * records beside the statistics.
 */
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

/** Starts on [initial]; from then on the strip keeps where it was, across rotations too. */
@Composable
fun rememberTrackerPanesState(initial: TrackerPane = TrackerPane.Records): TrackerPanesState {
    val anchored = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(initial) }
    val scope = rememberCoroutineScope()
    return remember(anchored, scope) { TrackerPanesState(anchored, scope) }
}

/**
 * A strip of the tracker [list], the tracker's [records] and its [stats], of
 * which a window shows part and a swipe drags the rest in under the finger.
 *
 * On a phone the list is left out and one pane fills the screen: a swipe left
 * brings in the statistics, a swipe right the records back, and on the records
 * a swipe right is the back gesture, with the screen underneath following the
 * finger in.
 *
 * In two columns each pane is half the window: the list beside the records,
 * and a swipe left moves the records over to make room for the statistics; a
 * swipe right moves them back, and from there goes no further.
 *
 * Back on the statistics, either way, slides back to the records, following
 * the system's edge swipe as it goes.
 *
 * The same layout either way, its children always in the same order, so a fold
 * or unfold keeps the record list's scroll and open months, and which pane was
 * showing: [TrackerPane.Stats] is the statistics on a phone and the records
 * beside them in two columns.
 */
@Composable
fun TrackerPanes(
    state: TrackerPanesState,
    twoPane: Boolean,
    /** False holds off building [stats] while they're off-screen anyway; a swipe builds them at once. */
    statsReady: Boolean,
    list: @Composable () -> Unit,
    records: @Composable () -> Unit,
    stats: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** False holds the strip still: beside the list there's no tracker yet to swipe to the statistics of. */
    swipeEnabled: Boolean = true,
) {
    val anchored = state.anchored
    val onePane = !twoPane
    val backSwipe = rememberBackSwipe()
    val flingVelocityPx = with(LocalDensity.current) { SwipeFlingVelocity.toPx() }
    // Per drag: whether it's moving the strip or playing the back gesture, and where the strip started.
    val drag = remember { DragTracking() }
    val dragState = rememberDraggableState { delta ->
        if (drag.mode == DragMode.Undecided) {
            // Only a phone's records go back on a swipe; in two columns the list is already beside them.
            drag.mode = if (onePane && delta > 0 && anchored.offsetOrZero() <= 0.5f) {
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
    PredictiveBackHandler(enabled = anchored.targetValue == TrackerPane.Stats) { events ->
        state.settleJob?.cancel()
        val travelPx = anchored.travelPx()
        try {
            events.collect { event -> anchored.anchoredDrag { dragTo(travelPx * (1 - event.progress)) } }
            state.show(TrackerPane.Records)
        } catch (e: CancellationException) {
            state.show(TrackerPane.Stats)
            throw e
        }
    }

    // A pane wholly off-screen is hidden from screen readers too (it isn't placed either; see below).
    val listHidden by remember(onePane) {
        derivedStateOf { onePane || anchored.offsetOrZero() >= anchored.travelPx() }
    }
    val recordsHidden by remember(onePane) {
        derivedStateOf { onePane && anchored.offsetOrZero() >= anchored.travelPx() }
    }
    val statsHidden by remember(onePane) {
        derivedStateOf { anchored.offsetOrZero() <= 0f }
    }

    Layout(
        content = {
            Box(if (listHidden) Modifier.clearAndSetSemantics {} else Modifier) {
                // A phone has the list as a screen of its own, underneath this one.
                if (twoPane) list()
            }
            VerticalDivider()
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
                enabled = swipeEnabled,
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
                            // Judged against the strip's travel: a whole phone screen, half a wide one.
                            val target = when (swipeOutcome(moved, velocity, anchored.travelPx(), flingVelocityPx)) {
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
        val (listPane, listDivider, recordsPane, statsDivider, statsPane) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        drag.widthPx = width.toFloat()
        val dividerConstraints = Constraints(maxWidth = width, minHeight = height, maxHeight = height)
        val listDividerPlaceable = listDivider.measure(dividerConstraints)
        val statsDividerPlaceable = statsDivider.measure(dividerConstraints)
        val dividerWidth = statsDividerPlaceable.width

        // Each pane's width, and the offset that brings in the statistics: a whole phone screen,
        // or a wide one's left column and divider, moving the records over into the left column.
        val paneWidth = if (twoPane) (width - dividerWidth) / 2 else width
        // The right column takes the odd pixel; it's the records first, then the statistics.
        val rightWidth = if (twoPane) width - dividerWidth - paneWidth else width
        val travel = if (twoPane) paneWidth + dividerWidth else width
        val newAnchors = DraggableAnchors {
            TrackerPane.Records at 0f
            TrackerPane.Stats at travel.toFloat()
        }
        if (anchored.anchors != newAnchors) anchored.updateAnchors(newAnchors, anchored.targetValue)

        val listPlaceable = listPane.measure(Constraints.fixed(paneWidth, height))
        val recordsPlaceable = recordsPane.measure(Constraints.fixed(rightWidth, height))
        val statsPlaceable = statsPane.measure(Constraints.fixed(rightWidth, height))
        layout(width, height) {
            // Read here, at placement, so the strip follows the finger without recomposing.
            val offset = anchored.offsetOrZero().roundToInt()
            // Where each pane starts on the strip; the strip is then shifted left by the offset.
            val recordsStart = if (twoPane) paneWidth + dividerWidth else 0
            // On a phone the divider overlaps the records' edge rather than taking room of its own.
            val statsStart = if (twoPane) recordsStart + recordsPlaceable.width + dividerWidth else width
            // A pane wholly off-screen isn't placed: not drawn, and hidden from screen
            // readers, yet measured and ready to slide straight in.
            if (twoPane && offset < recordsStart) {
                listPlaceable.place(-offset, 0)
                listDividerPlaceable.place(paneWidth - offset, 0)
            }
            if (offset < recordsStart + recordsPlaceable.width) recordsPlaceable.place(recordsStart - offset, 0)
            if (offset > statsStart - width) {
                // The divider rides on the statistics' leading edge, setting the two apart mid-swipe.
                statsDividerPlaceable.place(statsStart - dividerWidth - offset, 0)
                statsPlaceable.place(statsStart - offset, 0)
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

/** How far the strip moves from the records to the statistics; 0 until first laid out. */
private fun AnchoredDraggableState<TrackerPane>.travelPx() =
    anchors.positionOf(TrackerPane.Stats).takeUnless { it.isNaN() } ?: 0f
