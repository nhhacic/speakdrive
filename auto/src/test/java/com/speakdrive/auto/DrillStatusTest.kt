package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import org.junit.Test

class DrillStatusTest {

    private fun attempt(target: String, number: Int, score: Int, passed: Boolean) = PronunciationAttempt(
        target = target,
        heard = target,
        words = emptyList(),
        accuracyPercent = score,
        modelSaidCorrect = passed,
        modelProblemWords = emptyList(),
        modelNotes = "",
        attemptNumber = number,
        passed = passed,
        timestamp = 0L
    )

    private fun status(
        state: ConversationState = ConversationState.ACTIVE,
        speaker: Speaker? = null,
        thinking: Boolean = false,
        target: String? = "Where is the gate?",
        attempts: List<PronunciationAttempt> = emptyList()
    ) = DrillStatus.of(state, speaker, thinking, target, attempts)

    @Test
    fun `no sentence to repeat means no drill status`() {
        assertThat(status(target = null)).isNull()
        assertThat(status(target = "  ")).isNull()
    }

    @Test
    fun `turn follows who is speaking`() {
        assertThat(status(speaker = Speaker.AI)!!.turn).isEqualTo(DrillTurn.AI_SPEAKING)
        assertThat(status(speaker = null)!!.turn).isEqualTo(DrillTurn.YOUR_TURN)
        assertThat(status(speaker = Speaker.USER)!!.turn).isEqualTo(DrillTurn.YOU_SPEAKING)
        assertThat(status(thinking = true)!!.turn).isEqualTo(DrillTurn.GRADING)
        // The AI's voice wins over a stale "thinking" flag.
        assertThat(status(speaker = Speaker.AI, thinking = true)!!.turn).isEqualTo(DrillTurn.AI_SPEAKING)
    }

    @Test
    fun `turn reflects paused and connecting lessons`() {
        assertThat(status(state = ConversationState.PAUSED, speaker = Speaker.AI)!!.turn).isEqualTo(DrillTurn.PAUSED)
        assertThat(status(state = ConversationState.CONNECTING)!!.turn).isEqualTo(DrillTurn.CONNECTING)
        assertThat(status(state = ConversationState.RECONNECTING)!!.turn).isEqualTo(DrillTurn.CONNECTING)
    }

    @Test
    fun `score is the latest try at the sentence on screen`() {
        val attempts = listOf(
            attempt("Where is the gate?", 1, 50, passed = false),
            attempt("where is the GATE", 2, 86, passed = false)
        )

        val drill = status(attempts = attempts)!!

        assertThat(drill.accuracyPercent).isEqualTo(86)
        assertThat(drill.attemptNumber).isEqualTo(2)
        assertThat(drill.passed).isFalse()
    }

    @Test
    fun `a new sentence starts without a score but keeps the lesson progress`() {
        val attempts = listOf(
            attempt("Where is the gate?", 1, 100, passed = true),
            attempt("I would like a window seat.", 1, 40, passed = false),
            attempt("I would like a window seat.", 2, 100, passed = true),
            attempt("How much is the ticket?", 3, 60, passed = false)
        )

        val drill = status(target = "Can I check in online?", attempts = attempts)!!

        assertThat(drill.accuracyPercent).isNull()
        assertThat(drill.attemptNumber).isNull()
        assertThat(drill.passed).isNull()
        assertThat(drill.passedSentences).isEqualTo(2)
        assertThat(drill.sentences).isEqualTo(3)
    }
}
