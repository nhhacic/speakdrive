package com.speakdrive.data.repository

import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.domain.StreakCalculator
import com.speakdrive.domain.TimeWindows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DayPractice(val date: LocalDate, val minutes: Int)

data class ProgressStats(
    val streakDays: Int = 0,
    val minutesToday: Int = 0,
    val wordsToday: Int = 0,
    val dueWordCount: Int = 0,
    val completedSessions: Int = 0,
    val totalMinutes: Int = 0,
    /** Completed lessons per topic id. */
    val topicCounts: Map<String, Int> = emptyMap(),
    /** Oldest first, always 7 entries ending today. */
    val lastSevenDays: List<DayPractice> = emptyList()
)

/** Learning statistics for the home and progress screens (F10). */
@Singleton
class ProgressRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val wordDao: WordDao
) {
    fun observeStats(zone: ZoneId = ZoneId.systemDefault()): Flow<ProgressStats> {
        val today = LocalDate.now(zone)
        val startOfToday = TimeWindows.startOfDay(today, zone)
        val weekStart = today.minusDays(6)
        val now = System.currentTimeMillis()

        val week = sessionDao.observePracticeSince(TimeWindows.startOfDay(weekStart, zone))
        val streak = sessionDao.observeSessionStartTimes()
        val words = combine(wordDao.observeCountLearnedSince(startOfToday), wordDao.observeDueCount(now)) { today, due -> today to due }
        val totals = combine(
            sessionDao.observeCompletedCount(),
            sessionDao.observeTotalDurationMs(),
            sessionDao.observeTopicCounts()
        ) { count, durationMs, topics -> Triple(count, durationMs, topics.associate { it.topicId to it.count }) }

        return combine(week, streak, words, totals) { weekEntries, startTimes, (wordsToday, due), (count, totalMs, topicCounts) ->
            val minutesByDay = weekEntries.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .mapValues { (_, entries) -> toMinutes(entries.sumOf { it.activeDurationMs }) }
            ProgressStats(
                streakDays = StreakCalculator.currentStreak(StreakCalculator.toLocalDates(startTimes, zone), today),
                minutesToday = minutesByDay[today] ?: 0,
                wordsToday = wordsToday,
                dueWordCount = due,
                completedSessions = count,
                totalMinutes = toMinutes(totalMs),
                topicCounts = topicCounts,
                lastSevenDays = (0L..6L).map { offset ->
                    val date = weekStart.plusDays(offset)
                    DayPractice(date, minutesByDay[date] ?: 0)
                }
            )
        }
    }

    private fun toMinutes(ms: Long): Int = TimeUnit.MILLISECONDS.toMinutes(ms + 30_000).toInt()
}
