package com.speakdrive.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** When to send the daily practice reminder and what it says. */
object ReminderPlanner {
    /** Used when the learner has no practice history yet: early evening. */
    const val DEFAULT_MINUTE = 19 * 60

    /** Remind this long before the usual practice time, so the learner sees it before getting in the car. */
    const val LEAD_MINUTES = 15

    private const val HISTORY_DAYS = 30L
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * The minute of the day the learner usually starts practising, from the last 30 days: the hour with
     * the most practice days (later days weigh more), then the average start minute within that hour.
     */
    fun usualPracticeMinute(startTimes: Collection<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val since = today.minusDays(HISTORY_DAYS)
        val starts = startTimes.map { Instant.ofEpochMilli(it).atZone(zone) }
            .filter { it.toLocalDate().isAfter(since) && !it.toLocalDate().isAfter(today) }
        if (starts.isEmpty()) return null
        // One vote per day and hour, so one long day of many short lessons does not dominate.
        val firstPerDayHour = starts.groupBy { it.toLocalDate() to it.hour }.values.map { it.minOf { t -> t } }
        val bestHour = firstPerDayHour.groupBy { it.hour }
            .maxBy { (_, times) -> times.sumOf { weight(it.toLocalDate(), today) } }
            .key
        val inHour = firstPerDayHour.filter { it.hour == bestHour }
        return (inHour.sumOf { it.hour * 60 + it.minute } / inHour.size)
    }

    private fun weight(day: LocalDate, today: LocalDate): Double =
        1.0 + (HISTORY_DAYS - java.time.temporal.ChronoUnit.DAYS.between(day, today)).coerceAtLeast(0) / HISTORY_DAYS.toDouble()

    /** The reminder minute: the learner's own choice, or shortly before their usual practice time. */
    fun reminderMinute(settingMinute: Int, startTimes: Collection<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (settingMinute in 0 until MINUTES_PER_DAY) return settingMinute
        val usual = usualPracticeMinute(startTimes, now, zone) ?: return DEFAULT_MINUTE
        return Math.floorMod(usual - LEAD_MINUTES, MINUTES_PER_DAY)
    }

    /** Next time the reminder should fire: today at [minuteOfDay] if still ahead, otherwise tomorrow. */
    fun nextTrigger(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val todayAt = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    data class Content(val title: String, val text: String)

    /** What the reminder says; [streakDays] is the streak that will break if the learner skips today. */
    fun content(
        streakDays: Int,
        freezesLeft: Int,
        dueWords: Int,
        dueMistakes: Int,
        dailyGoalMinutes: Int,
        vietnamese: Boolean
    ): Content {
        val title = when {
            streakDays > 0 && vietnamese -> "🔥 Giữ chuỗi $streakDays ngày nhé!"
            streakDays > 0 -> "🔥 Keep your $streakDays-day streak!"
            vietnamese -> "Đến giờ luyện nói tiếng Anh rồi"
            else -> "Time for your English speaking practice"
        }
        val parts = mutableListOf(
            if (vietnamese) "Hôm nay bạn chưa luyện. Chỉ cần $dailyGoalMinutes phút trên đường đi."
            else "You haven't practised today. Just $dailyGoalMinutes minutes on your drive."
        )
        if (dueMistakes > 0 || dueWords > 0) {
            parts += if (vietnamese) {
                listOfNotNull(
                    dueMistakes.takeIf { it > 0 }?.let { "$it câu sai" },
                    dueWords.takeIf { it > 0 }?.let { "$it từ" }
                ).joinToString(" và ") + " đang chờ ôn."
            } else {
                listOfNotNull(
                    dueMistakes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "mistake" else "mistakes"}" },
                    dueWords.takeIf { it > 0 }?.let { "$it ${if (it == 1) "word" else "words"}" }
                ).joinToString(" and ") + " waiting for review."
            }
        }
        if (streakDays > 0 && freezesLeft > 0) {
            parts += if (vietnamese) "Còn $freezesLeft lượt bảo toàn chuỗi nếu bạn bận."
            else "$freezesLeft streak ${if (freezesLeft == 1) "freeze" else "freezes"} left if you're busy."
        }
        return Content(title, parts.joinToString(" "))
    }
}
