package com.speakdrive.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DIFFICULTY_LEVEL = stringPreferencesKey("difficulty_level")
        val SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val LAST_TOPIC_ID = stringPreferencesKey("last_topic_id")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
    }

    val difficultyLevel: Flow<String> = dataStore.data.map { it[DIFFICULTY_LEVEL] ?: "Beginner" }
    val selectedVoice: Flow<String> = dataStore.data.map { it[SELECTED_VOICE] ?: "en-US-Standard-A" }
    val lastTopicId: Flow<String?> = dataStore.data.map { it[LAST_TOPIC_ID] }
    val dailyGoalMinutes: Flow<Int> = dataStore.data.map { it[DAILY_GOAL_MINUTES] ?: 15 }
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[IS_ONBOARDING_COMPLETED] ?: false }

    suspend fun setDifficultyLevel(level: String) {
        dataStore.edit { it[DIFFICULTY_LEVEL] = level }
    }

    suspend fun setSelectedVoice(voiceId: String) {
        dataStore.edit { it[SELECTED_VOICE] = voiceId }
    }

    suspend fun setLastTopicId(topicId: String) {
        dataStore.edit { it[LAST_TOPIC_ID] = topicId }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = minutes }
    }

    suspend fun setIsOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[IS_ONBOARDING_COMPLETED] = completed }
    }
}
