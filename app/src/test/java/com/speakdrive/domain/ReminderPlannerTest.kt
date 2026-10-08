package com.speakdrive.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, zone)

    private fun at(daysAgo: Long, hour: Int, minute: Int): Long =
        LocalDate.of(2026, 10, 8).minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `the usual practice time comes from the most common hour`() {
        val starts = listOf(at(1, 7, 10), at(2, 7, 20), at(3, 7, 30), at(4, 20, 0), at(2, 7, 50))

        // 7:10, 7:20 and 7:30 (the first lesson of each day in that hour).
        assertThat(ReminderPlanner.usualPracticeMinute(starts, now.toInstant().toEpochMilli(), zone)).isEqualTo(7 * 60 + 20)
    }

    @Test
    fun `history older than a month is ignored`() {
        val starts = listOf(at(40, 6, 0), at(45, 6, 0), at(46, 6, 0))
        assertThat(ReminderPlanner.usualPracticeMinute(starts, now.toInstant().toEpochMilli(), zone)).isNull()
    }

    @Test
    fun `automatic reminders come shortly before the usual time and default to the evening`() {
        val nowMs = now.toInstant().toEpochMilli()
        assertThat(ReminderPlanner.reminderMinute(-1, listOf(at(1, 7, 30)), nowMs, zone)).isEqualTo(7 * 60 + 15)
        assertThat(ReminderPlanner.reminderMinute(-1, listOf(at(1, 0, 5)), nowMs, zone)).isEqualTo(23 * 60 + 50)
        assertThat(ReminderPlanner.reminderMinute(-1, emptyList(), nowMs, zone)).isEqualTo(ReminderPlanner.DEFAULT_MINUTE)
        assertThat(ReminderPlanner.reminderMinute(6 * 60, listOf(at(1, 7, 30)), nowMs, zone)).isEqualTo(6 * 60)
    }

    @Test
    fun `the next trigger is today when still ahead, otherwise tomorrow`() {
        assertThat(ReminderPlanner.nextTrigger(now, 19 * 60)).isEqualTo(now.withHour(19))
        assertThat(ReminderPlanner.nextTrigger(now, 7 * 60)).isEqualTo(now.withHour(7).plusDays(1))
        assertThat(ReminderPlanner.nextTrigger(now, 12 * 60)).isEqualTo(now.plusDays(1))
    }

    @Test
    fun `reminder text mentions the streak, what is due and freezes`() {
        val vi = ReminderPlanner.content(streakDays = 5, freezesLeft = 1, dueWords = 3, dueMistakes = 2, dailyGoalMinutes = 10, vietnamese = true)
        assertThat(vi.title).contains("5 ngày")
        assertThat(vi.text).contains("10 phút")
        assertThat(vi.text).contains("2 câu sai và 3 từ")
        assertThat(vi.text).contains("1 lượt bảo toàn")

        val en = ReminderPlanner.content(streakDays = 0, freezesLeft = 0, dueWords = 1, dueMistakes = 0, dailyGoalMinutes = 15, vietnamese = false)
        assertThat(en.title).isEqualTo("Time for your English speaking practice")
        assertThat(en.text).contains("1 word waiting for review")
        assertThat(en.text).doesNotContain("freeze")
    }
}
