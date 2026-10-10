package com.speakdrive.ai

import com.speakdrive.ai.di.EngineDispatcher
import com.speakdrive.ai.diagnostics.DiagnosticsLogSaver
import com.speakdrive.ai.diagnostics.LiveLog
import com.speakdrive.ai.live.LiveModels
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.drill.DrillSentenceManager
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.Scenario
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.story.StoryRecommender
import com.speakdrive.ai.live.LiveToolCall
import com.speakdrive.ai.network.ConnectivityObserver
import com.speakdrive.ai.pronunciation.AzureAssessment
import com.speakdrive.ai.pronunciation.AzureSpeechConfig
import com.speakdrive.ai.pronunciation.PronunciationAssessor
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.speakdrive.ai.pronunciation.PronunciationGrader
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.ai.session.CommandCategory
import com.speakdrive.ai.session.CommandContext
import com.speakdrive.ai.session.TranscriptAccumulator
import com.speakdrive.ai.session.VoiceCommand
import com.speakdrive.ai.session.VoiceCommandRecognizer
import com.speakdrive.ai.session.VoiceSettingsTools
import com.speakdrive.ai.summary.SummaryGenerator
import com.speakdrive.ai.summary.SummaryParser
import com.speakdrive.ai.util.suspendRunCatching
import com.speakdrive.ai.live.LiveToolHandler
import com.speakdrive.ai.model.LearnerMemory
import com.speakdrive.ai.offline.OfflineDrillCoach
import com.speakdrive.ai.offline.OfflineDrillListener
import com.speakdrive.audio.NoOfflineSpeech
import com.speakdrive.audio.OfflineSpeech
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.audio.AudioFocus
import com.speakdrive.audio.AudioFocusState
import com.speakdrive.audio.VoiceAnnouncer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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
import kotlinx.coroutines.withContext
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
open class ConversationEngine @Inject constructor(
    private val liveClient: LiveConversationClient,
    private val summaryGenerator: SummaryGenerator,
    private val topicManager: TopicManager,
    private val sessionStore: SessionStore,
    private val settings: LearningSettings,
    private val audioFocus: AudioFocus,
    private val connectivity: ConnectivityObserver,
    private val announcer: VoiceAnnouncer,
    private val micPermission: MicPermissionChecker,
    private val pronunciationAssessor: PronunciationAssessor,
    private val storyRecommender: StoryRecommender,
    private val drillSentenceManager: DrillSentenceManager,
    private val sentenceTranslator: com.speakdrive.ai.translation.SentenceTranslator,
    private val offlineSpeech: OfflineSpeech,
    private val diagnosticsSaver: DiagnosticsLogSaver,
    @EngineDispatcher dispatcher: CoroutineDispatcher
) {
    /** Secondary constructor for tests without optional managers. */
    constructor(
        liveClient: LiveConversationClient,
        summaryGenerator: SummaryGenerator,
        topicManager: TopicManager,
        sessionStore: SessionStore,
        settings: LearningSettings,
        audioFocus: AudioFocus,
        connectivity: ConnectivityObserver,
        announcer: VoiceAnnouncer,
        micPermission: MicPermissionChecker,
        pronunciationAssessor: PronunciationAssessor,
        dispatcher: CoroutineDispatcher,
        offlineSpeech: OfflineSpeech = NoOfflineSpeech,
        diagnosticsSaver: DiagnosticsLogSaver = DiagnosticsLogSaver { null }
    ) : this(
        liveClient = liveClient,
        summaryGenerator = summaryGenerator,
        topicManager = topicManager,
        sessionStore = sessionStore,
        settings = settings,
        audioFocus = audioFocus,
        connectivity = connectivity,
        announcer = announcer,
        micPermission = micPermission,
        pronunciationAssessor = pronunciationAssessor,
        storyRecommender = StoryRecommender(topicManager),
        drillSentenceManager = DrillSentenceManager(),
        sentenceTranslator = com.speakdrive.ai.translation.SentenceTranslator(DrillSentenceManager()),
        offlineSpeech = offlineSpeech,
        diagnosticsSaver = diagnosticsSaver,
        dispatcher = dispatcher
    )

    /** Replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    private val engineDispatcher = dispatcher

    /**
     * Last resort for exceptions nobody caught in the engine's background work: log them instead of
     * crashing the app in the middle of a drive. Tests replace it to fail on unexpected errors.
     */
    internal var onUncaughtError: (Throwable) -> Unit = { LiveLog.e(TAG, "Unexpected error in the lesson engine", it) }

    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> onUncaughtError(e) }
    )
    private val mutex = Mutex()
    private val accumulator = TranscriptAccumulator { clock() }

    private val _state = MutableStateFlow(ConversationState.IDLE)
    open val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _lesson = MutableStateFlow<ActiveLesson?>(null)
    open val lesson: StateFlow<ActiveLesson?> = _lesson.asStateFlow()

    private val _transcript = MutableStateFlow(emptyList<TranscriptTurn>())

    /** True while the phone runs the offline "repeat after me" practice because the network is gone. */
    private val _offlinePractice = MutableStateFlow(false)
    open val offlinePractice: StateFlow<Boolean> = _offlinePractice.asStateFlow()
    private var offlineJob: Job? = null
    private var offlineStartedAt = 0L
    private var offlineEndedAt: Long? = null
    open val transcript: StateFlow<List<TranscriptTurn>> = _transcript.asStateFlow()

    /** Who is talking right now, for the mic animation. Null when nobody is. */
    private val _activeSpeaker = MutableStateFlow<Speaker?>(null)
    val activeSpeaker: StateFlow<Speaker?> = _activeSpeaker.asStateFlow()

    /** Whether the AI is currently processing/thinking before responding. */
    private val _isAiThinking = MutableStateFlow(false)
    open val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()
    private var replyWatchJob: Job? = null

    /** Fresh connections opened because the AI stopped answering, since it last spoke. */
    private var stallReconnects = 0

    /** When the model last called a tool; a tool call is an answer to the learner too. */
    private var lastModelToolCallAt = Long.MIN_VALUE

    private fun setAiThinking(thinking: Boolean) {
        replyWatchJob?.cancel()
        replyWatchJob = null
        _isAiThinking.value = thinking
    }

    /**
     * The AI should speak next (after the learner's turn, a kick-off or a tool result): shows
     * "thinking" and makes sure an answer comes. The Live server sometimes drops a turn: it hears
     * the learner (or takes our request) and then sends nothing, which left the learner talking to a
     * silent AI. If no answer starts in time, [retry] is sent as text; if that is ignored too, the
     * connection is replaced (the recap keeps the conversation going).
     */
    private fun awaitAiReply(retry: () -> String) {
        setAiThinking(true)
        val sessionId = _lesson.value?.sessionId ?: return
        replyWatchJob = scope.launch { watchForReply(sessionId, retry) }
    }

    private suspend fun watchForReply(sessionId: String, retry: () -> String) {
        delay(REPLY_RETRY_AFTER_MS)
        mutex.withLock {
            if (!replyMissing(sessionId)) return
            LiveLog.w(TAG, "The AI has not answered for $REPLY_RETRY_AFTER_MS ms; asking again")
            suspendRunCatching { liveClient.sendText(retry()) }
                .onFailure { LiveLog.w(TAG, "Could not ask the AI again", it) }
        }
        delay(REPLY_RECONNECT_AFTER_MS)
        mutex.withLock {
            if (!replyMissing(sessionId)) return
            replyWatchJob = null
            if (stallReconnects >= MAX_STALL_RECONNECTS) {
                LiveLog.w(TAG, "The AI still does not answer after $stallReconnects fresh connections; giving up")
                _isAiThinking.value = false
                return
            }
            stallReconnects++
            LiveLog.w(TAG, "The AI ignored the request again; opening a fresh connection ($stallReconnects)")
            requestReconnectLocked(ReconnectReason.STALLED)
        }
    }

    private fun replyMissing(sessionId: String): Boolean =
        _lesson.value?.sessionId == sessionId && _state.value == ConversationState.ACTIVE &&
            _activeSpeaker.value == null && liveClient.isConnected

    /** What the learner said since the AI last spoke, if the AI has not answered it yet. */
    private fun unansweredLearnerText(): String? =
        _transcript.value.lastOrNull()?.takeIf { it.speaker == Speaker.USER }?.text

    private val _error = MutableStateFlow<EngineError?>(null)
    open val error: StateFlow<EngineError?> = _error.asStateFlow()

    /** Emits the id of each lesson as soon as it ends and its transcript is saved. */
    private val _endedSessions = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val endedSessions: SharedFlow<String> = _endedSessions.asSharedFlow()

    /** Sessions whose AI summary is still being generated in the background. */
    private val _summarizing = MutableStateFlow(emptySet<String>())
    val summarizingSessionIds: StateFlow<Set<String>> = _summarizing.asStateFlow()

    /** Graded attempts of the current repeat-after-me lesson, oldest first. */
    private val _pronunciationAttempts = MutableStateFlow(emptyList<PronunciationAttempt>())
    val pronunciationAttempts: StateFlow<List<PronunciationAttempt>> = _pronunciationAttempts.asStateFlow()

    /** The sentence the AI last asked the learner to repeat, for display on the phone. */
    private val _drillTarget = MutableStateFlow<String?>(null)
    open val drillTarget: StateFlow<String?> = _drillTarget.asStateFlow()

    /** Translated meaning of the repeat drill sentence for display as subtitle. */
    private val _drillTargetTranslation = MutableStateFlow<String?>(null)
    open val drillTargetTranslation: StateFlow<String?> = _drillTargetTranslation.asStateFlow()
    private var translationJob: Job? = null

    internal fun setDrillTarget(target: String?) {
        _drillTarget.value = target
        translationJob?.cancel()
        if (target.isNullOrBlank() || !learnerSettings.showTranslationSubtitle) {
            _drillTargetTranslation.value = null
            return
        }

        val targetLang = learnerSettings.appLanguage
        val instant = sentenceTranslator.getInstantTranslation(target, targetLang)
            ?: sentenceTranslator.generateInstantFallbackTranslation(target, targetLang)

        if (instant != null) {
            _drillTargetTranslation.value = instant
        } else {
            // Clear stale subtitle from previous sentence while async translation is in flight
            _drillTargetTranslation.value = null
        }

        translationJob = scope.launch {
            try {
                // Short debounce to wait for streaming sentence boundary
                delay(80)
                if (_drillTarget.value != target || !isActive) return@launch
                val translated = sentenceTranslator.translate(target, targetLang)
                if (_drillTarget.value == target && isActive && !translated.isNullOrBlank()) {
                    _drillTargetTranslation.value = translated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveLog.w(TAG, "Failed to translate drill target '$target': ${e.message}")
            }
        }
    }

    private val attemptCounts = mutableMapOf<String, Int>()

    /** Whether the app is currently projecting / connected to Android Auto. */
    private val _isCarConnected = MutableStateFlow(false)
    open val isCarConnected: StateFlow<Boolean> = _isCarConnected.asStateFlow()

    /** Whether the app is currently in foreground/focus on the phone. */
    private val _isAppFocused = MutableStateFlow(true)
    open val isAppFocused: StateFlow<Boolean> = _isAppFocused.asStateFlow()

    /** Whether the phone screen is currently turned on. */
    private val _isScreenOn = MutableStateFlow(true)
    open val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    open fun setAppFocused(focused: Boolean) {
        if (_isAppFocused.value != focused) {
            LiveLog.i(TAG, "App focus changed: $focused")
            _isAppFocused.value = focused
            if (!focused) {
                checkAutoPause()
            } else {
                checkAutoResume()
            }
        }
    }

    open fun setScreenOn(screenOn: Boolean) {
        if (_isScreenOn.value != screenOn) {
            LiveLog.i(TAG, "Screen on state changed: $screenOn")
            _isScreenOn.value = screenOn
            if (!screenOn) {
                checkAutoPause()
            } else {
                checkAutoResume()
            }
        }
    }

    internal fun checkAutoPause() {
        if (!learnerSettings.autoPauseWhenUnfocused) return
        if (_isCarConnected.value) return
        if (_state.value != ConversationState.ACTIVE) return
        if (isWithinStartupGrace()) {
            LiveLog.d(TAG, "Ignoring auto-pause during startup grace period (${clock() - lessonStartedAt}ms)")
            return
        }

        if (!_isAppFocused.value || !_isScreenOn.value) {
            LiveLog.i(TAG, "Auto-pausing lesson: appFocused=${_isAppFocused.value}, screenOn=${_isScreenOn.value}, carConnected=${_isCarConnected.value}")
            pauseByUnfocusedAsync()
        }
    }

    internal fun pauseByUnfocusedAsync() {
        scope.launch {
            mutex.withLock {
                if (_state.value == ConversationState.ACTIVE) pauseLocked(PauseReason.APP_UNFOCUSED)
            }
        }
    }

    internal fun checkAutoResume() {
        if (!_isAppFocused.value || !_isScreenOn.value) return
        scope.launch {
            mutex.withLock {
                if (PauseReason.APP_UNFOCUSED !in pauseReasons) return@withLock
                pauseReasons -= PauseReason.APP_UNFOCUSED
                // Still paused for another reason (a phone call, the learner's own pause...)? Then stay paused.
                if (_state.value == ConversationState.PAUSED && pauseReasons.isEmpty()) {
                    LiveLog.i(TAG, "App regained focus and screen is on; auto-resuming lesson")
                    resumeLocked()
                }
            }
        }
    }

    open fun setCarConnected(connected: Boolean) {
        if (_isCarConnected.value != connected) {
            LiveLog.i(TAG, "Car connection status changed: $connected")
            _isCarConnected.value = connected
            liveClient.setCarConnected(connected)
            if (!connected) {
                checkAutoPause()
            }
            if (_state.value == ConversationState.ACTIVE && _lesson.value?.mode == SessionMode.REPEAT_AFTER_ME) {
                scope.launch {
                    suspendRunCatching {
                        liveClient.sendText(
                            PromptTemplates.carConnectionSwitchMessage(
                                isCarConnected = connected,
                                length = learnerSettings.drillSentenceLength
                            )
                        )
                    }.onFailure { LiveLog.w(TAG, "Could not send car connection switch update to Live client", it) }
                }
            }
        }
    }

    /**
     * In repeat-after-me mode, controls whether the engine should listen for and extract
     * a NEW drill sentence from AI speech.
     * While false (e.g. during pronunciation feedback / retries), the current drill target
     * is kept strictly UNCHANGED on screen so feedback doesn't corrupt it.
     */
    @Volatile
    private var awaitingNewDrillTarget = true

    private var activeSince = 0L
    private var accumulatedActiveMs = 0L
    private var lastActivityAt = 0L
    private var unansweredNudges = 0
    private var lessonStartedAt = 0L
    private var pausedAt = 0L

    /** Why the lesson is paused. It resumes on its own only when every automatic reason is gone. */
    private enum class PauseReason {
        /** The learner pressed pause (or asked by voice). Only the learner resumes. */
        USER,
        /** A phone call or navigation prompt took audio focus for a moment. Resumes on focus GAIN. */
        FOCUS_TRANSIENT,
        /** Another app took audio focus for good. Only the learner resumes. */
        FOCUS_LOSS,
        /** The app went to the background or the screen turned off (auto-pause setting). */
        APP_UNFOCUSED,
        /** The learner stayed silent after several nudges. */
        SILENCE
    }

    private val pauseReasons = mutableSetOf<PauseReason>()

    /** Set when a fresh connection was parked paused: the AI must be asked to speak on resume. */
    private var resumeNeedsKickoff = false

    /** Incremented on every successful connection, so late events of an old connection are ignored. */
    private var connectionGeneration = 0

    /** Why the connection is being replaced; decides what the AI is asked to say on the new one. */
    private enum class ReconnectReason { GO_AWAY, DROPPED, STALLED, NETWORK_BACK }

    private var reconnectReason = ReconnectReason.DROPPED

    /** Voice command categories the model has a tool for on this connection; the rest go by the parser at once. */
    private var toolCategories = emptySet<CommandCategory>()
    private var reconnectJob: Job? = null
    private var goAwayJob: Job? = null
    private var endRequestJob: Job? = null
    private var watchdogJob: Job? = null

    /** A level change suggested by the last lesson summary, waiting for the learner's answer. */
    private var pendingLevelRecommendation: LevelRecommendation? = null

    // Client-side voice command recognition: what the learner is saying right now, as one utterance.
    private val utteranceBuffer = StringBuilder()
    private var utteranceStartedAt = 0L
    private var lastLearnerUtterance = ""
    private var lastAiTranscriptAt = Long.MIN_VALUE
    private var pendingFallbackJob: Job? = null
    private val lastToolCallAt = mutableMapOf<CommandCategory, Long>()
    private val lastFallbackAppliedAt = mutableMapOf<CommandCategory, Long>()

    open fun isWithinStartupGrace(): Boolean = clock() - lessonStartedAt < STARTUP_GRACE_PERIOD_MS
    private var learnerSettings = LearnerSettings()
    private var cachedStorySessions = listOf<CompletedSession>()

    /** True once the AI said [PromptTemplates.STORY_END_MARKER]; stops podcast auto-continue. */
    private var storyFinished = false
    private var storyAutoContinues = 0

    private var speakerResetJob: Job? = null
    private var pauseCloseJob: Job? = null
    private var draftJob: Job? = null

    init {
        scope.launch { _state.collect { LiveLog.i(TAG, "Lesson state: $it") } }
        scope.launch { liveClient.events.collect(::onLiveEvent) }
        scope.launch { audioFocus.state.collect(::onAudioFocusChanged) }
        scope.launch { connectivity.isOnline.collect(::onConnectivityChanged) }
        scope.launch {
            settings.observeLearnerSettings().collect { updated ->
                val previousStyle = learnerSettings.storytellingStyle
                val previousDrillLength = learnerSettings.drillSentenceLength
                val previousLevel = learnerSettings.level
                val previousVietnameseHelp = learnerSettings.allowVietnameseHelp
                learnerSettings = updated
                val drill = _lesson.value?.mode == SessionMode.REPEAT_AFTER_ME
                liveClient.updateInterruptions(updated.allowBargeIn && !drill)
                liveClient.setVolume(updated.aiVolume / 100f)
                // Voice/tool changes update learnerSettings first, so this only fires for changes made
                // elsewhere (e.g. the Settings screen) while a session is active.
                if (updated.storytellingStyle != previousStyle && isStoryLessonActive()) {
                    LiveLog.i(TAG, "Storytelling style changed outside the session: ${updated.storytellingStyle}")
                    onStorytellingStyleChanged()
                    suspendRunCatching {
                        liveClient.sendText(PromptTemplates.storytellingStyleSwitchMessage(updated.storytellingStyle))
                    }.onFailure { LiveLog.w(TAG, "Could not send storytelling style update to Live client", it) }
                }
                if (updated.drillSentenceLength != previousDrillLength && _state.value == ConversationState.ACTIVE && drill) {
                    LiveLog.i(TAG, "Drill sentence length changed outside session: ${updated.drillSentenceLength}")
                    suspendRunCatching {
                        liveClient.sendText(PromptTemplates.drillSentenceLengthSwitchMessage(updated.drillSentenceLength, _isCarConnected.value))
                    }.onFailure { LiveLog.w(TAG, "Could not send drill sentence length update to Live client", it) }
                }
                if (updated.level != previousLevel && _state.value == ConversationState.ACTIVE) {
                    val currentLesson = _lesson.value
                    if (currentLesson != null && currentLesson.level != updated.level) {
                        LiveLog.i(TAG, "Difficulty level changed outside session: ${updated.level.displayName} (${updated.level.cefr})")
                        _lesson.value = currentLesson.copy(level = updated.level)
                        suspendRunCatching {
                            liveClient.sendText(PromptTemplates.difficultyLevelSwitchMessage(updated.level, currentLesson.mode))
                        }.onFailure { LiveLog.w(TAG, "Could not send difficulty level update to Live client", it) }
                    }
                }
                if (updated.allowVietnameseHelp != previousVietnameseHelp && _state.value == ConversationState.ACTIVE) {
                    LiveLog.i(TAG, "Vietnamese help changed outside session: ${updated.allowVietnameseHelp}")
                    suspendRunCatching {
                        liveClient.sendText(PromptTemplates.vietnameseHelpSwitchMessage(updated.allowVietnameseHelp))
                    }.onFailure { LiveLog.w(TAG, "Could not send Vietnamese help update to Live client", it) }
                }
            }
        }
    }

    private fun isStoryLessonActive(): Boolean =
        _lesson.value?.mode == SessionMode.STORY_LISTENING && _state.value == ConversationState.ACTIVE

    private fun isContinuousStoryActive(): Boolean =
        isStoryLessonActive() && learnerSettings.storytellingStyle == StorytellingStyle.CONTINUOUS && !storyFinished

    /** Resets the podcast auto-continue bookkeeping (new story, replay, style switch...). */
    private fun onStorytellingStyleChanged() {
        storyAutoContinues = 0
        lastActivityAt = clock()
    }

    private fun resetStoryProgress() {
        storyFinished = false
        storyAutoContinues = 0
    }

    /** Time spent actively talking in the current lesson. */
    fun activeDurationMs(): Long =
        accumulatedActiveMs + if (_state.value == ConversationState.ACTIVE) clock() - activeSince else 0L

    fun clearError() {
        _error.value = null
    }

    /** Stops background work; only tests need this since the engine lives as long as the app. */
    internal fun shutdown() {
        setAiThinking(false)
        scope.cancel()
    }

    /**
     * Starts a lesson. A lesson that is already running is ended (and saved) first.
     * @return true if the conversation is now running.
     */
    open suspend fun start(request: LessonRequest): Boolean {
        cancelReconnect()
        return mutex.withLock { startLocked(request) }
    }

    private suspend fun startLocked(request: LessonRequest): Boolean {
        if (_state.value.isInLesson) endLocked()
        endRequestJob?.cancel()
        _error.value = null

        if (!micPermission.hasMicPermission()) return failStart(EngineError.MissingMicPermission)
        if (!connectivity.isOnlineNow()) {
            announcer.announce(ANNOUNCE_OFFLINE_AT_START)
            return failStart(EngineError.NoNetwork)
        }

        learnerSettings = suspendRunCatching { settings.snapshot() }.getOrDefault(learnerSettings)
        liveClient.setVolume(learnerSettings.aiVolume / 100f)
        val lesson = resolveLesson(request, learnerSettings)
        if (lesson.mode == SessionMode.STORY_LISTENING) {
            cachedStorySessions = suspendRunCatching { sessionStore.recentStorySessions(20) }.getOrDefault(emptyList())
        }
        accumulator.clear()
        _transcript.value = emptyList()
        _pronunciationAttempts.value = emptyList()
        setDrillTarget(null)
        awaitingNewDrillTarget = true
        synchronized(attemptCounts) { attemptCounts.clear() }
        accumulatedActiveMs = 0L
        unansweredNudges = 0
        stallReconnects = 0
        resetStoryProgress()
        pauseReasons.clear()
        resumeNeedsKickoff = false
        clearUtterance()
        lastLearnerUtterance = ""
        lastToolCallAt.clear()
        lastFallbackAppliedAt.clear()
        _isAppFocused.value = true
        _lesson.value = lesson
        _state.value = ConversationState.CONNECTING

        if (!audioFocus.request()) {
            _lesson.value = null
            return failStart(EngineError.AudioFocusDenied)
        }

        try {
            connectLive(lesson, recap = false)
            val kickoff = PromptTemplates.kickoffMessage(lesson)
            liveClient.sendText(kickoff)
            awaitAiReply { kickoff }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LiveLog.e(TAG, "Could not start the lesson", e)
            audioFocus.abandon()
            suspendRunCatching { liveClient.disconnect() }
            _lesson.value = null
            return failStart(mapError(e))
        }

        suspendRunCatching {
            settings.setLastTopicId(lesson.topic.id)
            settings.setLastSession(lesson.topic.id, lesson.mode, lesson.scenario?.id)
        }.onFailure { LiveLog.w(TAG, "Could not remember the last lesson", it) }
        markActive()
        startDraftSaver()
        return true
    }

    /** User-initiated pause (play/pause button, steering-wheel button). */
    open suspend fun pause() = mutex.withLock {
        pauseLocked(PauseReason.USER)
    }

    /** User-initiated resume: clears every pause reason. */
    open suspend fun resume(): Boolean = mutex.withLock { resumeLocked() }

    /**
     * Ends the lesson, generates the summary and saves the session.
     * @return the saved session id, or null if nothing worth saving happened.
     */
    open suspend fun end(): String? {
        cancelReconnect()
        return mutex.withLock { endLocked() }
    }

    /** Stops a reconnect in progress so Stop / a new lesson never waits for slow connection attempts. */
    private suspend fun cancelReconnect() {
        val job = reconnectJob ?: return
        if (job == currentCoroutineContext()[Job]) return
        job.cancelAndJoin()
        if (reconnectJob === job) reconnectJob = null
    }

    /** Cancels [job] unless it is the coroutine running this code (which must finish its own work). */
    private suspend fun cancelUnlessCurrent(job: Job?) {
        if (job != null && job != currentCoroutineContext()[Job]) job.cancel()
    }

    // region Lesson lifecycle (call with mutex held)

    private suspend fun resolveLesson(request: LessonRequest, prefs: LearnerSettings): ActiveLesson {
        val scenarioMatch = topicManager.getScenario(request.scenarioId)
        val storyResolved = if (request.mode == SessionMode.STORY_LISTENING && scenarioMatch == null && request.topicId == null) {
            val recent = suspendRunCatching { sessionStore.recentStorySessions(20) }.getOrDefault(emptyList())
            storyRecommender.recommendStory(recent, learnerLevel = request.level ?: prefs.level)
        } else {
            null
        }
        val topic = scenarioMatch?.first
            ?: storyResolved?.first
            ?: topicManager.getTopicById(request.topicId)
            ?: topicManager.suggestTopic(
                suspendRunCatching { sessionStore.recentTopicIds(RECENT_TOPICS_FOR_SUGGESTION) }.getOrDefault(emptyList())
            )
        val reviewWords = if (request.mode == SessionMode.VOCAB_REVIEW) {
            when {
                request.targetWords.isNotEmpty() -> request.targetWords
                else -> {
                    val due = suspendRunCatching { sessionStore.wordsDueForReview(MAX_REVIEW_WORDS) }.getOrDefault(emptyList())
                    if (due.isNotEmpty()) due else suspendRunCatching { sessionStore.recentWords(MAX_REVIEW_WORDS) }.getOrDefault(emptyList())
                }
            }
        } else {
            emptyList()
        }
        val reviewMistakes = if (request.mode == SessionMode.MISTAKE_REVIEW) {
            val due = suspendRunCatching { sessionStore.mistakesDueForReview(MAX_REVIEW_MISTAKES) }.getOrDefault(emptyList())
            due.ifEmpty { suspendRunCatching { sessionStore.recentMistakes(MAX_REVIEW_MISTAKES) }.getOrDefault(emptyList()) }
        } else {
            emptyList()
        }
        val learnerMemory = if (prefs.rememberLearner) {
            suspendRunCatching { sessionStore.learnerMemory() }.getOrDefault(LearnerMemory.EMPTY)
        } else {
            null
        }
        val mode = when {
            request.mode == SessionMode.VOCAB_REVIEW && reviewWords.isEmpty() -> SessionMode.FREE_TALK
            request.mode == SessionMode.MISTAKE_REVIEW && reviewMistakes.isEmpty() -> SessionMode.FREE_TALK
            request.mode == SessionMode.ROLEPLAY && scenarioMatch == null && topic.scenarios.isEmpty() -> SessionMode.FREE_TALK
            else -> request.mode
        }
        val scenario = scenarioMatch?.second ?: storyResolved?.second ?: when (mode) {
            SessionMode.ROLEPLAY -> topic.scenarios.randomOrNull()
            SessionMode.STORY_LISTENING -> topic.scenarios.firstOrNull()
            else -> null
        }
        val chosenVoice = if (prefs.randomVoice) {
            AiVoice.entries.random().id
        } else {
            prefs.voiceId
        }
        return ActiveLesson(
            sessionId = UUID.randomUUID().toString(),
            topic = topic,
            scenario = scenario,
            level = request.level ?: prefs.level,
            mode = mode,
            startedAt = clock(),
            reviewWords = reviewWords,
            voiceId = chosenVoice,
            resumeStoryContext = request.resumeStoryContext,
            resumeStoryTitle = request.resumeStoryTitle,
            reviewMistakes = if (mode == SessionMode.MISTAKE_REVIEW) reviewMistakes else emptyList(),
            learnerMemory = learnerMemory
        )
    }

    private suspend fun connectLive(lesson: ActiveLesson, recap: Boolean) {
        val drill = lesson.mode == SessionMode.REPEAT_AFTER_ME
        val sampleDrillSentences = if (drill) {
            val recentTargets = suspendRunCatching { sessionStore.recentDrillTargets(30) }.getOrDefault(emptyList()).toSet()
            drillSentenceManager.getSampleSentencesForPrompt(
                category = learnerSettings.drillCategory,
                level = lesson.level,
                topicId = lesson.topic.id,
                isCarConnected = _isCarConnected.value,
                excludeTexts = recentTargets,
                limit = 10
            ).map { it.text }
        } else emptyList()
        // Only the settings this kind of lesson changes; the voice parser covers the others.
        val tools = VoiceSettingsTools.toolsFor(lesson.mode) + if (drill) listOf(PronunciationDrill.checkAttemptTool) else emptyList()
        val instruction = PromptTemplates.buildSystemInstruction(
            lesson = lesson,
            settings = learnerSettings,
            recap = if (recap) accumulator.snapshot() else emptyList(),
            isCarConnected = _isCarConnected.value,
            sampleDrillSentences = sampleDrillSentences,
            toolNames = tools.map { it.name }.toSet()
        )
        val sessionId = lesson.sessionId
        val models = LiveModels.forMode(lesson.mode)
        LiveLog.i(
            TAG,
            "Connecting lesson ${sessionId.take(8)}: mode=${lesson.mode} topic=${lesson.topic.id} " +
                "scenario=${lesson.scenario?.id} level=${lesson.level} recap=$recap (${if (recap) accumulator.snapshot().size else 0} turns) " +
                "models=$models tools=${tools.size}"
        )
        liveClient.connect(
            LiveSessionConfig(
                systemInstruction = instruction,
                voiceId = lesson.voiceId,
                // The learner never needs to talk over the AI in a drill.
                enableInterruptions = learnerSettings.allowBargeIn && !drill,
                tools = tools,
                toolHandler = LiveToolHandler { call -> handleLiveToolCall(sessionId, call) },
                models = models,
                resumeKey = sessionId,
                // Any later connection of this lesson tries to continue the server-side conversation.
                resume = recap
            )
        )
        toolCategories = if (liveClient.settingsToolsActive) tools.mapNotNull { CommandCategory.forTool(it.name) }.toSet() else emptySet()
        connectionGeneration++
        LiveLog.i(
            TAG,
            "Lesson ${sessionId.take(8)} connected to ${liveClient.connectedModel}" +
                (if (liveClient.lastConnectResumed) ", conversation resumed on the server" else "")
        )
    }

    /**
     * Answers a Gemini tool call. Calls arrive on the Live client's background thread; all lesson
     * state is changed on the engine's own thread, and calls that belong to a lesson that has
     * already ended are refused so they cannot bring it back.
     */
    private suspend fun handleLiveToolCall(sessionId: String, call: LiveToolCall): Map<String, Any> {
        // The model is answering through a tool: a slow tool (Azure grading) is not a dropped turn.
        withContext(engineDispatcher) {
            lastModelToolCallAt = clock()
            if (_lesson.value?.sessionId == sessionId) setAiThinking(true)
        }
        val result = answerToolCall(sessionId, call)
        withContext(engineDispatcher) {
            // The model speaks once it has the result; make sure it does.
            if (_lesson.value?.sessionId == sessionId && _state.value == ConversationState.ACTIVE && _isAiThinking.value) {
                awaitAiReply { PromptTemplates.UNANSWERED_PROMPT_MESSAGE }
            }
        }
        return result
    }

    private suspend fun answerToolCall(sessionId: String, call: LiveToolCall): Map<String, Any> {
        if (call.name == PronunciationDrill.CHECK_ATTEMPT_FUNCTION) return gradeAttempt(sessionId, call)
        return withContext(engineDispatcher) {
            if (_lesson.value?.sessionId != sessionId || !_state.value.isInLesson) return@withContext STALE_TOOL_RESPONSE
            val category = CommandCategory.forTool(call.name)
            if (category != null) {
                lastToolCallAt[category] = clock()
                // The model handled it: the client-side safety net must not apply the same change again.
                val appliedAt = lastFallbackAppliedAt[category]
                if (appliedAt != null && clock() - appliedAt < FALLBACK_DEDUP_MS) {
                    LiveLog.i(TAG, "Tool ${call.name} ignored: the same change was just applied from the transcript")
                    return@withContext mapOf(
                        "status" to "success",
                        "already_applied" to true,
                        "instruction" to "This change was already applied. Confirm it in a few words and continue."
                    )
                }
            }
            dispatchToolCall(call)
        }
    }

    private suspend fun dispatchToolCall(call: LiveToolCall): Map<String, Any> = when (call.name) {
        VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION -> handleSetDifficultyLevel(call)
        VoiceSettingsTools.SET_VIETNAMESE_HELP_FUNCTION -> handleSetVietnameseHelp(call)
        VoiceSettingsTools.SET_APP_LANGUAGE_FUNCTION -> handleSetAppLanguage(call)
        VoiceSettingsTools.SET_STORYTELLING_STYLE_FUNCTION -> handleSetStorytellingStyle(call)
        VoiceSettingsTools.SET_PRONUNCIATION_STRICTNESS_FUNCTION -> handleSetPronunciationStrictness(call)
        VoiceSettingsTools.SET_BARGE_IN_FUNCTION -> handleSetBargeIn(call)
        VoiceSettingsTools.SET_VOICE_FUNCTION -> handleSetVoice(call)
        VoiceSettingsTools.SET_RANDOM_VOICE_FUNCTION -> handleSetRandomVoice(call)
        VoiceSettingsTools.SET_STORY_DURATION_FUNCTION -> handleSetStoryDuration(call)
        VoiceSettingsTools.SET_MULTI_VOICE_FUNCTION -> handleSetMultiVoice(call)
        VoiceSettingsTools.NEXT_STORY_FUNCTION -> handleNextStoryTool(call)
        VoiceSettingsTools.REPLAY_STORY_FUNCTION -> handleReplayStoryTool(call)
        VoiceSettingsTools.RESUME_STORY_FUNCTION -> handleResumeStoryTool(call)
        VoiceSettingsTools.SKIP_DRILL_SENTENCE_FUNCTION -> handleSkipDrillSentence(call)
        VoiceSettingsTools.REPEAT_DRILL_SENTENCE_FUNCTION -> handleRepeatDrillSentence(call)
        VoiceSettingsTools.APPLY_LEVEL_RECOMMENDATION_FUNCTION -> handleApplyLevelRecommendation(call)
        VoiceSettingsTools.SET_ADAPTIVE_LEVEL_FUNCTION -> handleSetAdaptiveLevel(call)
        VoiceSettingsTools.SET_DRILL_SENTENCE_LENGTH_FUNCTION -> handleSetDrillSentenceLength(call)
        VoiceSettingsTools.SET_DRILL_CATEGORY_FUNCTION -> handleSetDrillCategory(call)
        VoiceSettingsTools.SET_AI_VOLUME_FUNCTION -> handleSetAiVolume(call)
        VoiceSettingsTools.SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION -> handleSetAutoPauseWhenUnfocused(call)
        VoiceSettingsTools.SET_TRANSLATION_SUBTITLES_FUNCTION -> handleSetTranslationSubtitles(call)
        VoiceSettingsTools.SET_LEARNER_MEMORY_FUNCTION -> handleSetLearnerMemory(call)
        VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION -> handleSetPracticeReminder(call)
        VoiceSettingsTools.SET_STREAK_FREEZE_FUNCTION -> handleSetStreakFreeze(call)
        VoiceSettingsTools.SET_OFFLINE_PRACTICE_FUNCTION -> handleSetOfflinePractice(call)
        VoiceSettingsTools.SET_AUTO_START_IN_CAR_FUNCTION -> booleanArg(call.args["enabled"])
            ?.let(::applyAutoStartInCarChange) ?: argumentError("enabled", call.args["enabled"], "true, false")
        VoiceSettingsTools.SET_DAILY_GOAL_FUNCTION -> VoiceSettingsTools.parseDailyGoal(call.args["minutes"], learnerSettings.dailyGoalMinutes)
            ?.let(::applyDailyGoalChange) ?: argumentError("minutes", call.args["minutes"], "5-60, more, less")
        VoiceSettingsTools.SET_AZURE_SCORING_FUNCTION -> booleanArg(call.args["enabled"])
            ?.let(::applyAzureScoringChange) ?: argumentError("enabled", call.args["enabled"], "true, false")
        VoiceSettingsTools.SET_SCREEN_AWAKE_FUNCTION -> VoiceSettingsTools.parseScreenAwakeMode(call.args["mode"] as? String)
            ?.let(::applyScreenAwakeChange) ?: argumentError("mode", call.args["mode"], ScreenAwakeMode.entries.joinToString { it.name })
        VoiceSettingsTools.SET_BETTER_PHRASING_FUNCTION -> handleSetBetterPhrasing(call)
        VoiceSettingsTools.GET_WEEKLY_DIGEST_FUNCTION -> handleGetWeeklyDigest()
        VoiceSettingsTools.SET_WEEKLY_DIGEST_FUNCTION -> handleSetWeeklyDigest(call)
        VoiceSettingsTools.CREATE_CUSTOM_SCENARIO_FUNCTION -> handleCreateCustomScenario(call)
        VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION -> handleSwitchSessionMode(call)
        else -> mapOf("status" to "unknown function")
    }

    /** Tool arguments arrive as String, Boolean or Number; anything else is unusable. */
    private fun booleanArg(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "yes", "on", "1", "enable", "enabled" -> true
            "false", "no", "off", "0", "disable", "disabled" -> false
            else -> null
        }
        else -> null
    }

    private fun argumentError(name: String, value: Any?, allowed: String): Map<String, Any> {
        LiveLog.w(TAG, "Unusable tool argument $name=$value")
        return mapOf("status" to "error", "message" to "Unknown $name: $value. Allowed: $allowed. Nothing was changed.")
    }

    private fun handleSwitchSessionMode(call: LiveToolCall): Map<String, Any> {
        val rawMode = call.args["mode"] as? String
        val targetMode = VoiceSettingsTools.parseSessionMode(rawMode)
            ?: return argumentError("mode", rawMode, SessionMode.entries.joinToString { it.name })
        return applySessionModeSwitch(targetMode)
    }

    internal fun applySessionModeSwitch(newMode: SessionMode, topicId: String? = null): Map<String, Any> {
        val currentLesson = _lesson.value
        val effectiveTopicId = topicId ?: currentLesson?.topic?.id ?: learnerSettings.lastTopicId
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH

        if (currentLesson?.mode == newMode && _state.value.isInLesson) {
            val alreadyMsg = when (newMode) {
                SessionMode.REPEAT_AFTER_ME -> if (isVi) "Bạn đang ở chế độ luyện phát âm shadowing rồi." else "You are already in repeat-after-me pronunciation mode."
                SessionMode.FREE_TALK -> if (isVi) "Bạn đang ở chế độ hội thoại tự do rồi." else "You are already in free conversation mode."
                SessionMode.STORY_LISTENING -> if (isVi) "Bạn đang ở chế độ nghe kể chuyện rồi." else "You are already in story listening mode."
                SessionMode.ROLEPLAY -> if (isVi) "Bạn đang ở chế độ nhập vai rồi." else "You are already in roleplay mode."
                SessionMode.VOCAB_REVIEW -> if (isVi) "Bạn đang ở chế độ ôn tập từ vựng rồi." else "You are already in vocabulary review mode."
                SessionMode.MISTAKE_REVIEW -> if (isVi) "Bạn đang ở chế độ ôn lỗi sai rồi." else "You are already in mistake review mode."
                SessionMode.IELTS_SPEAKING -> if (isVi) "Bạn đang ở chế độ luyện thi IELTS Speaking rồi." else "You are already in IELTS speaking mode."
            }
            confirmOutLoud(alreadyMsg)
            return mapOf(
                "status" to "already_active",
                "mode" to newMode.name,
                "instruction" to "The learner is already in ${newMode.name} mode. Tell them so in one short sentence and continue."
            )
        }

        // The lesson restarts in the new mode, so the AI leaves the conversation: the app says it,
        // briefly, while the new session connects (the new AI greets in the new mode right after).
        val confirmationMsg = when (newMode) {
            SessionMode.REPEAT_AFTER_ME -> if (isVi) "Đã chuyển sang chế độ luyện phát âm shadowing." else "Switched to repeat-after-me pronunciation mode."
            SessionMode.FREE_TALK -> if (isVi) "Đã chuyển sang chế độ hội thoại tự do." else "Switched to free conversation mode."
            SessionMode.STORY_LISTENING -> if (isVi) "Đã chuyển sang chế độ luyện nghe kể chuyện." else "Switched to story listening mode."
            SessionMode.ROLEPLAY -> if (isVi) "Đã chuyển sang chế độ nhập vai." else "Switched to roleplay mode."
            SessionMode.VOCAB_REVIEW -> if (isVi) "Đã chuyển sang chế độ ôn tập từ vựng." else "Switched to vocabulary review mode."
            SessionMode.MISTAKE_REVIEW -> if (isVi) "Đã chuyển sang chế độ ôn lỗi sai." else "Switched to mistake review mode."
            SessionMode.IELTS_SPEAKING -> if (isVi) "Đã chuyển sang chế độ luyện thi IELTS Speaking." else "Switched to IELTS speaking mode."
        }
        announcer.announce(confirmationMsg)

        val fromSessionId = currentLesson?.sessionId
        scope.launch {
            // The lesson changed in the meantime (ended, or another switch won): do not restart again.
            if (fromSessionId != null && _lesson.value?.sessionId != fromSessionId) return@launch
            if (effectiveTopicId != null) {
                suspendRunCatching {
                    settings.setLastSession(effectiveTopicId, newMode, if (newMode == SessionMode.ROLEPLAY) currentLesson?.scenario?.id else null)
                }
            }
            start(LessonRequest(mode = newMode, topicId = effectiveTopicId))
        }

        return mapOf(
            "status" to "switched",
            "new_mode" to newMode.name,
            "instruction" to "The app is restarting the lesson in the new mode right now and has already told the learner. Say nothing."
        )
    }

    private fun handleApplyLevelRecommendation(call: LiveToolCall): Map<String, Any> {
        val acceptArg = call.args["accept"]
        val accept = when (acceptArg) {
            null -> true
            is Boolean -> acceptArg
            is String -> VoiceSettingsTools.parseApplyLevelRecommendation(acceptArg)
                ?: return argumentError("accept", acceptArg, "true, false")
            else -> return argumentError("accept", acceptArg, "true, false")
        }
        val targetArg = call.args["target_level"] as? String
        val newLevel = answerLevelRecommendation(accept, VoiceSettingsTools.parseLevel(targetArg, learnerSettings.level))
        return levelRecommendationResult(accept, newLevel)
    }

    private fun levelRecommendationResult(accept: Boolean, level: DifficultyLevel): Map<String, Any> = if (accept) {
        mapOf(
            "status" to "applied",
            "new_level" to level.name,
            "instruction" to "The learner's level is now ${level.displayName} (${level.cefr}) from the next lesson. Confirm in one short sentence and continue."
        )
    } else {
        mapOf(
            "status" to "dismissed",
            "level" to level.name,
            "instruction" to "The learner keeps the ${level.displayName} level. Acknowledge it in one short sentence and continue."
        )
    }

    /**
     * Accepts or dismisses the level change suggested by the last lesson summary (or [explicitTarget]).
     * Without a pending suggestion, accepting moves one level up, as the tool always did.
     * @return the level the learner ends up with.
     */
    private fun answerLevelRecommendation(accept: Boolean, explicitTarget: DifficultyLevel? = null): DifficultyLevel {
        val currentLevel = learnerSettings.level
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val recommendation = pendingLevelRecommendation
        pendingLevelRecommendation = null
        if (!accept) {
            confirmOutLoud(if (isVi) "Giữ nguyên cấp độ ${currentLevel.getLabel(true)}" else "Kept current level ${currentLevel.displayName}")
            return currentLevel
        }
        val newLevel = explicitTarget
            ?: recommendation?.targetLevel
            ?: currentLevel.nextLevel()
            ?: currentLevel
        learnerSettings = learnerSettings.copy(level = newLevel)
        persist("level") { settings.setLevel(newLevel) }
        confirmOutLoud(
            if (isVi) "Đã chuyển cấp độ sang ${newLevel.getLabel(true)} cho buổi học tiếp theo!"
            else "Updated learning level to ${newLevel.displayName} for your next lesson!"
        )
        return newLevel
    }

    private fun handleSetAdaptiveLevel(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = booleanArg(enabledArg)
            ?: (enabledArg as? String)?.let(VoiceSettingsTools::parseAdaptiveLevel)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyAdaptiveLevelChange(enabled)
    }

    private fun applyAdaptiveLevelChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(adaptiveLevelRecommendation = enabled)
        persist("adaptive level") { settings.setAdaptiveLevelRecommendation(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (enabled) {
            if (isVi) "Đã bật tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation enabled."
        } else {
            if (isVi) "Đã tắt tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation disabled."
        }
        confirmOutLoud(confirmation)
        return mapOf(
            "status" to "updated",
            "adaptive_level_enabled" to enabled,
            "instruction" to "Level suggestions after each lesson were turned ${if (enabled) "on" else "off"}. Confirm in one short sentence and continue."
        )
    }

    private fun handleSetDrillSentenceLength(call: LiveToolCall): Map<String, Any> {
        val raw = call.args["length"] as? String
        val parsed = VoiceSettingsTools.parseDrillSentenceLength(raw)
            ?: return argumentError("length", raw, DrillSentenceLength.entries.joinToString { it.name })
        return applyDrillSentenceLength(parsed)
    }

    internal fun applyDrillSentenceLength(length: DrillSentenceLength): Map<String, Any> {
        learnerSettings = learnerSettings.copy(drillSentenceLength = length)
        persist("drill sentence length") { settings.setDrillSentenceLength(length) }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            when (length) {
                DrillSentenceLength.AUTO_ON_CAR -> "Đã đặt độ dài câu tự động ngắn khi kết nối ô tô."
                DrillSentenceLength.ALWAYS_SHORT -> "Đã chuyển sang các câu nhắc lại ngắn gọn."
                DrillSentenceLength.STANDARD -> "Đã chuyển sang độ dài câu tiêu chuẩn theo cấp độ."
            }
        } else {
            when (length) {
                DrillSentenceLength.AUTO_ON_CAR -> "Repeat sentences will now shorten automatically while driving on Android Auto."
                DrillSentenceLength.ALWAYS_SHORT -> "Repeat sentences are now set to short and concise."
                DrillSentenceLength.STANDARD -> "Repeat sentences are now set to standard length."
            }
        }
        confirmOutLoud(confirmation)

        return mapOf(
            "status" to "success",
            "new_length" to length.name,
            "instruction" to "Drill sentence length set to ${length.name}. Confirm warmly in one short sentence to the learner, then continue with: " +
                PromptTemplates.drillSentenceLengthSwitchMessage(length, _isCarConnected.value)
        )
    }

    private fun handleSetDrillCategory(call: LiveToolCall): Map<String, Any> {
        val catArg = call.args["category"] as? String
        val parsed = VoiceSettingsTools.parseDrillCategory(catArg)
            ?: return argumentError("category", catArg, DrillCategory.entries.joinToString { it.name })
        return applyDrillCategory(parsed)
    }

    internal fun applyDrillCategory(category: DrillCategory): Map<String, Any> {
        learnerSettings = learnerSettings.copy(drillCategory = category)
        persist("drill category") { settings.setDrillCategory(category) }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            "Đã chuyển chủ đề luyện âm sang: ${category.getLabel(true)}."
        } else {
            "Drill category switched to: ${category.displayName}."
        }
        confirmOutLoud(confirmation)

        val switchMsg = PromptTemplates.drillCategorySwitchMessage(category)

        return mapOf(
            "status" to "success",
            "drill_category" to category.name,
            "instruction" to "Confirm warmly in one short sentence to the learner, then continue with: $switchMsg"
        )
    }

    private fun handleSetAiVolume(call: LiveToolCall): Map<String, Any> {
        val volArg = call.args["volume"]
        val parsed = when (volArg) {
            is Number -> volArg.toInt()
            is String -> VoiceSettingsTools.parseVolume(volArg, learnerSettings.aiVolume)
            else -> null
        } ?: return argumentError("volume", volArg, "a percentage 10-100, \"louder\" or \"softer\"")
        return applyAiVolume(parsed)
    }

    internal fun applyAiVolume(volume: Int): Map<String, Any> {
        val clamped = volume.coerceIn(10, 100)
        learnerSettings = learnerSettings.copy(aiVolume = clamped)
        persist("AI volume") { settings.setAiVolume(clamped) }
        liveClient.setVolume(clamped / 100f)

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            "Đã điều chỉnh âm lượng giọng nói thành $clamped phần trăm."
        } else {
            "AI voice volume set to $clamped percent."
        }
        confirmOutLoud(confirmation)

        return mapOf(
            "status" to "success",
            "new_volume" to clamped,
            "instruction" to "AI voice volume set to $clamped%. Confirm warmly in one short sentence to the learner, then naturally continue the conversation."
        )
    }

    private fun handleSetAutoPauseWhenUnfocused(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val parsed = VoiceSettingsTools.parseAutoPauseWhenUnfocused(enabledArg)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyAutoPauseWhenUnfocusedChange(parsed)
    }

    internal fun applyAutoPauseWhenUnfocusedChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(autoPauseWhenUnfocused = enabled)
        persist("auto-pause") { settings.setAutoPauseWhenUnfocused(enabled) }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật tự động tạm dừng khi rời ứng dụng hoặc tắt màn hình."
            else "Đã tắt tự động tạm dừng khi rời ứng dụng hoặc tắt màn hình."
        } else {
            if (enabled) "Auto-pause when leaving app or turning screen off enabled."
            else "Auto-pause when leaving app or turning screen off disabled."
        }
        confirmOutLoud(confirmation)

        if (enabled) {
            checkAutoPause()
        }

        return mapOf(
            "status" to "success",
            "auto_pause_when_unfocused" to enabled,
            "instruction" to "Auto-pause when app is unfocused or screen off set to $enabled. Confirm warmly in one short sentence to the learner, then naturally continue the conversation."
        )
    }

    private fun handleSetTranslationSubtitles(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val parsed = VoiceSettingsTools.parseTranslationSubtitles(enabledArg)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyTranslationSubtitlesChange(parsed)
    }

    internal fun applyTranslationSubtitlesChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(showTranslationSubtitle = enabled)
        persist("translation subtitles") { settings.setShowTranslationSubtitle(enabled) }
        LiveLog.i(TAG, "Switched showTranslationSubtitle to $enabled")

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật phụ đề dịch nghĩa câu nói."
            else "Đã tắt phụ đề dịch nghĩa câu nói."
        } else {
            if (enabled) "Translation subtitles enabled."
            else "Translation subtitles disabled."
        }
        confirmOutLoud(confirmation)

        if (!enabled) {
            translationJob?.cancel()
            _drillTargetTranslation.value = null
        } else {
            _drillTarget.value?.let { currentTarget ->
                setDrillTarget(currentTarget)
            }
        }

        return mapOf(
            "status" to "success",
            "translation_subtitles" to enabled,
            "instruction" to "Translation subtitles have been ${if (enabled) "enabled" else "disabled"}. " +
                "Confirm warmly in one short sentence (e.g. \"${if (enabled) "Translation subtitles enabled!" else "Subtitles hidden, challenge mode on!"}\") and continue."
        )
    }

    private fun handleSetLearnerMemory(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val parsed = VoiceSettingsTools.parseLearnerMemory(enabledArg)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyLearnerMemoryChange(parsed)
    }

    /**
     * Turns the AI's memory of the learner on or off. Off takes effect at once: the current lesson stops
     * collecting personal facts. Already stored notes are kept until the learner deletes them in the app.
     */
    internal fun applyLearnerMemoryChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(rememberLearner = enabled)
        persist("learner memory") { settings.setRememberLearner(enabled) }
        LiveLog.i(TAG, "Switched rememberLearner to $enabled")
        _lesson.value?.let { current ->
            _lesson.value = current.copy(learnerMemory = if (enabled) current.learnerMemory ?: LearnerMemory.EMPTY else null)
        }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật ghi nhớ. Từ buổi sau, gia sư sẽ nhớ lỗi sai và những điều bạn kể."
            else "Đã tắt ghi nhớ. Gia sư sẽ không ghi nhớ thêm gì về bạn."
        } else {
            if (enabled) "Memory is on. From the next lesson your tutor will remember your mistakes and what you share."
            else "Memory is off. Your tutor will not remember anything new about you."
        }
        confirmOutLoud(confirmation)

        return mapOf(
            "status" to "success",
            "remember_learner" to enabled,
            "instruction" to if (enabled) {
                "Memory has been turned on. Confirm warmly in one short sentence and continue."
            } else {
                "Memory has been turned off. Confirm in one short sentence, do not mention any personal details about the learner " +
                    "for the rest of this lesson, and continue."
            }
        )
    }

    private fun handleSetPracticeReminder(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val timeArg = call.args["time"]
        val minute = VoiceSettingsTools.parseReminderTime(timeArg)
        if (timeArg != null && timeArg.toString().isNotBlank() && minute == null) {
            return argumentError("time", timeArg, "HH:MM in 24-hour time, or auto")
        }
        // A time without "enabled" means the learner wants the reminder at that time.
        val enabled = booleanArg(enabledArg) ?: if (minute != null) true else return argumentError("enabled", enabledArg, "true, false")
        return applyPracticeReminderChange(enabled, minute)
    }

    /** Turns the daily reminder on or off or moves it; the app reschedules it when the setting changes. */
    internal fun applyPracticeReminderChange(enabled: Boolean, minuteOfDay: Int?): Map<String, Any> {
        learnerSettings = learnerSettings.copy(
            practiceReminderEnabled = enabled,
            practiceReminderMinute = minuteOfDay ?: learnerSettings.practiceReminderMinute
        )
        persist("practice reminder") { settings.setPracticeReminder(enabled, minuteOfDay) }
        LiveLog.i(TAG, "Practice reminder enabled=$enabled minute=$minuteOfDay")

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val time = learnerSettings.practiceReminderMinute
            .takeIf { it != LearnerSettings.REMINDER_AUTO }
            ?.let { "%d:%02d".format(it / 60, it % 60) }
        val confirmation = when {
            !enabled -> if (isVi) "Đã tắt nhắc luyện tập hằng ngày." else "Daily practice reminders are off."
            time != null -> if (isVi) "Đã đặt nhắc luyện tập lúc $time mỗi ngày." else "Daily practice reminder set for $time."
            else -> if (isVi) "Đã bật nhắc luyện tập, tự động theo giờ bạn hay luyện." else "Daily practice reminder on, timed to when you usually practise."
        }
        confirmOutLoud(confirmation)
        return mapOf(
            "status" to "success",
            "reminder_enabled" to enabled,
            "reminder_time" to (time ?: if (enabled) "auto" else "off"),
            "instruction" to when {
                !enabled -> "The daily practice reminder was turned off."
                time != null -> "The daily practice reminder is set for $time every day."
                else -> "The daily practice reminder is on, timed to when the learner usually practises."
            } + " Confirm in one short sentence and continue the lesson."
        )
    }

    private fun handleSetStreakFreeze(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = booleanArg(enabledArg) ?: return argumentError("enabled", enabledArg, "true, false")
        return applyStreakFreezeChange(enabled)
    }

    internal fun applyStreakFreezeChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(streakFreezeEnabled = enabled)
        persist("streak freeze") { settings.setStreakFreeze(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật bảo toàn chuỗi ngày học." else "Đã tắt bảo toàn chuỗi ngày học."
        } else {
            if (enabled) "Streak freezes are on." else "Streak freezes are off."
        }
        confirmOutLoud(confirmation)
        return mapOf(
            "status" to "success",
            "streak_freeze" to enabled,
            "instruction" to "Streak freezes were turned ${if (enabled) "on" else "off"}. Confirm in one short sentence and continue the lesson."
        )
    }

    private fun handleSetOfflinePractice(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = booleanArg(enabledArg) ?: return argumentError("enabled", enabledArg, "true, false")
        return applyOfflinePracticeChange(enabled)
    }

    private fun handleSetBetterPhrasing(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = VoiceSettingsTools.parseBetterPhrasing(enabledArg)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyBetterPhrasingChange(enabled)
    }

    internal fun applyBetterPhrasingChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(betterPhrasingEnabled = enabled)
        persist("better phrasing") { settings.setBetterPhrasing(enabled) }
        val current = _lesson.value
        if (current != null) {
            _lesson.value = current.copy(betterPhrasingEnabled = enabled)
        }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật gợi ý nói tự nhiên hơn." else "Đã tắt gợi ý nói tự nhiên hơn."
        } else {
            if (enabled) "Natural phrasing upgrades are on." else "Natural phrasing upgrades are off."
        }
        confirmOutLoud(confirmation)
        return mapOf(
            "status" to "success",
            "better_phrasing" to enabled,
            "instruction" to "Natural phrasing upgrades were turned ${if (enabled) "on" else "off"}. Confirm warmly in one short sentence and continue."
        )
    }

    private suspend fun handleGetWeeklyDigest(): Map<String, Any> {
        val digest = suspendRunCatching { sessionStore.weeklyDigest() }.getOrNull()
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val response = digest ?: if (isVi) {
            "Bạn chưa có buổi học nào trong tuần qua. Hãy bắt đầu một buổi luyện tập hôm nay nhé!"
        } else {
            "You haven't had any practice sessions in the past week. Let's start practicing today!"
        }
        confirmOutLoud(response)
        return mapOf(
            "status" to "success",
            "digest" to response,
            "instruction" to "Tell the learner this weekly progress digest in a few short spoken sentences, in the language it is written in, " +
                "then continue the lesson warmly: $response"
        )
    }

    private fun handleSetWeeklyDigest(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = VoiceSettingsTools.parseWeeklyDigest(enabledArg)
            ?: return argumentError("enabled", enabledArg, "true, false")
        return applyWeeklyDigestChange(enabled)
    }

    internal fun applyWeeklyDigestChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(weeklyDigestEnabled = enabled)
        persist("weekly digest") { settings.setWeeklyDigestEnabled(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật báo cáo tuần bằng giọng nói." else "Đã tắt báo cáo tuần bằng giọng nói."
        } else {
            if (enabled) "Weekly spoken digest is on." else "Weekly spoken digest is off."
        }
        confirmOutLoud(confirmation)
        return mapOf(
            "status" to "success",
            "weekly_digest" to enabled,
            "instruction" to "Weekly progress digest was turned ${if (enabled) "on" else "off"}. Confirm warmly in one short sentence and continue."
        )
    }

    private suspend fun handleCreateCustomScenario(call: LiveToolCall): Map<String, Any> {
        val titleVi = (call.args["title_vi"] as? String)?.trim().orEmpty()
        val titleEn = (call.args["title_en"] as? String)?.trim().orEmpty().ifBlank { titleVi }
        val aiRole = (call.args["ai_role"] as? String)?.trim().orEmpty().ifBlank { "Partner" }
        val learnerRole = (call.args["learner_role"] as? String)?.trim().orEmpty().ifBlank { "Learner" }
        val context = (call.args["custom_context"] as? String)?.trim().orEmpty()
        val objective = (call.args["mission_objective"] as? String)?.trim()

        if (titleVi.isBlank() && titleEn.isBlank()) {
            return argumentError("title_vi", null, "A scenario title is required")
        }

        val scenario = suspendRunCatching {
            sessionStore.saveCustomScenario(
                titleVi = titleVi.ifBlank { titleEn },
                titleEn = titleEn,
                aiRole = aiRole,
                learnerRole = learnerRole,
                customContext = context,
                missionObjective = objective
            )
        }.getOrNull()

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val feedback = if (isVi) {
            "Đã lưu tình huống \"${titleVi.ifBlank { titleEn }}\" vào danh sách luyện tập của bạn."
        } else {
            "Saved scenario \"$titleEn\" to your practice list."
        }
        confirmOutLoud(feedback)
        return mapOf(
            "status" to "success",
            "scenario_id" to (scenario?.id ?: "saved"),
            "instruction" to "The custom scenario was saved to the learner's practice list. Warmly acknowledge it to the learner and ask if they'd like to practice it now or continue."
        )
    }

    /** Tool result for a setting the AI confirms itself; [change] says what changed, in a sentence. */
    private fun confirmedByModel(key: String, value: Any, change: String): Map<String, Any> = mapOf(
        "status" to "success",
        key to value,
        "instruction" to "$change Confirm it in one short sentence and continue the lesson."
    )

    internal fun applyAutoStartInCarChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(autoStartOnCarConnect = enabled)
        persist("auto-start in the car") { settings.setAutoStartOnCarConnect(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        confirmOutLoud(
            if (isVi) {
                if (enabled) "Đã bật tự động bắt đầu bài học khi kết nối Android Auto." else "Đã tắt tự động bắt đầu bài học khi kết nối Android Auto."
            } else {
                if (enabled) "Lessons will start automatically when Android Auto connects." else "Lessons will no longer start automatically in the car."
            }
        )
        return confirmedByModel(
            "auto_start_in_car", enabled,
            if (enabled) "Lessons will now start automatically when Android Auto connects." else "Lessons will no longer start automatically when Android Auto connects."
        )
    }

    internal fun applyDailyGoalChange(minutes: Int): Map<String, Any> {
        val goal = minutes.coerceIn(LearnerSettings.MIN_DAILY_GOAL_MINUTES, LearnerSettings.MAX_DAILY_GOAL_MINUTES)
        learnerSettings = learnerSettings.copy(dailyGoalMinutes = goal)
        persist("daily goal") { settings.setDailyGoalMinutes(goal) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        confirmOutLoud(if (isVi) "Đã đặt mục tiêu $goal phút mỗi ngày." else "Daily goal set to $goal minutes.")
        return confirmedByModel("daily_goal_minutes", goal, "The daily practice goal is now $goal minutes.")
    }

    internal fun applyAzureScoringChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(azureEnabled = enabled)
        persist("Azure scoring") { settings.setAzureEnabled(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val needsKey = enabled && !AzureSpeechConfig(learnerSettings.azureRegion, learnerSettings.azureKey).isComplete
        confirmOutLoud(
            when {
                needsKey && isVi -> "Đã bật chấm điểm Azure. Hãy nhập khoá Azure trong Cài đặt khi xe đã dừng."
                needsKey -> "Azure scoring is on. Enter your Azure key in Settings when the car is parked."
                isVi -> if (enabled) "Đã bật chấm điểm phát âm bằng Azure." else "Đã tắt chấm điểm phát âm bằng Azure."
                else -> if (enabled) "Azure pronunciation scoring is on." else "Azure pronunciation scoring is off."
            }
        )
        val change = when {
            needsKey -> "Azure pronunciation scoring is on, but no Azure key is set: tell the learner to enter it in Settings once the car is parked."
            enabled -> "Azure pronunciation scoring is on."
            else -> "Azure pronunciation scoring is off."
        }
        return confirmedByModel("azure_scoring", enabled, change) + ("azure_key_missing" to needsKey)
    }

    internal fun applyScreenAwakeChange(mode: ScreenAwakeMode): Map<String, Any> {
        learnerSettings = learnerSettings.copy(screenAwakeMode = mode)
        persist("screen awake mode") { settings.setScreenAwakeMode(mode) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        confirmOutLoud(
            if (isVi) "Màn hình khi học: ${mode.getLabel(true)}." else "Screen during lessons: ${mode.getLabel(false)}."
        )
        return confirmedByModel("screen_awake_mode", mode.name, "Screen during lessons is now set to: ${mode.getLabel(false)}.")
    }

    /** Turning it off while offline stops the practice; turning it on while offline starts it. */
    internal fun applyOfflinePracticeChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(offlinePracticeEnabled = enabled)
        persist("offline practice") { settings.setOfflinePractice(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        confirmOutLoud(
            if (isVi) {
                if (enabled) "Đã bật luyện offline khi mất sóng." else "Đã tắt luyện offline khi mất sóng."
            } else {
                if (enabled) "Offline practice is on." else "Offline practice is off."
            }
        )
        if (_state.value == ConversationState.WAITING_FOR_NETWORK) {
            if (enabled) startOfflineDrillLocked() else stopOfflineDrillLocked()
        }
        return mapOf(
            "status" to "success",
            "offline_practice" to enabled,
            "instruction" to "Offline practice was turned ${if (enabled) "on" else "off"}. Confirm in one short sentence and continue the lesson."
        )
    }

    private fun handleSetAppLanguage(call: LiveToolCall): Map<String, Any> {
        val langArg = call.args["language"] as? String
        val parsed = VoiceSettingsTools.parseAppLanguage(langArg)
        if (parsed == null) {
            LiveLog.w(TAG, "Could not parse app language from arg: $langArg")
            return mapOf("status" to "error", "message" to "Unknown language: $langArg")
        }
        applyAppLanguageChange(parsed)
        return mapOf(
            "status" to "success",
            "app_language" to parsed.code,
            "instruction" to "App interface and explanation language updated to ${parsed.englishName} (${parsed.nativeName}). Confirm warmly in one short sentence to the learner in ${parsed.englishName} or Vietnamese, and continue the conversation in English."
        )
    }

    private fun handleSetDifficultyLevel(call: LiveToolCall): Map<String, Any> {
        val levelArg = call.args["level"] as? String
        val parsedLevel = VoiceSettingsTools.parseLevel(levelArg, _lesson.value?.level ?: learnerSettings.level)
        if (parsedLevel == null) {
            LiveLog.w(TAG, "Could not parse difficulty level from arg: $levelArg")
            return mapOf("status" to "error", "message" to "Unknown level: $levelArg")
        }
        applyDifficultyChange(parsedLevel)
        val instruction = if (_lesson.value?.mode == SessionMode.STORY_LISTENING) {
            "Story difficulty level changed to ${parsedLevel.displayName} (${parsedLevel.cefr}). Confirm warmly in one short sentence (e.g. \"Adapting the story to ${parsedLevel.displayName} level with simpler vocabulary and pacing!\") and immediately continue narrating the next events of the story adapted to this level."
        } else {
            "Difficulty level changed to ${parsedLevel.displayName} (${parsedLevel.cefr}). Confirm warmly in one short sentence and continue adapting your language to this level."
        }
        return mapOf(
            "status" to "success",
            "new_level" to parsedLevel.displayName,
            "cefr" to parsedLevel.cefr,
            "instruction" to instruction
        )
    }

    private fun handleSetVietnameseHelp(call: LiveToolCall): Map<String, Any> {
        val enabled = booleanArg(call.args["enabled"]) ?: return argumentError("enabled", call.args["enabled"], "true, false")
        applyVietnameseHelpChange(enabled)
        val instruction = if (enabled) {
            "Vietnamese help has been enabled. Confirm warmly to the learner in one short sentence (e.g. \"Sure, I will provide brief explanations in Vietnamese when needed!\") and continue. If the learner struggles, you may give one short explanation in Vietnamese."
        } else {
            "English only mode has been enabled. Confirm warmly to the learner in one short sentence (e.g. \"Got it, let's practice English only!\") and continue encouraging the learner to speak English."
        }
        return mapOf(
            "status" to "success",
            "vietnamese_help" to enabled,
            "instruction" to instruction
        )
    }

    private fun handleSetPronunciationStrictness(call: LiveToolCall): Map<String, Any> {
        val strictnessArg = call.args["strictness"] as? String
        val parsed = VoiceSettingsTools.parseStrictness(strictnessArg)
        if (parsed == null) {
            LiveLog.w(TAG, "Could not parse pronunciation strictness from arg: $strictnessArg")
            return mapOf("status" to "error", "message" to "Unknown strictness: $strictnessArg")
        }
        applyPronunciationStrictnessChange(parsed)
        val currentLevel = _lesson.value?.level ?: learnerSettings.level
        val instruction = if (parsed == PronunciationStrictness.AUTO) {
            val resolved = parsed.resolveForLevel(currentLevel)
            "Pronunciation grading strictness changed to Auto by Level (${resolved.displayName} for your ${currentLevel.displayName} level). Confirm warmly to the learner in one short sentence (e.g. \"Got it, I will grade your pronunciation automatically based on your ${currentLevel.displayName} level!\") and continue."
        } else {
            "Pronunciation grading strictness changed to ${parsed.displayName} (${parsed.labelVi}). Confirm warmly to the learner in one short sentence (e.g. \"Got it, I will grade your pronunciation with ${parsed.displayName} criteria now!\") and continue."
        }
        return mapOf(
            "status" to "success",
            "new_strictness" to parsed.displayName,
            "instruction" to instruction
        )
    }

    private fun handleSetBargeIn(call: LiveToolCall): Map<String, Any> {
        val enabled = booleanArg(call.args["enabled"]) ?: return argumentError("enabled", call.args["enabled"], "true, false")
        applyBargeInChange(enabled)
        val instruction = if (enabled) {
            "Barge-in / interruption mode has been enabled. Confirm warmly to the learner in one short sentence (e.g. \"Barge-in is now turned on, you can interrupt me anytime!\") and continue."
        } else {
            "Barge-in / interruption mode has been disabled. Confirm warmly to the learner in one short sentence (e.g. \"Barge-in is turned off, I will speak without interruption until you take your turn.\") and continue."
        }
        return mapOf(
            "status" to "success",
            "barge_in" to enabled,
            "instruction" to instruction
        )
    }

    private fun handleSetVoice(call: LiveToolCall): Map<String, Any> {
        val voiceArg = call.args["voice"] as? String
        if (VoiceSettingsTools.isRandomVoiceRequested(voiceArg)) {
            return handleSetRandomVoice(call.copy(args = mapOf("enabled" to true)))
        }
        val parsed = VoiceSettingsTools.parseVoice(voiceArg)
        if (parsed == null) {
            LiveLog.w(TAG, "Could not parse voice from arg: $voiceArg")
            return mapOf("status" to "error", "message" to "Unknown voice: $voiceArg")
        }
        applyVoiceChange(parsed)
        return mapOf(
            "status" to "success",
            "new_voice" to parsed.id,
            "instruction" to "Preferred AI voice updated to ${parsed.name} (${parsed.labelVi}). Confirm warmly in one short sentence explaining that this new voice will take effect starting in your next session, and continue."
        )
    }

    private fun handleSetRandomVoice(call: LiveToolCall): Map<String, Any> {
        val enabled = booleanArg(call.args["enabled"]) ?: return argumentError("enabled", call.args["enabled"], "true, false")
        applyRandomVoiceChange(enabled)
        val instruction = if (enabled) {
            "Random AI voice selection has been enabled for each lesson. Confirm warmly to the learner in one short sentence (e.g. \"Đã bật chế độ giọng ngẫu nhiên mỗi bài học!\") and continue."
        } else {
            "Random AI voice selection has been disabled. The current voice will be used for subsequent lessons. Confirm warmly to the learner in one short sentence (e.g. \"Đã tắt giọng ngẫu nhiên, giữ giọng hiện tại!\") and continue."
        }
        return mapOf(
            "status" to "success",
            "random_voice" to enabled,
            "instruction" to instruction
        )
    }

    private fun handleSetStorytellingStyle(call: LiveToolCall): Map<String, Any> {
        val styleArg = call.args["style"] as? String
        val parsedStyle = VoiceSettingsTools.parseStorytellingStyle(styleArg)
        if (parsedStyle == null) {
            LiveLog.w(TAG, "Could not parse storytelling style from arg: $styleArg")
            return mapOf("status" to "error", "message" to "Unknown style: $styleArg")
        }
        applyStorytellingStyleChange(parsedStyle)
        onStorytellingStyleChanged()
        return mapOf(
            "status" to "success",
            "new_style" to parsedStyle.displayName,
            "storytelling_style" to parsedStyle.displayName,
            "instruction" to PromptTemplates.storytellingStyleSwitchMessage(parsedStyle)
        )
    }

    private fun handleNextStoryTool(call: LiveToolCall): Map<String, Any> {
        val reason = call.args["reason"] as? String ?: "skipped"
        LiveLog.i(TAG, "Learner requested next story tool (reason: $reason)")
        val switched = performNextStory()
        return if (switched != null) {
            mapOf(
                "status" to "success",
                "new_story" to switched.second.titleEn,
                "topic" to switched.first.titleEn,
                "instruction" to "You have switched to a brand new story: \"${switched.second.titleEn}\" in ${switched.first.titleEn}. Introduce this new story enthusiastically in one short sentence and start narrating the opening scene right away!"
            )
        } else {
            mapOf("status" to "no_story_available")
        }
    }

    private fun handleReplayStoryTool(call: LiveToolCall): Map<String, Any> {
        val lesson = _lesson.value
        val title = lesson?.scenario?.titleEn ?: lesson?.topic?.titleEn ?: "the story"
        resetStoryProgress()
        return mapOf(
            "status" to "success",
            "instruction" to "Restart \"$title\" from the beginning. Introduce Chapter 1 in one short energetic sentence and start telling the story from the opening scene again."
        )
    }

    private fun handleSetStoryDuration(call: LiveToolCall): Map<String, Any> {
        val durationArg = call.args["duration"] as? String
        val parsedDuration = VoiceSettingsTools.parseStoryDuration(durationArg)
        if (parsedDuration == null) {
            LiveLog.w(TAG, "Could not parse story duration from arg: $durationArg")
            return mapOf("status" to "error", "message" to "Unknown story duration: $durationArg")
        }
        applyStoryDurationChange(parsedDuration)
        return mapOf(
            "status" to "success",
            "new_duration" to parsedDuration.name,
            "instruction" to when (parsedDuration) {
                StoryDuration.SHORT_5_MIN -> "Switched story duration to Short Story (~5 minutes). Confirm warmly in one short sentence, and tailor the narrative to wrap up excitingly in about 5 minutes."
                StoryDuration.FULL_10_TO_30_MIN -> "Switched story duration to Full Audio Drama (10 to 30 minutes). Confirm warmly in one short sentence, and expand the narrative with rich scenes and character dialogue."
            }
        )
    }

    private fun handleSetMultiVoice(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = booleanArg(enabledArg)
            ?: (enabledArg as? String)?.let(VoiceSettingsTools::parseMultiVoice)
            ?: return argumentError("enabled", enabledArg, "true, false")
        applyMultiVoiceChange(enabled)
        return mapOf(
            "status" to "success",
            "multi_voice_enabled" to enabled,
            "instruction" to if (enabled) {
                "Multi-voice audio drama mode is now enabled! Perform character dialogue using varied tones, pitches, and emotions. Confirm enthusiastically in one short sentence and continue."
            } else {
                "Multi-voice audio drama mode is now disabled. Continue the story in a single clear narrator voice. Confirm in one short sentence."
            }
        )
    }

    private fun handleResumeStoryTool(call: LiveToolCall): Map<String, Any> {
        LiveLog.i(TAG, "Learner requested resume story tool")
        resumeStory()
        return mapOf(
            "status" to "success",
            "instruction" to "Resuming the story from where you left off. Welcome the learner back in one short sentence and continue the narrative."
        )
    }

    /** Saves a setting in the background; a failed write is logged, never fatal. */
    private fun persist(what: String, write: suspend () -> Unit) {
        scope.launch {
            suspendRunCatching { write() }.onFailure { LiveLog.w(TAG, "Could not save $what", it) }
        }
    }

    private fun applyDifficultyChange(newLevel: DifficultyLevel) {
        val currentLesson = _lesson.value ?: return
        if (currentLesson.level == newLevel) return
        _lesson.value = currentLesson.copy(level = newLevel)
        learnerSettings = learnerSettings.copy(level = newLevel)
        persist("level") { settings.setLevel(newLevel) }
        LiveLog.i(TAG, "Switched difficulty level to ${newLevel.displayName} (${newLevel.cefr})")
    }

    private fun applyVietnameseHelpChange(enabled: Boolean) {
        if (learnerSettings.allowVietnameseHelp == enabled) return
        learnerSettings = learnerSettings.copy(allowVietnameseHelp = enabled)
        persist("Vietnamese help") { settings.setAllowVietnameseHelp(enabled) }
        LiveLog.i(TAG, "Switched allowVietnameseHelp to $enabled")
    }

    private fun applyAppLanguageChange(newLanguage: AppLanguage) {
        if (learnerSettings.appLanguage == newLanguage) return
        learnerSettings = learnerSettings.copy(appLanguage = newLanguage)
        persist("app language") { settings.setAppLanguage(newLanguage) }
        LiveLog.i(TAG, "Switched appLanguage to ${newLanguage.name} (${newLanguage.code})")
        if (learnerSettings.showTranslationSubtitle) {
            _drillTarget.value?.let { currentTarget ->
                setDrillTarget(currentTarget)
            }
        }
    }

    private fun applyStorytellingStyleChange(newStyle: StorytellingStyle) {
        if (learnerSettings.storytellingStyle == newStyle) return
        learnerSettings = learnerSettings.copy(storytellingStyle = newStyle)
        persist("storytelling style") { settings.setStorytellingStyle(newStyle) }
        LiveLog.i(TAG, "Switched storytellingStyle to ${newStyle.displayName}")
    }

    private fun applyPronunciationStrictnessChange(newStrictness: PronunciationStrictness) {
        if (learnerSettings.pronunciationStrictness == newStrictness) return
        learnerSettings = learnerSettings.copy(pronunciationStrictness = newStrictness)
        persist("pronunciation strictness") { settings.setPronunciationStrictness(newStrictness) }
        LiveLog.i(TAG, "Switched pronunciationStrictness to ${newStrictness.displayName}")
    }

    private fun applyBargeInChange(enabled: Boolean) {
        if (learnerSettings.allowBargeIn == enabled) return
        learnerSettings = learnerSettings.copy(allowBargeIn = enabled)
        persist("barge-in") { settings.setAllowBargeIn(enabled) }
        LiveLog.i(TAG, "Switched allowBargeIn to $enabled")
    }

    private fun applyVoiceChange(voice: AiVoice) {
        if (learnerSettings.voiceId == voice.id && !learnerSettings.randomVoice) return
        learnerSettings = learnerSettings.copy(voiceId = voice.id, randomVoice = false)
        persist("voice") {
            settings.setRandomVoice(false)
            settings.setVoice(voice)
        }
        LiveLog.i(TAG, "Switched preferred voice to ${voice.name} (${voice.id})")
    }

    private fun applyRandomVoiceChange(enabled: Boolean) {
        if (learnerSettings.randomVoice == enabled) return
        learnerSettings = learnerSettings.copy(randomVoice = enabled)
        persist("random voice") { settings.setRandomVoice(enabled) }
        LiveLog.i(TAG, "Switched randomVoice to $enabled")
    }

    private fun applyStoryDurationChange(newDuration: StoryDuration) {
        if (learnerSettings.storyDuration == newDuration) return
        learnerSettings = learnerSettings.copy(storyDuration = newDuration)
        persist("story duration") { settings.setStoryDuration(newDuration) }
        LiveLog.i(TAG, "Switched storyDuration to ${newDuration.displayName}")
    }

    private fun applyMultiVoiceChange(enabled: Boolean) {
        if (learnerSettings.multiVoiceStorytelling == enabled) return
        learnerSettings = learnerSettings.copy(multiVoiceStorytelling = enabled)
        persist("multi-voice storytelling") { settings.setMultiVoiceStorytelling(enabled) }
        LiveLog.i(TAG, "Switched multiVoiceStorytelling to $enabled")
    }

    /**
     * Skips the current story and switches to a recommended one without disconnecting.
     * Can be invoked via voice tool, voice command fallback, steering wheel button, or phone UI.
     */
    open fun nextStory(): Boolean {
        val currentLesson = _lesson.value ?: return false
        if (currentLesson.mode != SessionMode.STORY_LISTENING) return next()

        val result = performNextStory() ?: return false
        scope.launch {
            suspendRunCatching {
                liveClient.sendText(
                    "System: The learner skipped the previous story. Switch immediately to the new story: \"${result.second.titleEn}\" (Topic: ${result.first.titleEn}). " +
                        "Introduce it enthusiastically in one short sentence and begin the opening scene!"
                )
            }.onFailure { LiveLog.w(TAG, "Could not send next story command to Live client", it) }
        }
        return true
    }

    private fun performNextStory(): Pair<Topic, Scenario>? {
        val currentLesson = _lesson.value ?: return null
        val currentScenarioId = currentLesson.scenario?.id
        val (newTopic, newScenario) = storyRecommender.recommendStory(
            recentSessions = cachedStorySessions,
            excludeScenarioIds = setOfNotNull(currentScenarioId),
            learnerLevel = currentLesson.level
        )
        _lesson.value = currentLesson.copy(
            topic = newTopic,
            scenario = newScenario
        )
        resetStoryProgress()
        return newTopic to newScenario
    }

    /**
     * Restarts the current story from chapter 1.
     */
    fun replayStory(): Boolean {
        val currentLesson = _lesson.value ?: return false
        val title = currentLesson.scenario?.titleEn ?: currentLesson.topic.titleEn
        resetStoryProgress()
        scope.launch {
            suspendRunCatching {
                liveClient.sendText(
                    "System: The learner requested to replay the story from the beginning. " +
                        "Re-introduce \"$title\" in one short sentence and start narrating Chapter 1 again."
                )
            }.onFailure { LiveLog.w(TAG, "Could not send replay story command to Live client", it) }
        }
        return true
    }

    /**
     * Resumes an unfinished story from a previous session.
     * Can be invoked via voice tool, voice command fallback, Android Auto media action, or phone UI.
     */
    fun resumeStory(): Boolean {
        scope.launch {
            val unfinished = suspendRunCatching { sessionStore.latestUnfinishedStorySession() }.getOrNull()
            if (unfinished != null) {
                val topic = topicManager.getTopicById(unfinished.topicId)
                    ?: topicManager.getStoryTopics().first()
                val scenario = unfinished.scenarioId?.let { topicManager.getScenario(it)?.second }
                    ?: topic.scenarios.firstOrNull()
                val title = scenario?.titleEn ?: topic.titleEn

                val lastTurns = unfinished.transcript.takeLast(4)
                val summaryRecap = unfinished.summary?.encouragement ?: ""
                val recapText = if (lastTurns.isNotEmpty()) {
                    lastTurns.joinToString("\n") { "${it.speaker}: ${it.text}" }
                } else summaryRecap

                val request = LessonRequest(
                    topicId = topic.id,
                    scenarioId = scenario?.id,
                    mode = SessionMode.STORY_LISTENING,
                    resumeStoryContext = recapText.takeIf { it.isNotBlank() },
                    resumeStoryTitle = title
                )
                start(request)
            } else {
                val (topic, scenario) = storyRecommender.recommendStory(cachedStorySessions, learnerLevel = learnerSettings.level)
                start(LessonRequest(topicId = topic.id, scenarioId = scenario.id, mode = SessionMode.STORY_LISTENING))
            }
        }
        return true
    }

    private fun handleSkipDrillSentence(call: LiveToolCall): Map<String, Any> {
        val reason = call.args["reason"] as? String ?: "skipped"
        LiveLog.i(TAG, "Live tool skip_drill_sentence invoked (reason=$reason)")
        skipDrillSentence()
        val topic = _lesson.value?.topic?.titleEn ?: "daily situations"
        return mapOf(
            "status" to "success",
            "instruction" to "The learner skipped this sentence. Confirm in one short phrase and immediately introduce a fresh, brand new sentence related to $topic that has not been used yet in this session. Never repeat previous sentences. You MUST ALWAYS start the next sentence with: \"Repeat after me: <sentence>\"."
        )
    }

    private fun handleRepeatDrillSentence(call: LiveToolCall): Map<String, Any> {
        LiveLog.i(TAG, "Live tool repeat_drill_sentence invoked")
        val target = _drillTarget.value
        val instruction = if (target.isNullOrBlank()) {
            "The learner requested to hear the sentence again. Please repeat the sentence clearly and slowly. " +
                "You MUST start with: \"Repeat after me: <sentence>\"."
        } else {
            "The learner requested to hear the target sentence again. " +
                "Say clearly: \"Repeat after me: $target\"."
        }
        return mapOf(
            "status" to "success",
            "instruction" to instruction
        )
    }

    /**
     * Advances to the next item in the lesson:
     * - In STORY_LISTENING: skips to the next story.
     * - In REPEAT_AFTER_ME: skips to the next practice sentence.
     * - In other modes: prompts the AI to move to the next question or topic.
     */
    open fun next(): Boolean {
        val currentLesson = _lesson.value ?: return false
        return when (currentLesson.mode) {
            SessionMode.STORY_LISTENING -> nextStory()
            SessionMode.REPEAT_AFTER_ME -> {
                skipDrillSentence()
                val topic = currentLesson.topic.titleEn
                scope.launch {
                    suspendRunCatching {
                        liveClient.sendText(
                            "System: The learner skipped to the next sentence. " +
                                "Confirm in one short phrase, then give a fresh, brand new sentence related to $topic that has not been used yet in this session. " +
                                "Never repeat previous sentences. You MUST ALWAYS start the new sentence with: \"Repeat after me: <sentence>\"."
                        )
                    }.onFailure { LiveLog.w(TAG, "Could not send skip drill sentence to Live client", it) }
                }
                true
            }
            else -> {
                scope.launch {
                    suspendRunCatching {
                        liveClient.sendText(
                            "System: The learner clicked 'Next'. Wrap up this thought and move on to the next question or topic."
                        )
                    }.onFailure { LiveLog.w(TAG, "Could not send next command to Live client", it) }
                }
                true
            }
        }
    }

    /**
     * Repeats the current item in the lesson:
     * - In STORY_LISTENING: replays the story from the beginning.
     * - In REPEAT_AFTER_ME: repeats the current drill target sentence clearly.
     * - In other modes: asks AI to repeat what it just said.
     */
    open fun repeat(): Boolean {
        val currentLesson = _lesson.value ?: return false
        return when (currentLesson.mode) {
            SessionMode.STORY_LISTENING -> replayStory()
            SessionMode.REPEAT_AFTER_ME -> {
                val target = _drillTarget.value
                confirmOutLoud("Đọc lại câu")
                scope.launch {
                    suspendRunCatching {
                        if (target.isNullOrBlank()) {
                            liveClient.sendText(
                                "System: The learner wants to hear the sentence again. Please repeat the sentence clearly and slowly. " +
                                    "You MUST start with: \"Repeat after me: <sentence>\"."
                            )
                        } else {
                            liveClient.sendText(
                                "System: The learner wants to hear the sentence again. Say clearly: \"Repeat after me: $target\"."
                            )
                        }
                    }.onFailure { LiveLog.w(TAG, "Could not send repeat command to Live client", it) }
                }
                true
            }
            else -> {
                scope.launch {
                    suspendRunCatching {
                        liveClient.sendText(
                            "System: The learner asked you to repeat what you just said. Please repeat your last sentence clearly."
                        )
                    }.onFailure { LiveLog.w(TAG, "Could not send repeat command to Live client", it) }
                }
                true
            }
        }
    }

    /**
     * Skips the current sentence in the pronunciation drill and clears the displayed target
     * until the AI delivers the next sentence.
     */
    fun skipDrillSentence(): Boolean {
        if (_lesson.value?.mode != SessionMode.REPEAT_AFTER_ME) return false
        setDrillTarget(null)
        awaitingNewDrillTarget = true
        confirmOutLoud("Chuyển câu tiếp theo")
        return true
    }

    /**
     * Called by the model after each "repeat after me" attempt. The verdict combines the model's
     * judgement with a word-by-word transcript check, and the model is told to follow it, so a wrong
     * attempt is never praised. Lesson state is read and written on the engine thread; the optional
     * Azure call blocks, so it runs on the caller's (background) thread in between.
     */
    private suspend fun gradeAttempt(sessionId: String, call: LiveToolCall): Map<String, Any> {
        val input = withContext(engineDispatcher) {
            if (_lesson.value?.sessionId != sessionId || !_state.value.isInLesson) return@withContext null
            val target = (call.args["target_sentence"] as? String)?.takeIf { it.isNotBlank() } ?: _drillTarget.value.orEmpty()
            val key = PronunciationDrill.key(target)
            val attemptNumber = (attemptCounts.getOrElse(key) { 0 } + 1).also { attemptCounts[key] = it }
            GradeInput(target, attemptNumber, learnerSettings, _lesson.value?.level ?: learnerSettings.level)
        } ?: return STALE_TOOL_RESPONSE

        val (azure, azureError) = assessWithAzure(input.target, call.learnerAudio, input.settings)

        return withContext(engineDispatcher) {
            if (_lesson.value?.sessionId != sessionId) return@withContext STALE_TOOL_RESPONSE
            val rawProblemWords = call.args["problem_words"]
            val parsedProblemWords = when (rawProblemWords) {
                is List<*> -> rawProblemWords.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
                is String -> rawProblemWords.split(',', ';').map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
                else -> emptyList()
            }
            val attempt = PronunciationGrader.grade(
                target = input.target,
                heard = call.learnerUtterance,
                modelSaidCorrect = (call.args["verdict"] as? String)?.trim()?.equals("correct", ignoreCase = true) == true,
                modelProblemWords = parsedProblemWords,
                modelNotes = call.args["problem_notes"] as? String ?: "",
                attemptNumber = input.attemptNumber,
                timestamp = clock(),
                azure = azure,
                azureError = azureError,
                strictness = input.settings.pronunciationStrictness,
                level = input.level
            )
            if (attempt.heard.isNotBlank()) _pronunciationAttempts.value = _pronunciationAttempts.value + attempt
            val isDoneWithSentence = attempt.passed || input.attemptNumber >= PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE
            awaitingNewDrillTarget = isDoneWithSentence
            val shown = _drillTarget.value
            if (shown.isNullOrBlank() || PronunciationDrill.key(shown) == PronunciationDrill.key(input.target)) {
                setDrillTarget(input.target)
            }
            LiveLog.d(
                TAG,
                "Attempt ${input.attemptNumber} at '${input.target}': passed=${attempt.passed} " +
                    "strictness=${input.settings.pronunciationStrictness} accuracy=${attempt.accuracyPercent}% " +
                    "azure=${azure?.pronunciationScore ?: azureError ?: "off"}"
            )
            PronunciationDrill.toolResponse(attempt)
        }
    }

    private class GradeInput(
        val target: String,
        val attemptNumber: Int,
        val settings: LearnerSettings,
        val level: DifficultyLevel
    )

    /**
     * Third, independent judge when switched on in settings. Failures never block the drill:
     * the attempt is then graded by the other two judges and the reason is shown to the learner.
     */
    private fun assessWithAzure(target: String, audio: ByteArray, prefs: LearnerSettings): Pair<AzureAssessment?, String?> {
        if (!prefs.azureEnabled) return null to null
        val config = AzureSpeechConfig(prefs.azureRegion, prefs.azureKey)
        if (!config.isComplete) return null to "Chưa nhập Region/Key Azure trong Cài đặt"
        if (audio.size < MIN_AZURE_AUDIO_BYTES) return null to null
        return runCatching { pronunciationAssessor.assess(target, audio, config) }
            .fold(
                onSuccess = { it to null },
                onFailure = {
                    LiveLog.w(TAG, "Azure assessment failed", it)
                    null to "Azure không phản hồi: ${it.message ?: it::class.simpleName}"
                }
            )
    }

    private fun markActive() {
        activeSince = clock()
        lastActivityAt = clock()
        lessonStartedAt = clock()
        _state.value = ConversationState.ACTIVE
        if (watchdogJob?.isActive != true) watchdogJob = scope.launch { silenceWatchdog() }
    }

    private fun stopActiveClock() {
        if (_state.value == ConversationState.ACTIVE) accumulatedActiveMs += clock() - activeSince
    }

    private fun failStart(error: EngineError): Boolean {
        _error.value = error
        _state.value = ConversationState.ERROR
        return false
    }

    /**
     * Records why the lesson should be paused and pauses it if it is running. A reason given while the
     * lesson is connecting, reconnecting or offline is kept, so the lesson comes back paused instead of
     * talking over a phone call.
     */
    private suspend fun pauseLocked(reason: PauseReason) {
        val state = _state.value
        if (!state.isInLesson) return
        LiveLog.i(TAG, "Pause requested ($reason) in state $state")
        pauseReasons += reason
        stopOfflineDrillLocked()
        // Keep the focus request after a transient loss so the system tells us when we may resume.
        if (reason != PauseReason.FOCUS_TRANSIENT) audioFocus.abandon()
        if (state != ConversationState.ACTIVE) return
        // A navigation prompt or call cuts the AI off mid-sentence (its queued audio is dropped):
        // remember that, so it picks up again on resume even after a short pause.
        if (_activeSpeaker.value == Speaker.AI) resumeNeedsKickoff = true
        stopActiveClock()
        suspendRunCatching {
            // A prompt is over in seconds: a Bluetooth headset or car stays on the call meanwhile.
            if (reason == PauseReason.FOCUS_TRANSIENT) liveClient.pauseAudioBriefly() else liveClient.pauseAudio()
        }.onFailure { LiveLog.w(TAG, "Could not pause audio", it) }
        enterPausedLocked()
    }

    /** Puts an open connection into the paused state (audio already stopped). */
    private fun enterPausedLocked() {
        _activeSpeaker.value = null
        setAiThinking(false)
        clearUtterance()
        pausedAt = clock()
        _state.value = ConversationState.PAUSED
        // An idle Live connection still counts toward its lifetime; close it after a long pause.
        pauseCloseJob?.cancel()
        pauseCloseJob = scope.launch {
            delay(CLOSE_AFTER_PAUSE_MS)
            mutex.withLock {
                if (_state.value == ConversationState.PAUSED) suspendRunCatching { liveClient.disconnect() }
            }
        }
    }

    private suspend fun resumeLocked(): Boolean {
        LiveLog.i(TAG, "Resume requested in state ${_state.value} (paused for $pauseReasons)")
        if (_state.value == ConversationState.WAITING_FOR_NETWORK) {
            if (!audioFocus.request()) {
                _error.value = EngineError.AudioFocusDenied
                return false
            }
            pauseReasons.clear()
            startOfflineDrillLocked()
            return true
        }
        if (_state.value != ConversationState.PAUSED) return _state.value == ConversationState.ACTIVE
        val lesson = _lesson.value ?: return false
        pauseCloseJob?.cancel()
        if (!audioFocus.request()) {
            _error.value = EngineError.AudioFocusDenied
            return false
        }
        pauseReasons.clear()
        unansweredNudges = 0
        return try {
            var welcomeBack: String? = null
            if (liveClient.isConnected) {
                liveClient.resumeAudio()
                if (resumeNeedsKickoff || clock() - pausedAt > SAY_WELCOME_BACK_AFTER_MS) {
                    welcomeBack = PromptTemplates.resumeMessage(lesson, _drillTarget.value)
                }
            } else {
                learnerSettings = suspendRunCatching { settings.snapshot() }.getOrDefault(learnerSettings)
                connectLive(lesson, recap = true)
                welcomeBack = PromptTemplates.resumeMessage(lesson, _drillTarget.value)
            }
            welcomeBack?.let { liveClient.sendText(it) }
            resumeNeedsKickoff = false
            markActive()
            welcomeBack?.let { awaitAiReply { it } }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LiveLog.e(TAG, "Could not resume", e)
            _error.value = mapError(e)
            false
        }
    }

    /**
     * Starts reconnecting in the background unless that is already happening. The attempts run
     * outside the engine lock so Pause, Stop and a new lesson are never stuck behind them.
     * Call with the mutex held.
     */
    private fun requestReconnectLocked(reason: ReconnectReason) {
        if (reconnectJob?.isActive == true) return
        val lesson = _lesson.value ?: return
        if (_state.value != ConversationState.ACTIVE && _state.value != ConversationState.WAITING_FOR_NETWORK) return
        LiveLog.i(TAG, "Reconnecting ($reason) from ${liveClient.connectedModel}")
        reconnectReason = reason
        stopActiveClock()
        _state.value = ConversationState.RECONNECTING
        _activeSpeaker.value = null
        setAiThinking(false)
        clearUtterance()
        val sessionId = lesson.sessionId
        reconnectJob = scope.launch { reconnectLoop(sessionId) }
    }

    private suspend fun reconnectLoop(sessionId: String) {
        for (attempt in 0 until MAX_RECONNECT_ATTEMPTS) {
            val lesson = mutex.withLock {
                if (_lesson.value?.sessionId != sessionId || _state.value != ConversationState.RECONNECTING) return
                if (!connectivity.isOnlineNow()) {
                    goOffline()
                    return
                }
                _lesson.value ?: return
            }
            val connected = try {
                (withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connectLive(lesson, recap = true) } != null)
                    .also { if (!it) LiveLog.w(TAG, "Reconnect attempt ${attempt + 1} timed out after $CONNECT_TIMEOUT_MS ms") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveLog.w(TAG, "Reconnect attempt ${attempt + 1} failed", e)
                false
            }
            mutex.withLock {
                if (_lesson.value?.sessionId != sessionId || _state.value != ConversationState.RECONNECTING) {
                    // Went offline or the state changed while connecting: drop this connection.
                    if (connected && _lesson.value?.sessionId == sessionId) suspendRunCatching { liveClient.disconnect() }
                    return
                }
                if (connected) {
                    onReconnectedLocked(lesson)
                    return
                }
            }
            if (attempt < MAX_RECONNECT_ATTEMPTS - 1) delay(RECONNECT_BACKOFF_MS * (attempt + 1))
        }
        mutex.withLock {
            if (_lesson.value?.sessionId != sessionId || _state.value != ConversationState.RECONNECTING) return
            reconnectJob = null
            LiveLog.e(TAG, "Giving up after $MAX_RECONNECT_ATTEMPTS reconnect attempts")
            _error.value = EngineError.ConnectionFailed(null)
            announcer.announce(ANNOUNCE_GIVE_UP)
            endLocked()
        }
    }

    private suspend fun onReconnectedLocked(lesson: ActiveLesson) {
        if (pauseReasons.isNotEmpty()) {
            // Paused while offline (a phone call, the learner's pause...): stay quiet until resumed.
            LiveLog.i(TAG, "Reconnected while paused for $pauseReasons; staying paused")
            suspendRunCatching { liveClient.pauseAudio() }
            resumeNeedsKickoff = true
            enterPausedLocked()
            return
        }
        val resumed = liveClient.lastConnectResumed
        val prompt = reconnectPrompt(lesson, resumed, reconnectReason)
        LiveLog.i(TAG, "Reconnected after $reconnectReason (${if (resumed) "conversation resumed" else "new conversation with recap"}); " +
            (if (prompt == null) "the learner speaks next" else "asking the AI to continue"))
        if (prompt != null) {
            suspendRunCatching { liveClient.sendText(prompt) }
                .onFailure { LiveLog.w(TAG, "Could not send the resume message after reconnecting", it) }
        }
        markActive()
        if (prompt != null) awaitAiReply { prompt }
    }

    /**
     * What the AI is asked to say on a new connection, or null when the learner simply speaks next.
     * Always: the opening if the AI never spoke; an answer if the learner was left unanswered (a
     * drill re-reads its sentence instead, the attempt's audio is gone with the old connection).
     * When the server kept the conversation (resumed), a planned switch after the AI finished
     * needs nothing at all and anything else just "continue"; otherwise "where were we?" with a recap.
     */
    private fun reconnectPrompt(lesson: ActiveLesson, resumed: Boolean, reason: ReconnectReason): String? {
        if (_transcript.value.none { it.speaker == Speaker.AI }) return PromptTemplates.kickoffMessage(lesson)
        val unanswered = unansweredLearnerText()
        val drillOrStory = lesson.mode == SessionMode.REPEAT_AFTER_ME || lesson.mode == SessionMode.STORY_LISTENING
        return when {
            unanswered != null && !drillOrStory -> PromptTemplates.unansweredTurnMessage(lesson.mode, unanswered)
            lesson.mode == SessionMode.REPEAT_AFTER_ME && unanswered != null -> PromptTemplates.resumeMessage(lesson, _drillTarget.value)
            resumed && reason == ReconnectReason.GO_AWAY && unanswered == null -> null
            resumed -> PromptTemplates.RESUMED_CONTINUE_MESSAGE
            else -> PromptTemplates.resumeMessage(lesson, _drillTarget.value)
        }
    }

    private suspend fun goOffline() {
        stopActiveClock()
        suspendRunCatching { liveClient.disconnect() }
        _activeSpeaker.value = null
        setAiThinking(false)
        clearUtterance()
        _state.value = ConversationState.WAITING_FOR_NETWORK
        if (!startOfflineDrillLocked()) announcer.announce(ANNOUNCE_OFFLINE)
    }

    /**
     * Starts the offline practice while the lesson waits for the network. Returns false when it is
     * turned off, paused, or already running. Call with the mutex held.
     */
    private fun startOfflineDrillLocked(): Boolean {
        val lesson = _lesson.value ?: return false
        if (offlineJob?.isActive == true) return true
        if (!offlineSpeech.isAvailable || !learnerSettings.offlinePracticeEnabled || pauseReasons.isNotEmpty()) return false
        if (_state.value != ConversationState.WAITING_FOR_NETWORK) return false
        val sentences = offlineSentences(lesson)
        if (sentences.isEmpty()) return false
        val coach = OfflineDrillCoach(offlineSpeech, OfflineDrillEvents(lesson.sessionId)) { clock() }
        val options = OfflineDrillCoach.Options(
            level = lesson.level,
            strictness = learnerSettings.pronunciationStrictness,
            vietnamese = learnerSettings.appLanguage != AppLanguage.ENGLISH
        )
        offlineStartedAt = clock()
        offlineEndedAt = null
        _offlinePractice.value = true
        // Started only once assigned, so its first events already count as the current practice.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                coach.run(sentences, options)
            } finally {
                offlineEndedAt = clock()
                offlineSpeech.release()
            }
        }
        offlineJob = job
        job.start()
        LiveLog.i(TAG, "Offline practice started with ${sentences.size} sentences")
        return true
    }

    /** Stops the offline practice (network back, pause, end) and counts its time as practice. */
    private fun stopOfflineDrillLocked() {
        val job = offlineJob ?: return
        offlineJob = null
        job.cancel()
        accumulatedActiveMs += ((offlineEndedAt ?: clock()) - offlineStartedAt).coerceAtLeast(0L)
        _offlinePractice.value = false
        // Outside a drill lesson the practice card belongs to the offline practice only.
        if (_lesson.value?.mode != SessionMode.REPEAT_AFTER_ME) setDrillTarget(null)
    }

    /** Curated sentences for the lesson's level and topic; words the learner finds hard come first. */
    private fun offlineSentences(lesson: ActiveLesson): List<String> {
        val short = learnerSettings.drillSentenceLength == DrillSentenceLength.ALWAYS_SHORT ||
            (learnerSettings.drillSentenceLength == DrillSentenceLength.AUTO_ON_CAR && _isCarConnected.value)
        val pool = drillSentenceManager.getSentences(
            category = learnerSettings.drillCategory,
            level = lesson.level,
            topicId = lesson.topic.id,
            isShortOnly = short
        ).map { it.text }.shuffled()
        val weak = lesson.learnerMemory?.weakWords.orEmpty().toSet()
        val ordered = pool.sortedByDescending { sentence -> TopicManager.normalize(sentence).split(' ').count { it in weak } }
        // A drill lesson goes on with the sentence it was practising.
        val current = _drillTarget.value?.takeIf { lesson.mode == SessionMode.REPEAT_AFTER_ME && it.isNotBlank() }
        return listOfNotNull(current) + ordered.filter { it != current }
    }

    /** Connects the offline coach to this lesson; events of a lesson that has ended are dropped. */
    private inner class OfflineDrillEvents(private val sessionId: String) : OfflineDrillListener {
        private fun isCurrent() = _lesson.value?.sessionId == sessionId && offlineJob != null

        override fun onTarget(target: String?) {
            if (isCurrent()) setDrillTarget(target)
        }

        override fun onTurn(speaker: Speaker, text: String) {
            if (!isCurrent()) return
            _transcript.value = accumulator.append(speaker, text)
            lastActivityAt = clock()
        }

        override fun onAttempt(attempt: PronunciationAttempt) {
            if (isCurrent()) _pronunciationAttempts.value = _pronunciationAttempts.value + attempt
        }

        override fun onEndRequested() {
            if (isCurrent()) scope.launch { end() }
        }
    }

    private suspend fun endLocked(): String? {
        val lesson = _lesson.value ?: run {
            if (_state.value == ConversationState.ERROR) _state.value = ConversationState.IDLE
            return null
        }
        stopActiveClock()
        LiveLog.i(
            TAG,
            "Ending lesson ${lesson.sessionId.take(8)} (${lesson.mode}) after ${activeDurationMs() / 1000} s active, " +
                "${_transcript.value.size} turns, last model ${liveClient.connectedModel}"
        )
        stopOfflineDrillLocked()
        _state.value = ConversationState.ENDING
        _activeSpeaker.value = null
        setAiThinking(false)
        pauseCloseJob?.cancel()
        draftJob?.cancel()
        pendingFallbackJob?.cancel()
        clearUtterance()
        cancelUnlessCurrent(watchdogJob)
        cancelUnlessCurrent(goAwayJob)
        cancelUnlessCurrent(reconnectJob)
        reconnectJob = null
        pauseReasons.clear()
        suspendRunCatching { liveClient.disconnect() }.onFailure { LiveLog.w(TAG, "Could not disconnect", it) }
        audioFocus.abandon()

        val turns = accumulator.snapshot()
        val hasLearnerSpeech = turns.any { it.speaker == Speaker.USER }
        val hasStoryContent = lesson.mode == SessionMode.STORY_LISTENING && turns.any { it.speaker == Speaker.AI }
        val storyCompleted = storyFinished
        _lesson.value = null
        if (!hasLearnerSpeech && !hasStoryContent) {
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
            LiveLog.e(TAG, "Could not save session", e)
        }
        _summarizing.value = _summarizing.value + lesson.sessionId
        _state.value = ConversationState.ENDED
        _endedSessions.tryEmit(lesson.sessionId)
        scope.launch { summarizeAndStore(lesson, draft, storyCompleted) }
        return lesson.sessionId
    }

    private suspend fun summarizeAndStore(lesson: ActiveLesson, draft: CompletedSession, storyCompleted: Boolean) {
        try {
            val summary: SessionSummary = try {
                summaryGenerator.summarize(lesson, draft.transcript, draft.pronunciationAttempts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveLog.w(TAG, "Summary generation failed", e)
                SummaryParser.createLocalFallbackSummary(lesson, draft.transcript, draft.pronunciationAttempts)
            }
            val graded = if (lesson.mode == SessionMode.REPEAT_AFTER_ME) {
                summary.copy(pronunciationScore = PronunciationDrill.score(draft.pronunciationAttempts))
            } else {
                summary
            }
            // A story counts as finished only when the AI actually told its ending (end marker), not
            // when someone happened to say "the end" or "moral".
            val completedStatus = if (lesson.mode == SessionMode.STORY_LISTENING) storyCompleted else true
            val reviewedIds = lesson.reviewMistakes.mapTo(mutableSetOf()) { it.id }
            val calculatedMetrics = com.speakdrive.ai.evaluation.FluencyMetricsCalculator.calculate(
                transcript = draft.transcript,
                activeDurationMs = draft.activeDurationMs
            )
            val stored = graded.copy(
                // The learner may have turned memory off during the lesson.
                learnerFacts = if (lesson.learnerMemory != null && learnerSettings.rememberLearner) graded.learnerFacts else emptyList(),
                mistakeResults = graded.mistakeResults.filter { it.mistakeId in reviewedIds }.distinctBy { it.mistakeId },
                fluencyMetrics = calculatedMetrics
            )
            sessionStore.saveSession(draft.copy(summary = stored, isCompleted = completedStatus))
            if (lesson.mode == SessionMode.VOCAB_REVIEW) sessionStore.markWordsReviewed(draft.reviewedWords)
            if (stored.mistakeResults.isNotEmpty()) sessionStore.recordMistakeReviews(stored.mistakeResults)
            graded.levelRecommendation
                ?.takeIf { it.direction != LevelAdjustmentDirection.KEEP && learnerSettings.adaptiveLevelRecommendation }
                ?.let { pendingLevelRecommendation = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LiveLog.e(TAG, "Could not store the summary", e)
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
        isCompleted = completed,
        pronunciationAttempts = _pronunciationAttempts.value
    )

    /** Saves the transcript every so often so a killed app does not lose the lesson. */
    private fun startDraftSaver() {
        draftJob?.cancel()
        draftJob = scope.launch {
            var lastSavedTurns = -1
            while (isActive) {
                delay(DRAFT_INTERVAL_MS)
                val lesson = _lesson.value ?: return@launch
                val turns = accumulator.snapshot()
                val shouldSave = turns.any { it.speaker == Speaker.USER } ||
                    (lesson.mode == SessionMode.STORY_LISTENING && turns.any { it.speaker == Speaker.AI })
                // Nothing new (e.g. while paused): no need to rewrite the same draft.
                val changed = turns.size != lastSavedTurns || _state.value == ConversationState.ACTIVE
                if (shouldSave && changed) {
                    suspendRunCatching { sessionStore.saveSession(buildRecord(lesson, turns, null, completed = false)) }
                        .onSuccess { lastSavedTurns = turns.size }
                        .onFailure { LiveLog.w(TAG, "Draft save failed", it) }
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
            LiveEvent.Interrupted -> {
                speakerResetJob?.cancel()
                _activeSpeaker.value = null
                setAiThinking(false)
            }
            LiveEvent.GoAway -> {
                val generation = connectionGeneration
                goAwayJob?.cancel()
                goAwayJob = scope.launch {
                    // Let the AI finish its sentence before swapping connections.
                    withTimeoutOrNull(GO_AWAY_WAIT_MS) { _activeSpeaker.first { it == null } }
                    mutex.withLock {
                        // A Disconnected event may already have replaced this connection.
                        if (generation == connectionGeneration && _state.value == ConversationState.ACTIVE) {
                            requestReconnectLocked(ReconnectReason.GO_AWAY)
                        }
                    }
                }
            }
            LiveEvent.EndLessonRequested -> onEndLessonRequested()
            is LiveEvent.Disconnected -> {
                val generation = connectionGeneration
                scope.launch {
                    mutex.withLock {
                        if (generation != connectionGeneration || _state.value != ConversationState.ACTIVE) return@withLock
                        LiveLog.w(TAG, "Connection lost: ${event.cause?.message ?: "closed"}")
                        if (connectivity.isOnlineNow()) requestReconnectLocked(ReconnectReason.DROPPED) else goOffline()
                    }
                }
            }
        }
    }

    /**
     * The model asked to end the lesson. Ends exactly this lesson after the goodbye has played, and
     * ignores the request when the learner was only repeating a drill sentence such as
     * "Let's call it a day." or "Stop the car here.".
     */
    private fun onEndLessonRequested() {
        val lesson = _lesson.value ?: return
        val target = _drillTarget.value
        val heard = currentUtteranceText()
        if (lesson.mode == SessionMode.REPEAT_AFTER_ME && !target.isNullOrBlank() && heard.isNotBlank() &&
            PronunciationGrader.similarity(target, heard) >= DRILL_ECHO_SIMILARITY
        ) {
            LiveLog.i(TAG, "Ignoring end-lesson request: the learner was repeating the drill sentence")
            return
        }
        val sessionId = lesson.sessionId
        endRequestJob?.cancel()
        endRequestJob = scope.launch {
            delay(GOODBYE_GRACE_MS) // let the spoken goodbye play out
            cancelReconnect()
            mutex.withLock { if (_lesson.value?.sessionId == sessionId) endLocked() }
        }
    }

    private fun onTranscript(speaker: Speaker, text: String) {
        if (_lesson.value == null) return
        _transcript.value = accumulator.append(speaker, text)
        if (speaker == Speaker.AI) {
            setAiThinking(false)
            stallReconnects = 0
            lastAiTranscriptAt = clock()
            // The AI answering means the learner has finished: judge what they said now.
            if (utteranceBuffer.isNotEmpty()) finalizeUtterance(aiStarted = true)
            _transcript.value.lastOrNull()?.let { turn ->
                if (_lesson.value?.mode == SessionMode.REPEAT_AFTER_ME) {
                    val current = _drillTarget.value
                    val newTarget = PronunciationDrill.extractTarget(turn.text)
                    if (newTarget != null) {
                        val isExtension = current != null &&
                            newTarget.length > current.length &&
                            newTarget.contains(current.trimEnd('.', '!', '?', ',', ' ', '"', '”'), ignoreCase = true)
                        val explicitCue = turn.text.contains("repeat after me", ignoreCase = true)
                        val canUpdate = awaitingNewDrillTarget || current == null || isExtension || explicitCue
                        if (canUpdate) {
                            setDrillTarget(newTarget)
                        }
                    }
                }
                if (!storyFinished && _lesson.value?.mode == SessionMode.STORY_LISTENING &&
                    turn.text.contains(STORY_END_DETECTION_PHRASE, ignoreCase = true)
                ) {
                    LiveLog.i(TAG, "Story finished; podcast auto-continue stopped")
                    storyFinished = true
                }
            }
        }
        lastActivityAt = clock()
        if (speaker == Speaker.USER) {
            setAiThinking(false)
            unansweredNudges = 0
            if (_drillTarget.value != null) {
                awaitingNewDrillTarget = false
            }
            if (utteranceBuffer.isEmpty()) utteranceStartedAt = clock()
            val joined = TranscriptAccumulator.joinChunks(utteranceBuffer.toString(), text)
            utteranceBuffer.setLength(0)
            utteranceBuffer.append(joined)
        }
        _activeSpeaker.value = speaker
        speakerResetJob?.cancel()
        val heardAt = clock()
        speakerResetJob = scope.launch {
            delay(SPEAKER_IDLE_MS)
            _activeSpeaker.value = null
            if (speaker == Speaker.USER) {
                finalizeUtterance(aiStarted = false)
                // A tool call since then (a drill grade) is already the answer; its own wait is running.
                if (_state.value == ConversationState.ACTIVE && lastModelToolCallAt < heardAt) {
                    val mode = _lesson.value?.mode ?: SessionMode.FREE_TALK
                    awaitAiReply { PromptTemplates.unansweredTurnMessage(mode, unansweredLearnerText()) }
                }
            }
        }
    }

    private fun currentUtteranceText(): String =
        utteranceBuffer.toString().trim().ifEmpty { lastLearnerUtterance }

    private fun clearUtterance() {
        utteranceBuffer.setLength(0)
        utteranceStartedAt = 0L
        pendingFallbackJob?.cancel()
        pendingFallbackJob = null
    }

    /**
     * Client-side safety net for voice commands, run once per finished learner utterance (not on
     * every streaming chunk). The model is normally the one changing settings through its tools; if
     * it calls the tool for the same setting, the safety net steps aside. Otherwise, after a short
     * grace period, the recognised command is applied here.
     */
    private fun finalizeUtterance(aiStarted: Boolean) {
        val text = utteranceBuffer.toString().trim()
        val startedAt = utteranceStartedAt
        utteranceBuffer.setLength(0)
        utteranceStartedAt = 0L
        if (text.isEmpty()) return
        LiveLog.d(TAG, "Learner finished speaking (${if (aiStarted) "AI already answering" else "waiting for the AI"}): $text")
        lastLearnerUtterance = text
        val lesson = _lesson.value ?: return
        val context = CommandContext(
            state = _state.value,
            mode = lesson.mode,
            currentLevel = lesson.level,
            settings = learnerSettings,
            drillTarget = _drillTarget.value,
            lastAiText = _transcript.value.lastOrNull { it.speaker == Speaker.AI }?.text,
            hasPendingLevelRecommendation = pendingLevelRecommendation != null
        )
        val command = VoiceCommandRecognizer.recognize(text, context) ?: return
        val category = command.category
        if ((lastToolCallAt[category] ?: Long.MIN_VALUE) >= startedAt) return // the model already handled it
        val grace = when {
            // The model has no tool for it on this connection: nothing to wait for.
            !liveClient.settingsToolsActive || category !in toolCategories -> 0L
            aiStarted -> TOOL_GRACE_AFTER_AI_MS
            else -> TOOL_GRACE_MS
        }
        val sessionId = lesson.sessionId
        pendingFallbackJob?.cancel()
        pendingFallbackJob = scope.launch {
            delay(grace)
            if (_lesson.value?.sessionId != sessionId || _state.value != ConversationState.ACTIVE) return@launch
            if ((lastToolCallAt[category] ?: Long.MIN_VALUE) >= startedAt) return@launch
            LiveLog.i(TAG, "Voice command from the transcript: $command")
            lastFallbackAppliedAt[category] = clock()
            // If the AI is already answering, add a note instead of asking for a second answer.
            val aiAnswering = lastAiTranscriptAt >= startedAt
            applyVoiceCommand(command, aiAnswering)
        }
    }

    /** Sends a "System:" note about a change; announces [fallback] out loud if that fails. */
    private fun notifyModel(note: String, aiAnswering: Boolean, fallback: String? = null) {
        scope.launch {
            val sent = suspendRunCatching { if (aiAnswering) liveClient.sendContext(note) else liveClient.sendText(note) }
            if (sent.isFailure) {
                LiveLog.w(TAG, "Could not tell the model about a voice command", sent.exceptionOrNull())
                fallback?.let(announcer::announce)
            }
        }
    }

    /**
     * A change made from the transcript: the model did not call the tool, so it gets the tool's
     * instruction as a note and confirms the change itself, as it would after the tool call.
     */
    private fun notifyModelOf(toolResult: Map<String, Any>, aiAnswering: Boolean, fallback: String? = null) {
        val instruction = toolResult["instruction"] as? String ?: return
        notifyModel("System: The app applied the learner's spoken request. $instruction", aiAnswering, fallback)
    }

    /**
     * One voice per confirmation. While the AI tutor is in the conversation it confirms a change
     * itself (from the tool result, or from a "System:" note when the change came from the
     * transcript); the on-device voice would only talk over it, in another voice and language.
     * The app reads [text] itself only when the AI cannot answer (paused, offline, reconnecting).
     */
    private fun confirmOutLoud(text: String) {
        if (!aiCanSpeak()) announcer.announce(text)
    }

    private fun aiCanSpeak(): Boolean = _state.value == ConversationState.ACTIVE && liveClient.isConnected

    private fun applyVoiceCommand(command: VoiceCommand, aiAnswering: Boolean) {
        when (command) {
            is VoiceCommand.SwitchMode -> applySessionModeSwitch(command.mode).let { result ->
                // A real switch restarts the lesson and the app says so; only "already in it" needs the AI.
                if (result["status"] == "already_active") notifyModelOf(result, aiAnswering)
            }
            is VoiceCommand.SetLevel -> {
                val level = command.level
                applyDifficultyChange(level)
                val note = if (_lesson.value?.mode == SessionMode.STORY_LISTENING) {
                    "System: The learner changed story difficulty level to ${level.displayName} (${level.cefr}). " +
                        "Confirm warmly in one short sentence (e.g. \"Adapting the story to ${level.displayName} level!\") and immediately continue narrating the next events of the story adapted to this level."
                } else {
                    "System: The learner requested to switch difficulty level to ${level.displayName} (${level.cefr}). " +
                        "Confirm warmly in one short sentence and adapt your vocabulary and sentence length to this level."
                }
                notifyModel(note, aiAnswering, "Difficulty level set to ${level.displayName}")
            }
            is VoiceCommand.SetVietnameseHelp -> {
                val enabled = command.enabled
                applyVietnameseHelpChange(enabled)
                notifyModel(
                    "System: The learner requested to ${if (enabled) "enable" else "disable"} Vietnamese help. " +
                        "Confirm warmly in one short sentence and continue.",
                    aiAnswering,
                    if (enabled) "Vietnamese help enabled" else "English only mode enabled"
                )
            }
            is VoiceCommand.SetAppLanguage -> {
                val language = command.language
                applyAppLanguageChange(language)
                notifyModel(
                    "System: The learner requested to change the app interface and explanation language to ${language.englishName} (${language.nativeName}). " +
                        "Confirm warmly in one short sentence in ${language.englishName} or Vietnamese, and continue the conversation in English.",
                    aiAnswering,
                    "App language set to ${language.englishName}"
                )
            }
            is VoiceCommand.SetStorytellingStyle -> {
                val style = command.style
                applyStorytellingStyleChange(style)
                onStorytellingStyleChanged()
                notifyModel(PromptTemplates.storytellingStyleSwitchMessage(style), aiAnswering, "Storytelling mode set to ${style.displayName}")
            }
            is VoiceCommand.SetStrictness -> {
                val strictness = command.strictness
                applyPronunciationStrictnessChange(strictness)
                val currentLevel = _lesson.value?.level ?: learnerSettings.level
                val (note, feedback) = if (strictness == PronunciationStrictness.AUTO) {
                    val resolved = strictness.resolveForLevel(currentLevel)
                    ("System: The learner requested to change pronunciation strictness to Auto by Level (${resolved.displayName} for level ${currentLevel.displayName}). " +
                        "Confirm warmly in one short sentence and adapt your grading accordingly.") to
                        "Pronunciation strictness set to Auto by Level (${resolved.displayName})"
                } else {
                    ("System: The learner requested to change pronunciation strictness to ${strictness.displayName} (${strictness.labelVi}). " +
                        "Confirm warmly in one short sentence and adapt your grading accordingly.") to
                        "Pronunciation strictness set to ${strictness.displayName}"
                }
                notifyModel(note, aiAnswering, feedback)
            }
            is VoiceCommand.SetBargeIn -> {
                val enabled = command.enabled
                applyBargeInChange(enabled)
                notifyModel(
                    "System: The learner requested to ${if (enabled) "enable" else "disable"} barge-in / interruption mode. " +
                        "Confirm warmly in one short sentence and continue.",
                    aiAnswering,
                    if (enabled) "Barge in enabled" else "Barge in disabled"
                )
            }
            is VoiceCommand.SetRandomVoice -> {
                val enabled = command.enabled
                applyRandomVoiceChange(enabled)
                notifyModel(
                    "System: The learner requested to ${if (enabled) "enable" else "disable"} random AI voice selection for lessons. " +
                        "Confirm warmly in one short sentence (e.g. \"${if (enabled) "Đã bật chọn giọng ngẫu nhiên mỗi bài học" else "Đã tắt giọng ngẫu nhiên, giữ giọng hiện tại"}\" or in English) and continue.",
                    aiAnswering,
                    if (enabled) "Random AI voice enabled" else "Random AI voice disabled"
                )
            }
            is VoiceCommand.SetVoice -> {
                val voice = command.voice
                applyVoiceChange(voice)
                notifyModel(
                    "System: The learner requested to change preferred AI voice to ${voice.name} (${voice.labelVi}). " +
                        "Confirm warmly in one short sentence that the new voice will be used starting in the next session, and continue.",
                    aiAnswering,
                    "AI voice set to ${voice.name}"
                )
            }
            is VoiceCommand.SetStoryDuration -> {
                val duration = command.duration
                applyStoryDurationChange(duration)
                notifyModel(
                    "System: The learner requested to set story duration to ${duration.displayName}. " +
                        "Confirm in one short sentence and adjust the story scope.",
                    aiAnswering,
                    "Story duration set to ${duration.displayName}"
                )
            }
            is VoiceCommand.SetMultiVoice -> {
                val enabled = command.enabled
                applyMultiVoiceChange(enabled)
                notifyModel(
                    "System: The learner requested to ${if (enabled) "enable" else "disable"} multi-voice audio drama mode. " +
                        "Confirm warmly in one short sentence and continue.",
                    aiAnswering,
                    if (enabled) "Multi-voice drama enabled" else "Single voice enabled"
                )
            }
            VoiceCommand.ResumeStory -> resumeStory()
            VoiceCommand.NextStory -> nextStory()
            VoiceCommand.ReplayStory -> replayStory()
            VoiceCommand.RepeatDrillSentence -> repeat()
            VoiceCommand.SkipDrillSentence -> next()
            is VoiceCommand.SetDrillLength -> notifyModelOf(applyDrillSentenceLength(command.length), aiAnswering)
            is VoiceCommand.SetDrillCategory -> notifyModelOf(applyDrillCategory(command.drillCategory), aiAnswering)
            is VoiceCommand.AnswerLevelRecommendation ->
                notifyModelOf(levelRecommendationResult(command.accept, answerLevelRecommendation(command.accept)), aiAnswering)
            is VoiceCommand.SetAdaptiveLevel -> notifyModelOf(applyAdaptiveLevelChange(command.enabled), aiAnswering)
            is VoiceCommand.SetVolume -> notifyModelOf(applyAiVolume(command.volume), aiAnswering)
            is VoiceCommand.SetAutoPause -> notifyModelOf(applyAutoPauseWhenUnfocusedChange(command.enabled), aiAnswering)
            is VoiceCommand.SetLearnerMemory -> {
                val enabled = command.enabled
                applyLearnerMemoryChange(enabled)
                notifyModel(
                    if (enabled) {
                        "System: The learner turned memory on. Confirm warmly in one short sentence and continue."
                    } else {
                        "System: The learner turned memory off. Confirm in one short sentence, do not mention any personal details " +
                            "about the learner for the rest of this lesson, and continue."
                    },
                    aiAnswering
                )
            }
            is VoiceCommand.SetPracticeReminder ->
                notifyModelOf(applyPracticeReminderChange(command.enabled, command.minuteOfDay), aiAnswering)
            is VoiceCommand.SetOfflinePractice -> {
                applyOfflinePracticeChange(command.enabled)
                notifyModel(
                    "System: The learner turned offline practice ${if (command.enabled) "on" else "off"}. " +
                        "Confirm in one short sentence and continue.",
                    aiAnswering
                )
            }
            is VoiceCommand.SetAutoStartInCar -> notifyModelOf(applyAutoStartInCarChange(command.enabled), aiAnswering)
            is VoiceCommand.SetDailyGoal -> notifyModelOf(applyDailyGoalChange(command.minutes), aiAnswering)
            is VoiceCommand.SetAzureScoring -> notifyModelOf(applyAzureScoringChange(command.enabled), aiAnswering)
            is VoiceCommand.SetScreenAwake -> notifyModelOf(applyScreenAwakeChange(command.mode), aiAnswering)
            is VoiceCommand.SetStreakFreeze -> {
                applyStreakFreezeChange(command.enabled)
                notifyModel(
                    "System: The learner turned streak freezes ${if (command.enabled) "on" else "off"}. " +
                        "Confirm in one short sentence and continue.",
                    aiAnswering
                )
            }
            is VoiceCommand.SetSubtitles -> {
                val enabled = command.enabled
                applyTranslationSubtitlesChange(enabled)
                notifyModel(
                    "System: The learner requested to ${if (enabled) "enable" else "disable"} translation subtitles. " +
                        "Confirm warmly in one short sentence (e.g. \"${if (enabled) "Translation subtitles enabled!" else "Subtitles hidden, challenge mode on!"}\") and continue.",
                    aiAnswering
                )
            }
            is VoiceCommand.SetBetterPhrasing -> {
                val enabled = command.enabled
                applyBetterPhrasingChange(enabled)
                notifyModel(
                    "System: The learner turned natural phrasing upgrades ${if (enabled) "on" else "off"}. " +
                        "Confirm warmly in one short sentence and continue.",
                    aiAnswering
                )
            }
            VoiceCommand.GetWeeklyDigest -> scope.launch {
                // The learner asked for it, so the AI reads it in a turn of its own even if it already started answering.
                val result = handleGetWeeklyDigest()
                notifyModelOf(result, aiAnswering = false, fallback = result["digest"] as? String)
            }
            is VoiceCommand.SetWeeklyDigest -> {
                val enabled = command.enabled
                applyWeeklyDigestChange(enabled)
                notifyModel(
                    "System: The learner turned weekly progress digest ${if (enabled) "on" else "off"}. " +
                        "Confirm warmly in one short sentence and continue.",
                    aiAnswering
                )
            }
            VoiceCommand.SaveDiagnosticsLog -> saveDiagnosticsLog(aiAnswering)
        }
    }

    /**
     * The learner asked to save the lesson log for the developer, usually right after something went
     * wrong. The file goes to the phone's Download folder; one voice confirms it (the AI if it can
     * speak, otherwise the device).
     */
    private fun saveDiagnosticsLog(aiAnswering: Boolean) {
        scope.launch {
            LiveLog.i(TAG, "The learner asked to save the diagnostics log")
            val place = suspendRunCatching { diagnosticsSaver.save() }.getOrNull()
            val spoken = if (place != null) ANNOUNCE_LOG_SAVED else ANNOUNCE_LOG_FAILED
            if (aiCanSpeak()) {
                val note = if (place != null) {
                    "System: The app saved a diagnostics log for the developer in the phone's Download folder. " +
                        "Say in a few words that the log was saved, then continue exactly where you were."
                } else {
                    "System: The app could not save the diagnostics log. Say so in a few words, then continue."
                }
                notifyModel(note, aiAnswering, fallback = spoken)
            } else {
                confirmOutLoud(spoken)
            }
        }
    }

    private suspend fun onAudioFocusChanged(focus: AudioFocusState) {
        mutex.withLock {
            when (focus) {
                AudioFocusState.LOSS_TRANSIENT, AudioFocusState.LOSS_TRANSIENT_CAN_DUCK -> pauseLocked(PauseReason.FOCUS_TRANSIENT)
                AudioFocusState.LOSS -> pauseLocked(PauseReason.FOCUS_LOSS)
                AudioFocusState.GAIN -> {
                    if (PauseReason.FOCUS_TRANSIENT in pauseReasons) {
                        pauseReasons -= PauseReason.FOCUS_TRANSIENT
                        // Only a pause caused by the call / prompt itself ends here; the learner's own
                        // pause or an auto-pause in the background stays.
                        if (_state.value == ConversationState.PAUSED && pauseReasons.isEmpty()) resumeLocked()
                        if (_state.value == ConversationState.WAITING_FOR_NETWORK && pauseReasons.isEmpty()) startOfflineDrillLocked()
                    }
                }
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
                stopOfflineDrillLocked()
                requestReconnectLocked(ReconnectReason.NETWORK_BACK)
            }
        }
    }

    /** Runs while a lesson is active: nudges a silent learner, keeps podcast stories going. */
    private suspend fun silenceWatchdog() {
        while (_lesson.value != null) {
            delay(SILENCE_CHECK_INTERVAL_MS)
            mutex.withLock {
                if (_state.value != ConversationState.ACTIVE || _activeSpeaker.value != null) return@withLock
                val silentFor = clock() - lastActivityAt
                if (isContinuousStoryActive() && storyAutoContinues < MAX_STORY_AUTO_CONTINUES) {
                    // Podcast mode: the learner is just listening, so silence means "keep going",
                    // not "re-engage me with a question".
                    if (silentFor < STORY_AUTO_CONTINUE_AFTER_MS) return@withLock
                    storyAutoContinues++
                    unansweredNudges = 0
                    lastActivityAt = clock()
                    suspendRunCatching { liveClient.sendText(PromptTemplates.CONTINUE_STORY_MESSAGE) }
                    return@withLock
                }
                if (silentFor < SILENCE_NUDGE_AFTER_MS) return@withLock
                if (unansweredNudges < MAX_UNANSWERED_NUDGES) {
                    unansweredNudges++
                    lastActivityAt = clock()
                    suspendRunCatching { liveClient.sendText(PromptTemplates.SILENCE_NUDGE) }
                } else {
                    pauseLocked(PauseReason.SILENCE)
                    announcer.announce(ANNOUNCE_AUTO_PAUSE)
                }
            }
        }
    }

    // endregion

    private fun mapError(e: Exception): EngineError {
        val name = e::class.simpleName.orEmpty()
        val message = e.message.orEmpty()
        val lower = message.lowercase()
        // What the server said: "Channel was closed by the server. Details: <reason>".
        val raw = message.ifBlank { name }
        return when {
            name == "PermissionMissingException" || e is SecurityException -> EngineError.MissingMicPermission
            !connectivity.isOnlineNow() -> EngineError.NoNetwork
            name in setOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException") ||
                isAuthRejection(lower) -> EngineError.AiNotConfigured(
                when {
                    "service_blocked" in lower || "api_key" in lower || "api key" in lower ->
                        "API Key bị chặn hoặc chưa bật Gemini Developer API trong Firebase/Google Cloud"
                    "too many attempts" in lower ->
                        "App Check đang tạm khoá sau nhiều lần bị từ chối – bấm Thử lại hoặc đóng hẳn app rồi mở lại"
                    "app check" in lower || "appcheck" in lower || "attestation" in lower ->
                        "Firebase App Check từ chối bản cài này (Debug Token chưa đăng ký hoặc sai app)"
                    "permission_denied" in lower || "403" in lower -> "Quyền truy cập bị từ chối (403)"
                    else -> "Server từ chối xác thực"
                } + " — $raw"
            )
            // The server closed the channel without saying why: most often App Check.
            (name == "ServiceConnectionHandshakeFailedException" || "channel was closed by the server" in lower) &&
                "details:" !in lower -> EngineError.AiNotConfigured(
                "Server đóng kết nối không nêu lý do – thường do Firebase App Check (Debug Token chưa đăng ký) — $raw"
            )
            else -> EngineError.ConnectionFailed(raw)
        }
    }

    private fun isAuthRejection(lower: String): Boolean = listOf(
        "app check", "appcheck", "attestation", "too many attempts", "unauthenticated", "permission_denied",
        "permission denied", "api key", "api_key", "service_blocked", "service_disabled", "firebaseapp", "403"
    ).any { it in lower }

    private companion object {
        const val TAG = "ConversationEngine"

        /** Answer to a tool call that belongs to a lesson that has already ended. */
        val STALE_TOOL_RESPONSE: Map<String, Any> = mapOf("status" to "stale", "message" to "That lesson has ended.")

        const val CONNECT_TIMEOUT_MS = 20_000L
        /** How long the safety net waits for the model to call the matching tool itself. */
        const val TOOL_GRACE_MS = 2_000L
        const val TOOL_GRACE_AFTER_AI_MS = 600L
        /** A tool call this soon after the safety net applied the same change is not applied again. */
        const val FALLBACK_DEDUP_MS = 8_000L
        const val DRILL_ECHO_SIMILARITY = 0.5

        const val RECENT_TOPICS_FOR_SUGGESTION = 3
        const val MAX_REVIEW_WORDS = 8
        const val MAX_REVIEW_MISTAKES = 5
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BACKOFF_MS = 1_500L
        const val GO_AWAY_WAIT_MS = 40_000L
        const val GOODBYE_GRACE_MS = 4_000L
        const val STARTUP_GRACE_PERIOD_MS = 5_000L
        const val SPEAKER_IDLE_MS = 1_200L
        /** No answer this long after the AI should have spoken: ask again (a normal answer starts within ~3 s). */
        const val REPLY_RETRY_AFTER_MS = 8_000L

        /** Still no answer this long after asking again: the connection is stuck, open a fresh one. */
        const val REPLY_RECONNECT_AFTER_MS = 10_000L

        /** Fresh connections tried for one unanswered stretch before leaving it to the silence watchdog. */
        const val MAX_STALL_RECONNECTS = 2
        const val SILENCE_CHECK_INTERVAL_MS = 5_000L
        const val SILENCE_NUDGE_AFTER_MS = 25_000L
        const val MAX_UNANSWERED_NUDGES = 3
        /** Podcast mode: how long the AI may stay quiet before we ask it to keep narrating. */
        const val STORY_AUTO_CONTINUE_AFTER_MS = 4_000L
        /** Safety cap so a model that never says the end marker cannot narrate forever. */
        const val MAX_STORY_AUTO_CONTINUES = 40
        const val STORY_END_DETECTION_PHRASE = "end of our story"
        const val CLOSE_AFTER_PAUSE_MS = 2 * 60_000L
        const val SAY_WELCOME_BACK_AFTER_MS = 5_000L
        const val DRAFT_INTERVAL_MS = 30_000L

        /** Less than ~0.3 s of audio cannot contain a sentence. */
        const val MIN_AZURE_AUDIO_BYTES = 16_000 * 2 * 3 / 10

        const val ANNOUNCE_OFFLINE = "Connection lost. Your lesson will continue when you are back online."
        const val ANNOUNCE_OFFLINE_AT_START = "There is no internet connection right now. Please try again later."
        const val ANNOUNCE_GIVE_UP = "Sorry, I could not reconnect. Your lesson has been saved."
        const val ANNOUNCE_AUTO_PAUSE = "I will pause the lesson for now. Press play when you are ready."
        const val ANNOUNCE_LOG_SAVED = "The diagnostics log is saved in the Download folder."
        const val ANNOUNCE_LOG_FAILED = "Sorry, I could not save the diagnostics log."
    }
}
