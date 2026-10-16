package com.spendobserver.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.format.formatAmount
import com.spendobserver.domain.format.monthName
import com.spendobserver.domain.stats.PeriodStats
import com.spendobserver.domain.stats.StatsPeriod
import com.spendobserver.domain.stats.StatsSubject
import com.spendobserver.domain.stats.TagStats
import com.spendobserver.domain.stats.TagTops
import com.spendobserver.domain.summary.costsWord
import com.spendobserver.domain.summary.incomesWord
import com.spendobserver.domain.summary.recordsWord
import com.spendobserver.domain.summary.russianPlural
import com.spendobserver.ui.common.amountText
import com.spendobserver.ui.common.rememberFormatLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    trackerId: Long,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StatsViewModel = viewModel(
        factory = remember(trackerId, recordRepository, tagRepository) {
            viewModelFactory {
                initializer { StatsViewModel(trackerId, recordRepository, tagRepository) }
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val metric by viewModel.metric.collectAsStateWithLifecycle()
    var showSubjectPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Outside the list, so the way back up and the type stay on screen while scrolling.
            // 48dp minimum, the height of a crumb link: without it the row would
            // shrink on "Всё время", where the only crumb is plain text.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp).padding(end = 16.dp),
            ) {
                Breadcrumbs(current.period, onSelect = viewModel::setPeriod, modifier = Modifier.weight(1f))
                TypeToggle(current.type, current.availableTypes, onSelect = viewModel::setType)
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("statsList"),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = when (val subject = current.subject) {
                                    StatsSubject.Overview -> "Общая статистика"
                                    is StatsSubject.OfTag -> "Тег «${subject.tag.name}»"
                                } + " (${typeLabel(current.type).lowercase()})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).testTag("subjectTitle"),
                            )
                            SubjectField(
                                current.subject,
                                onClick = { showSubjectPicker = true },
                                modifier = Modifier.widthIn(max = 168.dp),
                            )
                        }
                        SummaryCard(current.type, current.subject, current.stats)
                        MetricToggle(metric, onSelect = viewModel::setMetric)
                        StatsChart(
                            period = current.period,
                            buckets = current.buckets,
                            metric = metric,
                            onDrillDown = viewModel::setPeriod,
                            modifier = Modifier.fillMaxWidth().testTag("statsChart"),
                        )
                    }
                }
            }
        }
        if (showSubjectPicker) {
            SubjectPickerDialog(
                tags = current.stats.tags,
                subject = current.subject,
                onSelect = { tagId ->
                    viewModel.selectTag(tagId)
                    showSubjectPicker = false
                },
                onDismiss = { showSubjectPicker = false },
            )
        }
    }
}

/**
 * "−" for costs, "+" for incomes — the signs amounts carry everywhere else
 * in the app; screen readers get the words. Compact (32dp, no check mark) so
 * it fits beside the breadcrumbs without making that row taller. A type with
 * no records in the tracker at all can't be picked — there'd be nothing to show.
 */
