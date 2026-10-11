package com.spendobserver.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.calculator.amountCentsFrom
import com.spendobserver.domain.record.PartInput
import com.spendobserver.domain.record.PartsError
import com.spendobserver.domain.record.PartsEvaluation
import com.spendobserver.domain.summary.formatCents
import com.spendobserver.ui.common.AlwaysLabeledTextField
import com.spendobserver.ui.common.LabeledOutlinedBox
import com.spendobserver.ui.common.TagNameDialog
import com.spendobserver.ui.navigation.RecordChange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Where a tag created from the "+" chip goes: the record (global) or one part (local). */
private data class NewTagTarget(val isGlobal: Boolean, val partId: Int? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    trackerId: Long,
    recordId: Long?,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    onSaved: (RecordChange) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecordFormViewModel = viewModel(
        factory = remember(trackerId, recordId, recordRepository, tagRepository) {
            viewModelFactory {
                initializer { RecordFormViewModel(trackerId, recordId, recordRepository, tagRepository) }
            }
        },
    )

    val type by viewModel.type.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val evaluation by viewModel.evaluation.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    val globalTagIds by viewModel.globalTagIds.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagNameError by viewModel.tagNameError.collectAsStateWithLifecycle()
    val globalTags = tags.filter { it.isGlobal }
    val localTags = tags.filterNot { it.isGlobal }
    val errors = if (showErrors) evaluation.errors else emptyList()

    var showDatePicker by remember { mutableStateOf(false) }
    var newTagTarget by remember { mutableStateOf<NewTagTarget?>(null) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var tagToRename by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var showDeleteRecordDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (viewModel.isEditing) "Редактировать запись" else "Новая запись") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Text("←") }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(
                            onClick = { showDeleteRecordDialog = true },
                            modifier = Modifier.testTag("deleteRecordButton"),
                        ) { Text("🗑") }
                    }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == RecordType.COST,
                    onClick = { viewModel.setType(RecordType.COST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Расход") }
                SegmentedButton(
                    selected = type == RecordType.INCOME,
                    onClick = { viewModel.setType(RecordType.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Доход") }
            }

            OutlinedTextField(
                value = formatDate(date),
                onValueChange = {},
                // enabled = false, not readOnly = true: a read-only field
                // still runs its own tap-to-focus/cursor gesture handling,
                // which wins over a plain Modifier.clickable wrapped around
                // it — so the dialog below never opened and the date stayed
                // stuck on today. Disabling the field entirely stops it
                // from touching input at all, leaving our clickable as the
                // only handler; the disabled-state colors are overridden
                // below so it still looks like a normal field, not greyed
                // out.
                enabled = false,
                label = { Text("Дата") },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .testTag("dateInput"),
            )

            // One row per tag level: global first, then — for a simple record —
            // local. A split record's local tags belong to each part, inside
            // its card, so then only the global row is here.
            TagChipsSection(
                title = "Общие теги",
                hint = "где, с кем",
                tags = globalTags,
                selectedIds = globalTagIds,
                onToggle = viewModel::toggleGlobalTag,
                onNewTag = { newTagTarget = NewTagTarget(isGlobal = true) },
                newTagChipTestTag = "newGlobalTagChip",
            )
            if (!parts.isSplit) {
                val only = parts.parts.single()
                TagChipsSection(
                    title = "Теги",
                    hint = "на что",
                    tags = localTags,
                    selectedIds = only.tagIds,
                    onToggle = { viewModel.togglePartTag(only.id, it) },
                    onNewTag = { newTagTarget = NewTagTarget(isGlobal = false, partId = only.id) },
                    newTagChipTestTag = "newTagChip",
                )
            }

            if (tags.isNotEmpty()) {
                TextButton(
                    onClick = { showManageTagsDialog = true },
                    modifier = Modifier.testTag("manageTagsButton"),
                ) { Text("Управление тегами") }
            }

            if (!parts.isSplit) {
                // Simple record: one amount field, exactly like before parts existed.
                val only = parts.parts.single()
                Column {
                    AmountField(
                        value = only.amountExpression,
                        onValueChange = { viewModel.setPartAmount(only.id, it) },
                        label = "Сумма",
                        error = errors.amountErrorFor(only.id),
                        modifier = Modifier.testTag("amountInput"),
                    )
                    TextButton(
                        onClick = viewModel::split,
                        modifier = Modifier.testTag("splitButton"),
                    ) { Text("Разбить по частям") }
                }
            } else {
                ReceiptTotalSection(
                    expression = parts.receiptTotalExpression,
                    errors = errors,
                    onChange = viewModel::setReceiptTotal,
                    onAdd = { viewModel.setReceiptTotal("") },
                    onClear = viewModel::clearReceiptTotal,
                )
            }

            if (parts.isSplit) {
                parts.parts.forEachIndexed { index, part ->
                    PartCard(
                        index = index,
                        part = part,
                        isRemainder = part.id == parts.remainderPartId,
                        canRemove = parts.parts.size > 1 && part.id != parts.remainderPartId,
                        evaluation = evaluation,
                        errors = errors,
                        localTags = localTags,
                        onAmountChange = { viewModel.setPartAmount(part.id, it) },
                        onToggleTag = { viewModel.togglePartTag(part.id, it) },
                        onNewTag = { newTagTarget = NewTagTarget(isGlobal = false, partId = part.id) },
                        onRemove = { viewModel.removePart(part.id) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = viewModel::addPart,
                        modifier = Modifier.testTag("addPartButton"),
                    ) { Text("+ Часть") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Итого: ${evaluation.totalCents?.let(::formatCents) ?: "—"}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("recordTotal"),
                    )
                }
            }

            AlwaysLabeledTextField(
                value = description,
                onValueChange = viewModel::setDescription,
                label = "Описание",
                placeholder = "Опционально",
                modifier = Modifier.testTag("descriptionInput"),
            )

            Button(
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.fillMaxWidth().testTag("saveButton"),
            ) { Text("Сохранить") }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setDate(it.toLocalDateFromUtcMillis()) }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showManageTagsDialog) {
        AlertDialog(
            onDismissRequest = { showManageTagsDialog = false },
            title = { Text("Управление тегами") },
            text = {
                Column {
                    for (tag in tags) {
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            supportingContent = if (tag.isGlobal) {
                                { Text("общий") }
                            } else {
                                null
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            showManageTagsDialog = false
                                            tagToRename = tag
                                        },
                                        modifier = Modifier.testTag("renameTagButton-${tag.name}"),
                                    ) { Text("✎") }
                                    IconButton(
                                        onClick = {
                                            showManageTagsDialog = false
                                            tagToDelete = tag
                                        },
                                        modifier = Modifier.testTag("deleteTagButton-${tag.name}"),
                                    ) { Text("🗑") }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageTagsDialog = false }) { Text("Закрыть") }
            },
        )
    }

    newTagTarget?.let { target ->
        TagNameDialog(
            title = if (target.isGlobal) "Новый общий тег" else "Новый тег",
            initialName = "",
            confirmLabel = "Создать",
            onConfirm = { name ->
                viewModel.createTag(name, isGlobal = target.isGlobal, forPartId = target.partId) { newTagTarget = null }
            },
            onDismiss = {
                newTagTarget = null
                viewModel.clearTagNameError()
            },
            error = tagNameError,
            onTextChange = viewModel::clearTagNameError,
        )
    }

    tagToRename?.let { tag ->
        TagNameDialog(
            title = "Переименовать тег",
            initialName = tag.name,
            confirmLabel = "Сохранить",
            onConfirm = { newName -> viewModel.renameTag(tag, newName) { tagToRename = null } },
            onDismiss = {
                tagToRename = null
                viewModel.clearTagNameError()
            },
            error = tagNameError,
            onTextChange = viewModel::clearTagNameError,
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("Удалить тег «${tag.name}»?") },
            text = { Text("Тег будет отвязан от всех записей, где он использовался.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag)
                    tagToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (showDeleteRecordDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRecordDialog = false },
            title = { Text("Удалить запись?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteRecordDialog = false
                    viewModel.delete(onSaved)
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRecordDialog = false }) { Text("Отмена") }
            },
        )
    }
}

/** A calculator-backed amount field with the evaluated "= 150.00" preview underneath. */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text("120+50/2") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = error != null,
            modifier = modifier.fillMaxWidth(),
        )
        val preview = amountCentsFrom(value)
        if (preview != null && value != formatCents(preview)) {
            Text("= ${formatCents(preview)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ReceiptTotalSection(
    expression: String?,
    errors: List<PartsError>,
    onChange: (String) -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
) {
    if (expression == null) {
        TextButton(
            onClick = onAdd,
            modifier = Modifier.testTag("addReceiptTotalButton"),
        ) { Text("+ Итог по чеку") }
        return
    }
    Column {
        AmountField(
            value = expression,
            onValueChange = onChange,
            label = "Итог по чеку",
            error = when {
                PartsError.InvalidReceiptTotal in errors -> "Введите итог чека"
                PartsError.PartsExceedReceiptTotal in errors -> "Сумма частей должна быть меньше итога чека"
                else -> null
            },
            modifier = Modifier.testTag("receiptTotalInput"),
        )
        TextButton(
            onClick = onClear,
            modifier = Modifier.testTag("clearReceiptTotalButton"),
        ) { Text("Убрать итог") }
    }
}

/**
 * One part in split mode. The remainder part (last, when there's a
 * receipt total) shows its computed amount instead of an input.
 */
@Composable
private fun PartCard(
    index: Int,
    part: PartInput,
    isRemainder: Boolean,
    canRemove: Boolean,
    evaluation: PartsEvaluation,
    errors: List<PartsError>,
    localTags: List<Tag>,
    onAmountChange: (String) -> Unit,
    onToggleTag: (Long) -> Unit,
    onNewTag: () -> Unit,
    onRemove: () -> Unit,
) {
    LabeledOutlinedBox(
        label = if (isRemainder) "Остаток" else "Часть ${index + 1}",
        modifier = Modifier.testTag("partCard-$index"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRemainder) {
                Text(
                    text = evaluation.partCents[part.id]?.let(::formatCents) ?: "—",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("remainderAmount"),
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.weight(1f)) {
                        AmountField(
                            value = part.amountExpression,
                            onValueChange = onAmountChange,
                            label = "Сумма",
                            error = errors.amountErrorFor(part.id),
                            modifier = Modifier.testTag("partAmountInput-$index"),
                        )
                    }
                    if (canRemove) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.padding(top = 4.dp).testTag("removePartButton-$index"),
                        ) { Text("✕") }
                    }
                }
            }
            TagChips(
                tags = localTags,
                selectedIds = part.tagIds,
                onToggle = onToggleTag,
                onNewTag = onNewTag,
                newTagChipTestTag = "newTagChip-$index",
            )
            if (errors.any { it is PartsError.DuplicateTags && part.id in it.partIds }) {
                Text("Часть с такими же тегами уже есть", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TagChipsSection(
    title: String,
    hint: String,
    tags: List<Tag>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onNewTag: () -> Unit,
    newTagChipTestTag: String,
    modifier: Modifier = Modifier,
) {
    // Same border-cut label as the date, amount, and description fields.
    LabeledOutlinedBox(label = "$title — $hint", modifier = modifier) {
        TagChips(
            tags = tags,
            selectedIds = selectedIds,
            onToggle = onToggle,
            onNewTag = onNewTag,
            newTagChipTestTag = newTagChipTestTag,
        )
    }
}

/**
 * A tag level's chips in two columns: the tags on the left (wrapping
 * within that column, or a placeholder when there are none yet) and the
 * "+" (new tag) button on the right, so the button always sits in the
 * same place instead of trailing after however many tags there are.
 */
@Composable
private fun TagChips(
    tags: List<Tag>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onNewTag: () -> Unit,
    newTagChipTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (tags.isEmpty()) {
            // Centered within a chip's 48dp touch-target height, the same
            // band the "+" chip centers its visible 32dp in.
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Тегов пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Selection only — no per-chip menu: a compact FilterChip's own
                // touch target and a nested IconButton's (48dp-minimum) touch
                // target overlap too closely to hit reliably, so rename/delete
                // live in the full-width rows of the "Управление тегами" dialog
                // instead (same layout TrackerListScreen's tracker rows already
                // use safely, since there the icon sits far from the label).
                for (tag in tags) {
                    FilterChip(
                        selected = tag.id in selectedIds,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Just "+": in a tags box it's clear what gets added; screen
        // readers still get the full action from the content description.
        FilterChip(
            selected = false,
            onClick = onNewTag,
            label = { Text("+") },
            modifier = Modifier
                .testTag(newTagChipTestTag)
                .semantics { contentDescription = "Новый тег" },
        )
    }
}

private fun List<PartsError>.amountErrorFor(partId: Int): String? =
    if (PartsError.InvalidAmount(partId) in this) "Введите сумму больше нуля" else null

private fun formatDate(date: LocalDate): String {
    val dd = date.day.toString().padStart(2, '0')
    val mm = date.month.number.toString().padStart(2, '0')
    return "$dd.$mm.${date.year}"
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toUtcMillis(): Long = toEpochDays() * MILLIS_PER_DAY

private fun Long.toLocalDateFromUtcMillis(): LocalDate = LocalDate.fromEpochDays((this / MILLIS_PER_DAY).toInt())
