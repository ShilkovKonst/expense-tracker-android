package com.spendobserver.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spendobserver.R
import com.spendobserver.domain.backup.BackupError
import com.spendobserver.domain.backup.BackupIssue
import com.spendobserver.domain.backup.BackupLocation

/** Why the import dialog shows "can't import"; worded only when shown, in the app's language. */
sealed interface ImportFailure {
    /** The file picker handed back nothing readable. */
    data object Unreadable : ImportFailure

    data class Invalid(val error: BackupError) : ImportFailure

    /** The database write failed; the transaction left everything as it was. */
    data object WriteFailed : ImportFailure
}

@Composable
fun ImportFailure.text(): String = when (this) {
    ImportFailure.Unreadable -> stringResource(R.string.import_error_unreadable)
    ImportFailure.WriteFailed -> stringResource(R.string.import_error_write)
    is ImportFailure.Invalid -> error.text()
}

@Composable
private fun BackupError.text(): String = when (this) {
    BackupError.Damaged -> stringResource(R.string.import_error_damaged)
    BackupError.NotSpendObserver -> stringResource(R.string.import_error_not_spendobserver)
    BackupError.NewerVersion -> stringResource(R.string.import_error_newer_version)
    BackupError.NoTrackers -> stringResource(R.string.import_error_no_trackers)
    is BackupError.DuplicateTracker -> stringResource(R.string.import_error_duplicate_tracker, title)
    is BackupError.Invalid -> stringResource(R.string.import_error_at, where.text(), issue.text())
}

/** "Tracker “Home”, record of 2025-10-02, part 2" — each level wraps the one before. */
@Composable
private fun BackupLocation.text(): String {
    var text = if (trackerTitle != null) {
        stringResource(R.string.import_where_tracker, trackerTitle)
    } else {
        stringResource(R.string.import_where_tracker_number, trackerNumber)
    }
    if (recordDate != null) text = stringResource(R.string.import_where_record, text, recordDate)
    if (partNumber != null) text = stringResource(R.string.import_where_part, text, partNumber)
    return text
}

@Composable
private fun BackupIssue.text(): String = when (this) {
    BackupIssue.EmptyTitle -> stringResource(R.string.import_issue_empty_title)
    is BackupIssue.BadCreatedAt -> stringResource(R.string.import_issue_bad_created_at, value)
    BackupIssue.EmptyTagName -> stringResource(R.string.import_issue_empty_tag_name)
    is BackupIssue.DuplicateTag -> stringResource(R.string.import_issue_duplicate_tag, name)
    is BackupIssue.BadRecordDate -> stringResource(R.string.import_issue_bad_record_date, value)
    is BackupIssue.UnknownType -> stringResource(R.string.import_issue_unknown_type, value)
    is BackupIssue.UnknownTag -> stringResource(R.string.import_issue_unknown_tag, name)
    is BackupIssue.TagNotGlobal -> stringResource(R.string.import_issue_tag_not_global, name)
    is BackupIssue.TagIsGlobal -> stringResource(R.string.import_issue_tag_is_global, name)
    BackupIssue.NoParts -> stringResource(R.string.import_issue_no_parts)
    BackupIssue.NonPositiveAmount -> stringResource(R.string.import_issue_non_positive_amount)
    BackupIssue.DuplicatePartTags -> stringResource(R.string.import_issue_duplicate_part_tags)
}
