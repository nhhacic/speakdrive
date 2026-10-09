package com.speakdrive.ui

import com.google.common.truth.Truth.assertThat
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.ui.screens.recentTrend
import org.junit.Test

class ProgressTrendTest {

    @Test
    fun `pairs each scored session with its own correction count`() {
        val sessions = listOf(
            session("a", fluency = 8, grammar = 8, vocabulary = 8),
            session("b", fluency = 6, grammar = 6, vocabulary = 6),
            session("c", fluency = 4, grammar = 4, vocabulary = 4)
        )

        val trend = recentTrend(sessions, mapOf("a" to 1, "b" to 4, "c" to 5))

        assertThat(trend.averages).containsExactly(8.0, 6.0, 4.0).inOrder()
        assertThat(trend.corrections).containsExactly(1, 4, 5).inOrder()
    }

    @Test
    fun `skips drafts and unscored sessions so both lists stay aligned`() {
        val sessions = listOf(
            session("draft", fluency = 9, grammar = 9, vocabulary = 9, completed = false),
            session("short"),
            session("scored", fluency = 7, grammar = 7, vocabulary = 7)
        )

        val trend = recentTrend(sessions, mapOf("draft" to 5, "short" to 3, "scored" to 2))

        assertThat(trend.averages).containsExactly(7.0)
        assertThat(trend.corrections).containsExactly(2)
    }

    @Test
    fun `sessions without stored corrections count as zero`() {
        val trend = recentTrend(listOf(session("a", fluency = 9, grammar = 9, vocabulary = 9)), emptyMap())

        assertThat(trend.corrections).containsExactly(0)
    }

    @Test
    fun `pronunciation score is used for drill sessions and only five sessions are taken`() {
        val sessions = (1..7).map { session("s$it", pronunciation = 85) }

        val trend = recentTrend(sessions, emptyMap())

        assertThat(trend.averages).hasSize(5)
        assertThat(trend.averages.first()).isEqualTo(9.0)
    }

    @Test
    fun `calculateWeeklyFluency computes averages and comparison with previous week`() {
        val now = 1_000_000_000_000L
        val oneDay = 86_400_000L
        val thisWeekSessions = listOf(
            session("s1", startedAt = now - oneDay, wordsPerMinute = 120, fillerWordsRatio = 0.04f, meanLengthOfUtterance = 6.5f),
            session("s2", startedAt = now - 2 * oneDay, wordsPerMinute = 130, fillerWordsRatio = 0.02f, meanLengthOfUtterance = 7.5f)
        )
        val prevWeekSessions = listOf(
            session("s3", startedAt = now - 8 * oneDay, wordsPerMinute = 110, fillerWordsRatio = 0.08f, meanLengthOfUtterance = 5.0f)
        )

        val summary = com.speakdrive.ui.screens.calculateWeeklyFluency(thisWeekSessions + prevWeekSessions, now = now)

        assertThat(summary.hasData).isTrue()
        assertThat(summary.currentWeekWpm).isEqualTo(125)
        assertThat(summary.previousWeekWpm).isEqualTo(110)
        assertThat(summary.currentWeekFillerRatio).isWithin(0.001f).of(0.03f)
        assertThat(summary.currentWeekMlu).isWithin(0.001f).of(7.0f)
    }

    private fun session(
        id: String,
        fluency: Int? = null,
        grammar: Int? = null,
        vocabulary: Int? = null,
        pronunciation: Int? = null,
        completed: Boolean = true,
        startedAt: Long = 0L,
        wordsPerMinute: Int? = null,
        fillerWordsRatio: Float? = null,
        meanLengthOfUtterance: Float? = null
    ) = SessionEntity(
        id = id,
        topicId = "travel",
        scenarioId = null,
        level = "INTERMEDIATE",
        mode = "FREE_TALK",
        startedAt = startedAt,
        endedAt = startedAt + 60_000,
        activeDurationMs = 60_000,
        fluencyScore = fluency,
        grammarScore = grammar,
        vocabularyScore = vocabulary,
        encouragement = null,
        nextSuggestion = null,
        isCompleted = completed,
        pronunciationScore = pronunciation,
        wordsPerMinute = wordsPerMinute,
        fillerWordsRatio = fillerWordsRatio,
        meanLengthOfUtterance = meanLengthOfUtterance
    )
}
