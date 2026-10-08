package com.speakdrive.data.repository

import com.speakdrive.data.local.dao.MemoryDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.domain.StreakCalculator
import com.speakdrive.domain.TimeWindows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DayPractice(val date: LocalDate, val minutes: Int)

data class ProgressStats(
    val streakDays: Int = 0,
    /** Streak freezes in reserve (0 when the learner turned freezes off). */
    val streakFreezes: Int = 0,
    val minutesToday: Int = 0,
    val wordsToday: Int = 0,
    val dueWordCount: Int = 0,
    /** Earlier mistakes due for review (mistake review mode). */
    val dueMistakeCount: Int = 0,
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
    private val wordDao: WordDao,
    private val preferences: UserPreferencesRepository,
    private val memoryDao: MemoryDao
) {
    fun observeStats(zone: ZoneId = ZoneId.systemDefault()): Flow<ProgressStats> {
        val today = LocalDate.now(zone)
        val startOfToday = TimeWindows.startOfDay(today, zone)
        val weekStart = today.minusDays(6)
        val now = System.currentTimeMillis()

        val week = sessionDao.observePracticeSince(TimeWindows.startOfDay(weekStart, zone))
        val freezesEnabled = preferences.preferences.map { it.learner.streakFreezeEnabled }.distinctUntilChanged()
        val streak = combine(sessionDao.observeSessionStartTimes(), freezesEnabled) { startTimes, enabled ->
            StreakCalculator.streakWithFreezes(StreakCalculator.toLocalDates(startTimes, zone), today, enabled)
        }
        val words = combine(
            wordDao.observeCountLearnedSince(startOfToday),
            wordDao.observeDueCount(now),
            memoryDao.observeDueMistakeCount(now)
        ) { today, due, dueMistakes -> Triple(today, due, dueMistakes) }
        val totals = combine(
            sessionDao.observeCompletedCount(),
            sessionDao.observeTotalDurationMs(),
            sessionDao.observeTopicCounts()
        ) { count, durationMs, topics -> Triple(count, durationMs, topics.associate { it.topicId to it.count }) }

        return combine(week, streak, words, totals) { weekEntries, streakStatus, (wordsToday, due, dueMistakes), (count, totalMs, topicCounts) ->
            val minutesByDay = weekEntries.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .mapValues { (_, entries) -> toMinutes(entries.sumOf { it.activeDurationMs }) }
            ProgressStats(
                streakDays = streakStatus.days,
                streakFreezes = streakStatus.freezesLeft,
                minutesToday = minutesByDay[today] ?: 0,
                wordsToday = wordsToday,
                dueWordCount = due,
                dueMistakeCount = dueMistakes,
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
