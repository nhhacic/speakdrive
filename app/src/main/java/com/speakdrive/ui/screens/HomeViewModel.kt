package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Topic
import com.speakdrive.data.repository.ProgressRepository
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.playback.CarConnectionObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TopicProgressUi(val topic: Topic, val completedLessons: Int)

data class HomeUiState(
    val stats: ProgressStats = ProgressStats(),
    val dailyGoalMinutes: Int = UserPreferences.DEFAULT_DAILY_GOAL,
    val level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    val lastTopic: Topic? = null,
    val topics: List<TopicProgressUi> = emptyList(),
    val isCarConnected: Boolean = false,
    /** A lesson that is running right now (possibly started from the car). */
    val currentLesson: ActiveLesson? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    carConnection: CarConnectionObserver,
    engine: ConversationEngine,
    private val topicManager: TopicManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        progressRepository.observeStats(),
        preferencesRepository.preferences,
        carConnection.isConnectedToCar,
        engine.lesson
    ) { stats, prefs, inCar, lesson ->
        HomeUiState(
            stats = stats,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            level = prefs.learner.level,
            lastTopic = topicManager.getTopicById(prefs.learner.lastTopicId),
            topics = topicManager.getAllTopics().map { TopicProgressUi(it, stats.topicCounts[it.id] ?: 0) },
            isCarConnected = inCar,
            currentLesson = lesson,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
