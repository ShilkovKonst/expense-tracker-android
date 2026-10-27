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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.calculator.CalculatorMode
import com.spendobserver.domain.calculator.amountCentsFrom
import com.spendobserver.domain.record.PartInput
import com.spendobserver.domain.record.PartsError
import com.spendobserver.domain.record.PartsEvaluation
import com.spendobserver.domain.summary.formatCents
import com.spendobserver.ui.common.AddChip
import com.spendobserver.ui.common.AlwaysLabeledTextField
import com.spendobserver.ui.common.FormActionButtons
import com.spendobserver.ui.common.FormActionsBottomPadding
import com.spendobserver.ui.common.LabeledOutlinedBox
import com.spendobserver.ui.common.TagNameDialog
import com.spendobserver.ui.common.amountText
import com.spendobserver.ui.common.dateText
import com.spendobserver.ui.navigation.RecordChange
import com.spendobserver.ui.common.tagNameText
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
    // Kept across recompositions (every keystroke here): fresh lists would redraw every tag row.
    val (globalTags, localTags) = remember(tags) { tags.partition { it.isGlobal } }
    val errors = if (showErrors) evaluation.errors else emptyList()

    var showDatePicker by remember { mutableStateOf(false) }
    var newTagTarget by remember { mutableStateOf<NewTagTarget?>(null) }
    var showDeleteRecordDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (viewModel.isEditing) R.string.record_edit_title else R.string.record_new_title)) },
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
        floatingActionButton = {
            FormActionButtons(
                onCancel = onBackClick,
                onSave = { viewModel.save(onSaved) },
                saveTestTag = "saveButton",
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = FormActionsBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == RecordType.COST,
                    onClick = { viewModel.setType(RecordType.COST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.record_type_cost)) }
                SegmentedButton(
                    selected = type == RecordType.INCOME,
                    onClick = { viewModel.setType(RecordType.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.record_type_income)) }
            }

            OutlinedTextField(
                value = dateText(date),
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
                label = { Text(stringResource(R.string.record_date_label)) },
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
                title = stringResource(R.string.tags_global_title),
                hint = stringResource(R.string.tags_global_hint),
                tags = globalTags,
                selectedIds = globalTagIds,
                onToggle = viewModel::toggleGlobalTag,
                onNewTag = { newTagTarget = NewTagTarget(isGlobal = true) },
                newTagChipTestTag = "newGlobalTagChip",
            )
            if (!parts.isSplit) {
                val only = parts.parts.single()
                TagChipsSection(
                    title = stringResource(R.string.tags_local_title),
                    hint = stringResource(R.string.tags_local_hint),
                    tags = localTags,
                    selectedIds = only.tagIds,
                    onToggle = { viewModel.togglePartTag(only.id, it) },
                    onNewTag = { newTagTarget = NewTagTarget(isGlobal = false, partId = only.id) },
                    newTagChipTestTag = "newTagChip",
                )
            }

            if (!parts.isSplit) {
                // Simple record: one amount field, exactly like before parts existed.
                val only = parts.parts.single()
                Column {
                    AmountField(
                        value = only.amountExpression,
                        onValueChange = { viewModel.setPartAmount(only.id, it) },
                        label = stringResource(R.string.amount_label),
                        error = errors.amountErrorFor(only.id),
                        mode = viewModel.calculatorMode,
                        modifier = Modifier.testTag("amountInput"),
                    )
                    TextButton(
                        onClick = viewModel::split,
                        modifier = Modifier.testTag("splitButton"),
                    ) { Text(stringResource(R.string.record_split)) }
                }
            } else {
                ReceiptTotalSection(
                    expression = parts.receiptTotalExpression,
                    errors = errors,
                    mode = viewModel.calculatorMode,
                    onChange = viewModel::setReceiptTotal,
                    onAdd = { viewModel.setReceiptTotal("") },
                    onClear = viewModel::clearReceiptTotal,
                )
            }

            if (parts.isSplit) {
                val addPartButton = @Composable {
                    TextButton(
                        onClick = viewModel::addPart,
                        modifier = Modifier.testTag("addPartButton"),
                    ) { Text(stringResource(R.string.part_add)) }
                }
                parts.parts.forEachIndexed { index, part ->
                    // New parts are inserted before the remainder, so the
                    // button that adds them sits right above it too.
                    if (part.id == parts.remainderPartId) addPartButton()
                    PartCard(
                        index = index,
                        part = part,
                        isRemainder = part.id == parts.remainderPartId,
                        canRemove = parts.parts.size > 1 && part.id != parts.remainderPartId,
                        evaluation = evaluation,
                        errors = errors,
                        mode = viewModel.calculatorMode,
                        localTags = localTags,
                        onAmountChange = { viewModel.setPartAmount(part.id, it) },
                        onToggleTag = { viewModel.togglePartTag(part.id, it) },
                        onNewTag = { newTagTarget = NewTagTarget(isGlobal = false, partId = part.id) },
                        onRemove = { viewModel.removePart(part.id) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parts.remainderPartId == null) addPartButton()
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.record_total, evaluation.totalCents?.let { amountText(it) } ?: "—"),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("recordTotal"),
                    )
                }
            }

            AlwaysLabeledTextField(
                value = description,
                onValueChange = viewModel::setDescription,
                label = stringResource(R.string.description_label),
                placeholder = stringResource(R.string.description_placeholder),
                modifier = Modifier.testTag("descriptionInput"),
            )
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
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }

    newTagTarget?.let { target ->
        TagNameDialog(
            title = stringResource(if (target.isGlobal) R.string.tag_new_global else R.string.tag_new),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                viewModel.createTag(name, isGlobal = target.isGlobal, forPartId = target.partId) { newTagTarget = null }
            },
            onDismiss = {
                newTagTarget = null
                viewModel.clearTagNameError()
            },
            error = tagNameError?.tagNameText(),
            onTextChange = viewModel::clearTagNameError,
        )
    }

    if (showDeleteRecordDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRecordDialog = false },
            title = { Text(stringResource(R.string.record_delete_title)) },
            text = { Text(stringResource(R.string.record_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteRecordDialog = false
                    viewModel.delete(onSaved)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRecordDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
    mode: CalculatorMode,
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
        // Only re-parsed when the field's text changes, not on every recomposition of the form.
        val preview = remember(value, mode) { amountCentsFrom(value, mode) }
        // Compared in the field's own plain form; shown in the locale's.
        if (preview != null && value != formatCents(preview)) {
            Text("= ${amountText(preview)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    mode: CalculatorMode,
    onChange: (String) -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
) {
    if (expression == null) {
        TextButton(
            onClick = onAdd,
            modifier = Modifier.testTag("addReceiptTotalButton"),
        ) { Text(stringResource(R.string.receipt_total_add)) }
        return
    }
    Column {
        AmountField(
            value = expression,
            onValueChange = onChange,
            label = stringResource(R.string.receipt_total_label),
            error = when {
                PartsError.InvalidReceiptTotal in errors -> stringResource(R.string.error_receipt_total)
                PartsError.PartsExceedReceiptTotal in errors -> stringResource(R.string.error_parts_exceed_total)
                else -> null
            },
            mode = mode,
            modifier = Modifier.testTag("receiptTotalInput"),
        )
        TextButton(
            onClick = onClear,
            modifier = Modifier.testTag("clearReceiptTotalButton"),
        ) { Text(stringResource(R.string.receipt_total_clear)) }
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
    mode: CalculatorMode,
    localTags: List<Tag>,
    onAmountChange: (String) -> Unit,
    onToggleTag: (Long) -> Unit,
    onNewTag: () -> Unit,
    onRemove: () -> Unit,
) {
    LabeledOutlinedBox(
        label = if (isRemainder) stringResource(R.string.part_remainder) else stringResource(R.string.part_number, index + 1),
        modifier = Modifier.testTag("partCard-$index"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRemainder) {
                Text(
                    text = evaluation.partCents[part.id]?.let { amountText(it) } ?: "—",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("remainderAmount"),
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.weight(1f)) {
                        AmountField(
                            value = part.amountExpression,
                            onValueChange = onAmountChange,
                            label = stringResource(R.string.amount_label),
                            error = errors.amountErrorFor(part.id),
                            mode = mode,
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
            // "+" stays inline here: on the card's border it would read as "add a part".
            TagChips(
                tags = localTags,
                selectedIds = part.tagIds,
                onToggle = onToggleTag,
                trailing = {
                    AddChip(
                        onClick = onNewTag,
                        description = stringResource(R.string.tag_new),
                        modifier = Modifier.testTag("newTagChip-$index"),
                    )
                },
            )
            if (errors.any { it is PartsError.DuplicateTags && part.id in it.partIds }) {
                Text(stringResource(R.string.error_duplicate_part_tags), color = MaterialTheme.colorScheme.error)
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
    // Same border-cut label as the date, amount, and description fields;
    // "+" cuts into the border at the other end.
    LabeledOutlinedBox(
        label = "$title — $hint",
        modifier = modifier,
        action = {
            AddChip(onClick = onNewTag, description = stringResource(R.string.tag_new), modifier = Modifier.testTag(newTagChipTestTag))
        },
    ) {
        TagChips(tags = tags, selectedIds = selectedIds, onToggle = onToggle)
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
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
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
                    text = stringResource(R.string.tags_none),
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
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun List<PartsError>.amountErrorFor(partId: Int): String? =
    if (PartsError.InvalidAmount(partId) in this) stringResource(R.string.error_amount) else null

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toUtcMillis(): Long = toEpochDays() * MILLIS_PER_DAY

private fun Long.toLocalDateFromUtcMillis(): LocalDate = LocalDate.fromEpochDays((this / MILLIS_PER_DAY).toInt())
