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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val AUTHOR_NAME = "Konstantin Shilkov"
internal const val FEEDBACK_EMAIL = "blacktormentoro@gmail.com"

/** A library shown under «Лицензии»; all of them come in through Gradle. */
private data class LibraryLicense(val name: String, val license: String)

private val LIBRARIES = listOf(
    LibraryLicense("AndroidX (Compose, Room, Navigation, Lifecycle, Core)", "Apache License 2.0"),
    LibraryLicense("Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime", "Apache License 2.0"),
    LibraryLicense("Vico", "Apache License 2.0"),
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Справка") },
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
                .padding(16.dp)
                .testTag("helpContent"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HelpSection("О приложении", tag = "helpAboutSection") {
                Paragraph(
                    "SpendObserver — приложение для учёта расходов и доходов. Приложение " +
                        "работает полностью локально: для его работы не требуются подключение " +
                        "к интернету, регистрация или облачные сервисы.",
                )
                Paragraph("Версия: $versionName", modifier = Modifier.testTag("helpVersion"))
                Paragraph("Автор: $AUTHOR_NAME")
                Paragraph("Электронная почта: $FEEDBACK_EMAIL")
                OutlinedButton(
                    onClick = { if (!openFeedback(context)) showNoMailApp = true },
                    modifier = Modifier.testTag("feedbackButton"),
                ) {
                    Text("Написать разработчику")
                }
            }
            HorizontalDivider()
            HelpSection("Данные пользователя", tag = "helpDataSection") {
                Paragraph(
                    "Приложение не передаёт данные пользователя третьим лицам и не отправляет " +
                        "их по сети. Все трекеры, записи и теги хранятся в памяти устройства.",
                )
                Paragraph(
                    "Если в настройках устройства включено резервное копирование в аккаунт " +
                        "Google, операционная система Android может сохранять данные приложения " +
                        "в облачное хранилище этого аккаунта. Такое копирование выполняется " +
                        "системой в соответствии с настройками устройства; приложение в нём " +
                        "не участвует.",
                )
                Paragraph(
                    "При удалении приложения его данные удаляются с устройства. Для сохранения " +
                        "данных или их переноса на другое устройство рекомендуется использовать " +
                        "экспорт в файл (меню «⋮» → «Сохранить все в файл») и последующий " +
                        "импорт (меню «⋮» → «Импорт из файла»).",
                )
            }
            HorizontalDivider()
            HelpSection("Лицензии", tag = "helpLicensesSection") {
                Paragraph("В приложении используются следующие библиотеки с открытым исходным кодом:")
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
            title = { Text("Почтовое приложение не найдено") },
            text = {
                Text(
                    "На устройстве не установлено приложение для отправки электронной почты. " +
                        "Вы можете скопировать адрес и написать на него из любого почтового сервиса: " +
                        FEEDBACK_EMAIL,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoMailApp = false
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("email", FEEDBACK_EMAIL)))
                            snackbarHostState.showSnackbar("Адрес скопирован")
                        }
                    },
                    modifier = Modifier.testTag("copyEmailButton"),
                ) {
                    Text("Скопировать адрес")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoMailApp = false }) { Text("Закрыть") }
            },
        )
    }
}

/** A titled block of the help screen; [content] is laid out as a column. */
@Composable
private fun HelpSection(
    title: String,
    tag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
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
