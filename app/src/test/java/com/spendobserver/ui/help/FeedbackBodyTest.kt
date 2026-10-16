package com.spendobserver.ui.help

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackBodyTest {

    @Test
    fun `body leaves room for the message and lists the details below a separator`() {
        val body = feedbackBody(
            appVersion = "1.0",
            androidRelease = "15",
            sdkInt = 35,
            device = "Google Pixel 8",
        )

        assertEquals(
            "\n\n---\n" +
                "Версия приложения: 1.0\n" +
                "Android: 15 (API 35)\n" +
                "Устройство: Google Pixel 8",
            body,
        )
    }
}
