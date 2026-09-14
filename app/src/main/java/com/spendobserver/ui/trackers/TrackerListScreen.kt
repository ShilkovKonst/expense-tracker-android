package com.spendobserver.ui.trackers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TrackerRepository

@Composable
fun TrackerListScreen(
    trackerRepository: TrackerRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: TrackerListViewModel = viewModel(
        factory = remember(trackerRepository) {
            viewModelFactory { initializer { TrackerListViewModel(trackerRepository) } }
        },
    )
    val trackers by viewModel.trackers.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var trackerToRename by remember { mutableStateOf<Tracker?>(null) }
    var trackerToDelete by remember { mutableStateOf<Tracker?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Text("+")
            }
        },
    ) { innerPadding ->
        if (trackers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Пока нет трекеров")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(trackers, key = { it.id }) { tracker ->
                    TrackerRow(
                        tracker = tracker,
                        onRenameClick = { trackerToRename = tracker },
                        onDeleteClick = { trackerToDelete = tracker },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        TrackerTitleDialog(
            title = "Новый трекер",
            initialTitle = "",
            confirmLabel = "Создать",
            onConfirm = { title ->
                viewModel.createTracker(title)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    trackerToRename?.let { tracker ->
        TrackerTitleDialog(
            title = "Переименовать трекер",
            initialTitle = tracker.title,
            confirmLabel = "Сохранить",
            onConfirm = { newTitle ->
                viewModel.renameTracker(tracker, newTitle)
                trackerToRename = null
            },
            onDismiss = { trackerToRename = null },
        )
    }

    trackerToDelete?.let { tracker ->
        AlertDialog(
            onDismissRequest = { trackerToDelete = null },
            title = { Text("Удалить «${tracker.title}»?") },
            text = { Text("Все записи этого трекера будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTracker(tracker)
                    trackerToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { trackerToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TrackerRow(
    tracker: Tracker,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(tracker.title) },
        trailingContent = {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("trackerMenuButton"),
            ) {
                Text("⋮")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    onClick = {
                        menuExpanded = false
                        onRenameClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                )
            }
        },
    )
}

@Composable
private fun TrackerTitleDialog(
    title: String,
    initialTitle: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth().testTag("trackerTitleInput"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
