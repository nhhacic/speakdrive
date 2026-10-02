package com.speakdrive.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Review intervals for vocabulary (F9). A word answered again moves to the next, longer interval. */
object SpacedRepetition {
    private val INTERVAL_DAYS = longArrayOf(1, 3, 7, 14, 30, 60)

    /** When a word that has been reviewed [reviewCount] times should come back. */
    fun nextReviewAt(reviewCount: Int, from: Long): Long {
        val index = reviewCount.coerceIn(0, INTERVAL_DAYS.lastIndex)
        return from + TimeUnit.DAYS.toMillis(INTERVAL_DAYS[index])
    }
}

object StreakCalculator {

    /**
     * Number of consecutive days with practice, counting back from today. A streak is still
     * alive when the learner practised yesterday but not yet today.
     */
    fun currentStreak(practiceDays: Set<LocalDate>, today: LocalDate): Int {
        var day = when {
            today in practiceDays -> today
            today.minusDays(1) in practiceDays -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (day in practiceDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun toLocalDates(timestamps: Collection<Long>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        timestamps.mapTo(mutableSetOf()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
}

object TimeWindows {
    fun startOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long = startOfDay(LocalDate.now(zone), zone)
}
