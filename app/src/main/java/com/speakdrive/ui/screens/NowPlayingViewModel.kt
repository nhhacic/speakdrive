package com.speakdrive.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The lesson shown in the mini bar above the bottom navigation; null when none is running. */
data class NowPlayingUi(val lesson: ActiveLesson? = null, val state: ConversationState = ConversationState.IDLE)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val engine: ConversationEngine
) : ViewModel() {

    val ui: StateFlow<NowPlayingUi> = combine(engine.lesson, engine.state) { lesson, state ->
        NowPlayingUi(lesson = lesson.takeIf { state.isInLesson }, state = state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUi())

    fun togglePause() {
        viewModelScope.launch {
            when (engine.state.value) {
                ConversationState.ACTIVE -> engine.pause()
                ConversationState.PAUSED -> engine.resume()
                else -> Unit
            }
        }
    }
}
