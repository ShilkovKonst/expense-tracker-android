package com.spendobserver.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** Which way a finished horizontal swipe went: the finger moved [Left] or [Right]. */
internal enum class SwipeDirection { Left, Right }

/** A swipe this far across the screen goes through even when released slowly. */
internal const val SwipeCommitFraction = 1f / 3

/** A flick at least this fast goes through however short it was. */
internal val SwipeFlingVelocity = 800.dp

/**
 * Where a horizontal drag of [dragPx] released at [velocityPx] per second leads
 * on a screen [widthPx] wide: null to stay. Far enough goes through unless flung
 * back; a fast flick the same way goes through however short.
 */
internal fun swipeOutcome(dragPx: Float, velocityPx: Float, widthPx: Float, flingVelocityPx: Float): SwipeDirection? {
    if (dragPx == 0f || widthPx <= 0f) return null
    // Positive while the release carries on the drag's way, negative when it's flung back.
    val onward = velocityPx * sign(dragPx)
    val farEnough = abs(dragPx) >= widthPx * SwipeCommitFraction && onward > -flingVelocityPx
    val flicked = onward >= flingVelocityPx
    if (!farEnough && !flicked) return null
    return if (dragPx < 0) SwipeDirection.Left else SwipeDirection.Right
}

/**
 * Plays a rightward drag as Android's predictive back gesture: the screen
 * underneath slides in under the finger, exactly as with the system's edge
 * swipe, then either finishes from where it is or slides back.
 */
class BackSwipe internal constructor(
    private val dispatcher: OnBackPressedDispatcher?,
    private val flingVelocityPx: Float,
) {
    private var active = false
    private var dragPx = 0f
    private var touchStart = Offset.Zero

    val isActive get() = active

    /** Where the finger went down; the gesture itself starts with the first move right. */
    fun onDragStarted(position: Offset) {
        touchStart = position
    }

    fun start() {
        val dispatcher = dispatcher ?: return
        active = true
        dragPx = 0f
        dispatcher.dispatchOnBackStarted(event(touchStart.x, 0f))
    }

    fun drag(delta: Float, widthPx: Float) {
        if (!active || widthPx <= 0f) return
        dragPx = (dragPx + delta).coerceAtLeast(0f)
        dispatcher?.dispatchOnBackProgressed(event(dragPx, (dragPx / widthPx).coerceIn(0f, 1f)))
    }

    fun finish(velocityPx: Float, widthPx: Float) {
        if (!active) return
        active = false
        if (swipeOutcome(dragPx, velocityPx, widthPx, flingVelocityPx) == SwipeDirection.Right) {
            dispatcher?.onBackPressed()
        } else {
            dispatcher?.dispatchOnBackCancelled()
        }
    }

    private fun event(x: Float, progress: Float) = BackEventCompat(x, touchStart.y, progress, BackEventCompat.EDGE_LEFT)
}

@Composable
fun rememberBackSwipe(): BackSwipe {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val flingVelocityPx = with(LocalDensity.current) { SwipeFlingVelocity.toPx() }
    return remember(dispatcher, flingVelocityPx) { BackSwipe(dispatcher, flingVelocityPx) }
}

/**
 * [content] that a swipe to the right takes back from, with the screen
 * underneath following the finger in (see [BackSwipe]). Leftward drags and
 * vertical scrolling inside are left alone.
 */
@Composable
fun BackSwipeArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backSwipe = rememberBackSwipe()
    var widthPx by remember { mutableIntStateOf(0) }
    val dragState = rememberDraggableState { delta ->
        if (!backSwipe.isActive && delta > 0) backSwipe.start()
        backSwipe.drag(delta, widthPx.toFloat())
    }
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { position -> backSwipe.onDragStarted(position) },
                onDragStopped = { velocity -> backSwipe.finish(velocity, widthPx.toFloat()) },
            ),
    ) {
        content()
    }
}
