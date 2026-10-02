package com.speakdrive.ai.session

import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.ReviewWord

/**
 * Persistence the conversation engine needs. Implemented by the app's Room/DataStore layer,
 * so this module stays free of storage details.
 */
interface SessionStore {
    suspend fun saveSession(session: CompletedSession)

    /** Topic ids of the most recent lessons, newest first. */
    suspend fun recentTopicIds(limit: Int): List<String>

    /** Words whose spaced-repetition review is due now. */
    suspend fun wordsDueForReview(limit: Int): List<ReviewWord>

    /** Moves reviewed words to their next review interval. */
    suspend fun markWordsReviewed(words: List<String>)
}

interface LearningSettings {
    suspend fun snapshot(): LearnerSettings
    suspend fun setLevel(level: DifficultyLevel)
    suspend fun setLastTopicId(topicId: String)
}
