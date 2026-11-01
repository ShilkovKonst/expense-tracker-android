package com.spendobserver.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the content out at the bottom while there's more below. Goes before
 * `verticalScroll` in the chain, so it covers the viewport, and after
 * [verticalScrollbar], which then stays unfaded. The offscreen layer keeps
 * [BlendMode.DstIn] to this content: it would erase the background too otherwise.
 */
fun Modifier.fadingBottomEdge(state: ScrollState, fadeHeight: Dp = 24.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = fadeHeight.toPx()
            // Eases out over the last stretch, so the fade doesn't vanish all at once at the end.
            val strength = ((state.maxValue - state.value) / fade).coerceIn(0f, 1f)
            val top = size.height - fade
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - strength)),
                    startY = top,
                    endY = size.height,
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * A thin thumb at the end edge, shown for as long as there's anything to
 * scroll: its job is to say so before the first touch. Goes before
 * `verticalScroll` in the chain, so `size` is the viewport.
 */
fun Modifier.verticalScrollbar(state: ScrollState, color: Color, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        if (state.maxValue == 0) return@drawWithContent
        val viewport = size.height
        val thumbHeight = viewport * viewport / (viewport + state.maxValue)
        val thumbTop = (viewport - thumbHeight) * state.value / state.maxValue
        val thumbWidth = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - thumbWidth, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2),
        )
    }
