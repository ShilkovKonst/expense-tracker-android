package com.spendobserver.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Background for the [index]-th block of a list: every other one a shade off
 * the screen's own background, barely enough to tell them apart. In the dark
 * theme a neutral overlay: the scheme's own container color can come out with a yellow tint there.
 */
@Composable
fun alternatingRowColor(index: Int): Color {
    if (index % 2 != 0) return Color.Transparent
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.5f) Color.White.copy(alpha = 0.03f) else colors.surfaceContainerLow
}

/**
 * Background for a block nested [level] steps deep (0 = the screen's own background):
 * each step a shade lighter in the dark theme, darker in the light one. Neutral overlays,
 * for the same reason as [alternatingRowColor].
 */
@Composable
fun nestedLevelColor(level: Int): Color {
    if (level <= 0) return Color.Transparent
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Half a step for an open year's months, a full one for an open month's records.
    val step = if (level == 1) 0.5f else 1f
    return if (dark) Color.White.copy(alpha = 0.04f * step) else Color.Black.copy(alpha = 0.03f * step)
}
