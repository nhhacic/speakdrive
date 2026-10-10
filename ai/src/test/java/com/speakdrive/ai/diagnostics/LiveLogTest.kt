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

    @Test
    fun `a report has the header, then the lesson log from the oldest file to the newest`() {
        val dir = java.nio.file.Files.createTempDirectory("livelog").toFile()
        try {
            java.io.File(dir, "live.2.log").writeText("oldest\n")
            java.io.File(dir, "live.1.log").writeText("older\n")
            java.io.File(dir, "live.log").writeText("newest\n")
            val out = java.io.StringWriter()

            LiveLog.writeReport(dir, out, "SpeakDrive 1.7.9")

            val text = out.toString()
            assertThat(text).startsWith("SpeakDrive 1.7.9")
            assertThat(text.indexOf("oldest")).isLessThan(text.indexOf("older"))
            assertThat(text.indexOf("older")).isLessThan(text.indexOf("newest"))
            assertThat(text).contains("===== logcat")
        } finally {
            dir.deleteRecursively()
        }
    }
}
