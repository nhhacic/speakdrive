package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.data.repository.ProgressRepository
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HistoryItem(
    val sessionId: String,
    val title: String,
    val subtitle: String,
    val averageScore: Int?,
    val isCompleted: Boolean
)

data class ProgressUiState(
    val stats: ProgressStats = ProgressStats(),
    val history: List<HistoryItem> = emptyList(),
    val topicRows: List<Pair<String, Int>> = emptyList()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    sessionRepository: SessionRepository,
    private val topicManager: TopicManager
) : ViewModel() {

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("vi"))

    val uiState: StateFlow<ProgressUiState> = combine(
        progressRepository.observeStats(),
        sessionRepository.observeHistory()
    ) { stats, sessions ->
        ProgressUiState(
            stats = stats,
            history = sessions.map { session ->
                val topic = topicManager.getTopicById(session.topicId)
                val scenario = topicManager.getScenario(session.scenarioId)?.second
                val title = when {
                    session.mode == SessionMode.VOCAB_REVIEW.name -> "📝 Ôn tập từ vựng"
                    session.mode == SessionMode.REPEAT_AFTER_ME.name -> "🗣️ Phát âm: ${topic?.titleVi ?: session.topicId}"
                    scenario != null -> "🎭 ${scenario.titleVi}"
                    else -> "${topic?.emoji.orEmpty()} ${topic?.titleVi ?: session.topicId}"
                }
                val scores = session.pronunciationScore?.let { listOf((it + 5) / 10) }
                    ?: listOfNotNull(session.fluencyScore, session.grammarScore, session.vocabularyScore)
                val minutes = (session.activeDurationMs / 60_000).coerceAtLeast(if (session.activeDurationMs > 0) 1 else 0)
                HistoryItem(
                    sessionId = session.id,
                    title = title,
                    subtitle = "${dateFormat.format(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()))} • " +
                        "$minutes phút • ${DifficultyLevel.fromStored(session.level).displayName}",
                    averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                    isCompleted = session.isCompleted
                )
            },
            topicRows = topicManager.getAllTopics().map { "${it.emoji} ${it.titleVi}" to (stats.topicCounts[it.id] ?: 0) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
