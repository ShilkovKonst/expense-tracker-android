package com.spendobserver.ui.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.MonthGroup
import com.spendobserver.domain.summary.YearGroup
import com.spendobserver.domain.summary.formatSignedCents
import com.spendobserver.domain.summary.recordsWord

private val MonthNames = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val IncomeColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: Long,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    onBackClick: () -> Unit,
    onAddRecordClick: () -> Unit = {},
    onRecordClick: (Record) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: TrackerDetailViewModel = viewModel(
        factory = remember(trackerId, trackerRepository, recordRepository) {
            viewModelFactory {
                initializer { TrackerDetailViewModel(trackerId, trackerRepository, recordRepository) }
            }
        },
    )
    val trackerTitle by viewModel.trackerTitle.collectAsStateWithLifecycle()
    val yearGroups by viewModel.yearGroups.collectAsStateWithLifecycle()
    val tagNamesByRecordId by viewModel.tagNamesByRecordId.collectAsStateWithLifecycle()

    var expandedYears by remember { mutableStateOf(emptySet<Int>()) }
    var expandedMonths by remember { mutableStateOf(emptySet<Pair<Int, Int>>()) }

    val recordCount = yearGroups.sumOf { yg -> yg.months.sumOf { it.records.size } }
    val totalNet = yearGroups.sumOf { it.totals.netCents }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(trackerTitle ?: "")
                        Text(
                            text = "$recordCount ${recordsWord(recordCount)} · ${formatSignedCents(totalNet)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecordClick) {
                Text("+")
            }
        },
    ) { innerPadding ->
        if (yearGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Записей пока нет")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                for (yearGroup in yearGroups) {
                    val yearExpanded = yearGroup.year in expandedYears
                    item(key = "year-${yearGroup.year}") {
                        YearHeader(
                            yearGroup = yearGroup,
                            expanded = yearExpanded,
                            onClick = {
                                expandedYears = if (yearExpanded) {
                                    expandedYears - yearGroup.year
                                } else {
                                    expandedYears + yearGroup.year
                                }
                            },
                        )
                    }
                    if (yearExpanded) {
                        for (monthGroup in yearGroup.months) {
                            val monthKey = yearGroup.year to monthGroup.month
                            val monthExpanded = monthKey in expandedMonths
                            item(key = "month-${monthGroup.year}-${monthGroup.month}") {
                                MonthHeader(
                                    monthGroup = monthGroup,
                                    expanded = monthExpanded,
                                    onClick = {
                                        expandedMonths = if (monthExpanded) {
                                            expandedMonths - monthKey
                                        } else {
                                            expandedMonths + monthKey
                                        }
                                    },
                                )
                            }
                            if (monthExpanded) {
                                items(monthGroup.records, key = { "record-${it.id}" }) { record ->
                                    RecordRow(
                                        record = record,
                                        tagNames = tagNamesByRecordId[record.id] ?: emptyList(),
                                        onClick = { onRecordClick(record) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(yearGroup: YearGroup, expanded: Boolean, onClick: () -> Unit) {
    val count = yearGroup.months.sumOf { it.records.size }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text(if (expanded) "▾" else "▸") },
        headlineContent = {
            Text(yearGroup.year.toString(), fontWeight = FontWeight.Bold)
        },
        supportingContent = { Text("$count ${recordsWord(count)}") },
        trailingContent = { NetAmountText(yearGroup.totals) },
    )
}

@Composable
private fun MonthHeader(monthGroup: MonthGroup, expanded: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(start = 16.dp),
        leadingContent = { Text(if (expanded) "▾" else "▸") },
        headlineContent = { Text(MonthNames[monthGroup.month - 1]) },
        supportingContent = { Text("${monthGroup.records.size} ${recordsWord(monthGroup.records.size)}") },
        trailingContent = { NetAmountText(monthGroup.totals) },
    )
}

/**
 * A record's preview row, two tiers: day number + tags + signed amount on
 * one line (tags wrap to more lines as needed, without pushing the amount
 * down — see [LabeledTagsField]), the description on its own full-width
 * line below. A single [Text] carries the "Описание: " label inline with
 * the value (see [DescriptionLine]) so wrapped lines run the full row
 * width instead of being squeezed into whatever's left of a fixed-width
 * label column — the label only costs space on its own first line.
 */
@Composable
private fun RecordRow(record: Record, tagNames: List<String>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = record.date.day.toString().padStart(2, '0'),
                modifier = Modifier.width(32.dp),
            )
            LabeledTagsField("Теги", tagNames, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Spacer(modifier = Modifier.width(8.dp))
            val (signedCents, color) = when (record.type) {
                RecordType.INCOME -> record.amountCents to IncomeColor
                RecordType.COST -> -record.amountCents to MaterialTheme.colorScheme.error
            }
            Text(
                text = formatSignedCents(signedCents),
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
        DescriptionLine(
            text = record.description?.takeIf { it.isNotBlank() } ?: "без описания",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DescriptionLine(text: String, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = labelColor)) {
                append("Описание: ")
            }
            append(text)
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * "Теги: " pinned to the top-left of the cell (not vertically centered —
 * with several tags wrapping to a second [FlowRow] line, centering would
 * float the label away from the first line of chips it's labeling).
 */
@Composable
private fun LabeledTagsField(label: String, tagNames: List<String>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tagNames.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (name in tagNames) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetAmountText(totals: AmountTotals) {
    val net = totals.netCents
    val color = when {
        net > 0 -> IncomeColor
        net < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(formatSignedCents(net), color = color)
}
