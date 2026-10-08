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

/** Keys used to recognise the same mistake or personal fact when it comes up again. */
object MemoryText {
    /** Stripped from both ends before comparing; the v6 migration uses the same set in SQL. */
    const val TRIM_CHARS = " .!?,;:\""

    fun normalize(text: String): String = text.trim { it.isWhitespace() || it in TRIM_CHARS }.lowercase()
}

data class StreakStatus(
    val days: Int,
    /** Freezes in reserve that will cover the next missed days. */
    val freezesLeft: Int,
    /** Missed days covered by a freeze in the current streak. */
    val frozenDays: Set<LocalDate> = emptySet()
)

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

    /** Practising this many days in a row earns one streak freeze. */
    const val DAYS_PER_FREEZE = 7

    /** Freezes a learner can keep in reserve. */
    const val MAX_FREEZES = 2

    /**
     * Streak where a missed day is covered by a freeze earned earlier (one per [DAYS_PER_FREEZE] practice
     * days in a row, at most [MAX_FREEZES] kept). Frozen days keep the streak alive but do not add to it.
     * Today does not count as missed until it is over. Everything is derived from the practice history,
     * so nothing extra has to be stored.
     */
    fun streakWithFreezes(practiceDays: Set<LocalDate>, today: LocalDate, freezesEnabled: Boolean = true): StreakStatus {
        if (!freezesEnabled) return StreakStatus(days = currentStreak(practiceDays, today), freezesLeft = 0)
        val first = practiceDays.filter { !it.isAfter(today) }.minOrNull() ?: return StreakStatus(0, 0)
        var streak = 0
        var freezes = 0
        var practicedInRow = 0
        val frozen = mutableSetOf<LocalDate>()
        var day: LocalDate = first
        while (!day.isAfter(today)) {
            when {
                day in practiceDays -> {
                    streak++
                    practicedInRow++
                    if (practicedInRow % DAYS_PER_FREEZE == 0 && freezes < MAX_FREEZES) freezes++
                }
                day == today -> Unit
                streak > 0 && freezes > 0 -> {
                    freezes--
                    frozen += day
                    practicedInRow = 0
                }
                else -> {
                    streak = 0
                    freezes = 0
                    practicedInRow = 0
                    frozen.clear()
                }
            }
            day = day.plusDays(1)
        }
        return StreakStatus(days = streak, freezesLeft = freezes, frozenDays = frozen)
    }

    fun toLocalDates(timestamps: Collection<Long>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        timestamps.mapTo(mutableSetOf()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
}

object TimeWindows {
    fun startOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long = startOfDay(LocalDate.now(zone), zone)
}
