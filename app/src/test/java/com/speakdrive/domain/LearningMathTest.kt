package com.speakdrive.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class LearningMathTest {

    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun `streak counts consecutive days ending today`() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(3)
    }

    @Test
    fun `streak survives until the learner practises today`() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertThat(StreakCalculator.currentStreak(days, today)).isEqualTo(2)
    }

    @Test
    fun `streak is broken after a missed day`() {
        assertThat(StreakCalculator.currentStreak(setOf(today.minusDays(2)), today)).isEqualTo(0)
        assertThat(StreakCalculator.currentStreak(emptySet(), today)).isEqualTo(0)
    }

    @Test
    fun `several lessons on one day count once`() {
        val morning = today.atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val evening = today.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val dates = StreakCalculator.toLocalDates(listOf(morning, evening), ZoneOffset.UTC)
        assertThat(dates).containsExactly(today)
    }

    @Test
    fun `review intervals grow and then stay at the longest`() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertThat(SpacedRepetition.nextReviewAt(0, 0)).isEqualTo(1 * day)
        assertThat(SpacedRepetition.nextReviewAt(1, 0)).isEqualTo(3 * day)
        assertThat(SpacedRepetition.nextReviewAt(2, 0)).isEqualTo(7 * day)
        assertThat(SpacedRepetition.nextReviewAt(5, 0)).isEqualTo(60 * day)
        assertThat(SpacedRepetition.nextReviewAt(50, 0)).isEqualTo(60 * day)
    }
}
