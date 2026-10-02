package com.speakdrive.ai.summary

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SummaryParserTest {

    @Test
    fun `parses a complete summary`() {
        val summary = SummaryParser.parse(
            """
            {
              "fluency_score": 7, "grammar_score": 6, "vocabulary_score": 8,
              "new_words": [{"word": "itinerary", "meaning_vi": "lịch trình", "example": "Here is my itinerary."}],
              "corrections": [{"original": "I goed", "corrected": "I went", "explanation_vi": "Quá khứ của go là went."}],
              "encouragement_vi": "Bạn tiến bộ rõ rệt!",
              "next_suggestion_vi": "Luyện thì quá khứ."
            }
            """.trimIndent()
        )
        assertThat(summary.fluencyScore).isEqualTo(7)
        assertThat(summary.newWords.single().meaning).isEqualTo("lịch trình")
        assertThat(summary.corrections.single().corrected).isEqualTo("I went")
        assertThat(summary.encouragement).isEqualTo("Bạn tiến bộ rõ rệt!")
    }

    @Test
    fun `accepts JSON wrapped in a code fence`() {
        val summary = SummaryParser.parse("```json\n{\"fluency_score\": 5}\n```")
        assertThat(summary.fluencyScore).isEqualTo(5)
        assertThat(summary.newWords).isEmpty()
    }

    @Test
    fun `clamps scores and drops empty or duplicate entries`() {
        val summary = SummaryParser.parse(
            """
            {"fluency_score": 15, "grammar_score": 0,
             "new_words": [{"word": "Commute"}, {"word": "commute "}, {"word": ""}],
             "corrections": [{"original": "", "corrected": "x"}]}
            """.trimIndent()
        )
        assertThat(summary.fluencyScore).isEqualTo(10)
        assertThat(summary.grammarScore).isEqualTo(1)
        assertThat(summary.vocabularyScore).isNull()
        assertThat(summary.newWords.map { it.word }).containsExactly("Commute")
        assertThat(summary.corrections).isEmpty()
    }

    @Test
    fun `rejects text without JSON`() {
        assertThrows(IllegalArgumentException::class.java) { SummaryParser.parse("Sorry, I can't do that.") }
    }

    @Test
    fun `fallback has no scores`() {
        val fallback = SummaryParser.fallback("Buổi học ngắn")
        assertThat(fallback.fluencyScore).isNull()
        assertThat(fallback.encouragement).isEqualTo("Buổi học ngắn")
    }
}
