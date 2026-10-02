package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.speakdrive.data.local.entity.LearnedWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: LearnedWordEntity)

    @Query("SELECT * FROM learned_words WHERE sessionId = :sessionId")
    fun getWordsBySession(sessionId: String): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE nextReviewAt <= :currentTime ORDER BY nextReviewAt ASC")
    fun getWordsForReview(currentTime: Long): Flow<List<LearnedWordEntity>>

    @Query("SELECT COUNT(*) FROM learned_words WHERE learnedAt >= :startOfDay")
    fun getTotalWordsLearnedToday(startOfDay: Long): Flow<Int>

    @Query("UPDATE learned_words SET reviewCount = reviewCount + 1, nextReviewAt = :nextReviewAt WHERE id = :wordId")
    suspend fun updateReviewCount(wordId: Long, nextReviewAt: Long)
}
