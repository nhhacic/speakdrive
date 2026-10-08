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
    val isCompleted: Boolean,
    /** Share of drill sentences passed (repeat-after-me lessons only). Added in schema v3. */
    val pronunciationScore: Int? = null,
    /** Recommended level (e.g. "PRE_INTERMEDIATE"). Added in schema v5. */
    val recommendedLevel: String? = null,
    /** "LEVEL_UP", "LEVEL_DOWN", or "KEEP". Added in schema v5. */
    val levelRecommendationDirection: String? = null,
    /** Reason for the recommendation. Added in schema v5. */
    val levelRecommendationReason: String? = null
)

/** One graded "repeat after me" attempt. Added in schema v3. */
@Entity(
    tableName = "pronunciation_attempts",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class PronunciationAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val target: String,
    val heard: String,
    val accuracyPercent: Int,
    val passed: Boolean,
    val attemptNumber: Int,
    val modelSaidCorrect: Boolean,
    /** Words to work on, separated by "|". */
    val problemWords: String,
    val notes: String,
    val timestamp: Long,
    /** Azure Pronunciation Assessment scores (0–100); null when Azure was off. Added in schema v4. */
    val azurePronScore: Int? = null,
    val azureAccuracy: Int? = null,
    val azureFluency: Int? = null,
    val azureCompleteness: Int? = null,
    /** Weak words and sounds reported by Azure, e.g. "three (th 35)|want (t 40)". */
    val azureWeakSounds: String? = null
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

/**
 * A mistake the learner made, scheduled for spaced-repetition review in mistake review lessons.
 * One row per distinct wrong sentence; making the same mistake again sends it back to the start.
 */
@Entity(
    tableName = "mistakes",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["normalizedOriginal"], unique = true), Index("sessionId"), Index("nextReviewAt")]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val original: String,
    /** Lower-cased copy of [original] without surrounding punctuation; keeps one row per mistake. */
    val normalizedOriginal: String,
    val corrected: String,
    val explanation: String,
    /** The lesson the mistake was last made in. */
    val sessionId: String?,
    val createdAt: Long,
    val reviewCount: Int = 0,
    val nextReviewAt: Long,
    val lastReviewedAt: Long? = null,
    /** How many lessons the learner made this mistake in. */
    val timesMade: Int = 1
)

/** A short fact the learner shared about themselves, so the AI can personalise later lessons. */
@Entity(
    tableName = "learner_facts",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["normalizedFact"], unique = true), Index("sessionId")]
)
data class LearnerFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val normalizedFact: String,
    val sessionId: String?,
    val createdAt: Long
)
