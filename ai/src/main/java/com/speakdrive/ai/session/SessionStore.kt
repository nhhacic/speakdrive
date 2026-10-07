package com.speakdrive.ai.session

import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.StorytellingStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

    /** Most recently learned words, for extra practice or when no words are due. */
    suspend fun recentWords(limit: Int): List<ReviewWord> = emptyList()

    /** Look up review words by their word texts. */
    suspend fun wordsByWordNames(words: List<String>): List<ReviewWord> = emptyList()

    /** Moves reviewed words to their next review interval. */
    suspend fun markWordsReviewed(words: List<String>)

    /** Most recent story listening sessions, newest first. */
    suspend fun recentStorySessions(limit: Int): List<CompletedSession> = emptyList()

    /** Latest story session that was paused or ended unfinished, with full transcript so far. */
    suspend fun latestUnfinishedStorySession(): CompletedSession? = null

    /** Recent drill target sentences practiced by the learner, newest first. */
    suspend fun recentDrillTargets(limit: Int = 30): List<String> = emptyList()
}

interface LearningSettings {
    suspend fun snapshot(): LearnerSettings
    fun observeLearnerSettings(): Flow<LearnerSettings> = emptyFlow()
    suspend fun setLevel(level: DifficultyLevel)
    suspend fun setLastTopicId(topicId: String)
    suspend fun setAllowVietnameseHelp(allowed: Boolean)
    suspend fun setPronunciationStrictness(strictness: PronunciationStrictness) {}
    suspend fun setStorytellingStyle(style: StorytellingStyle) {}
    suspend fun setAllowBargeIn(allowed: Boolean) {}
    suspend fun setVoice(voice: AiVoice) {}
    suspend fun setRandomVoice(enabled: Boolean) {}
    suspend fun setStoryDuration(duration: com.speakdrive.ai.model.StoryDuration) {}
    suspend fun setMultiVoiceStorytelling(enabled: Boolean) {}
    suspend fun setAppLanguage(language: com.speakdrive.ai.model.AppLanguage) {}
    suspend fun setAdaptiveLevelRecommendation(enabled: Boolean) {}
    suspend fun setDrillSentenceLength(length: com.speakdrive.ai.model.DrillSentenceLength) {}
    suspend fun setDrillCategory(category: com.speakdrive.ai.model.DrillCategory) {}
    suspend fun setAiVolume(volume: Int) {}
    suspend fun setAutoPauseWhenUnfocused(enabled: Boolean) {}
    suspend fun setShowTranslationSubtitle(enabled: Boolean) {}
}

