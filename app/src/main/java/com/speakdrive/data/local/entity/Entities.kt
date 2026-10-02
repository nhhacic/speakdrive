package com.speakdrive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions", indices = [Index("startedAt")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val scenarioId: String?,
    val level: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val fluencyScore: Int?,
    val grammarScore: Int?,
    val vocabularyScore: Int?,
    val encouragement: String?,
    val nextSuggestion: String?,
    val isCompleted: Boolean
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    /** "USER" or "AI". */
    val speaker: String,
    val text: String,
    val timestamp: Long,
    val position: Int
)

@Entity(
    tableName = "corrections",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val original: String,
    val corrected: String,
    val explanation: String
)

/** A word the learner met in a lesson, scheduled for spaced-repetition review. */
@Entity(
    tableName = "learned_words",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["normalizedWord"], unique = true), Index("sessionId"), Index("nextReviewAt")]
)
data class LearnedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    /** Lower-cased, trimmed copy of [word]; keeps one row per word. */
    val normalizedWord: String,
    val meaning: String,
    val exampleSentence: String,
    val sessionId: String?,
    val learnedAt: Long,
    val reviewCount: Int = 0,
    val nextReviewAt: Long
)
