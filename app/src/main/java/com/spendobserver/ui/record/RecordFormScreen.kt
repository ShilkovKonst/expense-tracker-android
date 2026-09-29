package com.spendobserver.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.spendobserver.domain.summary.formatCents
import com.spendobserver.ui.common.TagNameDialog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    trackerId: Long,
    recordId: Long?,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    onSaved: () -> Unit,
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

    val amountExpression by viewModel.amountExpression.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val selectedTagIds by viewModel.selectedTagIds.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showNewTagDialog by remember { mutableStateOf(false) }
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

            Column {
                OutlinedTextField(
                    value = amountExpression,
                    onValueChange = viewModel::setAmountExpression,
                    label = { Text("Сумма") },
                    placeholder = { Text("120+50/2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = saveError != null,
                    modifier = Modifier.fillMaxWidth().testTag("amountInput"),
                )
                val preview = amountCentsFrom(amountExpression)
                if (preview != null && amountExpression != preview.let { formatCents(it) }) {
                    Text("= ${formatCents(preview)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saveError != null) {
                    Text(saveError!!, color = MaterialTheme.colorScheme.error)
                }
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

            Column {
                Text("Теги", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    // Selection only — no per-chip menu: a compact FilterChip's own
                    // touch target and a nested IconButton's (48dp-minimum) touch
                    // target overlap too closely to hit reliably, so rename/delete
                    // live in the full-width rows of the "Управление тегами" dialog
                    // below instead (same layout TrackerListScreen's tracker rows
                    // already use safely, since there the icon sits far from the
                    // label with room to spare).
                    for (tag in tags) {
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showNewTagDialog = true },
                        label = { Text("+ Новый тег") },
                        modifier = Modifier.testTag("newTagChip"),
                    )
                }
                if (tags.isNotEmpty()) {
                    TextButton(
                        onClick = { showManageTagsDialog = true },
                        modifier = Modifier.testTag("manageTagsButton"),
                    ) { Text("Управление тегами") }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = viewModel::setDescription,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth().testTag("descriptionInput"),
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

    if (showNewTagDialog) {
        TagNameDialog(
            title = "Новый тег",
            initialName = "",
            confirmLabel = "Создать",
            onConfirm = { name ->
                viewModel.createTag(name)
                showNewTagDialog = false
            },
            onDismiss = { showNewTagDialog = false },
        )
    }

    tagToRename?.let { tag ->
        TagNameDialog(
            title = "Переименовать тег",
            initialName = tag.name,
            confirmLabel = "Сохранить",
            onConfirm = { newName ->
                viewModel.renameTag(tag, newName)
                tagToRename = null
            },
            onDismiss = { tagToRename = null },
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

private fun formatDate(date: LocalDate): String {
    val dd = date.day.toString().padStart(2, '0')
    val mm = date.month.number.toString().padStart(2, '0')
    return "$dd.$mm.${date.year}"
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toUtcMillis(): Long = toEpochDays() * MILLIS_PER_DAY

private fun Long.toLocalDateFromUtcMillis(): LocalDate = LocalDate.fromEpochDays((this / MILLIS_PER_DAY).toInt())