@Composable
private fun TypeToggle(type: RecordType, available: Set<RecordType>, onSelect: (RecordType) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
        for ((index, option) in listOf(RecordType.COST, RecordType.INCOME).withIndex()) {
            SegmentedButton(
                selected = type == option,
                onClick = { onSelect(option) },
                enabled = option in available,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                icon = {},
                contentPadding = PaddingValues(0.dp),
                // A fixed width overrides the 58dp Material minimum: a sign needs far less.
                modifier = Modifier
                    .width(48.dp)
                    .testTag(if (option == RecordType.COST) "costTypeButton" else "incomeTypeButton")
                    .semantics { contentDescription = typeLabel(option) },
            ) {
                Text(if (option == RecordType.COST) "−" else "+", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** `Всё время › 2026 › Октябрь`: every level above the current one is a link back to it. */
@Composable
private fun Breadcrumbs(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    val locale = rememberFormatLocale()
    val crumbs = buildList {
        add("Всё время" to StatsPeriod.AllTime)
        when (period) {
            StatsPeriod.AllTime -> Unit
            is StatsPeriod.Year -> add(period.year.toString() to period)
            is StatsPeriod.Month -> {
                add(period.year.toString() to StatsPeriod.Year(period.year))
                add(monthName(period.month, locale) to period)
            }
        }
    }
    FlowRow(
        modifier = modifier.padding(horizontal = 4.dp).testTag("breadcrumbs"),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (label, target) ->
            if (index > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (index == crumbs.lastIndex) {
                Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
            } else {
                TextButton(onClick = { onSelect(target) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun SummaryCard(type: RecordType, subject: StatsSubject, stats: PeriodStats) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("summaryCard")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (subject) {
                StatsSubject.Overview -> {
                    val total = stats.costCount + stats.incomeCount
                    Text(
                        "$total ${recordsWord(total)} " +
                            "(${stats.costCount} ${costsWord(stats.costCount)}, " +
                            "${stats.incomeCount} ${incomesWord(stats.incomeCount)})",
                    )
                    Text(
                        "${typeLabel(type)}: ${amountText(stats.amountCents)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    TopsBlock("Где/с кем", stats.globalTops)
                    TopsBlock("На что", stats.localTops)
                }
                is StatsSubject.OfTag -> {
                    val tagStats = stats.tags.first { it.tag.id == subject.tag.id }
                    val count = tagStats.recordCount
                    Text("Встречается в $count ${russianPlural(count, "записи", "записях", "записях")}")
                    Text("${typeLabel(type)}: ${amountText(tagStats.amountCents)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun typeLabel(type: RecordType) = when (type) {
    RecordType.COST -> "Расходы"
    RecordType.INCOME -> "Доходы"
}

/** Nothing at all when the level has no used tags — an empty "Где/с кем" would only be noise. */
@Composable
private fun TopsBlock(title: String, tops: TagTops) {
    if (tops.byCount.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    val locale = rememberFormatLocale()
    Text("Чаще всего: " + tops.byCount.joinToString { "${it.tag.name} (${it.recordCount})" })
    Text("Больше всего: " + tops.byAmount.joinToString { "${it.tag.name} (${formatAmount(it.amountCents, locale)})" })
}

/** A compact pill showing the current choice; a tap opens the picker over the screen. */
@Composable
private fun SubjectField(subject: StatsSubject, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
        modifier = modifier.height(32.dp).testTag("subjectField"),
    ) {
        Text(
            text = when (subject) {
                StatsSubject.Overview -> "Общая"
                is StatsSubject.OfTag -> subject.tag.name
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(" ▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Общая" first, then the tags grouped by level with their count and sum
 * for the current period; the selected row is highlighted, unused tags are
 * dimmed. [onSelect] gets null for the overview.
 */
@Composable
private fun SubjectPickerDialog(
    tags: List<TagStats>,
    subject: StatsSubject,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedId = (subject as? StatsSubject.OfTag)?.tag?.id
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Статистика",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false).testTag("subjectList")) {
                    item(key = "overview") {
                        SelectableRow(
                            title = "Общая",
                            trailing = null,
                            selected = selectedId == null,
                            dimmed = false,
                            onClick = { onSelect(null) },
                            modifier = Modifier.testTag("overviewRow"),
                        )
                    }
                    for ((title, level) in listOf("Где/с кем" to true, "На что" to false)) {
                        val levelTags = tags.filter { it.tag.isGlobal == level }
                        if (levelTags.isEmpty()) continue
                        item(key = "header-$level") { SectionHeader(title) }
                        items(levelTags, key = { "tag-${it.tag.id}" }) { stats ->
                            SelectableRow(
                                title = stats.tag.name,
                                trailing = "${stats.recordCount} · ${amountText(stats.amountCents)}",
                                selected = stats.tag.id == selectedId,
                                dimmed = stats.recordCount == 0,
                                onClick = { onSelect(stats.tag.id) },
                                modifier = Modifier.testTag("tagRow-${stats.tag.name}"),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
                ) { Text("Отмена") }
            }
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    trailing: String?,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(title, color = textColor, fontWeight = if (selected) FontWeight.SemiBold else null)
        },
        trailingContent = trailing?.let { { Text(it, color = textColor) } },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun MetricToggle(metric: ChartMetric, onSelect: (ChartMetric) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = metric == ChartMetric.AMOUNT,
            onClick = { onSelect(ChartMetric.AMOUNT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("Сумма") }
        SegmentedButton(
            selected = metric == ChartMetric.COUNT,
            onClick = { onSelect(ChartMetric.COUNT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("Записи") }
    }
}
