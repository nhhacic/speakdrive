package com.speakdrive.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.speakdrive.BuildConfig
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.session.LearningSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// A corrupted preferences file must not crash every launch: start over with defaults instead.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)


data class UserPreferences(
    val learner: LearnerSettings = LearnerSettings(),
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL,
    val onboardingCompleted: Boolean = false,
    val autoStartOnCarConnect: Boolean = true,
    val screenAwakeMode: ScreenAwakeMode = ScreenAwakeMode.ALWAYS_ON,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val showTranslationSubtitle: Boolean = true
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 15
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LearningSettings {

    private val dataStore = context.dataStore

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
        val appLang = AppLanguage.fromCode(prefs[APP_LANGUAGE])
        val showSubs = prefs[SHOW_TRANSLATION_SUBTITLE] ?: true
        UserPreferences(
            learner = LearnerSettings(
                level = DifficultyLevel.fromStored(prefs[DIFFICULTY_LEVEL]),
                voiceId = AiVoice.fromId(prefs[VOICE_ID]).id,
                allowVietnameseHelp = prefs[ALLOW_VIETNAMESE_HELP] ?: true,
                allowBargeIn = prefs[ALLOW_BARGE_IN] ?: false,
                azureEnabled = prefs[AZURE_ENABLED] ?: false,
                // The key is never shipped inside the APK: each learner enters their own in Settings.
                azureRegion = prefs[AZURE_REGION]?.takeIf { it.isNotBlank() } ?: BuildConfig.AZURE_SPEECH_REGION,
                azureKey = prefs[AZURE_KEY].orEmpty(),
                lastTopicId = prefs[LAST_TOPIC_ID],
                lastSessionMode = prefs[LAST_SESSION_MODE]?.let { modeStr ->
                    runCatching { com.speakdrive.ai.model.SessionMode.valueOf(modeStr) }.getOrNull()
                } ?: com.speakdrive.ai.model.SessionMode.FREE_TALK,
                lastScenarioId = prefs[LAST_SCENARIO_ID],
                pronunciationStrictness = PronunciationStrictness.fromStored(prefs[PRONUNCIATION_STRICTNESS]),
                storytellingStyle = StorytellingStyle.fromStored(prefs[STORYTELLING_STYLE]),
                randomVoice = prefs[RANDOM_VOICE] ?: false,
                storyDuration = com.speakdrive.ai.model.StoryDuration.fromStored(prefs[STORY_DURATION]),
                multiVoiceStorytelling = prefs[MULTI_VOICE_STORYTELLING] ?: true,
                appLanguage = appLang,
                adaptiveLevelRecommendation = prefs[ADAPTIVE_LEVEL_RECOMMENDATION] ?: true,
                drillSentenceLength = DrillSentenceLength.fromStored(prefs[DRILL_SENTENCE_LENGTH]),
                drillCategory = DrillCategory.fromStored(prefs[DRILL_CATEGORY]),
                aiVolume = (prefs[AI_VOLUME] ?: 80).coerceIn(10, 100),
                autoPauseWhenUnfocused = prefs[AUTO_PAUSE_WHEN_UNFOCUSED] ?: true,
                showTranslationSubtitle = showSubs,
                rememberLearner = prefs[REMEMBER_LEARNER] ?: true,
                betterPhrasingEnabled = prefs[BETTER_PHRASING_ENABLED] ?: true,
                practiceReminderEnabled = prefs[PRACTICE_REMINDER_ENABLED] ?: true,
                practiceReminderMinute = prefs[PRACTICE_REMINDER_MINUTE]
                    ?.takeIf { it in 0 until MINUTES_PER_DAY } ?: LearnerSettings.REMINDER_AUTO,
                streakFreezeEnabled = prefs[STREAK_FREEZE_ENABLED] ?: true,
                offlinePracticeEnabled = prefs[OFFLINE_PRACTICE_ENABLED] ?: true,
                autoStartOnCarConnect = prefs[AUTO_START_ON_CAR_CONNECT] ?: true,
                dailyGoalMinutes = clampGoal(prefs[DAILY_GOAL_MINUTES] ?: UserPreferences.DEFAULT_DAILY_GOAL),
                screenAwakeMode = ScreenAwakeMode.fromStored(prefs[SCREEN_AWAKE_MODE])
            ),
            dailyGoalMinutes = clampGoal(prefs[DAILY_GOAL_MINUTES] ?: UserPreferences.DEFAULT_DAILY_GOAL),
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            autoStartOnCarConnect = prefs[AUTO_START_ON_CAR_CONNECT] ?: true,
            screenAwakeMode = ScreenAwakeMode.fromStored(prefs[SCREEN_AWAKE_MODE]),
            appLanguage = appLang,
            showTranslationSubtitle = showSubs
        )
    }

    override suspend fun snapshot(): LearnerSettings = preferences.first().learner

    override fun observeLearnerSettings(): Flow<LearnerSettings> = preferences.map { it.learner }.distinctUntilChanged()

    override suspend fun setLevel(level: DifficultyLevel) {
        dataStore.edit { it[DIFFICULTY_LEVEL] = level.name }
    }

    override suspend fun setLastTopicId(topicId: String) {
        dataStore.edit { it[LAST_TOPIC_ID] = topicId }
    }

    override suspend fun setLastSession(topicId: String, mode: com.speakdrive.ai.model.SessionMode, scenarioId: String?) {
        dataStore.edit {
            it[LAST_TOPIC_ID] = topicId
            it[LAST_SESSION_MODE] = mode.name
            if (scenarioId != null) {
                it[LAST_SCENARIO_ID] = scenarioId
            } else {
                it.remove(LAST_SCENARIO_ID)
            }
        }
    }

    override suspend fun setVoice(voice: AiVoice) {
        dataStore.edit {
            it[VOICE_ID] = voice.id
            it[RANDOM_VOICE] = false
        }
    }

    override suspend fun setRandomVoice(enabled: Boolean) {
        dataStore.edit { it[RANDOM_VOICE] = enabled }
    }

    override suspend fun setAllowVietnameseHelp(allowed: Boolean) {
        dataStore.edit { it[ALLOW_VIETNAMESE_HELP] = allowed }
    }

    override suspend fun setPronunciationStrictness(strictness: PronunciationStrictness) {
        dataStore.edit { it[PRONUNCIATION_STRICTNESS] = strictness.name }
    }

    override suspend fun setStorytellingStyle(style: StorytellingStyle) {
        dataStore.edit { it[STORYTELLING_STYLE] = style.name }
    }

    override suspend fun setStoryDuration(duration: com.speakdrive.ai.model.StoryDuration) {
        dataStore.edit { it[STORY_DURATION] = duration.name }
    }

    override suspend fun setMultiVoiceStorytelling(enabled: Boolean) {
        dataStore.edit { it[MULTI_VOICE_STORYTELLING] = enabled }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[APP_LANGUAGE] = language.code }
        try {
            context.getSharedPreferences("speakdrive_locale", Context.MODE_PRIVATE)
                .edit()
                .putString("cached_language", language.code)
                .apply()
        } catch (_: Exception) {}
    }

    override suspend fun setAllowBargeIn(allowed: Boolean) {
        dataStore.edit { it[ALLOW_BARGE_IN] = allowed }
    }

    override suspend fun setAzureEnabled(enabled: Boolean) {
        dataStore.edit { it[AZURE_ENABLED] = enabled }
    }

    suspend fun setAzureCredentials(region: String, key: String) {
        dataStore.edit {
            it[AZURE_REGION] = region.trim()
            it[AZURE_KEY] = key.trim()
        }
    }

    override suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = clampGoal(minutes) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setAutoStartOnCarConnect(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_ON_CAR_CONNECT] = enabled }
    }

    override suspend fun setScreenAwakeMode(mode: ScreenAwakeMode) {
        dataStore.edit { it[SCREEN_AWAKE_MODE] = mode.name }
    }

    override suspend fun setAdaptiveLevelRecommendation(enabled: Boolean) {
        dataStore.edit { it[ADAPTIVE_LEVEL_RECOMMENDATION] = enabled }
    }

    override suspend fun setDrillSentenceLength(length: DrillSentenceLength) {
        dataStore.edit { it[DRILL_SENTENCE_LENGTH] = length.name }
    }

    override suspend fun setDrillCategory(category: DrillCategory) {
        dataStore.edit { it[DRILL_CATEGORY] = category.name }
    }

    override suspend fun setAiVolume(volume: Int) {
        val clamped = volume.coerceIn(10, 100)
        dataStore.edit { it[AI_VOLUME] = clamped }
    }

    override suspend fun setAutoPauseWhenUnfocused(enabled: Boolean) {
        dataStore.edit { it[AUTO_PAUSE_WHEN_UNFOCUSED] = enabled }
    }

    override suspend fun setShowTranslationSubtitle(enabled: Boolean) {
        dataStore.edit { it[SHOW_TRANSLATION_SUBTITLE] = enabled }
    }

    override suspend fun setRememberLearner(enabled: Boolean) {
        dataStore.edit { it[REMEMBER_LEARNER] = enabled }
    }

    /** [minuteOfDay] null keeps the current time; [LearnerSettings.REMINDER_AUTO] follows the learner's habit. */
    override suspend fun setPracticeReminder(enabled: Boolean, minuteOfDay: Int?) {
        dataStore.edit { prefs ->
            prefs[PRACTICE_REMINDER_ENABLED] = enabled
            if (minuteOfDay != null) {
                prefs[PRACTICE_REMINDER_MINUTE] =
                    if (minuteOfDay in 0 until MINUTES_PER_DAY) minuteOfDay else LearnerSettings.REMINDER_AUTO
            }
        }
    }

    override suspend fun setStreakFreeze(enabled: Boolean) {
        dataStore.edit { it[STREAK_FREEZE_ENABLED] = enabled }
    }

    override suspend fun setOfflinePractice(enabled: Boolean) {
        dataStore.edit { it[OFFLINE_PRACTICE_ENABLED] = enabled }
    }

    override suspend fun setBetterPhrasing(enabled: Boolean) {
        dataStore.edit { it[BETTER_PHRASING_ENABLED] = enabled }
    }

    private fun clampGoal(minutes: Int): Int =
        minutes.coerceIn(LearnerSettings.MIN_DAILY_GOAL_MINUTES, LearnerSettings.MAX_DAILY_GOAL_MINUTES)

    /** "Delete all my data": back to the default settings, including the learner's Azure key. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
        runCatching {
            context.getSharedPreferences("speakdrive_locale", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private companion object {
        val DIFFICULTY_LEVEL = stringPreferencesKey("difficulty_level")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val ALLOW_VIETNAMESE_HELP = booleanPreferencesKey("allow_vietnamese_help")
        val ALLOW_BARGE_IN = booleanPreferencesKey("allow_barge_in")
        val AZURE_ENABLED = booleanPreferencesKey("azure_enabled")
        val AZURE_REGION = stringPreferencesKey("azure_region")
        val AZURE_KEY = stringPreferencesKey("azure_key")
        val LAST_TOPIC_ID = stringPreferencesKey("last_topic_id")
        val LAST_SESSION_MODE = stringPreferencesKey("last_session_mode")
        val LAST_SCENARIO_ID = stringPreferencesKey("last_scenario_id")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_START_ON_CAR_CONNECT = booleanPreferencesKey("auto_start_on_car_connect")
        val SCREEN_AWAKE_MODE = stringPreferencesKey("screen_awake_mode")
        val PRONUNCIATION_STRICTNESS = stringPreferencesKey("pronunciation_strictness")
        val STORYTELLING_STYLE = stringPreferencesKey("storytelling_style")
        val RANDOM_VOICE = booleanPreferencesKey("random_voice")
        val STORY_DURATION = stringPreferencesKey("story_duration")
        val MULTI_VOICE_STORYTELLING = booleanPreferencesKey("multi_voice_storytelling")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ADAPTIVE_LEVEL_RECOMMENDATION = booleanPreferencesKey("adaptive_level_recommendation")
        val DRILL_SENTENCE_LENGTH = stringPreferencesKey("drill_sentence_length")
        val DRILL_CATEGORY = stringPreferencesKey("drill_category")
        val AI_VOLUME = intPreferencesKey("ai_volume")
        val AUTO_PAUSE_WHEN_UNFOCUSED = booleanPreferencesKey("auto_pause_when_unfocused")
        val SHOW_TRANSLATION_SUBTITLE = booleanPreferencesKey("show_translation_subtitle")
        val REMEMBER_LEARNER = booleanPreferencesKey("remember_learner")
        val BETTER_PHRASING_ENABLED = booleanPreferencesKey("better_phrasing_enabled")
        val PRACTICE_REMINDER_ENABLED = booleanPreferencesKey("practice_reminder_enabled")
        val PRACTICE_REMINDER_MINUTE = intPreferencesKey("practice_reminder_minute")
        val STREAK_FREEZE_ENABLED = booleanPreferencesKey("streak_freeze_enabled")
        val OFFLINE_PRACTICE_ENABLED = booleanPreferencesKey("offline_practice_enabled")
        const val MINUTES_PER_DAY = 24 * 60
    }
}
