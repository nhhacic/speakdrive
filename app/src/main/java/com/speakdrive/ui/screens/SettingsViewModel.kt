package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setLevel(level: DifficultyLevel) = viewModelScope.launch { repository.setLevel(level) }

    fun setVoice(voice: AiVoice) = viewModelScope.launch { repository.setVoice(voice) }

    fun setAllowVietnameseHelp(allowed: Boolean) = viewModelScope.launch { repository.setAllowVietnameseHelp(allowed) }

    fun setDailyGoal(minutes: Int) = viewModelScope.launch { repository.setDailyGoalMinutes(minutes) }
}
