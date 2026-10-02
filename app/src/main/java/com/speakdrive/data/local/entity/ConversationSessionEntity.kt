package com.speakdrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class ConversationSessionEntity(
    @PrimaryKey
    val id: String,
    val topicId: String,
    val difficultyLevel: String,
    val startTime: Long,
    val endTime: Long? = null,
    val fluencyScore: Float? = null,
    val grammarScore: Float? = null,
    val vocabularyScore: Float? = null,
    val isCompleted: Boolean = false
)
