package com.spendobserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
/** Income amounts and bars; expenses use the scheme's error red, so the two read as a pair. */
val incomeColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)
