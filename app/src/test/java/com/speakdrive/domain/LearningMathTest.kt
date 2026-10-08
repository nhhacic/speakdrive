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

    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun `without freezes the streak matches the plain streak`() {
        val practice = days(0, 1, 2, 5)
        val status = StreakCalculator.streakWithFreezes(practice, today, freezesEnabled = false)
        assertThat(status.days).isEqualTo(StreakCalculator.currentStreak(practice, today))
        assertThat(status.freezesLeft).isEqualTo(0)
    }

    @Test
    fun `seven days in a row earn a freeze that covers one missed day`() {
        // Days 9..3 ago practised (7 in a row), 2 days ago missed, yesterday practised.
        val practice = days(9, 8, 7, 6, 5, 4, 3, 1)
        val status = StreakCalculator.streakWithFreezes(practice, today)

        assertThat(status.days).isEqualTo(8)
        assertThat(status.freezesLeft).isEqualTo(0)
        assertThat(status.frozenDays).containsExactly(today.minusDays(2))
        // Without freezes the same history is a one-day streak.
        assertThat(StreakCalculator.currentStreak(practice, today)).isEqualTo(1)
    }

    @Test
    fun `a missed day without a freeze breaks the streak`() {
        val practice = days(5, 4, 3, 1, 0)
        val status = StreakCalculator.streakWithFreezes(practice, today)
        assertThat(status.days).isEqualTo(2)
        assertThat(status.frozenDays).isEmpty()
    }

    @Test
    fun `today does not count as missed and freezes are capped`() {
        // 21 days in a row up to yesterday: three freezes earned, only two kept.
        val practice = (1L..21L).map { today.minusDays(it) }.toSet()
        val status = StreakCalculator.streakWithFreezes(practice, today)
        assertThat(status.days).isEqualTo(21)
        assertThat(status.freezesLeft).isEqualTo(StreakCalculator.MAX_FREEZES)
    }

    @Test
    fun `memory text ignores case and surrounding punctuation`() {
        assertThat(MemoryText.normalize("  I goed to Hue!! ")).isEqualTo("i goed to hue")
        assertThat(MemoryText.normalize("\"Works as a nurse.\"")).isEqualTo("works as a nurse")
    }
}
