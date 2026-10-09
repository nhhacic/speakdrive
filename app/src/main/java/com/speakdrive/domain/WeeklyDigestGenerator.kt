package com.speakdrive.domain

import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyDigestGenerator @Inject constructor(
    private val sessionDao: SessionDao,
    private val wordDao: WordDao
) {

    data class WeeklyStats(
        val totalMinutes: Int,
        val sessionCount: Int,
        val averageWpm: Int,
        val newWordsCount: Int,
        val spokenSpokenSummaryVi: String,
        val spokenSummaryEn: String
    )

    suspend fun generateDigest(now: Long = System.currentTimeMillis()): WeeklyStats {
        val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000
        val twoWeeksAgo = now - 14L * 24 * 60 * 60 * 1000

        val thisWeekSessions = sessionDao.getSessionsSince(oneWeekAgo)
        val totalDurationMs = thisWeekSessions.sumOf { it.activeDurationMs }
        val totalMinutes = (totalDurationMs / 60000L).toInt()
        val sessionCount = thisWeekSessions.size

        val wpmList = thisWeekSessions.mapNotNull { it.wordsPerMinute }.filter { it > 0 }
        val avgWpm = if (wpmList.isNotEmpty()) (wpmList.sum() / wpmList.size) else 0

        val newWords = wordDao.getAllWords().count { it.learnedAt >= oneWeekAgo }

        val viDigest = buildString {
            append("Chào bạn! ")
            if (totalMinutes > 0) {
                append("Tuần qua bạn đã luyện nói $totalMinutes phút qua $sessionCount buổi học. ")
                if (avgWpm > 0) {
                    append("Tốc độ nói trung bình đạt $avgWpm từ trên phút. ")
                }
                if (newWords > 0) {
                    append("Bạn đã tích lũy thêm $newWords từ vựng và cụm từ mới. ")
                }
                append("Hãy cùng tiếp tục duy trì phong độ hôm nay nhé!")
            } else {
                append("Một tuần mới lại bắt đầu! Hãy dành ít phút cùng luyện phản xạ tiếng Anh hôm nay nhé.")
            }
        }

        val enDigest = buildString {
            append("Hello! ")
            if (totalMinutes > 0) {
                append("Last week you practiced for $totalMinutes minutes across $sessionCount sessions. ")
                if (avgWpm > 0) {
                    append("Your average speaking speed was $avgWpm words per minute. ")
                }
                if (newWords > 0) {
                    append("You mastered $newWords new words and collocations. ")
                }
                append("Let's keep up the momentum today!")
            } else {
                append("A fresh week begins! Let's get some English speaking practice in today.")
            }
        }

        return WeeklyStats(
            totalMinutes = totalMinutes,
            sessionCount = sessionCount,
            averageWpm = avgWpm,
            newWordsCount = newWords,
            spokenSpokenSummaryVi = viDigest,
            spokenSummaryEn = enDigest
        )
    }
}
