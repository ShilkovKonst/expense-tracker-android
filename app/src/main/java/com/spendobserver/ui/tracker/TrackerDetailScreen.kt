package com.spendobserver.ui.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spendobserver.ui.common.BackButton
import com.spendobserver.ui.common.ExpandChevron
import com.spendobserver.ui.theme.IncomeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.MonthGroup
import com.spendobserver.domain.summary.YearGroup
import com.spendobserver.ui.backup.ExportScope
import com.spendobserver.ui.backup.rememberExportActions
import com.spendobserver.ui.common.DeleteTrackerDialog
import com.spendobserver.ui.common.amountText
import com.spendobserver.ui.common.monthText
import com.spendobserver.ui.common.signedAmountText
import com.spendobserver.ui.navigation.RecordChange
import kotlinx.coroutines.launch

// Record-row typography: date, the "Теги:"/"Описание:" labels, and the
// amount all share one size+weight so they read as one line; month and
// year step up by +1sp each so the hierarchy (year > month > record row)
// is visible at a glance. All three levels use the same SemiBold weight —
// only the size changes.
private val RecordTextWeight = FontWeight.SemiBold
private val RecordTextSize = 14.sp
private val MonthTextSize = 15.sp
private val YearTextSize = 16.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: Long,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    backupRepository: BackupRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddRecordClick: () -> Unit = {},
    onRecordClick: (Record) -> Unit = {},
    onEditTrackerClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onTrackerDeleted: () -> Unit = {},
    recordChange: RecordChange? = null,
    onRecordChangeConsumed: () -> Unit = {},
) {
    val viewModel: TrackerDetailViewModel = viewModel(
        factory = remember(trackerId, trackerRepository, recordRepository) {
            viewModelFactory {
                initializer { TrackerDetailViewModel(trackerId, trackerRepository, recordRepository) }
            }
        },
    )
    val trackerTitle by viewModel.trackerTitle.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val focusTarget by viewModel.focusTarget.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showDeleteTrackerDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportActions = rememberExportActions(backupRepository) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val exportScope = ExportScope.One(trackerId, trackerTitle.orEmpty())

    // A save/delete result from the record form: hand it to the ViewModel
    // and clear it from the back-stack entry, so it fires exactly once.
    LaunchedEffect(recordChange) {
        if (recordChange != null) {
            viewModel.onRecordChanged(recordChange)
            onRecordChangeConsumed()
        }
    }
    // Re-runs as `items` updates: the target row may only appear after the
    // expansion and Room's re-emission have both reached the list.
    LaunchedEffect(focusTarget, items) {
        when (val target = focusTarget) {
            null -> Unit
            FocusTarget.None -> viewModel.onFocusHandled()
            is FocusTarget.Item -> {
                val index = items.indexOfFirst { it.key == target.key }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                    viewModel.onFocusHandled()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(trackerTitle ?: "")
                        Text(
                            text = pluralStringResource(R.plurals.records_count, summary.recordCount, summary.recordCount) +
                                " · " + signedAmountText(summary.totals.netCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onClick = onBackClick)
                },
                // One overflow menu, like the tracker list's, instead of a row of icons
                // squeezing the title.
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("trackerMenuButton")) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            /** A menu entry that closes the menu before acting. */
                            @Composable
                            fun item(label: String, tag: String, action: () -> Unit) = DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showMenu = false
                                    action()
                                },
                                modifier = Modifier.testTag(tag),
                            )
                            item(stringResource(R.string.menu_stats), "statsItem", onStatsClick)
                            item(stringResource(R.string.menu_save_to_file), "exportSaveItem") { exportActions.save(exportScope) }
                            item(stringResource(R.string.menu_share), "exportShareItem") { exportActions.share(exportScope) }
                            HorizontalDivider()
                            item(stringResource(R.string.menu_edit), "editTrackerItem", onEditTrackerClick)
                            item(stringResource(R.string.action_delete), "deleteTrackerItem") { showDeleteTrackerDialog = true }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRecordClick,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.record_add)) },
            )
        },
    ) { innerPadding ->
        if (summary.recordCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tracker_no_records))
            }
        } else {
            LazyColumn(
                state = listState,
                // Room below the last row for the FAB (56dp + 16dp margins),
                // so the last record's amount can scroll out from under it.
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                // contentType: a scrolled-off record row is reused only for another record, not rebuilt as a header.
                items(items, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is AccordionItem.Year -> YearHeader(
                            yearGroup = item.group,
                            expanded = item.expanded,
                            onClick = { viewModel.toggleYear(item.group.year) },
                        )
                        is AccordionItem.Month -> MonthHeader(
                            monthGroup = item.group,
                            expanded = item.expanded,
                            onClick = { viewModel.toggleMonth(item.group.year, item.group.month) },
                        )
                        is AccordionItem.RecordEntry -> RecordRow(
                            details = item.record,
                            partsExpanded = item.partsExpanded,
                            onTogglePartsClick = { viewModel.toggleRecordParts(item.record.record.id) },
                            onClick = { onRecordClick(item.record.record) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteTrackerDialog) {
        DeleteTrackerDialog(
            trackerTitle = trackerTitle.orEmpty(),
            onConfirm = {
                showDeleteTrackerDialog = false
                viewModel.deleteTracker(onDeleted = onTrackerDeleted)
            },
            onDismiss = { showDeleteTrackerDialog = false },
        )
    }
}

@Composable
private fun YearHeader(yearGroup: YearGroup, expanded: Boolean, onClick: () -> Unit) {
    val count = yearGroup.months.sumOf { it.records.size }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { ExpandChevron(expanded) },
        headlineContent = {
            Text(yearGroup.year.toString(), fontWeight = RecordTextWeight, fontSize = YearTextSize)
        },
        supportingContent = { Text(pluralStringResource(R.plurals.records_count, count, count)) },
        trailingContent = { NetAmountText(yearGroup.totals) },
    )
}

@Composable
private fun MonthHeader(monthGroup: MonthGroup, expanded: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(start = 16.dp),
        leadingContent = { ExpandChevron(expanded) },
        headlineContent = {
            Text(monthText(monthGroup.month), fontWeight = RecordTextWeight, fontSize = MonthTextSize)
        },
        supportingContent = {
            Text(pluralStringResource(R.plurals.records_count, monthGroup.records.size, monthGroup.records.size))
        },
        trailingContent = { NetAmountText(monthGroup.totals) },
    )
}

/**
 * A record's preview row in three columns, each confined to its own width
 * so text never runs into a neighbor:
 *
 * 1. the day of month, fixed width;
 * 2. the data — tags, the parts with their subtotals, the description —
 *    taking whatever width is left (`weight(1f)`), so anything long wraps
 *    *inside* this column;
 * 3. the record's signed total.
 *
 * Columns 1 and 3 are measured first and the middle one gets the rest,
 * which is what keeps it from overlapping them. The date, the "Теги: "
 * label, and the total share [RecordTextSize]/[RecordTextWeight] and are
 * baseline-aligned: the middle [Column] reports its first line's baseline.
 *
 * A one-part record shows a single tags line (global tags highlighted).
 * A split record shows its global tags, then a collapsed "▸ N частей"
 * toggle; expanded, one line per part with its local tags and subtotal.
 * The toggle has its own click handler, so tapping it expands the parts
 * instead of opening the record.
 */
@Composable
private fun RecordRow(
    details: RecordWithParts,
    partsExpanded: Boolean,
    onTogglePartsClick: () -> Unit,
    onClick: () -> Unit,
) {
    val record = details.record
    val isSplit = details.parts.size > 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = record.date.day.toString().padStart(2, '0'),
            fontWeight = RecordTextWeight,
            fontSize = RecordTextSize,
            modifier = Modifier.width(32.dp).alignByBaseline(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp)
                .alignByBaseline(),
        ) {
            val globalChips = details.tags.map { TagChip(it.name, global = true) }
            val headerChips = if (isSplit) globalChips else globalChips + details.parts.single().tags.map { TagChip(it.name) }
            LabeledTagsField(stringResource(R.string.record_tags_label), headerChips)
            if (isSplit) {
                PartsToggle(
                    count = details.parts.size,
                    expanded = partsExpanded,
                    onClick = onTogglePartsClick,
                    modifier = Modifier.testTag("partsToggle-${record.id}"),
                )
                if (partsExpanded) {
                    for (part in details.parts) {
                        PartLine(tagNames = part.tags.map { it.name }, amountCents = part.part.amountCents)
                    }
                }
            }
            DescriptionLine(text = record.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.record_no_description))
        }
        val (signedCents, color) = when (record.type) {
            RecordType.INCOME -> details.amountCents to IncomeColor
            RecordType.COST -> -details.amountCents to MaterialTheme.colorScheme.error
        }
        Text(
            text = signedAmountText(signedCents),
            color = color,
            fontWeight = RecordTextWeight,
            fontSize = RecordTextSize,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun PartsToggle(count: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
    ) {
        ExpandChevron(expanded, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = pluralStringResource(R.plurals.parts_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private data class TagChip(val name: String, val global: Boolean = false)

/** One part of a split record: its tags, and its subtotal pinned to the right edge of the data column. */
@Composable
private fun PartLine(tagNames: List<String>, amountCents: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f).padding(end = 8.dp).alignByBaseline()) {
            if (tagNames.isEmpty()) {
                Text(
                    stringResource(R.string.part_no_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TagChips(tagNames.map { TagChip(it) })
            }
        }
        Text(
            text = amountText(amountCents),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun DescriptionLine(text: String, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.record_description_label)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = RecordTextWeight, fontSize = RecordTextSize, color = labelColor)) {
                append(label)
                append(" ")
            }
            append(text)
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * "Теги: " baseline-aligned with the first line of chips (not vertically
 * centered — with several tags wrapping to a second [FlowRow] line,
 * centering would float the label away from the first line it's
 * labeling). The field's own first baseline is the label's, so callers
 * can line it up with the date and amount via `alignByBaseline()`.
 */
@Composable
private fun LabeledTagsField(label: String, chips: List<TagChip>, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Text(
            text = "$label ",
            fontWeight = RecordTextWeight,
            fontSize = RecordTextSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        if (chips.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alignByBaseline())
        } else {
            TagChips(chips, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Global tags are filled, local ones outlined — the same split as the form's two tag sections. */
@Composable
private fun TagChips(chips: List<TagChip>, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        for (chip in chips) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (chip.global) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = chip.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
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
    Text(signedAmountText(net), color = color)
}
