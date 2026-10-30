package com.spendobserver.ui.trackers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Two blocks stacked in the whole height given, shared by [splitHeights].
 * Each block's wish is its max intrinsic height: a block that scrolls inside
 * can't be measured with unlimited height, but can still report what it holds.
 */
@Composable
internal fun SplitByContentColumn(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            first()
            second()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (top, bottom) = measurables
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val (topHeight, bottomHeight) = splitHeights(
            first = top.maxIntrinsicHeight(width),
            second = bottom.maxIntrinsicHeight(width),
            available = (constraints.maxHeight - gap).coerceAtLeast(0),
        )
        val topPlaceable = top.measure(Constraints.fixed(width, topHeight))
        val bottomPlaceable = bottom.measure(Constraints.fixed(width, bottomHeight))
        layout(width, constraints.maxHeight) {
            topPlaceable.place(0, 0)
            bottomPlaceable.place(0, topHeight + gap)
        }
    }
}

/**
 * Equal halves of [available], unless exactly one block wants more than a
 * half: then the other gets just what it wants and the bigger one the rest.
 */
internal fun splitHeights(first: Int, second: Int, available: Int): Pair<Int, Int> {
    val half = available / 2
    return when {
        first < half && second > half -> first to available - first
        second < half && first > half -> available - second to second
        else -> half to available - half
    }
}
