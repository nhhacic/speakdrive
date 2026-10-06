package com.speakdrive.ai.session

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.Speaker
import org.junit.Test

class TranscriptAccumulatorTest {

    private var now = 1_000L
    private val accumulator = TranscriptAccumulator { now }

    @Test
    fun `joins chunks from the same speaker`() {
        accumulator.append(Speaker.AI, "Hello")
        accumulator.append(Speaker.AI, " there,")
        val turns = accumulator.append(Speaker.AI, " how are you?")

        assertThat(turns).hasSize(1)
        assertThat(turns.single().text).isEqualTo("Hello there, how are you?")
    }

    @Test
    fun `adds a space only where words would merge`() {
        accumulator.append(Speaker.USER, "I am")
        accumulator.append(Speaker.USER, "fine")
        val turns = accumulator.append(Speaker.USER, ".")

        assertThat(turns.single().text).isEqualTo("I am fine.")
    }

    @Test
    fun `a new speaker starts a new turn with its own id and time`() {
        accumulator.append(Speaker.AI, "Question?")
        now = 5_000L
        val turns = accumulator.append(Speaker.USER, "  Answer  ")

        assertThat(turns.map { it.speaker }).containsExactly(Speaker.AI, Speaker.USER).inOrder()
        assertThat(turns[1].text).isEqualTo("Answer")
        assertThat(turns[1].timestamp).isEqualTo(5_000L)
        assertThat(turns.map { it.id }.toSet()).hasSize(2)
    }

    @Test
    fun `ignores blank chunks that would start a turn`() {
        accumulator.append(Speaker.AI, "Hi")
        val turns = accumulator.append(Speaker.USER, "   ")

        assertThat(turns).hasSize(1)
    }

    @Test
    fun `clear starts over`() {
        accumulator.append(Speaker.AI, "Hi")
        accumulator.clear()

        assertThat(accumulator.snapshot()).isEmpty()
    }

    @Test
    fun `preserves paragraphs and newlines for storytelling and long speeches`() {
        accumulator.append(Speaker.AI, "Chapter One: The Crushing Ice.")
        accumulator.append(Speaker.AI, "\n\nThe ship was stuck.")
        val turns = accumulator.append(Speaker.AI, "\n\nCaptain was calm.")

        assertThat(turns).hasSize(1)
        assertThat(turns.single().text).isEqualTo("Chapter One: The Crushing Ice.\n\nThe ship was stuck.\n\nCaptain was calm.")
    }
}
