package com.speakdrive.ai.session

import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerMemory
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.MistakeReviewResult
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewMistake
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

    /** Earlier mistakes whose review is due now, the ones made most often first. */
    suspend fun mistakesDueForReview(limit: Int): List<ReviewMistake> = emptyList()

    /** Most recent mistakes, for extra practice when none are due. */
    suspend fun recentMistakes(limit: Int): List<ReviewMistake> = emptyList()

    /** Fixed mistakes move to a longer review interval; the others come back tomorrow. */
    suspend fun recordMistakeReviews(results: List<MistakeReviewResult>) {}

    /** What the AI should remember about the learner at the start of a lesson. */
    suspend fun learnerMemory(): LearnerMemory = LearnerMemory.EMPTY
}

interface LearningSettings {
    suspend fun snapshot(): LearnerSettings
    fun observeLearnerSettings(): Flow<LearnerSettings> = emptyFlow()
    suspend fun setLevel(level: DifficultyLevel)
    suspend fun setLastTopicId(topicId: String)
    suspend fun setLastSession(topicId: String, mode: com.speakdrive.ai.model.SessionMode, scenarioId: String? = null) {}
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
    suspend fun setAzureEnabled(enabled: Boolean) {}
    suspend fun setAutoStartOnCarConnect(enabled: Boolean) {}
    suspend fun setDailyGoalMinutes(minutes: Int) {}
    suspend fun setScreenAwakeMode(mode: com.speakdrive.ai.model.ScreenAwakeMode) {}
    suspend fun setRememberLearner(enabled: Boolean) {}
    suspend fun setPracticeReminder(enabled: Boolean, minuteOfDay: Int? = null) {}
    suspend fun setStreakFreeze(enabled: Boolean) {}
    suspend fun setOfflinePractice(enabled: Boolean) {}
}

