package com.speakdrive.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.session.LearningSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ScreenAwakeMode(
    val shortLabelVi: String,
    val descriptionVi: String,
    val timeoutSeconds: Int,
    val shortLabelEn: String = "",
    val descriptionEn: String = ""
) {
    ALWAYS_ON(
        shortLabelVi = "Luôn bật",
        descriptionVi = "Màn hình luôn sáng trong suốt buổi luyện nói để tiện nhìn văn bản.",
        timeoutSeconds = -1,
        shortLabelEn = "Always on",
        descriptionEn = "Screen stays continuously on while practicing to easily read transcripts."
    ),
    FOLLOW_SYSTEM(
        shortLabelVi = "Theo máy",
        descriptionVi = "Màn hình tự tắt và khoá theo cài đặt thời gian chờ của điện thoại khi bạn không chạm vào máy. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 0,
        shortLabelEn = "System default",
        descriptionEn = "Screen turns off according to your phone display sleep timeout. Microphone continues listening."
    ),
    AFTER_30_SECONDS(
        shortLabelVi = "Sau 30s",
        descriptionVi = "Màn hình tự tắt sau 30 giây nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 30,
        shortLabelEn = "After 30s",
        descriptionEn = "Screen turns off after 30 seconds of inactivity. Microphone continues listening."
    ),
    AFTER_1_MINUTE(
        shortLabelVi = "Sau 1 phút",
        descriptionVi = "Màn hình tự tắt sau 1 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 60,
        shortLabelEn = "After 1m",
        descriptionEn = "Screen turns off after 1 minute of inactivity. Microphone continues listening."
    ),
    AFTER_2_MINUTES(
        shortLabelVi = "Sau 2 phút",
        descriptionVi = "Màn hình tự tắt sau 2 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 120,
        shortLabelEn = "After 2m",
        descriptionEn = "Screen turns off after 2 minutes of inactivity. Microphone continues listening."
    ),
    AFTER_5_MINUTES(
        shortLabelVi = "Sau 5 phút",
        descriptionVi = "Màn hình tự tắt sau 5 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 300,
        shortLabelEn = "After 5m",
        descriptionEn = "Screen turns off after 5 minutes of inactivity. Microphone continues listening."
    );

    fun getLabel(isVi: Boolean): String = if (isVi) shortLabelVi else shortLabelEn
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(name: String?): ScreenAwakeMode =
            entries.firstOrNull { it.name == name } ?: ALWAYS_ON
    }
}

data class UserPreferences(
    val learner: LearnerSettings = LearnerSettings(),
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL,
    val onboardingCompleted: Boolean = false,
    val autoStartOnCarConnect: Boolean = true,
    val screenAwakeMode: ScreenAwakeMode = ScreenAwakeMode.ALWAYS_ON,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM
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

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val appLang = AppLanguage.fromCode(prefs[APP_LANGUAGE])
        UserPreferences(
            learner = LearnerSettings(
                level = DifficultyLevel.fromStored(prefs[DIFFICULTY_LEVEL]),
                voiceId = AiVoice.fromId(prefs[VOICE_ID]).id,
                allowVietnameseHelp = prefs[ALLOW_VIETNAMESE_HELP] ?: true,
                allowBargeIn = prefs[ALLOW_BARGE_IN] ?: false,
                azureEnabled = prefs[AZURE_ENABLED] ?: false,
                // Defaults to preconfigured BuildConfig credentials if not explicitly overridden.
                azureRegion = prefs[AZURE_REGION]?.takeIf { it.isNotBlank() } ?: BuildConfig.AZURE_SPEECH_REGION,
                azureKey = prefs[AZURE_KEY]?.takeIf { it.isNotBlank() } ?: BuildConfig.AZURE_SPEECH_KEY,
                lastTopicId = prefs[LAST_TOPIC_ID],
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
                autoPauseWhenUnfocused = prefs[AUTO_PAUSE_WHEN_UNFOCUSED] ?: true
            ),
            dailyGoalMinutes = prefs[DAILY_GOAL_MINUTES] ?: UserPreferences.DEFAULT_DAILY_GOAL,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            autoStartOnCarConnect = prefs[AUTO_START_ON_CAR_CONNECT] ?: true,
            screenAwakeMode = ScreenAwakeMode.fromStored(prefs[SCREEN_AWAKE_MODE]),
            appLanguage = appLang
        )
    }

    override suspend fun snapshot(): LearnerSettings = preferences.first().learner

    override fun observeLearnerSettings(): Flow<LearnerSettings> = preferences.map { it.learner }

    override suspend fun setLevel(level: DifficultyLevel) {
        dataStore.edit { it[DIFFICULTY_LEVEL] = level.name }
    }

    override suspend fun setLastTopicId(topicId: String) {
        dataStore.edit { it[LAST_TOPIC_ID] = topicId }
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

    suspend fun setAzureEnabled(enabled: Boolean) {
        dataStore.edit { it[AZURE_ENABLED] = enabled }
    }

    suspend fun setAzureCredentials(region: String, key: String) {
        dataStore.edit {
            it[AZURE_REGION] = region.trim()
            it[AZURE_KEY] = key.trim()
        }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = minutes }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAutoStartOnCarConnect(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_ON_CAR_CONNECT] = enabled }
    }

    suspend fun setScreenAwakeMode(mode: ScreenAwakeMode) {
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

    private companion object {
        val DIFFICULTY_LEVEL = stringPreferencesKey("difficulty_level")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val ALLOW_VIETNAMESE_HELP = booleanPreferencesKey("allow_vietnamese_help")
        val ALLOW_BARGE_IN = booleanPreferencesKey("allow_barge_in")
        val AZURE_ENABLED = booleanPreferencesKey("azure_enabled")
        val AZURE_REGION = stringPreferencesKey("azure_region")
        val AZURE_KEY = stringPreferencesKey("azure_key")
        val LAST_TOPIC_ID = stringPreferencesKey("last_topic_id")
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
    }
}
