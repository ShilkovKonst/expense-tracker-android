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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.common.TagNameDialog

/** Renaming a tracker and managing its tags (create/rename/delete) — one screen, reached via the ✎ button on a tracker row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditScreen(
    trackerId: Long,
    trackerRepository: TrackerRepository,
    tagRepository: TagRepository,
    onSaved: () -> Unit,
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
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    var showNewTagDialog by remember { mutableStateOf(false) }
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
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Название") },
                    singleLine = true,
                    isError = saveError != null,
                    modifier = Modifier.fillMaxWidth().testTag("trackerNameInput"),
                )
                if (saveError != null) {
                    Text(saveError!!, color = MaterialTheme.colorScheme.error)
                }
            }

            Column {
                Text("Теги", style = MaterialTheme.typography.labelLarge)
                if (tags.isEmpty()) {
                    Text(
                        text = "Тегов пока нет",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    for (tag in tags) {
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { tagToRename = tag },
                                        modifier = Modifier.testTag("renameTagButton-${tag.name}"),
                                    ) { Text("✎") }
                                    IconButton(
                                        onClick = { tagToDelete = tag },
                                        modifier = Modifier.testTag("deleteTagButton-${tag.name}"),
                                    ) { Text("🗑") }
                                }
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { showNewTagDialog = true },
                    modifier = Modifier.testTag("newTagButton"),
                ) { Text("+ Новый тег") }
            }

            Button(
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.fillMaxWidth().testTag("saveTrackerButton"),
            ) { Text("Сохранить") }
        }
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
}
