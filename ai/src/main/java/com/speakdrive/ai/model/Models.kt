package com.speakdrive.ai.model

import java.util.UUID

enum class DifficultyLevel {
    BEGINNER, INTERMEDIATE, ADVANCED
}

enum class ConversationState {
    IDLE, CONNECTING, ACTIVE, PAUSED, ENDED, ERROR
}

enum class MessageRole {
    USER, AI, SYSTEM
}

data class Topic(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val description: String,
    val iconRes: Int,
    val subScenarios: List<String>
)

data class ConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val topicId: String,
    val level: DifficultyLevel,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val messages: MutableList<ConversationMessage> = mutableListOf()
)

data class SessionSummary(
    val sessionId: String,
    val durationMs: Long,
    val vocabularySuggestions: List<String>,
    val grammarCorrections: List<String>,
    val overallFeedback: String
)
