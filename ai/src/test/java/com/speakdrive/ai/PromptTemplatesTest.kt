package com.speakdrive.ai

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import org.junit.Test

class PromptTemplatesTest {

    private val topics = TopicManager()

    private fun lesson(
        mode: SessionMode = SessionMode.FREE_TALK,
        level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        scenarioId: String? = null,
        words: List<ReviewWord> = emptyList()
    ): ActiveLesson {
        val scenario = topics.getScenario(scenarioId)
        return ActiveLesson(
            sessionId = "s1",
            topic = scenario?.first ?: topics.getTopicById("travel")!!,
            scenario = scenario?.second,
            level = level,
            mode = mode,
            startedAt = 0,
            reviewWords = words
        )
    }

    @Test
    fun `always includes the driving safety rules`() {
        val prompt = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings())
        assertThat(prompt).contains("DRIVING")
        assertThat(prompt).contains("Never ask the learner to look at")
        assertThat(prompt).contains(PromptTemplates.END_LESSON_FUNCTION)
    }

    @Test
    fun `reflects the level`() {
        val beginner = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.BEGINNER), LearnerSettings())
        val advanced = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.ADVANCED), LearnerSettings())
        assertThat(beginner).contains("BEGINNER (CEFR A1–A2)")
        assertThat(advanced).contains("ADVANCED (CEFR C1–C2)")
    }

    @Test
    fun `vietnamese help follows the setting`() {
        val allowed = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(allowVietnameseHelp = true))
        val englishOnly = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(allowVietnameseHelp = false))
        assertThat(allowed).contains("explanation in Vietnamese")
        assertThat(englishOnly).contains("Always speak English")
    }

    @Test
    fun `roleplay names both roles`() {
        val prompt = PromptTemplates.buildSystemInstruction(lesson(SessionMode.ROLEPLAY, scenarioId = "food_order"), LearnerSettings())
        assertThat(prompt).contains("a waiter at a busy restaurant")
        assertThat(prompt).contains("a customer ordering dinner")
    }

    @Test
    fun `vocabulary review lists the words`() {
        val prompt = PromptTemplates.buildSystemInstruction(
            lesson(SessionMode.VOCAB_REVIEW, words = listOf(ReviewWord("itinerary", "lịch trình"))),
            LearnerSettings()
        )
        assertThat(prompt).contains("itinerary (lịch trình)")
    }

    @Test
    fun `recap is added only when reconnecting`() {
        val turns = listOf(TranscriptTurn(1, Speaker.USER, "I visited Hue", 0))
        val fresh = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings())
        val resumed = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(), recap = turns)
        assertThat(fresh).doesNotContain("CONVERSATION SO FAR")
        assertThat(resumed).contains("CONVERSATION SO FAR")
        assertThat(resumed).contains("Learner: I visited Hue")
    }

    @Test
    fun `transcript formatting keeps the most recent turns`() {
        val turns = (1..100).map { TranscriptTurn(it.toLong(), if (it % 2 == 0) Speaker.AI else Speaker.USER, "turn number $it", 0) }
        val text = PromptTemplates.formatTranscript(turns, maxChars = 200)
        assertThat(text.length).isAtMost(220)
        assertThat(text).contains("turn number 100")
        assertThat(text).doesNotContain("turn number 1\n")
    }

    @Test
    fun `summary prompt asks for the expected JSON fields`() {
        val prompt = PromptTemplates.summaryPrompt(lesson(), listOf(TranscriptTurn(1, Speaker.USER, "hello", 0)))
        listOf("fluency_score", "grammar_score", "vocabulary_score", "new_words", "corrections", "encouragement_vi", "next_suggestion_vi")
            .forEach { assertThat(prompt).contains(it) }
    }
}
