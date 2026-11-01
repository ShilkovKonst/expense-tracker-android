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
