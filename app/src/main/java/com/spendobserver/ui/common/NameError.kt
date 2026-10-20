package com.spendobserver.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spendobserver.R

/**
 * Why a tracker title or a tag name was refused. ViewModels hold this, not
 * text: the words depend on the screen's language and on what is being named,
 * so they're picked only when shown — see [trackerNameText] and [tagNameText].
 */
enum class NameError { EMPTY, TAKEN }

@Composable
fun NameError.trackerNameText(): String = stringResource(
    when (this) {
        NameError.EMPTY -> R.string.error_name_empty
        NameError.TAKEN -> R.string.error_tracker_name_taken
    },
)

@Composable
fun NameError.tagNameText(): String = stringResource(
    when (this) {
        NameError.EMPTY -> R.string.error_name_empty
        NameError.TAKEN -> R.string.error_tag_name_taken
    },
)
