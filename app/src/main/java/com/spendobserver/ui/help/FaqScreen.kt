package com.spendobserver.ui.help

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendobserver.ui.common.BackButton
import com.spendobserver.ui.common.ExpandChevron
import com.spendobserver.R

/** One question of the FAQ and its answer, one paragraph per array item. */
internal data class FaqEntry(@StringRes val question: Int, @ArrayRes val answer: Int)

internal val FAQ = listOf(
    FaqEntry(R.string.faq_trackers_q, R.array.faq_trackers_a),
    FaqEntry(R.string.faq_records_q, R.array.faq_records_a),
    FaqEntry(R.string.faq_parts_q, R.array.faq_parts_a),
    FaqEntry(R.string.faq_tag_levels_q, R.array.faq_tag_levels_a),
    FaqEntry(R.string.faq_tag_manage_q, R.array.faq_tag_manage_a),
    FaqEntry(R.string.faq_calculator_q, R.array.faq_calculator_a),
    FaqEntry(R.string.faq_stats_q, R.array.faq_stats_a),
    FaqEntry(R.string.faq_export_q, R.array.faq_export_a),
    FaqEntry(R.string.faq_import_q, R.array.faq_import_a),
)

/**
 * Usage questions, each expandable to its answer — a separate screen from
 * [HelpScreen], reached by its own item in the tracker list's menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.faq_title)) },
                navigationIcon = {
                    BackButton(onClick = onBackClick)
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .testTag("faqContent"),
        ) {
            FAQ.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                FaqItem(entry, tag = "faqItem-$index")
            }
        }
    }
}

@Composable
private fun FaqItem(entry: FaqEntry, tag: String) {
    // Saveable, so open answers survive rotation and the trip back from another screen.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.testTag(tag)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                stringResource(entry.question),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ExpandChevron(
                expanded = expanded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stringArrayResource(entry.answer).forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
