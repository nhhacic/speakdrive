package com.speakdrive.ai

import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Quản lý logic phiên hội thoại.
 */
@Singleton
class ConversationEngine @Inject constructor(
    private val geminiLiveManager: GeminiLiveManager,
    private val topicManager: TopicManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private var currentSessionId: String? = null
    private var startTime: Long = 0

    init {
        scope.launch {
            geminiLiveManager.isConnected.collect { connected ->
                if (connected && _state.value == ConversationState.CONNECTING) {
                    _state.value = ConversationState.ACTIVE
                } else if (!connected && _state.value == ConversationState.ACTIVE) {
                    _state.value = ConversationState.ENDED
                }
            }
        }
    }

    fun startSession(topicId: String?, level: DifficultyLevel) {
        if (_state.value != ConversationState.IDLE && _state.value != ConversationState.ENDED) {
            return
        }
        _state.value = ConversationState.CONNECTING
        currentSessionId = UUID.randomUUID().toString()
        startTime = System.currentTimeMillis()

        val topic = topicId?.let { topicManager.getTopicById(it) }
        val systemInstruction = PromptTemplates.buildSystemInstruction(level, topic)

        geminiLiveManager.updateSystemInstruction(systemInstruction)
        scope.launch { geminiLiveManager.connect() }
    }

    fun pauseSession() {
        if (_state.value == ConversationState.ACTIVE) {
            _state.value = ConversationState.PAUSED
            scope.launch { geminiLiveManager.disconnect() }
        }
    }

    fun resumeSession() {
        if (_state.value == ConversationState.PAUSED) {
            _state.value = ConversationState.CONNECTING
            scope.launch { geminiLiveManager.connect() }
        }
    }

    fun endSession(): SessionSummary? {
        if (_state.value == ConversationState.IDLE) return null
        
        scope.launch { geminiLiveManager.disconnect() }
        _state.value = ConversationState.ENDED
        
        val duration = System.currentTimeMillis() - startTime
        val sid = currentSessionId ?: return null
        
        currentSessionId = null
        
        return SessionSummary(
            sessionId = sid,
            durationMs = duration,
            vocabularySuggestions = listOf("Accelerate", "Navigate", "Intersection"),
            grammarCorrections = listOf("Remember to use past tense when describing your previous trip."),
            overallFeedback = "Good job! Try to speak a bit slower and articulate the words clearly."
        )
    }
}
