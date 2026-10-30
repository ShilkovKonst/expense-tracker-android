package com.spendobserver.ui.help

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendobserver.R
import com.spendobserver.ui.common.BackButton
import kotlinx.coroutines.launch

private const val AUTHOR_NAME = "Konstantin Shilkov"
internal const val FEEDBACK_EMAIL = "blacktormentoro@gmail.com"

/** A library shown under "Licenses". Names stay untranslated. */
private data class LibraryLicense(val name: String, val license: String)

private val LIBRARIES = listOf(
    LibraryLicense("AndroidX (Compose, Room, Navigation, Lifecycle, Core)", "Apache License 2.0"),
    LibraryLicense("Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime", "Apache License 2.0"),
    LibraryLicense("Vico", "Apache License 2.0"),
    LibraryLicense("Material Symbols", "Apache License 2.0"),
)

/**
 * Help, data notice, feedback, about and licenses — one scrolling screen of
 * mostly static text, so it needs no ViewModel or repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Swappable so a UI test can stand in for the mail app (and for its absence).
    openFeedback: (Context) -> Boolean = ::openFeedbackEmail,
) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showNoMailApp by remember { mutableStateOf(false) }
    val addressCopied = stringResource(R.string.help_address_copied)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    BackButton(onClick = onBackClick)
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
                .padding(16.dp)
                .testTag("helpContent"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HelpSection(title = null, tag = "helpAboutSection") {
                Paragraph(stringResource(R.string.help_about_text))
                Paragraph(stringResource(R.string.help_version, versionName), modifier = Modifier.testTag("helpVersion"))
                Paragraph(stringResource(R.string.help_author, AUTHOR_NAME))
                Paragraph(stringResource(R.string.help_email, FEEDBACK_EMAIL))
                OutlinedButton(
                    onClick = { if (!openFeedback(context)) showNoMailApp = true },
                    modifier = Modifier.testTag("feedbackButton"),
                ) {
                    Text(stringResource(R.string.help_write))
                }
            }
            HorizontalDivider()
            HelpSection(stringResource(R.string.help_data_title), tag = "helpDataSection") {
                Paragraph(stringResource(R.string.help_data_local))
                Paragraph(stringResource(R.string.help_data_backup))
                // The menu items by their own resources, so the text always names them as the menu does.
                Paragraph(
                    stringResource(
                        R.string.help_data_uninstall,
                        stringResource(R.string.menu_save_all),
                        stringResource(R.string.menu_import),
                    ),
                )
            }
            HorizontalDivider()
            HelpSection(stringResource(R.string.help_licenses_title), tag = "helpLicensesSection") {
                Paragraph(stringResource(R.string.help_licenses_intro))
                LIBRARIES.forEach { library ->
                    Column {
                        Text(library.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            library.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showNoMailApp) {
        AlertDialog(
            onDismissRequest = { showNoMailApp = false },
            title = { Text(stringResource(R.string.help_no_mail_title)) },
            text = {
                Text(stringResource(R.string.help_no_mail_text, FEEDBACK_EMAIL))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoMailApp = false
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("email", FEEDBACK_EMAIL)))
                            snackbarHostState.showSnackbar(addressCopied)
                        }
                    },
                    modifier = Modifier.testTag("copyEmailButton"),
                ) {
                    Text(stringResource(R.string.help_copy_address))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoMailApp = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

/** A block of the help screen, titled unless the app bar already names it; [content] is laid out as a column. */
@Composable
private fun HelpSection(
    title: String?,
    tag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Paragraph(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/**
 * The `versionName` from the Gradle `defaultConfig`, read from the installed
 * package — avoids turning on the BuildConfig build feature just for this.
 */
internal fun appVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
