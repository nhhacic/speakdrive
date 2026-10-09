package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MistakeEntity
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.domain.ReminderPlanner
import com.speakdrive.reminder.PracticeReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LearnerMemoryUiState(
    val rememberLearner: Boolean = true,
    val reminderEnabled: Boolean = true,
    /** The learner's own reminder time, or [LearnerSettings.REMINDER_AUTO]. */
    val reminderMinute: Int = LearnerSettings.REMINDER_AUTO,
    /** When an automatic reminder would fire, from the learner's practice habit. */
    val autoReminderMinute: Int = ReminderPlanner.DEFAULT_MINUTE,
    val streakFreezeEnabled: Boolean = true,
    val offlinePracticeEnabled: Boolean = true,
    val weeklyDigestEnabled: Boolean = true,
    val canNotify: Boolean = true,
    val facts: List<LearnerFactEntity> = emptyList(),
    val mistakes: List<MistakeEntity> = emptyList()
)

/** Memory, reminder and streak freeze settings, plus the list of what the AI remembers. */
@HiltViewModel
class LearnerMemoryViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val sessions: SessionRepository,
    private val reminder: PracticeReminder
) : ViewModel() {

    private val canNotify = MutableStateFlow(reminder.canNotify())

    val uiState: StateFlow<LearnerMemoryUiState> = combine(
        preferences.preferences,
        sessions.observeLearnerFacts(),
        sessions.observeMistakes(),
        canNotify
    ) { prefs, facts, mistakes, notify ->
        val learner = prefs.learner
        LearnerMemoryUiState(
            rememberLearner = learner.rememberLearner,
            reminderEnabled = learner.practiceReminderEnabled,
            reminderMinute = learner.practiceReminderMinute,
            autoReminderMinute = reminder.reminderMinute(learner.copy(practiceReminderMinute = LearnerSettings.REMINDER_AUTO)),
            streakFreezeEnabled = learner.streakFreezeEnabled,
            offlinePracticeEnabled = learner.offlinePracticeEnabled,
            weeklyDigestEnabled = learner.weeklyDigestEnabled,
            canNotify = notify,
            facts = facts,
            mistakes = mistakes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnerMemoryUiState())

    fun setWeeklyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setWeeklyDigestEnabled(enabled) }
    }

    /** Call after returning from the permission dialog or the system notification settings. */
    fun refreshNotificationState() {
        canNotify.value = reminder.canNotify()
    }

    fun setRememberLearner(enabled: Boolean) = viewModelScope.launch { preferences.setRememberLearner(enabled) }

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setPracticeReminder(enabled) }

    /** [minuteOfDay] is minutes after midnight, or [LearnerSettings.REMINDER_AUTO]. */
    fun setReminderTime(minuteOfDay: Int) = viewModelScope.launch { preferences.setPracticeReminder(true, minuteOfDay) }

    fun setStreakFreeze(enabled: Boolean) = viewModelScope.launch { preferences.setStreakFreeze(enabled) }

    fun setOfflinePractice(enabled: Boolean) = viewModelScope.launch { preferences.setOfflinePractice(enabled) }

    fun deleteFact(id: Long) = viewModelScope.launch { sessions.deleteLearnerFact(id) }

    fun forgetAllFacts() = viewModelScope.launch { sessions.forgetLearnerFacts() }

    fun deleteMistake(id: Long) = viewModelScope.launch { sessions.deleteMistake(id) }
}

/** "7:05" style, 24-hour. */
fun formatMinuteOfDay(minute: Int): String = "%d:%02d".format(minute / 60, minute % 60)
