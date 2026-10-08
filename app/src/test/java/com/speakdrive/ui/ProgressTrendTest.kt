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

    private fun session(
        id: String,
        fluency: Int? = null,
        grammar: Int? = null,
        vocabulary: Int? = null,
        pronunciation: Int? = null,
        completed: Boolean = true
    ) = SessionEntity(
        id = id,
        topicId = "travel",
        scenarioId = null,
        level = "INTERMEDIATE",
        mode = "FREE_TALK",
        startedAt = 0,
        endedAt = 0,
        activeDurationMs = 60_000,
        fluencyScore = fluency,
        grammarScore = grammar,
        vocabularyScore = vocabulary,
        encouragement = null,
        nextSuggestion = null,
        isCompleted = completed,
        pronunciationScore = pronunciation
    )
}
