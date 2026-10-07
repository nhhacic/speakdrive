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
        val fallback = SummaryParser.fallback("Buổi học ngắn", com.speakdrive.ai.model.DifficultyLevel.ELEMENTARY)
        assertThat(fallback.fluencyScore).isNull()
        assertThat(fallback.encouragement).isEqualTo("Buổi học ngắn")
        assertThat(fallback.levelRecommendation).isNotNull()
        assertThat(fallback.levelRecommendation?.targetLevel).isEqualTo(com.speakdrive.ai.model.DifficultyLevel.ELEMENTARY)
    }

    @Test
    fun `parses explicit level recommendation from JSON`() {
        val summary = SummaryParser.parse(
            """
            {
              "fluency_score": 9, "grammar_score": 8, "vocabulary_score": 9,
              "encouragement_vi": "Rất tốt!",
              "next_suggestion_vi": "Tiếp tục phát huy!",
              "level_recommendation_direction": "UP",
              "recommended_level": "PRE_INTERMEDIATE",
              "level_recommendation_reason_vi": "Bạn đã sẵn sàng để nâng lên Tiền trung cấp!",
              "level_recommendation_reason_en": "You are ready for Pre-Intermediate!"
            }
            """.trimIndent(),
            currentLevel = com.speakdrive.ai.model.DifficultyLevel.ELEMENTARY
        )

        assertThat(summary.levelRecommendation).isNotNull()
        assertThat(summary.levelRecommendation?.direction).isEqualTo(com.speakdrive.ai.model.LevelAdjustmentDirection.LEVEL_UP)
        assertThat(summary.levelRecommendation?.targetLevel).isEqualTo(com.speakdrive.ai.model.DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(summary.levelRecommendation?.reasonVi).contains("Tiền trung cấp")
    }

    @Test
    fun `falls back to LevelEvaluator when recommendation fields are missing in JSON`() {
        val summary = SummaryParser.parse(
            """
            {
              "fluency_score": 9, "grammar_score": 9, "vocabulary_score": 9,
              "encouragement_vi": "Xuất sắc!",
              "next_suggestion_vi": "Tiếp tục luyện tập!"
            }
            """.trimIndent(),
            currentLevel = com.speakdrive.ai.model.DifficultyLevel.ELEMENTARY,
            learnerTurns = 4
        )

        assertThat(summary.levelRecommendation).isNotNull()
        assertThat(summary.levelRecommendation?.direction).isEqualTo(com.speakdrive.ai.model.LevelAdjustmentDirection.LEVEL_UP)
        assertThat(summary.levelRecommendation?.targetLevel).isEqualTo(com.speakdrive.ai.model.DifficultyLevel.PRE_INTERMEDIATE)
    }

    @Test
    fun `createLocalFallbackSummary generates rich report for pronunciation drill attempts`() {
        val topic = com.speakdrive.ai.model.Topic(
            id = "business",
            titleVi = "Công việc & Kinh doanh",
            titleEn = "Work & Business",
            description = "Giao tiếp công sở",
            emoji = "💼",
            keywords = emptyList(),
            scenarios = emptyList()
        )
        val lesson = com.speakdrive.ai.model.ActiveLesson(
            sessionId = "session-123",
            topic = topic,
            scenario = null,
            level = com.speakdrive.ai.model.DifficultyLevel.PRE_INTERMEDIATE,
            mode = com.speakdrive.ai.model.SessionMode.REPEAT_AFTER_ME,
            startedAt = 1000L,
            reviewWords = emptyList(),
            voiceId = "Puck"
        )

        // 10 passed, 1 failed sentence
        val attempts = (1..10).map { i ->
            com.speakdrive.ai.pronunciation.PronunciationAttempt(
                target = "Sentence number $i for practice.",
                heard = "Sentence number $i for practice.",
                words = emptyList(),
                accuracyPercent = 100,
                modelSaidCorrect = true,
                modelProblemWords = emptyList(),
                modelNotes = "",
                attemptNumber = 1,
                passed = true,
                timestamp = 1000L + i * 100
            )
        } + listOf(
            com.speakdrive.ai.pronunciation.PronunciationAttempt(
                target = "Please review the reports before the presentation.",
                heard = "Please review the reports before the present",
                words = emptyList(),
                accuracyPercent = 75,
                modelSaidCorrect = false,
                modelProblemWords = listOf("presentation"),
                modelNotes = "Unclear ending on presentation",
                attemptNumber = 1,
                passed = false,
                timestamp = 2500L
            )
        )

        val summary = SummaryParser.createLocalFallbackSummary(lesson, emptyList(), attempts)

        assertThat(summary.pronunciationScore).isEqualTo(90) // 10/11 = 90%
        assertThat(summary.corrections).hasSize(1)
        assertThat(summary.corrections.first().corrected).isEqualTo("Please review the reports before the presentation.")
        assertThat(summary.corrections.first().explanation).contains("presentation")
        assertThat(summary.encouragement).contains("10/11")
        assertThat(summary.encouragement).contains("90%")
        assertThat(summary.nextSuggestion).contains("Please review the reports before the presentation.")
        assertThat(summary.levelRecommendation).isNotNull()
    }

    @Test
    fun `createLocalFallbackSummary generates report for free talk with learner turns`() {
        val topic = com.speakdrive.ai.model.Topic(
            id = "travel",
            titleVi = "Du lịch",
            titleEn = "Travel",
            description = "Đi lại và khám phá",
            emoji = "✈️",
            keywords = emptyList(),
            scenarios = emptyList()
        )
        val lesson = com.speakdrive.ai.model.ActiveLesson(
            sessionId = "session-456",
            topic = topic,
            scenario = null,
            level = com.speakdrive.ai.model.DifficultyLevel.INTERMEDIATE,
            mode = com.speakdrive.ai.model.SessionMode.FREE_TALK,
            startedAt = 1000L,
            reviewWords = emptyList(),
            voiceId = "Puck"
        )

        val turns = listOf(
            com.speakdrive.ai.model.TranscriptTurn(1, com.speakdrive.ai.model.Speaker.AI, "Where do you want to travel?", 1000L),
            com.speakdrive.ai.model.TranscriptTurn(2, com.speakdrive.ai.model.Speaker.USER, "I want to visit Da Nang and Hoi An next summer.", 1100L),
            com.speakdrive.ai.model.TranscriptTurn(3, com.speakdrive.ai.model.Speaker.AI, "That sounds wonderful! What will you do there?", 1200L),
            com.speakdrive.ai.model.TranscriptTurn(4, com.speakdrive.ai.model.Speaker.USER, "I will eat local food and swim in the sea.", 1300L)
        )

        val summary = SummaryParser.createLocalFallbackSummary(lesson, turns, emptyList())

        assertThat(summary.fluencyScore).isNotNull()
        assertThat(summary.grammarScore).isNotNull()
        assertThat(summary.vocabularyScore).isNotNull()
        assertThat(summary.encouragement).contains("2 lượt đối thoại")
        assertThat(summary.encouragement).contains("Du lịch")
        assertThat(summary.levelRecommendation).isNotNull()
    }
}

