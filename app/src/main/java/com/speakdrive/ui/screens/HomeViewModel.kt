package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.auto.VoiceCommandHandler
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
    val customScenarios: List<CustomScenario> = emptyList(),
    val isCarConnected: Boolean = false,
    /** A lesson that is running right now (possibly started from the car). */
    val currentLesson: ActiveLesson? = null,
    /** State of [currentLesson], e.g. [ConversationState.PAUSED]. */
    val lessonState: ConversationState = ConversationState.IDLE,
    val isLoading: Boolean = true
)

/**
 * Topics suggested on the home screen: the last topic first, then the two most practised ones,
 * then topics the learner has not tried yet, so the list mixes "keep going" with something new.
 */
internal fun recommendTopics(
    topics: List<TopicProgressUi>,
    lastTopicId: String?,
    limit: Int = 5
): List<TopicProgressUi> {
    val last = topics.firstOrNull { it.topic.id == lastTopicId }
    val practised = topics
        .filter { it.completedLessons > 0 && it.topic.id != lastTopicId }
        .sortedByDescending { it.completedLessons }
        .take(2)
    val picked = listOfNotNull(last) + practised
    val pickedIds = picked.map { it.topic.id }.toSet()
    val rest = topics.filterNot { it.topic.id in pickedIds }.sortedBy { it.completedLessons > 0 }
    return (picked + rest).take(limit)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    preferencesRepository: UserPreferencesRepository,
    private val sessionStore: SessionStore,
    carConnection: CarConnectionObserver,
    private val engine: ConversationEngine,
    private val topicManager: TopicManager,
    private val voiceCommandHandler: VoiceCommandHandler
) : ViewModel() {

    private val recentStoriesFlow = MutableStateFlow<List<CompletedSession>>(emptyList())
    private val unfinishedStoryFlow = MutableStateFlow<CompletedSession?>(null)
    private val customScenariosFlow = MutableStateFlow<List<CustomScenario>>(emptyList())

    init {
        viewModelScope.launch {
            refreshRecentStories()
            refreshCustomScenarios()
        }
        viewModelScope.launch {
            engine.endedSessions.collect {
                refreshRecentStories()
                refreshCustomScenarios()
            }
        }
    }

    private suspend fun refreshRecentStories() {
        recentStoriesFlow.value = sessionStore.recentStorySessions(5)
        unfinishedStoryFlow.value = sessionStore.latestUnfinishedStorySession()
    }

    private suspend fun refreshCustomScenarios() {
        customScenariosFlow.value = sessionStore.customScenarios()
    }

    fun resumeStory() {
        engine.resumeStory()
    }

    /** Media id for a spoken command from the home screen mic, e.g. "ôn từ vựng" → `review`. */
    fun resolveVoiceCommand(transcript: String): String = voiceCommandHandler.resolve(transcript)

    fun deleteCustomScenario(id: String) {
        viewModelScope.launch {
            sessionStore.deleteCustomScenario(id)
            refreshCustomScenarios()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(progressRepository.observeStats(), preferencesRepository.preferences, carConnection.isConnectedToCar) { stats, prefs, inCar ->
            Triple(stats, prefs, inCar)
        },
        combine(engine.lesson, engine.state) { lesson, state -> lesson to state },
        recentStoriesFlow,
        unfinishedStoryFlow,
        customScenariosFlow
    ) { (stats, prefs, inCar), (lesson, lessonState), recentStories, unfinishedStory, customScenarios ->
        HomeUiState(
            stats = stats,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            level = prefs.learner.level,
            lastTopic = topicManager.getTopicById(prefs.learner.lastTopicId),
            topics = topicManager.getConversationTopics().map { TopicProgressUi(it, stats.topicCounts[it.id] ?: 0) },
            storyTopics = topicManager.getStoryTopics(),
            recentStories = recentStories,
            unfinishedStory = unfinishedStory,
            customScenarios = customScenarios,
            isCarConnected = inCar,
            currentLesson = lesson,
            lessonState = lessonState,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
