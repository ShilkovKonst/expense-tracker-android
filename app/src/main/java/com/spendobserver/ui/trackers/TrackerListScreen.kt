package com.spendobserver.ui.trackers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.TrackerStats
import com.spendobserver.domain.summary.formatSignedCents
import com.spendobserver.domain.summary.recordsWord
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.common.DeleteTrackerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    onTrackerClick: (Tracker) -> Unit = {},
    onEditTrackerClick: (Tracker) -> Unit = {},
    onTrackerCreated: (trackerId: Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: TrackerListViewModel = viewModel(
        factory = remember(trackerRepository, recordRepository) {
            viewModelFactory {
                initializer { TrackerListViewModel(trackerRepository, recordRepository.observeStatsByTracker()) }
            }
        },
    )
    val trackers by viewModel.trackers.collectAsStateWithLifecycle()
    val statsByTracker by viewModel.statsByTracker.collectAsStateWithLifecycle()
    val createError by viewModel.createError.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var trackerToDelete by remember { mutableStateOf<Tracker?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Список трекеров") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Text("+") },
                text = { Text("Добавить трекер") },
            )
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
            LazyColumn(
                // Room below the last row for the FAB (56dp + 16dp margins).
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(trackers, key = { it.id }) { tracker ->
                    TrackerRow(
                        tracker = tracker,
                        stats = statsByTracker[tracker.id] ?: TrackerStats.EMPTY,
                        onClick = { onTrackerClick(tracker) },
                        onEditClick = { onEditTrackerClick(tracker) },
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
            error = createError,
            onTextChange = viewModel::clearCreateError,
            // Stays open on a refused name (createError explains why); closes once created.
            onConfirm = { title ->
                viewModel.createTracker(title) { id ->
                    showCreateDialog = false
                    onTrackerCreated(id)
                }
            },
            onDismiss = {
                showCreateDialog = false
                viewModel.clearCreateError()
            },
        )
    }

    trackerToDelete?.let { tracker ->
        DeleteTrackerDialog(
            trackerTitle = tracker.title,
            onConfirm = {
                viewModel.deleteTracker(tracker)
                trackerToDelete = null
            },
            onDismiss = { trackerToDelete = null },
        )
    }
}

@Composable
private fun TrackerRow(
    tracker: Tracker,
    stats: TrackerStats,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(tracker.title) },
        supportingContent = {
            Text(
                text = "${stats.recordCount} ${recordsWord(stats.recordCount)} · ${formatSignedCents(stats.totals.netCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("editTrackerButton-${tracker.title}"),
                ) { Text("✎") }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("deleteTrackerButton-${tracker.title}"),
                ) { Text("🗑") }
            }
        },
    )
}

@Composable
private fun TrackerTitleDialog(
    title: String,
    initialTitle: String,
    confirmLabel: String,
    error: String?,
    onTextChange: () -> Unit,
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
                onValueChange = {
                    text = it
                    onTextChange()
                },
                singleLine = true,
                label = { Text("Название") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("trackerTitleInput"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trimInvisible().isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
