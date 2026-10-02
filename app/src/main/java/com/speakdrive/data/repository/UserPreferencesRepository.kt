package com.speakdrive.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.session.LearningSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val learner: LearnerSettings = LearnerSettings(),
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL,
    val onboardingCompleted: Boolean = false
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
        UserPreferences(
            learner = LearnerSettings(
                level = DifficultyLevel.fromStored(prefs[DIFFICULTY_LEVEL]),
                voiceId = AiVoice.fromId(prefs[VOICE_ID]).id,
                allowVietnameseHelp = prefs[ALLOW_VIETNAMESE_HELP] ?: true,
                lastTopicId = prefs[LAST_TOPIC_ID]
            ),
            dailyGoalMinutes = prefs[DAILY_GOAL_MINUTES] ?: UserPreferences.DEFAULT_DAILY_GOAL,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false
        )
    }

    override suspend fun snapshot(): LearnerSettings = preferences.first().learner

    override suspend fun setLevel(level: DifficultyLevel) {
        dataStore.edit { it[DIFFICULTY_LEVEL] = level.name }
    }

    override suspend fun setLastTopicId(topicId: String) {
        dataStore.edit { it[LAST_TOPIC_ID] = topicId }
    }

    suspend fun setVoice(voice: AiVoice) {
        dataStore.edit { it[VOICE_ID] = voice.id }
    }

    suspend fun setAllowVietnameseHelp(allowed: Boolean) {
        dataStore.edit { it[ALLOW_VIETNAMESE_HELP] = allowed }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = minutes }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val DIFFICULTY_LEVEL = stringPreferencesKey("difficulty_level")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val ALLOW_VIETNAMESE_HELP = booleanPreferencesKey("allow_vietnamese_help")
        val LAST_TOPIC_ID = stringPreferencesKey("last_topic_id")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
