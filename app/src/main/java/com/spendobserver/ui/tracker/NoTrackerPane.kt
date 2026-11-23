package com.spendobserver.ui.tracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendobserver.R
import com.spendobserver.ui.common.AppTopBar
import com.spendobserver.ui.common.twoLineTopBarHeight

/**
 * The right column beside the tracker list while no tracker is open there.
 * Its empty top bar is the records' height, so the bar's lower edge runs
 * straight across both columns and stays put when a tracker opens.
 */
@Composable
fun NoTrackerPane(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("noTrackerPane"),
        topBar = { AppTopBar(title = {}, height = twoLineTopBarHeight()) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tracker_pick_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
