package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.DifficultyLevel
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

    fun setLevel(level: DifficultyLevel) = viewModelScope.launch { repository.setLevel(level) }

    fun setVoice(voice: AiVoice) = viewModelScope.launch { repository.setVoice(voice) }

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
                onFailure = { AzureTestState.Failed(it.message ?: "Không kết nối được tới Azure") }
            )
        }
    }

    fun setDailyGoal(minutes: Int) = viewModelScope.launch { repository.setDailyGoalMinutes(minutes) }
}
