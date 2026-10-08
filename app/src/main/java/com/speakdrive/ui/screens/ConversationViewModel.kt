package com.speakdrive.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.playback.PlaybackConnection
import com.speakdrive.ui.components.MicState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ConversationUiState(
    val lesson: ActiveLesson? = null,
    val state: ConversationState = ConversationState.IDLE,
    val transcript: List<TranscriptTurn> = emptyList(),
    val micState: MicState = MicState.BUSY,
    val activeSpeaker: Speaker? = null,
    val isAiThinking: Boolean = false,
    val elapsed: String = "00:00",
    val error: EngineError? = null,
    val drill: DrillUiState? = null,
    val isCarConnected: Boolean = false,
    val isBargeInEnabled: Boolean = false,
    val aiVolume: Int = 80,
    val screenAwakeMode: ScreenAwakeMode = ScreenAwakeMode.ALWAYS_ON
)

/** Repeat-after-me progress shown above the transcript. */
data class DrillUiState(
    val target: String?,
    val lastAttempt: PronunciationAttempt?,
    val passedSentences: Int,
    val sentences: Int,
    val translation: String? = null
)

sealed interface ConversationEvent {
    data class Saved(val sessionId: String) : ConversationEvent

    /** The lesson ended before the learner said anything, so nothing was stored. */
    data object Discarded : ConversationEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val engine: ConversationEngine,
    private val playback: PlaybackConnection,
    private val preferencesRepository: UserPreferencesRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _events = Channel<ConversationEvent>(Channel.BUFFERED)
    val events: Flow<ConversationEvent> = _events.receiveAsFlow()

    /** The lesson this screen is showing; lessons that ended before it are ignored. */
    private var trackedLessonId: String? = null

    /** Survives process death: when Android restores this screen later, it must not start the
     * lesson again by itself.
     */
    private var startRequested: Boolean
        get() = savedState[KEY_START_REQUESTED] ?: false
        set(value) {
            savedState[KEY_START_REQUESTED] = value
        }

    private val _startError = MutableStateFlow<EngineError?>(null)

    /** Refreshes the elapsed time once a second, but only while the lesson is running. */
    private val ticker: Flow<Unit> = engine.state
        .map { it == ConversationState.ACTIVE }
        .distinctUntilChanged()
        .flatMapLatest { active ->
            if (active) {
                flow {
                    while (true) {
                        emit(Unit)
                        delay(1_000)
                    }
                }
            } else {
                flowOf(Unit)
            }
        }

    val uiState: StateFlow<ConversationUiState> = combine(
        combine(engine.lesson, engine.state, engine.transcript, ::Triple),
        combine(engine.activeSpeaker, combine(engine.error, _startError) { e1, e2 -> e1 ?: e2 }, engine.isAiThinking, ::Triple),
        combine(
            combine(engine.drillTarget, engine.drillTargetTranslation, engine.offlinePractice, ::Triple),
            engine.pronunciationAttempts,
            ::Pair
        ),
        combine(engine.isCarConnected, preferencesRepository.observeLearnerSettings().map { Triple(it.allowBargeIn, it.aiVolume, it.screenAwakeMode) }, ::Pair),
        ticker
    ) { (lesson, state, transcript), (speaker, error, isThinking), (drillData, attempts), (isCarConnected, settingsPair), _ ->
        val (target, translation, offlinePractice) = drillData
        val bySentence = attempts.groupBy { PronunciationDrill.key(it.target) }
        val currentTarget = target ?: attempts.lastOrNull()?.target
        ConversationUiState(
            // Offline practice shows the sentence card in any mode.
            drill = if (lesson?.mode == SessionMode.REPEAT_AFTER_ME || offlinePractice) {
                DrillUiState(
                    target = currentTarget,
                    translation = translation,
                    // Only show the grade while it still refers to the sentence on screen.
                    lastAttempt = attempts.lastOrNull()?.takeIf { currentTarget == null || PronunciationDrill.key(it.target) == PronunciationDrill.key(currentTarget) },
                    passedSentences = bySentence.values.count { tries -> tries.any { it.passed } },
                    sentences = bySentence.size
                )
            } else {
                null
            },
            lesson = lesson,
            state = state,
            transcript = transcript,
            micState = micStateFor(state, speaker, isThinking),
            activeSpeaker = speaker,
            isAiThinking = isThinking,
            elapsed = formatElapsed(engine.activeDurationMs()),
            error = error,
            isCarConnected = isCarConnected,
            isBargeInEnabled = settingsPair.first,
            aiVolume = settingsPair.second,
            screenAwakeMode = settingsPair.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    fun setAiVolume(volume: Int) {
        viewModelScope.launch {
            preferencesRepository.setAiVolume(volume)
        }
    }

    init {
        viewModelScope.launch {
            engine.lesson.collect { lesson -> if (lesson != null) trackedLessonId = lesson.sessionId }
        }
        viewModelScope.launch {
            engine.endedSessions.collect { id -> if (id == trackedLessonId) _events.send(ConversationEvent.Saved(id)) }
        }
    }

    /** Starts [mediaId] once per screen; a null id just shows the lesson already running. */
    fun start(mediaId: String?) {
        if (mediaId == null || startRequested) return
        startRequested = true
        trackedLessonId = null
        _startError.value = null
        engine.clearError()
        viewModelScope.launch {
            try {
                playback.play(mediaId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ConversationViewModel", "Failed to start lesson via playback: $mediaId", e)
                // Let the learner try again.
                startRequested = false
                _startError.value = EngineError.ConnectionFailed(e.localizedMessage ?: e.message)
            }
        }
    }

    fun retry(mediaId: String?) {
        startRequested = false
        _startError.value = null
        start(mediaId)
    }

    fun togglePause() {
        viewModelScope.launch {
            when (engine.state.value) {
                ConversationState.ACTIVE -> engine.pause()
                ConversationState.PAUSED -> engine.resume()
                else -> Unit
            }
        }
    }

    fun end() {
        viewModelScope.launch {
            runCatching { playback.stop() }
            if (engine.end() == null) _events.send(ConversationEvent.Discarded)
        }
    }

    fun next() {
        viewModelScope.launch { engine.next() }
    }

    fun repeat() {
        viewModelScope.launch { engine.repeat() }
    }

    fun nextStory() {
        viewModelScope.launch { engine.nextStory() }
    }

    fun replayStory() {
        viewModelScope.launch { engine.replayStory() }
    }

    fun toggleBargeIn() {
        viewModelScope.launch {
            val current = uiState.value.isBargeInEnabled
            preferencesRepository.setAllowBargeIn(!current)
        }
    }

    fun setBargeIn(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAllowBargeIn(enabled)
        }
    }

    private fun micStateFor(
        state: ConversationState,
        speaker: Speaker?,
        isThinking: Boolean
    ): MicState = when (state) {
        ConversationState.ACTIVE -> when {
            speaker == Speaker.AI -> MicState.AI_SPEAKING
            isThinking -> MicState.AI_THINKING
            speaker == Speaker.USER -> MicState.USER_SPEAKING
            else -> MicState.LISTENING
        }
        ConversationState.PAUSED -> MicState.PAUSED
        else -> MicState.BUSY
    }

    private companion object {
        const val KEY_START_REQUESTED = "start_requested"
    }

    private fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
