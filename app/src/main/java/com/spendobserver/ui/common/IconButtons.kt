package com.spendobserver.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.spendobserver.R

/** The app bar's back arrow. */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
    }
}

/** Points right while collapsed, down while expanded. The row it sits in carries the meaning, so no description. */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(
        painterResource(if (expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}
