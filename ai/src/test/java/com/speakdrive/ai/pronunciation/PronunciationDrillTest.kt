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
        assertThat(instruction).contains("Repeat after me:")
    }

    @Test
    fun `a passed attempt is confirmed without exaggerated praise`() {
        val response = PronunciationDrill.toolResponse(attempt("Good morning", "good morning", correct = true))
        assertThat(response["final_verdict"]).isEqualTo("correct")
        assertThat(response["instruction"] as String).contains("no exaggerated praise")
        assertThat(response["instruction"] as String).contains("Repeat after me:")
    }

    @Test
    fun `nothing heard asks the learner to speak up`() {
        val response = PronunciationDrill.toolResponse(attempt("Good morning", "", correct = true))
        assertThat(response["instruction"] as String).contains("louder")
    }

    @Test
    fun `extracts the sentence to repeat from what the AI said`() {
        // Standard "Repeat after me"
        assertThat(PronunciationDrill.extractTarget("Great. Repeat after me: I'd like a window seat, please."))
            .isEqualTo("I'd like a window seat, please.")
        assertThat(PronunciationDrill.extractTarget("repeat after me, \"Where is the station?\""))
            .isEqualTo("Where is the station?")

        // Next sentence / Next one prefixes
        assertThat(PronunciationDrill.extractTarget("Clear and accurate. Next sentence: Can you help me find the gate?"))
            .isEqualTo("Can you help me find the gate?")
        assertThat(PronunciationDrill.extractTarget("Here is the next one: Could I have the bill, please?"))
            .isEqualTo("Could I have the bill, please?")
        assertThat(PronunciationDrill.extractTarget("The next sentence is: I need a single room."))
            .isEqualTo("I need a single room.")

        // Try / Practice prefixes
        assertThat(PronunciationDrill.extractTarget("Correct! Let's try: How much does this cost?"))
            .isEqualTo("How much does this cost?")
        assertThat(PronunciationDrill.extractTarget("Now try saying: Where can I buy a ticket?"))
            .isEqualTo("Where can I buy a ticket?")
        assertThat(PronunciationDrill.extractTarget("Please repeat: Can I have a glass of water?"))
            .isEqualTo("Can I have a glass of water?")

        // Vietnamese prefixes
        assertThat(PronunciationDrill.extractTarget("Đọc theo tôi: Where is the restroom?"))
            .isEqualTo("Where is the restroom?")
        assertThat(PronunciationDrill.extractTarget("Câu tiếp theo: Can you call a taxi for me?"))
            .isEqualTo("Can you call a taxi for me?")
        assertThat(PronunciationDrill.extractTarget("Nhắc lại theo tôi: I would like to check in."))
            .isEqualTo("I would like to check in.")

        // Quoted sentences
        assertThat(PronunciationDrill.extractTarget("Clear and accurate. \"I would like to order a pizza.\""))
            .isEqualTo("I would like to order a pizza.")

        // Acknowledge + Sentence directly
        assertThat(PronunciationDrill.extractTarget("Clear and accurate. Where is the check-in desk?"))
            .isEqualTo("Where is the check-in desk?")
        assertThat(PronunciationDrill.extractTarget("Correct! Could you help me with my bags?"))
            .isEqualTo("Could you help me with my bags?")

        // Trailing filler cleanups
        assertThat(PronunciationDrill.extractTarget("Repeat after me: Where is the train station? Now your turn."))
            .isEqualTo("Where is the train station?")
        assertThat(PronunciationDrill.extractTarget("Next sentence: I'd like a window seat, please. Can you say that?"))
            .isEqualTo("I'd like a window seat, please.")
        assertThat(PronunciationDrill.extractTarget("Repeat after me: Can you call a taxi? Go ahead."))
            .isEqualTo("Can you call a taxi?")
        assertThat(PronunciationDrill.extractTarget("Let's try: I need a receipt. Take your time."))
            .isEqualTo("I need a receipt.")

        // Multiple sentences in turn - picks the latest
        assertThat(PronunciationDrill.extractTarget("Repeat after me: I want a coffee. Actually, repeat after me: I want some tea."))
            .isEqualTo("I want some tea.")

        // Non-target conversational text
        assertThat(PronunciationDrill.extractTarget("How are you today?")).isNull()

        // Coach feedback then whole sentence again in quotes
        assertThat(PronunciationDrill.extractTarget(
            """this, "solve," "solve," making sure to get the clear 'l' sound and the final 'v'. Now, let's try the whole sentence again: "He's still trying to solve that mystery from last week.""""
        )).isEqualTo("He's still trying to solve that mystery from last week.")

        // Whole sentence without quotes
        assertThat(PronunciationDrill.extractTarget(
            "Now let's try the whole sentence again: He's still trying to solve that mystery from last week."
        )).isEqualTo("He's still trying to solve that mystery from last week.")

        // Incomplete streaming intro ending with colon should not be parsed as target
        assertThat(PronunciationDrill.extractTarget("Now, let's try the whole sentence again:")).isNull()
        assertThat(PronunciationDrill.extractTarget("Repeat after me:")).isNull()

        // Vietnamese whole sentence again variants
        assertThat(PronunciationDrill.extractTarget("Thử lại cả câu: \"He's still trying to solve that mystery from last week.\""))
            .isEqualTo("He's still trying to solve that mystery from last week.")
        assertThat(PronunciationDrill.extractTarget("Đọc lại cả câu: He's still trying to solve that mystery from last week."))
            .isEqualTo("He's still trying to solve that mystery from last week.")
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
