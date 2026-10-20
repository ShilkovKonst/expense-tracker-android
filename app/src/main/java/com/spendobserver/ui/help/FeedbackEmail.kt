package com.spendobserver.ui.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.spendobserver.R

/**
 * The prefilled letter body: room for the message on top, then the details a
 * bug report needs. The detail labels stay English whatever the app's language,
 * for the developer reading them. Plain parameters instead of reading [Build]
 * here, so the text can be checked in a JVM test.
 */
internal fun feedbackBody(
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
    device: String,
): String = buildString {
    appendLine()
    appendLine()
    appendLine("---")
    appendLine("App version: $appVersion")
    appendLine("Android: $androidRelease (API $sdkInt)")
    append("Device: $device")
}

/**
 * The feedback letter for the user's mail app. `mailto:` with ACTION_SENDTO
 * lists only mail apps (not messengers, as ACTION_SEND would).
 */
internal fun feedbackIntent(context: Context): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        putExtra(
            Intent.EXTRA_TEXT,
            feedbackBody(
                appVersion = appVersionName(context),
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
            ),
        )
    }

/**
 * Opens the user's mail app with [feedbackIntent].
 *
 * Returns false when no mail app is installed, so the caller can offer the
 * address to copy instead. Catching the exception, rather than checking
 * `resolveActivity` first, needs no `<queries>` entry in the manifest
 * (package visibility rules since Android 11).
 */
internal fun openFeedbackEmail(context: Context): Boolean =
    try {
        context.startActivity(feedbackIntent(context))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
