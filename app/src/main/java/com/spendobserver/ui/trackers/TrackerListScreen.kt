package com.spendobserver.ui.trackers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
import com.spendobserver.domain.model.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.domain.summary.TrackerStats
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.backup.ExportScope
import com.spendobserver.ui.backup.ImportDialogs
import com.spendobserver.ui.backup.ImportState
import com.spendobserver.ui.backup.ImportViewModel
import com.spendobserver.ui.backup.rememberExportActions
import com.spendobserver.ui.backup.rememberImportLauncher
import com.spendobserver.ui.common.DeleteTrackerDialog
import com.spendobserver.ui.common.alternatingRowColor
import com.spendobserver.ui.common.signedAmountText
import com.spendobserver.ui.common.trackerNameText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onTrackerClick: (Tracker) -> Unit = {},
    onEditTrackerClick: (Tracker) -> Unit = {},
    onStatsClick: (Tracker) -> Unit = {},
    onTrackerCreated: (trackerId: Long) -> Unit = {},
    onHelpClick: () -> Unit = {},
    onFaqClick: () -> Unit = {},
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
    var showThemeDialog by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("trackerListMenuButton")) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
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
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_theme)) },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                },
                                modifier = Modifier.testTag("themeItem"),
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
                modifier = Modifier.testTag("addTrackerButton"),
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
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
                itemsIndexed(trackers, key = { _, tracker -> tracker.id }) { index, tracker ->
                    TrackerRow(
                        tracker = tracker,
                        position = index,
                        stats = statsByTracker[tracker.id] ?: TrackerStats.EMPTY,
                        onClick = { onTrackerClick(tracker) },
                        onStatsClick = { onStatsClick(tracker) },
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
            error = createError?.trackerNameText(),
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

    if (showThemeDialog) {
        ThemeDialog(
            selected = themeMode,
            onSelect = { mode ->
                onThemeModeChange(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
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
    position: Int,
    stats: TrackerStats,
    onClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    // Each row keeps its own open/closed flag; LazyColumn keys rows by tracker id, so it stays with its tracker.
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = alternatingRowColor(position)),
        headlineContent = { Text(tracker.title) },
        supportingContent = {
            Text(
                text = pluralStringResource(R.plurals.records_count, stats.recordCount, stats.recordCount) +
                    " · " + signedAmountText(stats.totals.netCents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            // The Box anchors the dropdown to the button, as in the top bar's menu.
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("trackerRowMenu-${tracker.title}"),
                ) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    /** A menu entry that closes the menu before acting. */
                    @Composable
                    fun item(label: String, tag: String, action: () -> Unit) = DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            showMenu = false
                            action()
                        },
                        modifier = Modifier.testTag("$tag-${tracker.title}"),
                    )
                    item(stringResource(R.string.menu_stats), "statsTrackerItem", onStatsClick)
                    item(stringResource(R.string.menu_settings), "editTrackerItem", onEditClick)
                    HorizontalDivider()
                    item(stringResource(R.string.action_delete), "deleteTrackerItem", onDeleteClick)
                }
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
                label = { Text(stringResource(R.string.name_label)) },
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
