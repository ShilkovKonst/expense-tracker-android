package com.spendobserver.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two blocks stacked in the whole height given, shared by [splitHeights].
 * Each block's wish is its max intrinsic height: a block that scrolls inside
 * can't be measured with unlimited height, but can still report what it holds.
 *
 * [between], a single layout node, sits between them at its own height. With
 * unlimited height (inside a scrolling parent) there's nothing to share: each
 * block takes what it needs, up to [maxSectionHeightWhenScrolling].
 */
@Composable
internal fun SplitByContentColumn(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
    between: (@Composable () -> Unit)? = null,
    maxSectionHeightWhenScrolling: Dp = 200.dp,
) {
    Layout(
        content = {
            first()
            between?.invoke()
            second()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val top = measurables.first()
        val bottom = measurables.last()
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val middlePlaceable = if (measurables.size > 2) {
            measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
        } else {
            null
        }
        val middleSpace = middlePlaceable?.let { it.height + gap } ?: 0
        val (topPlaceable, bottomPlaceable) = if (constraints.hasBoundedHeight) {
            val (topHeight, bottomHeight) = splitHeights(
                first = top.maxIntrinsicHeight(width),
                second = bottom.maxIntrinsicHeight(width),
                available = (constraints.maxHeight - gap - middleSpace).coerceAtLeast(0),
            )
            top.measure(Constraints.fixed(width, topHeight)) to bottom.measure(Constraints.fixed(width, bottomHeight))
        } else {
            val limit = Constraints(minWidth = width, maxWidth = width, maxHeight = maxSectionHeightWhenScrolling.roundToPx())
            top.measure(limit) to bottom.measure(limit)
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            topPlaceable.height + gap + middleSpace + bottomPlaceable.height
        }
        layout(width, height) {
            topPlaceable.place(0, 0)
            middlePlaceable?.place(0, topPlaceable.height + gap)
            bottomPlaceable.place(0, topPlaceable.height + gap + middleSpace)
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
