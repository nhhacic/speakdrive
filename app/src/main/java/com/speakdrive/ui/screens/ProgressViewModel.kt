package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.evaluation.LevelEvaluator
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.data.local.entity.SessionEntity
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
    val isCompleted: Boolean,
    val wordsPerMinute: Int? = null
)

data class WeeklyFluencySummary(
    val currentWeekWpm: Int? = null,
    val previousWeekWpm: Int? = null,
    val currentWeekFillerRatio: Float? = null,
    val currentWeekMlu: Float? = null,
    val hasData: Boolean = false
)

data class ProgressUiState(
    val stats: ProgressStats = ProgressStats(),
    val currentLevel: DifficultyLevel = DifficultyLevel.BEGINNER,
    val roadmapRecommendation: LevelRecommendation? = null,
    val isAdaptiveEnabled: Boolean = true,
    val weeklyFluency: WeeklyFluencySummary = WeeklyFluencySummary(),
    val history: List<HistoryItem> = emptyList(),
    val topicRows: List<TopicStatRow> = emptyList()
)

/** Scores and correction counts of the same recent sessions, newest first. */
data class TrendInputs(val averages: List<Double>, val corrections: List<Int>)

/**
 * Takes the [limit] most recent completed sessions that have scores, so each average lines up with
 * the number of mistakes corrected in that same session.
 */
fun recentTrend(sessions: List<SessionEntity>, correctionCounts: Map<String, Int>, limit: Int = 5): TrendInputs {
    val scored = sessions.asSequence()
        .filter { it.isCompleted }
        .mapNotNull { s ->
            val scores = s.pronunciationScore?.let { listOf((it + 5) / 10.0) }
                ?: listOfNotNull(s.fluencyScore?.toDouble(), s.grammarScore?.toDouble(), s.vocabularyScore?.toDouble())
            if (scores.isEmpty()) null else s to scores.average()
        }
        .take(limit)
        .toList()
    return TrendInputs(
        averages = scored.map { it.second },
        corrections = scored.map { correctionCounts[it.first.id] ?: 0 }
    )
}

fun calculateWeeklyFluency(sessions: List<SessionEntity>, now: Long = System.currentTimeMillis()): WeeklyFluencySummary {
    val sevenDaysMs = 7L * 24 * 3600 * 1000
    val fourteenDaysMs = 14L * 24 * 3600 * 1000

    val completed = sessions.filter { it.isCompleted }
    val thisWeek = completed.filter { it.startedAt >= now - sevenDaysMs }
    val prevWeek = completed.filter { it.startedAt in (now - fourteenDaysMs) until (now - sevenDaysMs) }

    val currentWpms = thisWeek.mapNotNull { it.wordsPerMinute }.filter { it > 0 }
    val prevWpms = prevWeek.mapNotNull { it.wordsPerMinute }.filter { it > 0 }
    val currentFillers = thisWeek.mapNotNull { it.fillerWordsRatio }
    val currentMlus = thisWeek.mapNotNull { it.meanLengthOfUtterance }.filter { it > 0f }

    val avgCurWpm = if (currentWpms.isNotEmpty()) currentWpms.average().toInt() else null
    val avgPrevWpm = if (prevWpms.isNotEmpty()) prevWpms.average().toInt() else null
    val avgFiller = if (currentFillers.isNotEmpty()) currentFillers.average().toFloat() else null
    val avgMlu = if (currentMlus.isNotEmpty()) currentMlus.average().toFloat() else null

    val hasData = avgCurWpm != null || avgFiller != null || avgMlu != null
    return WeeklyFluencySummary(
        currentWeekWpm = avgCurWpm,
        previousWeekWpm = avgPrevWpm,
        currentWeekFillerRatio = avgFiller,
        currentWeekMlu = avgMlu,
        hasData = hasData
    )
}

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
        sessionRepository.observeCorrectionCounts(),
        preferencesRepository.preferences
    ) { stats, sessions, correctionCounts, prefs ->
        val currentLevel = prefs.learner.level
        val isAdaptive = prefs.learner.adaptiveLevelRecommendation

        val roadmapRecommendation = if (isAdaptive) {
            val trend = recentTrend(sessions, correctionCounts)
            LevelEvaluator.evaluateTrend(currentLevel, trend.averages, trend.corrections)
        } else null

        val weeklyFluency = calculateWeeklyFluency(sessions)

        ProgressUiState(
            stats = stats,
            currentLevel = currentLevel,
            roadmapRecommendation = roadmapRecommendation,
            isAdaptiveEnabled = isAdaptive,
            weeklyFluency = weeklyFluency,
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
                    isCompleted = session.isCompleted,
                    wordsPerMinute = session.wordsPerMinute
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
