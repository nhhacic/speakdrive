package com.speakdrive.ai.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class CoroutinesTest {

    @Test
    fun `returns success and failure like runCatching`() {
        assertThat(suspendRunCatching { 42 }.getOrNull()).isEqualTo(42)
        assertThat(suspendRunCatching { error("boom") }.exceptionOrNull()).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun `rethrows cancellation instead of swallowing it`() {
        assertThrows(CancellationException::class.java) {
            suspendRunCatching { throw CancellationException("cancelled") }
        }
    }
}
