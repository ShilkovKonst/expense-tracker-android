package com.spendobserver.ui.common

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Just "+": where it sits (a tags box) makes clear what gets added, while
 * [description] spells it out for screen readers. The tonal fill sets it
 * apart from the outlined tag chips around it, so it reads as a button.
 */
@Composable
fun AddChip(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text("+") },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = null,
        modifier = modifier.semantics { contentDescription = description },
    )
}
