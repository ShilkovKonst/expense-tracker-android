package com.spendobserver.ui.trackers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.russianPlural
import com.spendobserver.ui.common.AddChip
import com.spendobserver.ui.common.LabeledOutlinedBox
import com.spendobserver.ui.common.TagNameDialog

/** Renaming a tracker and managing its tags (create/rename/delete) — one screen, reached via the ✎ button on a tracker row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditScreen(
    trackerId: Long,
    trackerRepository: TrackerRepository,
    tagRepository: TagRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TrackerEditViewModel = viewModel(
        factory = remember(trackerId, trackerRepository, tagRepository) {
            viewModelFactory { initializer { TrackerEditViewModel(trackerId, trackerRepository, tagRepository) } }
        },
    )
    val title by viewModel.title.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagUsage by viewModel.tagUsage.collectAsStateWithLifecycle()
    val tagNameError by viewModel.tagNameError.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val isTitleChanged by viewModel.isTitleChanged.collectAsStateWithLifecycle()

    /** Non-null while the new-tag dialog is open: which level the new tag goes on. */
    var newTagIsGlobal by remember { mutableStateOf<Boolean?>(null) }
    var tagToRename by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Редактировать трекер") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Text("←") }
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
            Column {
                // Only the name needs saving (tags are stored as they change), so its
                // revert/save sit right beside it, live only while there's a change.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = viewModel::setTitle,
                        label = { Text("Название") },
                        singleLine = true,
                        isError = saveError != null,
                        modifier = Modifier.weight(1f).testTag("trackerNameInput"),
                    )
                    IconButton(
                        onClick = viewModel::revertTitle,
                        enabled = isTitleChanged,
                        modifier = Modifier
                            .testTag("revertTitleButton")
                            .semantics { contentDescription = "Вернуть название" },
                    ) { Text("✕") }
                    IconButton(
                        onClick = { viewModel.saveTitle() },
                        enabled = isTitleChanged,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .testTag("saveTrackerButton")
                            .semantics { contentDescription = "Сохранить название" },
                    ) { Text("✓", fontWeight = FontWeight.Bold) }
                }
                if (saveError != null) {
                    Text(saveError!!, color = MaterialTheme.colorScheme.error)
                }
            }

            TagSection(
                title = "Общие теги",
                hint = "где, с кем",
                tags = tags.filter { it.isGlobal },
                tagUsage = tagUsage,
                onNewTag = { newTagIsGlobal = true },
                onRename = { tagToRename = it },
                onDelete = { tagToDelete = it },
                newTagButtonTestTag = "newGlobalTagButton",
            )

            TagSection(
                title = "Теги",
                hint = "на что",
                tags = tags.filterNot { it.isGlobal },
                tagUsage = tagUsage,
                onNewTag = { newTagIsGlobal = false },
                onRename = { tagToRename = it },
                onDelete = { tagToDelete = it },
                newTagButtonTestTag = "newTagButton",
            )
        }
    }

    newTagIsGlobal?.let { isGlobal ->
        TagNameDialog(
            title = if (isGlobal) "Новый общий тег" else "Новый тег",
            initialName = "",
            confirmLabel = "Создать",
            onConfirm = { name -> viewModel.createTag(name, isGlobal) { newTagIsGlobal = null } },
            onDismiss = {
                newTagIsGlobal = null
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
}

/**
 * One level's tags. ⇄ moves a tag to the other level, enabled only while
 * no record uses it — otherwise existing records would hold it on the
 * wrong level (see [Tag.isGlobal]).
 */
@Composable
private fun TagSection(
    title: String,
    hint: String,
    tags: List<Tag>,
    tagUsage: Map<Long, Int>,
    onNewTag: () -> Unit,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    newTagButtonTestTag: String,
) {
    // Same border-cut label and "+" as the record form's tag boxes.
    LabeledOutlinedBox(
        label = "$title — $hint",
        action = {
            AddChip(onClick = onNewTag, description = "Новый тег", modifier = Modifier.testTag(newTagButtonTestTag))
        },
    ) {
        if (tags.isEmpty()) {
            Text(
                text = "Тегов пока нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        for (tag in tags) {
            val usage = tagUsage[tag.id] ?: 0
            // A plain Row rather than ListItem: ListItem's own 16dp inset on top
            // of the box's would push names twice as far from the border.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (usage == 0) {
                            "не используется"
                        } else {
                            "в $usage ${recordsInWord(usage)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onRename(tag) },
                    modifier = Modifier.testTag("renameTagButton-${tag.name}"),
                ) { Text("✎") }
                IconButton(
                    onClick = { onDelete(tag) },
                    modifier = Modifier.testTag("deleteTagButton-${tag.name}"),
                ) { Text("🗑") }
            }
        }
    }
}

/** Prepositional case for "в N …": в 1 записи, в 2 записях, в 5 записях. */
private fun recordsInWord(count: Int) = russianPlural(count, "записи", "записях", "записях")
