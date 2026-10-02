package com.speakdrive.ai

import android.util.Log
import com.speakdrive.ai.di.EngineDispatcher
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.network.ConnectivityObserver
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.ai.session.TranscriptAccumulator
import com.speakdrive.ai.summary.SummaryGenerator
import com.speakdrive.ai.summary.SummaryParser
import com.speakdrive.audio.AudioFocus
import com.speakdrive.audio.AudioFocusState
import com.speakdrive.audio.VoiceAnnouncer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one English lesson at a time and is shared by the phone UI and Android Auto.
 *
 * Responsibilities beyond the plain conversation:
 * - pauses for phone calls / navigation prompts (audio focus) and resumes afterwards;
 * - reconnects when the Live API connection expires (~10 min) or the network drops,
 *   carrying the conversation so far in the system instruction;
 * - nudges a learner who has been silent for a while, and pauses if they stay silent;
 * - at the end, generates a summary and stores the session.
 */
@Singleton
class ConversationEngine @Inject constructor(
    private val liveClient: LiveConversationClient,
    private val summaryGenerator: SummaryGenerator,
    private val topicManager: TopicManager,
    private val sessionStore: SessionStore,
    private val settings: LearningSettings,
    private val audioFocus: AudioFocus,
    private val connectivity: ConnectivityObserver,
    private val announcer: VoiceAnnouncer,
    private val micPermission: MicPermissionChecker,
    @EngineDispatcher dispatcher: CoroutineDispatcher
) {
    /** Replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val accumulator = TranscriptAccumulator { clock() }

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _lesson = MutableStateFlow<ActiveLesson?>(null)
    val lesson: StateFlow<ActiveLesson?> = _lesson.asStateFlow()

    private val _transcript = MutableStateFlow(emptyList<TranscriptTurn>())
    val transcript: StateFlow<List<TranscriptTurn>> = _transcript.asStateFlow()

    /** Who is talking right now, for the mic animation. Null when nobody is. */
    private val _activeSpeaker = MutableStateFlow<Speaker?>(null)
    val activeSpeaker: StateFlow<Speaker?> = _activeSpeaker.asStateFlow()

    private val _error = MutableStateFlow<EngineError?>(null)
    val error: StateFlow<EngineError?> = _error.asStateFlow()

    /** Emits the id of each lesson as soon as it ends and its transcript is saved. */
    private val _endedSessions = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val endedSessions: SharedFlow<String> = _endedSessions.asSharedFlow()

    /** Sessions whose AI summary is still being generated in the background. */
    private val _summarizing = MutableStateFlow(emptySet<String>())
    val summarizingSessionIds: StateFlow<Set<String>> = _summarizing.asStateFlow()

    private var activeSince = 0L
    private var accumulatedActiveMs = 0L
    private var lastActivityAt = 0L
    private var unansweredNudges = 0
    private var pausedByFocus = false
    private var pausedAt = 0L
    private var learnerSettings = LearnerSettings()

    private var speakerResetJob: Job? = null
    private var pauseCloseJob: Job? = null
    private var draftJob: Job? = null

    init {
        scope.launch { liveClient.events.collect(::onLiveEvent) }
        scope.launch { audioFocus.state.collect(::onAudioFocusChanged) }
        scope.launch { connectivity.isOnline.collect(::onConnectivityChanged) }
        scope.launch { silenceWatchdog() }
    }

    /** Time spent actively talking in the current lesson. */
    fun activeDurationMs(): Long =
        accumulatedActiveMs + if (_state.value == ConversationState.ACTIVE) clock() - activeSince else 0L

    fun clearError() {
        _error.value = null
    }

    /** Stops background work; only tests need this since the engine lives as long as the app. */
    internal fun shutdown() {
        scope.cancel()
    }

    /**
     * Starts a lesson. A lesson that is already running is ended (and saved) first.
     * @return true if the conversation is now running.
     */
    suspend fun start(request: LessonRequest): Boolean = mutex.withLock {
        if (_state.value.isInLesson) endLocked()
        _error.value = null

        if (!micPermission.hasMicPermission()) return@withLock failStart(EngineError.MissingMicPermission)
        if (!connectivity.isOnlineNow()) {
            announcer.announce(ANNOUNCE_OFFLINE_AT_START)
            return@withLock failStart(EngineError.NoNetwork)
        }

        learnerSettings = settings.snapshot()
        val lesson = resolveLesson(request, learnerSettings)
        accumulator.clear()
        _transcript.value = emptyList()
        accumulatedActiveMs = 0L
        unansweredNudges = 0
        pausedByFocus = false
        _lesson.value = lesson
        _state.value = ConversationState.CONNECTING

        if (!audioFocus.request()) {
            _lesson.value = null
            return@withLock failStart(EngineError.AudioFocusDenied)
        }

        try {
            connectLive(lesson, recap = false)
            liveClient.sendText(PromptTemplates.kickoffMessage(lesson))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the lesson", e)
            audioFocus.abandon()
            liveClient.disconnect()
            _lesson.value = null
            return@withLock failStart(mapError(e))
        }

        settings.setLastTopicId(lesson.topic.id)
        markActive()
        startDraftSaver()
        true
    }

    /** User-initiated pause (play/pause button, steering-wheel button). */
    suspend fun pause() = mutex.withLock { pauseLocked(byFocus = false) }

    suspend fun resume(): Boolean = mutex.withLock { resumeLocked() }

    /**
     * Ends the lesson, generates the summary and saves the session.
     * @return the saved session id, or null if nothing worth saving happened.
     */
    suspend fun end(): String? = mutex.withLock { endLocked() }

    // region Lesson lifecycle (call with mutex held)

    private suspend fun resolveLesson(request: LessonRequest, prefs: LearnerSettings): ActiveLesson {
        val scenarioMatch = topicManager.getScenario(request.scenarioId)
        val topic = scenarioMatch?.first
            ?: topicManager.getTopicById(request.topicId)
            ?: topicManager.suggestTopic(sessionStore.recentTopicIds(RECENT_TOPICS_FOR_SUGGESTION))
        val reviewWords = if (request.mode == SessionMode.VOCAB_REVIEW) {
            sessionStore.wordsDueForReview(MAX_REVIEW_WORDS)
        } else {
            emptyList()
        }
        val mode = when {
            request.mode == SessionMode.VOCAB_REVIEW && reviewWords.isEmpty() -> SessionMode.FREE_TALK
            request.mode == SessionMode.ROLEPLAY && scenarioMatch == null && topic.scenarios.isEmpty() -> SessionMode.FREE_TALK
            else -> request.mode
        }
        val scenario = scenarioMatch?.second ?: if (mode == SessionMode.ROLEPLAY) topic.scenarios.random() else null
        return ActiveLesson(
            sessionId = UUID.randomUUID().toString(),
            topic = topic,
            scenario = scenario,
            level = request.level ?: prefs.level,
            mode = mode,
            startedAt = clock(),
            reviewWords = reviewWords
        )
    }

    private suspend fun connectLive(lesson: ActiveLesson, recap: Boolean) {
        val instruction = PromptTemplates.buildSystemInstruction(
            lesson = lesson,
            settings = learnerSettings,
            recap = if (recap) accumulator.snapshot() else emptyList()
        )
        liveClient.connect(LiveSessionConfig(systemInstruction = instruction, voiceId = learnerSettings.voiceId))
    }

    private fun markActive() {
        activeSince = clock()
        lastActivityAt = clock()
        _state.value = ConversationState.ACTIVE
    }

    private fun stopActiveClock() {
        if (_state.value == ConversationState.ACTIVE) accumulatedActiveMs += clock() - activeSince
    }

    private fun failStart(error: EngineError): Boolean {
        _error.value = error
        _state.value = ConversationState.ERROR
        return false
    }

    private suspend fun pauseLocked(byFocus: Boolean) {
        if (_state.value != ConversationState.ACTIVE) return
        stopActiveClock()
        liveClient.pauseAudio()
        _activeSpeaker.value = null
        pausedByFocus = byFocus
        pausedAt = clock()
        _state.value = ConversationState.PAUSED
        // Keep the focus request after a transient loss so the system tells us when we may resume.
        if (!byFocus) audioFocus.abandon()
        // An idle Live connection still counts toward its lifetime; close it after a long pause.
        pauseCloseJob?.cancel()
        pauseCloseJob = scope.launch {
            delay(CLOSE_AFTER_PAUSE_MS)
            mutex.withLock { if (_state.value == ConversationState.PAUSED) liveClient.disconnect() }
        }
    }

    private suspend fun resumeLocked(): Boolean {
        if (_state.value != ConversationState.PAUSED) return _state.value == ConversationState.ACTIVE
        val lesson = _lesson.value ?: return false
        pauseCloseJob?.cancel()
        if (!audioFocus.request()) {
            _error.value = EngineError.AudioFocusDenied
            return false
        }
        pausedByFocus = false
        unansweredNudges = 0
        return try {
            if (liveClient.isConnected) {
                liveClient.resumeAudio()
                if (clock() - pausedAt > SAY_WELCOME_BACK_AFTER_MS) liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
            } else {
                learnerSettings = settings.snapshot()
                connectLive(lesson, recap = true)
                liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
            }
            markActive()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not resume", e)
            _error.value = mapError(e)
            false
        }
    }

    private suspend fun reconnectLocked() {
        val lesson = _lesson.value ?: return
        if (_state.value != ConversationState.ACTIVE && _state.value != ConversationState.WAITING_FOR_NETWORK) return
        stopActiveClock()
        _state.value = ConversationState.RECONNECTING
        _activeSpeaker.value = null
        repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
            if (!connectivity.isOnlineNow()) {
                goOffline()
                return
            }
            try {
                connectLive(lesson, recap = true)
                liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
                markActive()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt ${attempt + 1} failed", e)
                delay(RECONNECT_BACKOFF_MS * (attempt + 1))
            }
        }
        _error.value = EngineError.ConnectionFailed(null)
        announcer.announce(ANNOUNCE_GIVE_UP)
        endLocked()
    }

    private suspend fun goOffline() {
        stopActiveClock()
        liveClient.disconnect()
        _activeSpeaker.value = null
        _state.value = ConversationState.WAITING_FOR_NETWORK
        announcer.announce(ANNOUNCE_OFFLINE)
    }

    private suspend fun endLocked(): String? {
        val lesson = _lesson.value ?: run {
            if (_state.value == ConversationState.ERROR) _state.value = ConversationState.IDLE
            return null
        }
        stopActiveClock()
        _state.value = ConversationState.ENDING
        _activeSpeaker.value = null
        pauseCloseJob?.cancel()
        draftJob?.cancel()
        liveClient.disconnect()
        audioFocus.abandon()

        val turns = accumulator.snapshot()
        val hasLearnerSpeech = turns.any { it.speaker == Speaker.USER }
        _lesson.value = null
        if (!hasLearnerSpeech) {
            // Nothing to learn from; do not clutter the history.
            _state.value = ConversationState.ENDED
            return null
        }

        // Save the transcript right away, then summarise in the background so that switching
        // topics in the car does not leave the driver waiting in silence.
        val draft = buildRecord(lesson, turns, summary = null, completed = false)
        try {
            sessionStore.saveSession(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not save session", e)
        }
        _summarizing.value = _summarizing.value + lesson.sessionId
        _state.value = ConversationState.ENDED
        _endedSessions.tryEmit(lesson.sessionId)
        scope.launch { summarizeAndStore(lesson, draft) }
        return lesson.sessionId
    }

    private suspend fun summarizeAndStore(lesson: ActiveLesson, draft: CompletedSession) {
        try {
            val summary: SessionSummary = try {
                summaryGenerator.summarize(lesson, draft.transcript)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Summary generation failed", e)
                SummaryParser.fallback("Chưa tạo được nhận xét do lỗi kết nối. Nội dung buổi học vẫn được lưu lại.")
            }
            sessionStore.saveSession(draft.copy(summary = summary, isCompleted = true))
            if (lesson.mode == SessionMode.VOCAB_REVIEW) sessionStore.markWordsReviewed(draft.reviewedWords)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not store the summary", e)
        } finally {
            _summarizing.value = _summarizing.value - lesson.sessionId
        }
    }

    private fun buildRecord(
        lesson: ActiveLesson,
        turns: List<TranscriptTurn>,
        summary: SessionSummary?,
        completed: Boolean
    ) = CompletedSession(
        id = lesson.sessionId,
        topicId = lesson.topic.id,
        scenarioId = lesson.scenario?.id,
        level = lesson.level,
        mode = lesson.mode,
        startedAt = lesson.startedAt,
        endedAt = clock(),
        activeDurationMs = activeDurationMs(),
        transcript = turns,
        summary = summary,
        reviewedWords = lesson.reviewWords.map { it.word },
        isCompleted = completed
    )

    /** Saves the transcript every so often so a killed app does not lose the lesson. */
    private fun startDraftSaver() {
        draftJob?.cancel()
        draftJob = scope.launch {
            while (isActive) {
                delay(DRAFT_INTERVAL_MS)
                val lesson = _lesson.value ?: return@launch
                val turns = accumulator.snapshot()
                if (turns.any { it.speaker == Speaker.USER }) {
                    runCatching { sessionStore.saveSession(buildRecord(lesson, turns, null, completed = false)) }
                        .onFailure { Log.w(TAG, "Draft save failed", it) }
                }
            }
        }
    }

    // endregion

    // region Event handling

    private fun onLiveEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.UserTranscript -> onTranscript(Speaker.USER, event.text)
            is LiveEvent.AiTranscript -> onTranscript(Speaker.AI, event.text)
            LiveEvent.GoAway -> scope.launch {
                // Let the AI finish its sentence before swapping connections.
                withTimeoutOrNull(GO_AWAY_WAIT_MS) { _activeSpeaker.first { it == null } }
                mutex.withLock { if (_state.value == ConversationState.ACTIVE) reconnectLocked() }
            }
            LiveEvent.EndLessonRequested -> scope.launch {
                delay(GOODBYE_GRACE_MS) // let the spoken goodbye play out
                end()
            }
            is LiveEvent.Disconnected -> scope.launch {
                mutex.withLock {
                    if (_state.value != ConversationState.ACTIVE) return@withLock
                    if (connectivity.isOnlineNow()) reconnectLocked() else goOffline()
                }
            }
        }
    }

    private fun onTranscript(speaker: Speaker, text: String) {
        if (_lesson.value == null) return
        _transcript.value = accumulator.append(speaker, text)
        lastActivityAt = clock()
        if (speaker == Speaker.USER) unansweredNudges = 0
        _activeSpeaker.value = speaker
        speakerResetJob?.cancel()
        speakerResetJob = scope.launch {
            delay(SPEAKER_IDLE_MS)
            _activeSpeaker.value = null
        }
    }

    private suspend fun onAudioFocusChanged(focus: AudioFocusState) {
        mutex.withLock {
            when (focus) {
                AudioFocusState.LOSS_TRANSIENT, AudioFocusState.LOSS_TRANSIENT_CAN_DUCK -> pauseLocked(byFocus = true)
                AudioFocusState.LOSS -> pauseLocked(byFocus = false)
                AudioFocusState.GAIN -> if (_state.value == ConversationState.PAUSED && pausedByFocus) resumeLocked()
                AudioFocusState.NONE -> Unit
            }
        }
    }

    private suspend fun onConnectivityChanged(online: Boolean) {
        mutex.withLock {
            val state = _state.value
            if (!online && (state == ConversationState.ACTIVE || state == ConversationState.RECONNECTING)) {
                goOffline()
            } else if (online && state == ConversationState.WAITING_FOR_NETWORK) {
                reconnectLocked()
            }
        }
    }

    private suspend fun silenceWatchdog() {
        while (scope.isActive) {
            delay(SILENCE_CHECK_INTERVAL_MS)
            mutex.withLock {
                if (_state.value != ConversationState.ACTIVE || _activeSpeaker.value != null) return@withLock
                if (clock() - lastActivityAt < SILENCE_NUDGE_AFTER_MS) return@withLock
                if (unansweredNudges < MAX_UNANSWERED_NUDGES) {
                    unansweredNudges++
                    lastActivityAt = clock()
                    runCatching { liveClient.sendText(PromptTemplates.SILENCE_NUDGE) }
                } else {
                    pauseLocked(byFocus = false)
                    announcer.announce(ANNOUNCE_AUTO_PAUSE)
                }
            }
        }
    }

    // endregion

    private fun mapError(e: Exception): EngineError {
        val name = e::class.simpleName.orEmpty()
        val message = e.message.orEmpty()
        return when {
            name == "PermissionMissingException" || e is SecurityException -> EngineError.MissingMicPermission
            name in setOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException") ||
                "FirebaseApp" in message || "API key" in message -> EngineError.AiNotConfigured
            !connectivity.isOnlineNow() -> EngineError.NoNetwork
            else -> EngineError.ConnectionFailed(message.ifBlank { name })
        }
    }

    private companion object {
        const val TAG = "ConversationEngine"

        const val RECENT_TOPICS_FOR_SUGGESTION = 3
        const val MAX_REVIEW_WORDS = 8
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BACKOFF_MS = 1_500L
        const val GO_AWAY_WAIT_MS = 40_000L
        const val GOODBYE_GRACE_MS = 4_000L
        const val SPEAKER_IDLE_MS = 1_200L
        const val SILENCE_CHECK_INTERVAL_MS = 5_000L
        const val SILENCE_NUDGE_AFTER_MS = 25_000L
        const val MAX_UNANSWERED_NUDGES = 3
        const val CLOSE_AFTER_PAUSE_MS = 2 * 60_000L
        const val SAY_WELCOME_BACK_AFTER_MS = 5_000L
        const val DRAFT_INTERVAL_MS = 30_000L

        const val ANNOUNCE_OFFLINE = "Connection lost. Your lesson will continue when you are back online."
        const val ANNOUNCE_OFFLINE_AT_START = "There is no internet connection right now. Please try again later."
        const val ANNOUNCE_GIVE_UP = "Sorry, I could not reconnect. Your lesson has been saved."
        const val ANNOUNCE_AUTO_PAUSE = "I will pause the lesson for now. Press play when you are ready."
    }
}
