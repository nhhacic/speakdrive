package com.speakdrive.data.repository

import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    suspend fun saveSession(session: ConversationSessionEntity) {
        val existingSession = sessionDao.getSessionById(session.id)
        if (existingSession != null) {
            sessionDao.updateSession(session)
        } else {
            sessionDao.insertSession(session)
        }
    }

    suspend fun getSession(sessionId: String): ConversationSessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    fun getRecentSessions(limit: Int = 10): Flow<List<ConversationSessionEntity>> {
        return sessionDao.getRecentSessions(limit)
    }

    fun getTotalMinutesToday(startOfDayMillis: Long): Flow<Int> {
        return sessionDao.getTotalMinutesToday(startOfDayMillis).map { it ?: 0 }
    }

    fun getStreakDays(): Flow<Int> {
        return sessionDao.getStreakDays()
    }

    fun getTopicProgress(): Flow<Map<String, Int>> {
        return sessionDao.getTopicProgress().map { progressList ->
            progressList.associate { it.topicId to it.count }
        }
    }
}
