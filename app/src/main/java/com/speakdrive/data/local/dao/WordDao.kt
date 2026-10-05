package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.speakdrive.data.local.entity.LearnedWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM learned_words WHERE nextReviewAt <= :now ORDER BY nextReviewAt ASC LIMIT :limit")
    suspend fun dueWords(now: Long, limit: Int): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words ORDER BY learnedAt DESC LIMIT :limit")
    suspend fun recentWords(limit: Int): List<LearnedWordEntity>

    @Query("SELECT COUNT(*) FROM learned_words WHERE nextReviewAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM learned_words WHERE normalizedWord IN (:normalizedWords)")
    suspend fun findByNormalized(normalizedWords: List<String>): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words WHERE id = :id")
    suspend fun getWordById(id: Long): LearnedWordEntity?

    @Upsert
    suspend fun upsertWord(word: LearnedWordEntity): Long

    @Query("DELETE FROM learned_words WHERE id = :id")
    suspend fun deleteWord(id: Long)

    @Query("UPDATE learned_words SET exampleSentence = :example WHERE id = :id")
    suspend fun updateExample(id: Long, example: String)

    @Query("UPDATE learned_words SET reviewCount = :reviewCount, nextReviewAt = :nextReviewAt WHERE id = :id")
    suspend fun updateSchedule(id: Long, reviewCount: Int, nextReviewAt: Long)

    @Query("SELECT * FROM learned_words WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeWordsForSession(sessionId: String): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words ORDER BY learnedAt DESC")
    fun observeAllWords(): Flow<List<LearnedWordEntity>>

    @Query("SELECT COUNT(*) FROM learned_words WHERE learnedAt >= :since")
    fun observeCountLearnedSince(since: Long): Flow<Int>

    @Query("DELETE FROM learned_words")
    suspend fun deleteAll()
}
