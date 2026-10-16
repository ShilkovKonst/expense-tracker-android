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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.TrackerStats
import com.spendobserver.domain.summary.formatSignedCents
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.backup.ExportScope
import com.spendobserver.ui.backup.ImportDialogs
import com.spendobserver.ui.backup.ImportState
import com.spendobserver.ui.backup.ImportViewModel
import com.spendobserver.ui.backup.rememberExportActions
import com.spendobserver.ui.backup.rememberImportLauncher
import com.spendobserver.ui.common.DeleteTrackerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    backupRepository: BackupRepository,
    onTrackerClick: (Tracker) -> Unit = {},
    onEditTrackerClick: (Tracker) -> Unit = {},
    onTrackerCreated: (trackerId: Long) -> Unit = {},
    onHelpClick: () -> Unit = {},
    onFaqClick: () -> Unit = {},
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
    var showMenu by remember { mutableStateOf(false) }

    val importViewModel: ImportViewModel = viewModel(
        factory = remember(backupRepository, trackerRepository) {
            viewModelFactory { initializer { ImportViewModel(backupRepository, trackerRepository) } }
        },
    )
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Plain Resources for strings built outside composition (the snackbar text below).
    val resources = LocalResources.current
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    val exportActions = rememberExportActions(backupRepository, onMessage = showMessage)
    val pickImportFile = rememberImportLauncher(onText = importViewModel::onFileLoaded)

    LaunchedEffect(importState) {
        val done = importState as? ImportState.Done ?: return@LaunchedEffect
        importViewModel.dismiss()
        val n = done.trackerCount
        showMessage(resources.getQuantityString(R.plurals.imported_trackers, n, n))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tracker_list_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("trackerListMenuButton")) {
                            Text("⋮")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_import)) },
                                onClick = {
                                    showMenu = false
                                    pickImportFile()
                                },
                                modifier = Modifier.testTag("importItem"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_save_all)) },
                                enabled = trackers.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    exportActions.save(ExportScope.All)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_all)) },
                                enabled = trackers.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    exportActions.share(ExportScope.All)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_help)) },
                                onClick = {
                                    showMenu = false
                                    onHelpClick()
                                },
                                modifier = Modifier.testTag("helpItem"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_faq)) },
                                onClick = {
                                    showMenu = false
                                    onFaqClick()
                                },
                                modifier = Modifier.testTag("faqItem"),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Text("+") },
                text = { Text(stringResource(R.string.tracker_list_add)) },
            )
        },
    ) { innerPadding ->
        if (trackers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tracker_list_empty))
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
            title = stringResource(R.string.tracker_new_title),
            initialTitle = "",
            confirmLabel = stringResource(R.string.action_create),
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

    ImportDialogs(
        state = importState,
        onSetResolution = importViewModel::setResolution,
        onConfirm = importViewModel::confirm,
        onDismiss = importViewModel::dismiss,
    )

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
                text = pluralStringResource(R.plurals.records_count, stats.recordCount, stats.recordCount) +
                    " · " + formatSignedCents(stats.totals.netCents),
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
                label = { Text(stringResource(R.string.tracker_name_label)) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
