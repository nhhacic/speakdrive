package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.evaluation.LevelEvaluator
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.data.repository.ProgressRepository
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class TopicStatRow(
    val emoji: String,
    val titleVi: String,
    val titleEn: String,
    val count: Int
)

data class HistoryItem(
    val sessionId: String,
    val mode: String,
    val topicId: String,
    val topicEmoji: String,
    val topicTitleVi: String,
    val topicTitleEn: String,
    val scenarioTitleVi: String?,
    val scenarioTitleEn: String?,
    val startedAt: Long,
    val activeDurationMs: Long,
    val level: DifficultyLevel,
    val averageScore: Int?,
    val isCompleted: Boolean
)

data class ProgressUiState(
    val stats: ProgressStats = ProgressStats(),
    val currentLevel: DifficultyLevel = DifficultyLevel.BEGINNER,
    val roadmapRecommendation: LevelRecommendation? = null,
    val isAdaptiveEnabled: Boolean = true,
    val history: List<HistoryItem> = emptyList(),
    val topicRows: List<TopicStatRow> = emptyList()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    sessionRepository: SessionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val topicManager: TopicManager
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        progressRepository.observeStats(),
        sessionRepository.observeHistory(),
        preferencesRepository.preferences
    ) { stats, sessions, prefs ->
        val currentLevel = prefs.learner.level
        val isAdaptive = prefs.learner.adaptiveLevelRecommendation

        val completedSessions = sessions.filter { it.isCompleted }
        val recentAverages = completedSessions.take(5).mapNotNull { s ->
            val scores = s.pronunciationScore?.let { listOf((it + 5) / 10.0) }
                ?: listOfNotNull(s.fluencyScore?.toDouble(), s.grammarScore?.toDouble(), s.vocabularyScore?.toDouble())
            if (scores.isNotEmpty()) scores.average() else null
        }
        val recentCorrections = completedSessions.take(5).map { 1 }

        val roadmapRecommendation = if (isAdaptive) {
            LevelEvaluator.evaluateTrend(currentLevel, recentAverages, recentCorrections)
        } else null

        ProgressUiState(
            stats = stats,
            currentLevel = currentLevel,
            roadmapRecommendation = roadmapRecommendation,
            isAdaptiveEnabled = isAdaptive,
            history = sessions.map { session ->
                val topic = topicManager.getTopicById(session.topicId)
                val scenario = topicManager.getScenario(session.scenarioId)?.second
                val scores = session.pronunciationScore?.let { listOf((it + 5) / 10) }
                    ?: listOfNotNull(session.fluencyScore, session.grammarScore, session.vocabularyScore)
                HistoryItem(
                    sessionId = session.id,
                    mode = session.mode,
                    topicId = session.topicId,
                    topicEmoji = topic?.emoji.orEmpty(),
                    topicTitleVi = topic?.titleVi ?: session.topicId,
                    topicTitleEn = topic?.titleEn ?: session.topicId,
                    scenarioTitleVi = scenario?.titleVi,
                    scenarioTitleEn = scenario?.titleEn,
                    startedAt = session.startedAt,
                    activeDurationMs = session.activeDurationMs,
                    level = DifficultyLevel.fromStored(session.level),
                    averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                    isCompleted = session.isCompleted
                )
            },
            topicRows = topicManager.getAllTopics().map {
                TopicStatRow(
                    emoji = it.emoji,
                    titleVi = it.titleVi,
                    titleEn = it.titleEn,
                    count = stats.topicCounts[it.id] ?: 0
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun applyRecommendedLevel(level: DifficultyLevel) {
        viewModelScope.launch {
            preferencesRepository.setLevel(level)
        }
    }
}
