package com.spendobserver.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
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
import com.spendobserver.ui.common.amountText
import com.spendobserver.ui.common.rememberFormatLocale
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    trackerId: Long,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Overridable so a test's "all time" axis doesn't grow with the real calendar. */
    today: () -> LocalDate = ::systemToday,
) {
    val viewModel: StatsViewModel = viewModel(
        factory = remember(trackerId, recordRepository, tagRepository) {
            viewModelFactory {
                initializer { StatsViewModel(trackerId, recordRepository, tagRepository, today) }
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
                title = { Text(stringResource(R.string.stats_title)) },
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
            // A plain scrolling Column: it's one block of content, nothing for a lazy list to skip.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("statsList"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = when (val subject = current.subject) {
                            StatsSubject.Overview ->
                                stringResource(R.string.stats_subject_overview, inlineTypeLabel(current.type))
                            is StatsSubject.OfTag ->
                                stringResource(R.string.stats_subject_tag, subject.tag.name, inlineTypeLabel(current.type))
                        },
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
                    type = current.type,
                    onDrillDown = viewModel::setPeriod,
                    modifier = Modifier.fillMaxWidth().testTag("statsChart"),
                )
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
            // Read here: the semantics block below isn't composable.
            val description = typeLabel(option)
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
                    .semantics { contentDescription = description },
            ) {
                Text(if (option == RecordType.COST) "−" else "+", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val CRUMB_SEPARATOR = "›"
private val CrumbPadding = 8.dp

/**
 * `Всё время › 2026 › Октябрь`: every level above the current one is a link back to it.
 * Always one line: where the labels don't fit beside the type toggle (a month in
 * French or German), all crumbs shrink by the same factor rather than wrap.
 */
@Composable
private fun Breadcrumbs(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    val locale = rememberFormatLocale()
    val allTime = stringResource(R.string.stats_all_time)
    val crumbs = buildList {
        add(allTime to StatsPeriod.AllTime)
        when (period) {
            StatsPeriod.AllTime -> Unit
            is StatsPeriod.Year -> add(period.year.toString() to period)
            is StatsPeriod.Month -> {
                add(period.year.toString() to StatsPeriod.Year(period.year))
                add(monthName(period.month, locale) to period)
            }
        }
    }
    val linkStyle = MaterialTheme.typography.titleSmall
    val currentStyle = linkStyle.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.padding(horizontal = 4.dp).testTag("breadcrumbs")) {
        val density = LocalDensity.current
        val scale = remember(crumbs, constraints.maxWidth, linkStyle, density) {
            val textWidth = crumbs.withIndex().sumOf { (index, crumb) ->
                measurer.measure(crumb.first, if (index == crumbs.lastIndex) currentStyle else linkStyle).size.width
            } + (crumbs.size - 1) * measurer.measure(CRUMB_SEPARATOR, linkStyle).size.width
            val paddingWidth = with(density) { (CrumbPadding * 2).roundToPx() } * crumbs.size
            val fit = (constraints.maxWidth - paddingWidth).toFloat() / textWidth
            // A little short of the exact fit: glyph widths don't scale perfectly linearly.
            if (fit >= 1f) 1f else fit * 0.97f
        }
        val scaled = { style: TextStyle -> style.copy(fontSize = style.fontSize * scale) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            crumbs.forEachIndexed { index, (label, target) ->
                if (index > 0) {
                    Text(CRUMB_SEPARATOR, style = scaled(linkStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index == crumbs.lastIndex) {
                    Text(label, style = scaled(currentStyle), maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = CrumbPadding))
                } else {
                    // A bare clickable label, not a TextButton: its 58dp minimum width
                    // and wide padding would push the current crumb off the line.
                    Text(
                        label,
                        style = scaled(linkStyle),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button) { onSelect(target) }
                            .padding(horizontal = CrumbPadding)
                            .wrapContentHeight(),
                    )
                }
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
                        stringResource(
                            R.string.stats_counts,
                            pluralStringResource(R.plurals.records_count, total, total),
                            pluralStringResource(R.plurals.stats_costs_count, stats.costCount, stats.costCount),
                            pluralStringResource(R.plurals.stats_incomes_count, stats.incomeCount, stats.incomeCount),
                        ),
                    )
                    Text(typeAmount(type, stats.amountCents), fontWeight = FontWeight.SemiBold)
                    TopsBlock(stringResource(R.string.stats_level_global), stats.globalTops)
                    TopsBlock(stringResource(R.string.stats_level_local), stats.localTops)
                }
                is StatsSubject.OfTag -> {
                    val tagStats = stats.tags.first { it.tag.id == subject.tag.id }
                    val count = tagStats.recordCount
                    Text(pluralStringResource(R.plurals.stats_tag_found_in, count, count))
                    Text(typeAmount(type, tagStats.amountCents), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun typeLabel(type: RecordType) = stringResource(
    when (type) {
        RecordType.COST -> R.string.stats_costs
        RecordType.INCOME -> R.string.stats_incomes
    },
)

/** The type as it reads inside a title's parentheses: "(expenses)", "(расходы)". */
@Composable
private fun inlineTypeLabel(type: RecordType) = stringResource(
    when (type) {
        RecordType.COST -> R.string.stats_costs_inline
        RecordType.INCOME -> R.string.stats_incomes_inline
    },
)

/** "Expenses: 1,700.00" — a whole string per type, so each language places its own colon. */
@Composable
private fun typeAmount(type: RecordType, cents: Long) = stringResource(
    when (type) {
        RecordType.COST -> R.string.stats_costs_amount
        RecordType.INCOME -> R.string.stats_incomes_amount
    },
    amountText(cents),
)

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
    Text(stringResource(R.string.stats_most_often, tops.byCount.joinToString { "${it.tag.name} (${it.recordCount})" }))
    Text(
        stringResource(
            R.string.stats_most_amount,
            tops.byAmount.joinToString { "${it.tag.name} (${formatAmount(it.amountCents, locale)})" },
        ),
    )
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
                StatsSubject.Overview -> stringResource(R.string.stats_overview)
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
                    stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                // Read here: the LazyColumn builder below isn't composable.
                val levels = listOf(
                    stringResource(R.string.stats_level_global) to true,
                    stringResource(R.string.stats_level_local) to false,
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false).testTag("subjectList")) {
                    item(key = "overview") {
                        SelectableRow(
                            title = stringResource(R.string.stats_overview),
                            trailing = null,
                            selected = selectedId == null,
                            dimmed = false,
                            onClick = { onSelect(null) },
                            modifier = Modifier.testTag("overviewRow"),
                        )
                    }
                    for ((title, level) in levels) {
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
                ) { Text(stringResource(R.string.action_cancel)) }
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
        ) { Text(stringResource(R.string.stats_metric_amount)) }
        SegmentedButton(
            selected = metric == ChartMetric.COUNT,
            onClick = { onSelect(ChartMetric.COUNT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.stats_metric_records)) }
    }
}
