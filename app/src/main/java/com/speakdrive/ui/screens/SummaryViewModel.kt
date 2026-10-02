package com.speakdrive.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.repository.SessionDetail
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.ui.navigation.SummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SummaryUiState(
    val isLoading: Boolean = true,
    val isSummarizing: Boolean = false,
    val detail: SessionDetail? = null,
    val title: String = "",
    val levelLabel: String = "",
    val durationLabel: String = "",
    /** What "practise again" should start. */
    val againMediaId: String = MediaIds.RESUME
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
    engine: ConversationEngine,
    private val topicManager: TopicManager
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SummaryRoute>().sessionId

    val uiState: StateFlow<SummaryUiState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        engine.summarizingSessionIds
    ) { detail, summarizing ->
        val session = detail?.session
        val topic = topicManager.getTopicById(session?.topicId)
        val scenario = topicManager.getScenario(session?.scenarioId)?.second
        val mode = session?.mode?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() }
        SummaryUiState(
            isLoading = false,
            isSummarizing = sessionId in summarizing,
            detail = detail,
            title = when {
                mode == SessionMode.VOCAB_REVIEW -> "Ôn tập từ vựng"
                mode == SessionMode.REPEAT_AFTER_ME -> "Luyện phát âm: ${topic?.titleVi.orEmpty()}"
                scenario != null -> "Nhập vai: ${scenario.titleVi}"
                topic != null -> "${topic.emoji} ${topic.titleVi}"
                else -> "Buổi học"
            },
            levelLabel = session?.let { DifficultyLevel.fromStored(it.level).displayName }.orEmpty(),
            durationLabel = session?.let { formatDuration(it.activeDurationMs) }.orEmpty(),
            againMediaId = when {
                mode == SessionMode.REPEAT_AFTER_ME -> MediaIds.pronunciation(topic?.id)
                scenario != null -> MediaIds.scenario(scenario.id)
                topic != null -> MediaIds.topic(topic.id)
                else -> MediaIds.RESUME
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms / 1000) % 60
        return if (minutes > 0) "$minutes phút $seconds giây" else "$seconds giây"
    }
}
