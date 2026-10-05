package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.pronunciation.AzureSpeechConfig
import com.speakdrive.ai.pronunciation.PronunciationAssessor
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface AzureTestState {
    data object Idle : AzureTestState
    data object Testing : AzureTestState
    data object Ok : AzureTestState
    data class Failed(val message: String) : AzureTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    private val assessor: PronunciationAssessor
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _azureTest = MutableStateFlow<AzureTestState>(AzureTestState.Idle)
    val azureTest: StateFlow<AzureTestState> = _azureTest.asStateFlow()

    fun setAppLanguage(language: AppLanguage) = viewModelScope.launch { repository.setAppLanguage(language) }

    fun setLevel(level: DifficultyLevel) = viewModelScope.launch { repository.setLevel(level) }

    fun setVoice(voice: AiVoice) = viewModelScope.launch { repository.setVoice(voice) }

    fun setRandomVoice(enabled: Boolean) = viewModelScope.launch { repository.setRandomVoice(enabled) }

    fun setPronunciationStrictness(strictness: PronunciationStrictness) =
        viewModelScope.launch { repository.setPronunciationStrictness(strictness) }

    fun setAllowVietnameseHelp(allowed: Boolean) = viewModelScope.launch { repository.setAllowVietnameseHelp(allowed) }

    fun setAllowBargeIn(allowed: Boolean) = viewModelScope.launch { repository.setAllowBargeIn(allowed) }

    fun setAzureEnabled(enabled: Boolean) = viewModelScope.launch { repository.setAzureEnabled(enabled) }

    /** Saves the Azure region and key, then checks them with Azure without sending any audio. */
    fun saveAndTestAzure(region: String, key: String) {
        _azureTest.value = AzureTestState.Testing
        viewModelScope.launch {
            repository.setAzureCredentials(region, key)
            val result = withContext(Dispatchers.IO) { assessor.testConnection(AzureSpeechConfig(region, key)) }
            _azureTest.value = result.fold(
                onSuccess = { AzureTestState.Ok },
                onFailure = { AzureTestState.Failed(it.message ?: "Connection failed") }
            )
        }
    }

    /** Tests Azure connection using the currently configured (default or saved) region and key. */
    fun testAzureConnection() {
        _azureTest.value = AzureTestState.Testing
        viewModelScope.launch {
            val settings = repository.snapshot()
            val region = settings.azureRegion
            val key = settings.azureKey
            val result = withContext(Dispatchers.IO) {
                assessor.testConnection(AzureSpeechConfig(region, key))
            }
            _azureTest.value = result.fold(
                onSuccess = { AzureTestState.Ok },
                onFailure = { AzureTestState.Failed(it.message ?: "Connection failed") }
            )
        }
    }

    fun setDailyGoal(minutes: Int) = viewModelScope.launch { repository.setDailyGoalMinutes(minutes) }

    fun setStorytellingStyle(style: StorytellingStyle) = viewModelScope.launch { repository.setStorytellingStyle(style) }

    fun setStoryDuration(duration: com.speakdrive.ai.model.StoryDuration) =
        viewModelScope.launch { repository.setStoryDuration(duration) }

    fun setMultiVoiceStorytelling(enabled: Boolean) =
        viewModelScope.launch { repository.setMultiVoiceStorytelling(enabled) }

    fun setAdaptiveLevelRecommendation(enabled: Boolean) =
        viewModelScope.launch { repository.setAdaptiveLevelRecommendation(enabled) }

    fun setDrillSentenceLength(length: DrillSentenceLength) =
        viewModelScope.launch { repository.setDrillSentenceLength(length) }

    fun setDrillCategory(category: DrillCategory) =
        viewModelScope.launch { repository.setDrillCategory(category) }

    fun setAiVolume(volume: Int) =
        viewModelScope.launch { repository.setAiVolume(volume) }
}

