package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MistakeEntity
import kotlinx.coroutines.flow.Flow

/** Mistakes to review and facts about the learner: what the AI remembers between lessons. */
@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMistake(mistake: MistakeEntity): Long

    @Update
    suspend fun updateMistake(mistake: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE normalizedOriginal IN (:normalized)")
    suspend fun findMistakes(normalized: List<String>): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE id IN (:ids)")
    suspend fun mistakesByIds(ids: List<Long>): List<MistakeEntity>

    /** Due mistakes, the ones made in the most lessons first. */
    @Query("SELECT * FROM mistakes WHERE nextReviewAt <= :now ORDER BY timesMade DESC, nextReviewAt ASC LIMIT :limit")
    suspend fun dueMistakes(now: Long, limit: Int): List<MistakeEntity>

    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentMistakes(limit: Int): List<MistakeEntity>

    /** Mistakes worth keeping in mind: repeated ones first, then the newest; mastered ones are left out. */
    @Query(
        "SELECT * FROM mistakes WHERE reviewCount < :masteredReviews " +
            "ORDER BY timesMade DESC, createdAt DESC, id DESC LIMIT :limit"
    )
    suspend fun activeMistakes(masteredReviews: Int, limit: Int): List<MistakeEntity>

    @Query("SELECT COUNT(*) FROM mistakes WHERE nextReviewAt <= :now")
    fun observeDueMistakeCount(now: Long): Flow<Int>

    @Query("SELECT * FROM mistakes ORDER BY nextReviewAt ASC, id ASC")
    fun observeAllMistakes(): Flow<List<MistakeEntity>>

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun deleteMistake(id: Long)

    @Query("DELETE FROM mistakes")
    suspend fun deleteAllMistakes()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFacts(facts: List<LearnerFactEntity>)

    @Query("SELECT * FROM learner_facts ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentFacts(limit: Int): List<LearnerFactEntity>

    @Query("SELECT * FROM learner_facts ORDER BY createdAt DESC, id DESC")
    fun observeFacts(): Flow<List<LearnerFactEntity>>

    /** Keeps only the newest [keep] facts so the lesson instructions stay short. */
    @Query("DELETE FROM learner_facts WHERE id NOT IN (SELECT id FROM learner_facts ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trimFacts(keep: Int)

    @Query("DELETE FROM learner_facts WHERE id = :id")
    suspend fun deleteFact(id: Long)

    @Query("DELETE FROM learner_facts")
    suspend fun deleteAllFacts()

    /** Failed drill attempts' problem words ("|"-separated), newest first. */
    @Query("SELECT problemWords FROM pronunciation_attempts WHERE passed = 0 AND problemWords != '' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentProblemWords(limit: Int): List<String>

    @Query("SELECT * FROM mistakes ORDER BY id ASC")
    suspend fun getAllMistakes(): List<MistakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMistakes(mistakes: List<MistakeEntity>)

    @Query("SELECT * FROM learner_facts ORDER BY id ASC")
    suspend fun getAllFacts(): List<LearnerFactEntity>
}
