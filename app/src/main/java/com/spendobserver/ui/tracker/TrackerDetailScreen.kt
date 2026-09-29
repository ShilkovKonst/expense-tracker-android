package com.spendobserver.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
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

    var expandedYears by remember { mutableStateOf(emptySet<Int>()) }
    var expandedMonths by remember { mutableStateOf(emptySet<Pair<Int, Int>>()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(trackerTitle ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
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
                                    RecordRow(record)
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
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text(if (expanded) "▾" else "▸") },
        headlineContent = {
            Text(yearGroup.year.toString(), fontWeight = FontWeight.Bold)
        },
        trailingContent = { NetAmountText(yearGroup.totals) },
    )
}

@Composable
private fun MonthHeader(monthGroup: MonthGroup, expanded: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(start = 16.dp),
        leadingContent = { Text(if (expanded) "▾" else "▸") },
        headlineContent = { Text(MonthNames[monthGroup.month - 1]) },
        trailingContent = { NetAmountText(monthGroup.totals) },
    )
}

@Composable
private fun RecordRow(record: Record) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(record.date.day.toString().padStart(2, '0'), modifier = Modifier.width(32.dp))
        Text(
            text = record.description.orEmpty(),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val (sign, color) = when (record.type) {
            RecordType.INCOME -> "+" to IncomeColor
            RecordType.COST -> "-" to MaterialTheme.colorScheme.error
        }
        Text("$sign${formatCents(record.amountCents)}", color = color)
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
    val sign = if (net > 0) "+" else ""
    Text("$sign${formatCents(net)}", color = color)
}

private fun formatCents(cents: Long): String {
    val negative = cents < 0
    val absCents = kotlin.math.abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}$whole.$fraction"
}
