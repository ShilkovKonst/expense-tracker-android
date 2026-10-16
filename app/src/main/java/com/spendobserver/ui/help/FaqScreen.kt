package com.spendobserver.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One question of the FAQ; each [answer] entry is a separate paragraph. */
internal data class FaqEntry(val question: String, val answer: List<String>)

internal val FAQ = listOf(
    FaqEntry(
        "Что такое трекер?",
        listOf(
            "Трекер — это отдельный журнал учёта со своими записями и тегами, например личный, " +
                "семейный или рабочий. Трекеры не зависят друг от друга.",
            "Для создания трекера нажмите «Добавить трекер» на главном экране и введите название. " +
                "Созданный трекер сразу открывается.",
            "Переименовать или удалить трекер можно кнопками ✎ и 🗑 в его строке на главном экране, " +
                "а также через меню «⋮» на экране трекера. При удалении трекера удаляются все его записи.",
        ),
    ),
    FaqEntry(
        "Как добавить, изменить или удалить запись?",
        listOf(
            "Откройте трекер и нажмите «Добавить запись». Выберите тип записи («Расход» или «Доход»), " +
                "укажите дату и сумму, при необходимости отметьте теги и добавьте описание, " +
                "затем нажмите «Сохранить».",
            "Записи сгруппированы по годам и месяцам; для каждой группы указаны количество записей и итог. " +
                "Чтобы изменить запись, нажмите на неё в списке. Удалить запись можно кнопкой 🗑 " +
                "в верхней части формы записи.",
        ),
    ),
    FaqEntry(
        "Что такое части записи и итог по чеку?",
        listOf(
            "Запись соответствует одному событию, например одному чеку, и может состоять из нескольких частей. " +
                "У каждой части своя сумма и свои теги. Сумма записи всегда равна сумме её частей.",
            "Чтобы разделить запись, нажмите «Разбить по частям» под полем суммы. Новые части " +
                "добавляются кнопкой «+ Часть», лишние удаляются кнопкой ✕.",
            "Если известна общая сумма чека, укажите её в поле «Итог по чеку» и выделите отдельные части. " +
                "Остаток рассчитывается автоматически и становится последней частью. " +
                "Сумма выделенных частей должна быть меньше итога по чеку.",
            "Две части одной записи не могут иметь одинаковый набор тегов.",
        ),
    ),
    FaqEntry(
        "Чем общие теги отличаются от обычных?",
        listOf(
            "Общие теги описывают запись целиком: где или с кем она совершена (например, название магазина " +
                "или поездки). Они отмечаются в поле «Общие теги» формы записи.",
            "Обычные теги описывают, на что потрачены или откуда получены средства (например, «продукты»). " +
                "Они отмечаются у каждой части записи отдельно.",
            "Уровень тега выбирается при создании и в дальнейшем не меняется. Новый тег создаётся кнопкой «+» " +
                "в соответствующем поле формы записи или на экране редактирования трекера.",
        ),
    ),
    FaqEntry(
        "Как переименовать или удалить тег?",
        listOf(
            "Откройте трекер, в меню «⋮» выберите «Редактировать» и воспользуйтесь кнопками ✎ и 🗑 " +
                "рядом с нужным тегом. Для каждого тега указано, в скольких записях он используется.",
            "При удалении тег снимается со всех записей; сами записи при этом сохраняются.",
        ),
    ),
    FaqEntry(
        "Как пользоваться калькулятором в поле суммы?",
        listOf(
            "В поле суммы можно ввести выражение с операциями +, −, × (*) и ÷ (/), например 120+50/2. " +
                "Результат отображается под полем и сохраняется как сумма.",
            "Выражение вычисляется последовательно слева направо, без приоритета умножения и деления: " +
                "120+50/2 = 85. В качестве десятичного разделителя допускаются точка и запятая. " +
                "Результат округляется до двух знаков после запятой и должен быть больше нуля.",
        ),
    ),
    FaqEntry(
        "Как пользоваться статистикой?",
        listOf(
            "Откройте трекер и в меню «⋮» выберите «Статистика». Переключатель «−»/«+» в верхней части " +
                "экрана выбирает расходы или доходы.",
            "Сводка показывает количество записей, сумму за выбранный период и теги обоих уровней, " +
                "которые встречаются чаще всего и на которые приходится наибольшая сумма. В поле выбора " +
                "можно указать отдельный тег, чтобы просмотреть статистику только по нему.",
            "На графике отображаются суммы или количество записей (переключатель «Сумма»/«Записи») " +
                "по годам, месяцам или дням. Нажатие на столбец открывает соответствующий год или месяц; " +
                "для возврата к более крупному периоду используйте ссылки в верхней строке.",
        ),
    ),
    FaqEntry(
        "Как сохранить данные в файл?",
        listOf(
            "Для сохранения всех трекеров откройте меню «⋮» на главном экране и выберите " +
                "«Сохранить все в файл» или «Поделиться всеми». Для сохранения одного трекера " +
                "воспользуйтесь пунктами «Сохранить в файл» или «Поделиться» в меню «⋮» на экране трекера.",
            "Данные сохраняются в файл формата JSON, который можно хранить как резервную копию " +
                "или использовать для переноса данных на другое устройство.",
        ),
    ),
    FaqEntry(
        "Как загрузить данные из файла?",
        listOf(
            "Откройте меню «⋮» на главном экране, выберите «Импорт из файла» и укажите ранее сохранённый файл. " +
                "Перед импортом отображается список трекеров из файла.",
            "Если трекер с таким же названием уже существует, выберите действие: «Копия» — добавить " +
                "трекер из файла под новым названием, «Заменить» — удалить существующий трекер со всеми " +
                "записями и загрузить трекер из файла.",
            "Перед импортом файл проверяется целиком. При обнаружении ошибки данные не загружаются, " +
                "а в сообщении указывается место ошибки.",
        ),
    ),
)

/**
 * Usage questions, each expandable to its answer — a separate screen from
 * [HelpScreen], reached by its own item in the tracker list's menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Вопросы и ответы") },
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
                .testTag("faqContent"),
        ) {
            FAQ.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                FaqItem(entry, tag = "faqItem-$index")
            }
        }
    }
}

@Composable
private fun FaqItem(entry: FaqEntry, tag: String) {
    // Saveable, so open answers survive rotation and the trip back from another screen.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.testTag(tag)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                entry.question,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.answer.forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
