package com.speakdrive.ai.pronunciation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PronunciationGraderTest {

    private fun grade(
        target: String,
        heard: String,
        modelSaidCorrect: Boolean = true,
        problems: List<String> = emptyList()
    ) = PronunciationGrader.grade(target, heard, modelSaidCorrect, problems, "", attemptNumber = 1, timestamp = 0)

    @Test
    fun `an exact repeat that the AI also accepts passes`() {
        val attempt = grade("I'd like a window seat, please.", "I'd like a window seat please")
        assertThat(attempt.passed).isTrue()
        assertThat(attempt.accuracyPercent).isEqualTo(100)
        assertThat(attempt.words.all { it.status == WordStatus.OK }).isTrue()
    }

    @Test
    fun `the AI cannot pass an attempt the transcript shows was wrong`() {
        // The AI (too kindly) says "correct", but the learner said "tree" and dropped "seat".
        val attempt = grade("I need three tickets to the city", "I need tree ticket to the city")
        assertThat(attempt.passed).isFalse()
        assertThat(attempt.words.filter { it.status == WordStatus.WRONG }.map { it.word to it.heardAs })
            .containsExactly("three" to "tree", "tickets" to "ticket")
        assertThat(attempt.problemWords).containsExactly("three", "tickets")
    }

    @Test
    fun `a clean transcript cannot overrule a problem the AI heard`() {
        val attempt = grade(
            "I think this is the right one",
            "I think this is the right one",
            modelSaidCorrect = false,
            problems = listOf("think")
        )
        assertThat(attempt.passed).isFalse()
        assertThat(attempt.accuracyPercent).isEqualTo(100)
        assertThat(attempt.problemWords).containsExactly("think")
    }

    @Test
    fun `problem words listed by the AI fail the attempt even with a correct verdict`() {
        val attempt = grade("She works here", "She works here", modelSaidCorrect = true, problems = listOf("works"))
        assertThat(attempt.passed).isFalse()
    }

    @Test
    fun `missing words are reported and fail the attempt`() {
        val attempt = grade("Could you please call me a taxi", "Could you call me taxi")
        assertThat(attempt.passed).isFalse()
        assertThat(attempt.words.filter { it.status == WordStatus.MISSING }.map { it.word }).containsExactly("please", "a")
        assertThat(attempt.accuracyPercent).isEqualTo(71)
    }

    @Test
    fun `contractions, digits, case and punctuation do not count as mistakes`() {
        assertThat(grade("I'm going to buy 2 apples.", "i am going to buy two apples").passed).isTrue()
        assertThat(grade("Okay, let's go!", "OK let us go").passed).isTrue()
    }

    @Test
    fun `extra words around the attempt are ignored`() {
        val attempt = grade("Where is the station", "um where is the station")
        assertThat(attempt.passed).isTrue()
    }

    @Test
    fun `long sentences tolerate one recognition slip, short ones do not`() {
        val long = grade(
            "I would like to book a table for four people tonight",
            "I would like to book a table for for people tonight"
        )
        assertThat(long.passed).isTrue()
        assertThat(long.problemWords).containsExactly("four")

        assertThat(grade("Book a table for four", "Book a table for for").passed).isFalse()
    }

    @Test
    fun `silence never passes`() {
        val attempt = grade("Good morning", "")
        assertThat(attempt.passed).isFalse()
        assertThat(attempt.accuracyPercent).isEqualTo(0)
    }
}
