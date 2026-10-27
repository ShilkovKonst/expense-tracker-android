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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.R
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.common.AddChip
import com.spendobserver.ui.common.LabeledOutlinedBox
import com.spendobserver.ui.common.TagNameDialog
import com.spendobserver.ui.common.tagNameText
import com.spendobserver.ui.common.trackerNameText

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
    // Kept across recompositions (every keystroke in the name field): fresh lists would redraw both sections.
    val (globalTags, localTags) = remember(tags) { tags.partition { it.isGlobal } }
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
                title = { Text(stringResource(R.string.tracker_edit_title)) },
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
                        label = { Text(stringResource(R.string.name_label)) },
                        singleLine = true,
                        isError = saveError != null,
                        modifier = Modifier.weight(1f).testTag("trackerNameInput"),
                    )
                    // Read here: the semantics blocks below aren't composable.
                    val revertDescription = stringResource(R.string.tracker_name_revert)
                    val saveDescription = stringResource(R.string.tracker_name_save)
                    IconButton(
                        onClick = viewModel::revertTitle,
                        enabled = isTitleChanged,
                        modifier = Modifier
                            .testTag("revertTitleButton")
                            .semantics { contentDescription = revertDescription },
                    ) { Text("✕") }
                    IconButton(
                        onClick = { viewModel.saveTitle() },
                        enabled = isTitleChanged,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .testTag("saveTrackerButton")
                            .semantics { contentDescription = saveDescription },
                    ) { Text("✓", fontWeight = FontWeight.Bold) }
                }
                if (saveError != null) {
                    Text(saveError!!.trackerNameText(), color = MaterialTheme.colorScheme.error)
                }
            }

            TagSection(
                title = stringResource(R.string.tags_global_title),
                hint = stringResource(R.string.tags_global_hint),
                tags = globalTags,
                tagUsage = tagUsage,
                onNewTag = { newTagIsGlobal = true },
                onRename = { tagToRename = it },
                onDelete = { tagToDelete = it },
                newTagButtonTestTag = "newGlobalTagButton",
            )

            TagSection(
                title = stringResource(R.string.tags_local_title),
                hint = stringResource(R.string.tags_local_hint),
                tags = localTags,
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
            title = stringResource(if (isGlobal) R.string.tag_new_global else R.string.tag_new),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name -> viewModel.createTag(name, isGlobal) { newTagIsGlobal = null } },
            onDismiss = {
                newTagIsGlobal = null
                viewModel.clearTagNameError()
            },
            error = tagNameError?.tagNameText(),
            onTextChange = viewModel::clearTagNameError,
        )
    }

    tagToRename?.let { tag ->
        TagNameDialog(
            title = stringResource(R.string.tag_rename_title),
            initialName = tag.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { newName -> viewModel.renameTag(tag, newName) { tagToRename = null } },
            onDismiss = {
                tagToRename = null
                viewModel.clearTagNameError()
            },
            error = tagNameError?.tagNameText(),
            onTextChange = viewModel::clearTagNameError,
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(stringResource(R.string.tag_delete_title, tag.name)) },
            text = { Text(stringResource(R.string.tag_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag)
                    tagToDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
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
            AddChip(onClick = onNewTag, description = stringResource(R.string.tag_new), modifier = Modifier.testTag(newTagButtonTestTag))
        },
    ) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_none),
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
                            stringResource(R.string.tag_unused)
                        } else {
                            pluralStringResource(R.plurals.tag_usage, usage, usage)
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
