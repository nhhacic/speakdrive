package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ui.components.MicState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val correction: String? = null,
    val timestamp: String
)

data class ConversationUiState(
    val topicTitle: String = "",
    val timeElapsed: String = "00:00",
    val messages: List<ChatMessage> = emptyList(),
    val micState: MicState = MicState.IDLE
)

@HiltViewModel
class ConversationViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    fun initTopic(topicId: String) {
        _uiState.update { it.copy(topicTitle = "Chủ đề: $topicId") }
        // Start session logic here
    }

    fun toggleListening() {
        _uiState.update { state ->
            val nextState = when (state.micState) {
                MicState.IDLE -> MicState.LISTENING
                MicState.LISTENING -> MicState.AI_SPEAKING
                MicState.AI_SPEAKING -> MicState.IDLE
            }
            state.copy(micState = nextState)
        }
    }

    fun endSession() {
        // End session logic
    }
}
