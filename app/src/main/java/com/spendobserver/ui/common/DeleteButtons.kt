package com.spendobserver.ui.common

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.spendobserver.R

// Every "delete" in the app is drawn in the theme's error red, so it stands apart from the neutral
// actions beside it (Cancel, Settings…) and isn't picked by habit. The red comes from the color
// scheme, dynamic colors included, so it suits the palette, as the expense amounts already do.

/** A dialog's confirm button for a deletion. */
@Composable
fun DeleteTextButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(R.string.action_delete)) }
}

/** The trash-can button that asks for a deletion. */
@Composable
fun DeleteIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
    }
}
