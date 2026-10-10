package com.speakdrive.ai.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveLogTest {

    @Test
    fun `a line has time, level, tag, thread, message and the error with its cause`() {
        val error = IllegalStateException("closed", java.io.IOException("socket reset"))

        val line = LiveLog.line("10-10 14:05:27.055", 'W', "GeminiLiveManager", "DefaultDispatcher-worker-1", "Receive loop ended", error)

        assertThat(line).isEqualTo(
            "10-10 14:05:27.055 W/GeminiLiveManager [DefaultDispatcher-worker-1] Receive loop ended" +
                " | java.lang.IllegalStateException: closed | cause java.io.IOException: socket reset\n"
        )
    }

    @Test
    fun `a line without an error is just the message`() {
        val line = LiveLog.line("10-10 14:05:27.055", 'I', "ConversationEngine", "main", "Lesson state: ACTIVE", null)

        assertThat(line).isEqualTo("10-10 14:05:27.055 I/ConversationEngine [main] Lesson state: ACTIVE\n")
    }
}
