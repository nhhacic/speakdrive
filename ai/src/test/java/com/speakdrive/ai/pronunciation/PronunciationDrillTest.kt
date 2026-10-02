package com.speakdrive.ai.pronunciation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PronunciationDrillTest {

    private fun attempt(target: String, heard: String, correct: Boolean, number: Int = 1) =
        PronunciationGrader.grade(target, heard, correct, emptyList(), "", number, 0)

    @Test
    fun `a failed attempt tells the AI not to praise and to fix named words`() {
        val response = PronunciationDrill.toolResponse(attempt("I want three", "I wan tree", correct = true))

        assertThat(response["final_verdict"]).isEqualTo("needs_work")
        val instruction = response["instruction"] as String
        assertThat(instruction).contains("Do not call it correct")
        assertThat(instruction).contains("want")
        assertThat(instruction).contains("three")
        assertThat(instruction).contains("repeat the whole sentence again")
        assertThat(response["speech_recognition_differences"] as String).contains("'three' sounded like 'tree'")
    }

    @Test
    fun `after the last allowed attempt the drill moves on without pretending`() {
        val last = attempt("I want three", "I wan tree", correct = false, number = PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE)
        val instruction = PronunciationDrill.toolResponse(last)["instruction"] as String
        assertThat(instruction).contains("still needs practice")
        assertThat(instruction).contains("next sentence")
    }

    @Test
    fun `a passed attempt is confirmed without exaggerated praise`() {
        val response = PronunciationDrill.toolResponse(attempt("Good morning", "good morning", correct = true))
        assertThat(response["final_verdict"]).isEqualTo("correct")
        assertThat(response["instruction"] as String).contains("no exaggerated praise")
    }

    @Test
    fun `nothing heard asks the learner to speak up`() {
        val response = PronunciationDrill.toolResponse(attempt("Good morning", "", correct = true))
        assertThat(response["instruction"] as String).contains("louder")
    }

    @Test
    fun `extracts the sentence to repeat from what the AI said`() {
        assertThat(PronunciationDrill.extractTarget("Great. Repeat after me: I'd like a window seat, please."))
            .isEqualTo("I'd like a window seat, please.")
        assertThat(PronunciationDrill.extractTarget("repeat after me, \"Where is the station?\"")).isEqualTo("Where is the station?")
        assertThat(PronunciationDrill.extractTarget("How are you today?")).isNull()
    }

    @Test
    fun `score is the share of sentences eventually passed`() {
        val attempts = listOf(
            attempt("One two three", "one two tree", correct = false),
            attempt("One two three", "one two three", correct = true, number = 2),
            attempt("Four five six", "for five", correct = false),
            attempt("Four five six", "for five sick", correct = false, number = 2)
        )
        assertThat(PronunciationDrill.score(attempts)).isEqualTo(50)
        assertThat(PronunciationDrill.score(emptyList())).isNull()
    }
}
