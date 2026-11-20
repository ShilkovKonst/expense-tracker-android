package com.spendobserver.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Every screen's top bar: a [CenterAlignedTopAppBar] with a thin line under it,
 * so the bar stays apart from content of the same background color — and,
 * in two columns, the two bars read as one band above both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    height: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
) {
    Column(modifier = modifier) {
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            expandedHeight = height,
        )
        HorizontalDivider()
    }
}

/**
 * The height of a bar whose title is a title line over a small subtitle line: the
 * usual 64dp, taller once a large font size no longer fits both lines in it.
 * The record list and the statistics use the same one, so side by side their bars
 * stay one band.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun twoLineTopBarHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines = with(LocalDensity.current) {
        typography.titleLarge.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
    }
    return max(TopAppBarDefaults.TopAppBarExpandedHeight, lines + TwoLineBarPadding)
}

/** Room above and below the two lines, so they don't touch the bar's edges. */
private val TwoLineBarPadding = 16.dp
