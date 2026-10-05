package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.data.repository.ProgressRepository
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.playback.CarConnectionObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopicProgressUi(val topic: Topic, val completedLessons: Int)

data class HomeUiState(
    val stats: ProgressStats = ProgressStats(),
    val dailyGoalMinutes: Int = UserPreferences.DEFAULT_DAILY_GOAL,
    val level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    val lastTopic: Topic? = null,
    val topics: List<TopicProgressUi> = emptyList(),
    val storyTopics: List<Topic> = emptyList(),
    val recentStories: List<CompletedSession> = emptyList(),
    val unfinishedStory: CompletedSession? = null,
    val isCarConnected: Boolean = false,
    /** A lesson that is running right now (possibly started from the car). */
    val currentLesson: ActiveLesson? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    private val sessionStore: SessionStore,
    carConnection: CarConnectionObserver,
    private val engine: ConversationEngine,
    private val topicManager: TopicManager
) : ViewModel() {

    private val recentStoriesFlow = MutableStateFlow<List<CompletedSession>>(emptyList())
    private val unfinishedStoryFlow = MutableStateFlow<CompletedSession?>(null)

    init {
        viewModelScope.launch {
            refreshRecentStories()
        }
        viewModelScope.launch {
            engine.endedSessions.collect {
                refreshRecentStories()
            }
        }
    }

    private suspend fun refreshRecentStories() {
        recentStoriesFlow.value = sessionStore.recentStorySessions(5)
        unfinishedStoryFlow.value = sessionStore.latestUnfinishedStorySession()
    }

    fun resumeStory() {
        engine.resumeStory()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(progressRepository.observeStats(), preferencesRepository.preferences, carConnection.isConnectedToCar) { stats, prefs, inCar ->
            Triple(stats, prefs, inCar)
        },
        engine.lesson,
        recentStoriesFlow,
        unfinishedStoryFlow
    ) { (stats, prefs, inCar), lesson, recentStories, unfinishedStory ->
        HomeUiState(
            stats = stats,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            level = prefs.learner.level,
            lastTopic = topicManager.getTopicById(prefs.learner.lastTopicId),
            topics = topicManager.getConversationTopics().map { TopicProgressUi(it, stats.topicCounts[it.id] ?: 0) },
            storyTopics = topicManager.getStoryTopics(),
            recentStories = recentStories,
            unfinishedStory = unfinishedStory,
            isCarConnected = inCar,
            currentLesson = lesson,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
