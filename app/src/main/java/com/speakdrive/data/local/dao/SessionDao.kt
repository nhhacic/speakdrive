package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.MessageEntity
import com.speakdrive.data.local.entity.PronunciationAttemptEntity
import com.speakdrive.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert
    suspend fun insertCorrections(corrections: List<CorrectionEntity>)

    @Insert
    suspend fun insertAttempts(attempts: List<PronunciationAttemptEntity>)

    @Query("DELETE FROM pronunciation_attempts WHERE sessionId = :sessionId")
    suspend fun deleteAttempts(sessionId: String)

    @Query("SELECT * FROM pronunciation_attempts WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeAttempts(sessionId: String): Flow<List<PronunciationAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordsIgnoringExisting(words: List<LearnedWordEntity>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM corrections WHERE sessionId = :sessionId")
    suspend fun deleteCorrections(sessionId: String)

    /** Replaces everything stored for a session; a draft is overwritten by later saves. */
    @Transaction
    suspend fun saveFullSession(
        session: SessionEntity,
        messages: List<MessageEntity>,
        corrections: List<CorrectionEntity>,
        words: List<LearnedWordEntity>,
        attempts: List<PronunciationAttemptEntity> = emptyList()
    ) {
        upsertSession(session)
        deleteMessages(session.id)
        deleteCorrections(session.id)
        deleteAttempts(session.id)
        if (messages.isNotEmpty()) insertMessages(messages)
        if (corrections.isNotEmpty()) insertCorrections(corrections)
        if (attempts.isNotEmpty()) insertAttempts(attempts)
        if (words.isNotEmpty()) insertWordsIgnoringExisting(words)
    }

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY position ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM corrections WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeCorrections(sessionId: String): Flow<List<CorrectionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT topicId FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentTopicIds(limit: Int): List<String>

    @Query("SELECT startedAt, activeDurationMs FROM sessions WHERE startedAt >= :since")
    fun observePracticeSince(since: Long): Flow<List<PracticeEntry>>

    @Query("SELECT startedAt FROM sessions ORDER BY startedAt DESC")
    fun observeSessionStartTimes(): Flow<List<Long>>

    @Query("SELECT topicId, COUNT(*) AS count FROM sessions WHERE isCompleted = 1 GROUP BY topicId")
    fun observeTopicCounts(): Flow<List<TopicCount>>

    @Query("SELECT COUNT(*) FROM sessions WHERE isCompleted = 1")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(activeDurationMs), 0) FROM sessions")
    fun observeTotalDurationMs(): Flow<Long>

    /** Messages and corrections go with their sessions (ON DELETE CASCADE). */
    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}

data class PracticeEntry(val startedAt: Long, val activeDurationMs: Long)

data class TopicCount(val topicId: String, val count: Int)
