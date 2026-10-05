package com.speakdrive.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.evaluation.LevelEvaluator
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.repository.SessionDetail
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.ui.navigation.SummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val isLoading: Boolean = true,
    val isSummarizing: Boolean = false,
    val detail: SessionDetail? = null,
    val mode: SessionMode? = null,
    val topicTitleVi: String? = null,
    val topicTitleEn: String? = null,
    val topicEmoji: String? = null,
    val scenarioTitleVi: String? = null,
    val scenarioTitleEn: String? = null,
    val level: DifficultyLevel? = null,
    val durationMs: Long = 0L,
    /** What "practise again" should start. */
    val againMediaId: String = MediaIds.RESUME,
    val levelRecommendation: LevelRecommendation? = null,
    val isLevelApplied: Boolean = false,
    val appliedLevel: DifficultyLevel? = null,
    val isAdaptiveEnabled: Boolean = true
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
    engine: ConversationEngine,
    private val topicManager: TopicManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SummaryRoute>().sessionId
    private val isLevelApplied = MutableStateFlow(false)
    private val appliedLevel = MutableStateFlow<DifficultyLevel?>(null)

    val uiState: StateFlow<SummaryUiState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        engine.summarizingSessionIds,
        userPreferencesRepository.observeLearnerSettings(),
        isLevelApplied,
        appliedLevel
    ) { detail, summarizing, learnerSettings, applied, targetAppliedLevel ->
        val session = detail?.session
        val topic = topicManager.getTopicById(session?.topicId)
        val scenario = topicManager.getScenario(session?.scenarioId)?.second
        val mode = session?.mode?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() }
        val currentLevel = session?.let { DifficultyLevel.fromStored(it.level) } ?: learnerSettings.level

        val recommendation = if (session?.recommendedLevel != null && session.levelRecommendationDirection != null) {
            val dir = runCatching { LevelAdjustmentDirection.valueOf(session.levelRecommendationDirection) }
                .getOrDefault(LevelAdjustmentDirection.KEEP)
            val target = DifficultyLevel.fromStored(session.recommendedLevel)
            LevelRecommendation(
                direction = dir,
                targetLevel = target,
                reasonVi = session.levelRecommendationReason.orEmpty(),
                reasonEn = ""
            )
        } else if (session != null && (session.fluencyScore != null || session.grammarScore != null)) {
            LevelEvaluator.evaluateSession(
                currentLevel = currentLevel,
                fluencyScore = session.fluencyScore,
                grammarScore = session.grammarScore,
                vocabularyScore = session.vocabularyScore,
                pronunciationScore = session.pronunciationScore,
                correctionsCount = detail.corrections.size,
                learnerTurns = detail.messages.count { it.speaker == "USER" }
            )
        } else null

        SummaryUiState(
            isLoading = false,
            isSummarizing = sessionId in summarizing,
            detail = detail,
            mode = mode,
            topicTitleVi = topic?.titleVi,
            topicTitleEn = topic?.titleEn,
            topicEmoji = topic?.emoji,
            scenarioTitleVi = scenario?.titleVi,
            scenarioTitleEn = scenario?.titleEn,
            level = currentLevel,
            durationMs = session?.activeDurationMs ?: 0L,
            againMediaId = when {
                mode == SessionMode.REPEAT_AFTER_ME -> MediaIds.pronunciation(topic?.id)
                scenario != null -> MediaIds.scenario(scenario.id)
                topic != null -> MediaIds.topic(topic.id)
                else -> MediaIds.RESUME
            },
            levelRecommendation = recommendation,
            isLevelApplied = applied,
            appliedLevel = targetAppliedLevel,
            isAdaptiveEnabled = learnerSettings.adaptiveLevelRecommendation
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun applyRecommendation(targetLevel: DifficultyLevel) {
        viewModelScope.launch {
            userPreferencesRepository.setLevel(targetLevel)
            appliedLevel.value = targetLevel
            isLevelApplied.value = true
        }
    }
}
