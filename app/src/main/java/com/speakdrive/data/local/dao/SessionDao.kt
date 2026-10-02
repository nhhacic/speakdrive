package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.speakdrive.data.local.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSessionEntity)

    @Update
    suspend fun updateSession(session: ConversationSessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ConversationSessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM sessions WHERE topicId = :topicId ORDER BY startTime DESC")
    fun getSessionsByTopic(topicId: String): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<ConversationSessionEntity>>

    @Query("SELECT SUM((endTime - startTime) / 60000) FROM sessions WHERE startTime >= :startOfDay AND isCompleted = 1")
    fun getTotalMinutesToday(startOfDay: Long): Flow<Int?>

    @Query("SELECT COUNT(DISTINCT date(startTime / 1000, 'unixepoch', 'localtime')) FROM sessions WHERE isCompleted = 1")
    fun getStreakDays(): Flow<Int>

    @Query("SELECT topicId, COUNT(*) as count FROM sessions WHERE isCompleted = 1 GROUP BY topicId")
    fun getTopicProgress(): Flow<List<TopicProgress>>
}

data class TopicProgress(
    val topicId: String,
    val count: Int
)
