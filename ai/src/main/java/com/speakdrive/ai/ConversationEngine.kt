package com.speakdrive.ai

import android.util.Log
import com.speakdrive.ai.di.EngineDispatcher
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
import com.speakdrive.ai.session.TranscriptAccumulator
import com.speakdrive.ai.session.VoiceCommandParser
import com.speakdrive.ai.session.VoiceSettingsTools
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
    private val storyRecommender: StoryRecommender = StoryRecommender(topicManager),
    private val drillSentenceManager: DrillSentenceManager = DrillSentenceManager(),
    private val sentenceTranslator: com.speakdrive.ai.translation.SentenceTranslator = com.speakdrive.ai.translation.SentenceTranslator(drillSentenceManager),
    @EngineDispatcher dispatcher: CoroutineDispatcher
) {
    /** Replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val accumulator = TranscriptAccumulator { clock() }

    private val _state = MutableStateFlow(ConversationState.IDLE)
    open val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _lesson = MutableStateFlow<ActiveLesson?>(null)
    open val lesson: StateFlow<ActiveLesson?> = _lesson.asStateFlow()

    private val _transcript = MutableStateFlow(emptyList<TranscriptTurn>())
    open val transcript: StateFlow<List<TranscriptTurn>> = _transcript.asStateFlow()

    /** Who is talking right now, for the mic animation. Null when nobody is. */
    private val _activeSpeaker = MutableStateFlow<Speaker?>(null)
    val activeSpeaker: StateFlow<Speaker?> = _activeSpeaker.asStateFlow()

    /** Whether the AI is currently processing/thinking before responding. */
    private val _isAiThinking = MutableStateFlow(false)
    open val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()
    private var thinkingTimeoutJob: Job? = null

    private fun setAiThinking(thinking: Boolean) {
        thinkingTimeoutJob?.cancel()
        _isAiThinking.value = thinking
        if (thinking) {
            thinkingTimeoutJob = scope.launch {
                delay(AI_THINKING_TIMEOUT_MS)
                _isAiThinking.value = false
            }
        }
    }

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
            Log.i(TAG, "App focus changed: $focused")
            _isAppFocused.value = focused
            if (!focused) {
                checkAutoPause()
            }
        }
    }

    open fun setScreenOn(screenOn: Boolean) {
        if (_isScreenOn.value != screenOn) {
            Log.i(TAG, "Screen on state changed: $screenOn")
            _isScreenOn.value = screenOn
            if (!screenOn) {
                checkAutoPause()
            }
        }
    }

    internal fun checkAutoPause() {
        if (!learnerSettings.autoPauseWhenUnfocused) return
        if (_isCarConnected.value) return
        if (_state.value != ConversationState.ACTIVE) return

        if (!_isAppFocused.value || !_isScreenOn.value) {
            Log.i(TAG, "Auto-pausing lesson: appFocused=${_isAppFocused.value}, screenOn=${_isScreenOn.value}, carConnected=${_isCarConnected.value}")
            pauseAsync()
        }
    }

    open fun pauseAsync() {
        scope.launch { pause() }
    }

    open fun setCarConnected(connected: Boolean) {
        if (_isCarConnected.value != connected) {
            Log.i(TAG, "Car connection status changed: $connected")
            _isCarConnected.value = connected
            liveClient.setCarConnected(connected)
            if (!connected) {
                checkAutoPause()
            }
            if (_state.value == ConversationState.ACTIVE && _lesson.value?.mode == SessionMode.REPEAT_AFTER_ME) {
                scope.launch {
                    runCatching {
                        liveClient.sendText(
                            PromptTemplates.carConnectionSwitchMessage(
                                isCarConnected = connected,
                                length = learnerSettings.drillSentenceLength
                            )
                        )
                    }.onFailure { Log.w(TAG, "Could not send car connection switch update to Live client", it) }
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
    private var pausedByFocus = false
    private var pausedAt = 0L
    private var learnerSettings = LearnerSettings()
    private var cachedStorySessions = listOf<CompletedSession>()

    /** True once the AI said [PromptTemplates.STORY_END_MARKER]; stops podcast auto-continue. */
    private var storyFinished = false
    private var storyAutoContinues = 0

    private var speakerResetJob: Job? = null
    private var pauseCloseJob: Job? = null
    private var draftJob: Job? = null

    init {
        scope.launch { liveClient.events.collect(::onLiveEvent) }
        scope.launch { audioFocus.state.collect(::onAudioFocusChanged) }
        scope.launch { connectivity.isOnline.collect(::onConnectivityChanged) }
        scope.launch { silenceWatchdog() }
        scope.launch {
            settings.observeLearnerSettings().collect { updated ->
                val previousStyle = learnerSettings.storytellingStyle
                val previousDrillLength = learnerSettings.drillSentenceLength
                learnerSettings = updated
                val drill = _lesson.value?.mode == SessionMode.REPEAT_AFTER_ME
                liveClient.updateInterruptions(updated.allowBargeIn && !drill)
                liveClient.setVolume(updated.aiVolume / 100f)
                // Voice/tool changes update learnerSettings first, so this only fires for changes made
                // elsewhere (e.g. the Settings screen) while a story is playing.
                if (updated.storytellingStyle != previousStyle && isStoryLessonActive()) {
                    Log.i(TAG, "Storytelling style changed outside the session: ${updated.storytellingStyle}")
                    onStorytellingStyleChanged()
                    runCatching {
                        liveClient.sendText(PromptTemplates.storytellingStyleSwitchMessage(updated.storytellingStyle))
                    }.onFailure { Log.w(TAG, "Could not send storytelling style update to Live client", it) }
                }
                if (updated.drillSentenceLength != previousDrillLength && _state.value == ConversationState.ACTIVE && drill) {
                    Log.i(TAG, "Drill sentence length changed outside session: ${updated.drillSentenceLength}")
                    runCatching {
                        liveClient.sendText(PromptTemplates.drillSentenceLengthSwitchMessage(updated.drillSentenceLength, _isCarConnected.value))
                    }.onFailure { Log.w(TAG, "Could not send drill sentence length update to Live client", it) }
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
    suspend fun start(request: LessonRequest): Boolean = mutex.withLock {
        if (_state.value.isInLesson) endLocked()
        _error.value = null

        if (!micPermission.hasMicPermission()) return@withLock failStart(EngineError.MissingMicPermission)
        if (!connectivity.isOnlineNow()) {
            announcer.announce(ANNOUNCE_OFFLINE_AT_START)
            return@withLock failStart(EngineError.NoNetwork)
        }

        learnerSettings = settings.snapshot()
        liveClient.setVolume(learnerSettings.aiVolume / 100f)
        val lesson = resolveLesson(request, learnerSettings)
        if (lesson.mode == SessionMode.STORY_LISTENING) {
            cachedStorySessions = runCatching { sessionStore.recentStorySessions(20) }.getOrDefault(emptyList())
        }
        accumulator.clear()
        _transcript.value = emptyList()
        _pronunciationAttempts.value = emptyList()
        _drillTarget.value = null
        awaitingNewDrillTarget = true
        synchronized(attemptCounts) { attemptCounts.clear() }
        accumulatedActiveMs = 0L
        unansweredNudges = 0
        resetStoryProgress()
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
            setAiThinking(true)
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
        val storyResolved = if (request.mode == SessionMode.STORY_LISTENING && scenarioMatch == null && request.topicId == null) {
            val recent = runCatching { sessionStore.recentStorySessions(20) }.getOrDefault(emptyList())
            storyRecommender.recommendStory(recent)
        } else {
            null
        }
        val topic = scenarioMatch?.first
            ?: storyResolved?.first
            ?: topicManager.getTopicById(request.topicId)
            ?: topicManager.suggestTopic(sessionStore.recentTopicIds(RECENT_TOPICS_FOR_SUGGESTION))
        val reviewWords = if (request.mode == SessionMode.VOCAB_REVIEW) {
            when {
                request.targetWords.isNotEmpty() -> request.targetWords
                else -> {
                    val due = sessionStore.wordsDueForReview(MAX_REVIEW_WORDS)
                    if (due.isNotEmpty()) due else sessionStore.recentWords(MAX_REVIEW_WORDS)
                }
            }
        } else {
            emptyList()
        }
        val mode = when {
            request.mode == SessionMode.VOCAB_REVIEW && reviewWords.isEmpty() -> SessionMode.FREE_TALK
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
            resumeStoryTitle = request.resumeStoryTitle
        )
    }

    private suspend fun connectLive(lesson: ActiveLesson, recap: Boolean) {
        val drill = lesson.mode == SessionMode.REPEAT_AFTER_ME
        val sampleDrillSentences = if (drill) {
            val recentTargets = runCatching { sessionStore.recentDrillTargets(30) }.getOrDefault(emptyList()).toSet()
            drillSentenceManager.getSampleSentencesForPrompt(
                category = learnerSettings.drillCategory,
                level = lesson.level,
                topicId = lesson.topic.id,
                isCarConnected = _isCarConnected.value,
                excludeTexts = recentTargets,
                limit = 10
            ).map { it.text }
        } else emptyList()
        val instruction = PromptTemplates.buildSystemInstruction(
            lesson = lesson,
            settings = learnerSettings,
            recap = if (recap) accumulator.snapshot() else emptyList(),
            isCarConnected = _isCarConnected.value,
            sampleDrillSentences = sampleDrillSentences
        )
        val tools = VoiceSettingsTools.allTools + if (drill) listOf(PronunciationDrill.checkAttemptTool) else emptyList()
        liveClient.connect(
            LiveSessionConfig(
                systemInstruction = instruction,
                voiceId = lesson.voiceId,
                // The learner never needs to talk over the AI in a drill.
                enableInterruptions = learnerSettings.allowBargeIn && !drill,
                tools = tools,
                toolHandler = ::handleLiveToolCall
            )
        )
    }

    private fun handleLiveToolCall(call: LiveToolCall): Map<String, Any> = when (call.name) {
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
        PronunciationDrill.CHECK_ATTEMPT_FUNCTION -> gradeAttempt(call)
        else -> mapOf("status" to "unknown function")
    }

    private fun handleApplyLevelRecommendation(call: LiveToolCall): Map<String, Any> {
        val acceptArg = call.args["accept"]
        val accept = when (acceptArg) {
            is Boolean -> acceptArg
            is String -> VoiceSettingsTools.parseApplyLevelRecommendation(acceptArg) ?: true
            else -> true
        }

        val currentLevel = learnerSettings.level
        if (!accept) {
            val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
            val msg = if (isVi) "Giữ nguyên cấp độ ${currentLevel.getLabel(true)}" else "Kept current level ${currentLevel.displayName}"
            announcer.announce(msg)
            return mapOf("status" to "dismissed", "level" to currentLevel.name)
        }

        val targetArg = call.args["target_level"] as? String
        val parsedTarget = VoiceSettingsTools.parseLevel(targetArg)
        val newLevel = parsedTarget ?: currentLevel.nextLevel() ?: currentLevel

        scope.launch { settings.setLevel(newLevel) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            "Đã chuyển cấp độ sang ${newLevel.getLabel(true)} cho buổi học tiếp theo!"
        } else {
            "Updated learning level to ${newLevel.displayName} for your next lesson!"
        }
        announcer.announce(confirmation)
        return mapOf("status" to "applied", "new_level" to newLevel.name)
    }

    private fun handleSetAdaptiveLevel(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val enabled = when (enabledArg) {
            is Boolean -> enabledArg
            is String -> VoiceSettingsTools.parseAdaptiveLevel(enabledArg) ?: true
            else -> true
        }

        scope.launch { settings.setAdaptiveLevelRecommendation(enabled) }
        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (enabled) {
            if (isVi) "Đã bật tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation enabled."
        } else {
            if (isVi) "Đã tắt tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation disabled."
        }
        announcer.announce(confirmation)
        return mapOf("status" to "updated", "adaptive_level_enabled" to enabled)
    }

    private fun handleSetDrillSentenceLength(call: LiveToolCall): Map<String, Any> {
        val raw = call.args["length"] as? String
        val parsed = VoiceSettingsTools.parseDrillSentenceLength(raw) ?: DrillSentenceLength.AUTO_ON_CAR
        return applyDrillSentenceLength(parsed)
    }

    internal fun applyDrillSentenceLength(length: DrillSentenceLength): Map<String, Any> {
        learnerSettings = learnerSettings.copy(drillSentenceLength = length)
        scope.launch { settings.setDrillSentenceLength(length) }

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
        announcer.announce(confirmation)

        if (_state.value == ConversationState.ACTIVE) {
            scope.launch {
                runCatching {
                    liveClient.sendText("System: Drill sentence length set to ${length.name}. " + PromptTemplates.drillSentenceLengthSwitchMessage(length, _isCarConnected.value))
                }
            }
        }

        return mapOf(
            "status" to "success",
            "new_length" to length.name,
            "instruction" to "Confirm warmly in one short sentence to the learner, then continue with: " +
                PromptTemplates.drillSentenceLengthSwitchMessage(length, _isCarConnected.value)
        )
    }

    private fun handleSetDrillCategory(call: LiveToolCall): Map<String, Any> {
        val catArg = call.args["category"] as? String
        val parsed = VoiceSettingsTools.parseDrillCategory(catArg) ?: DrillCategory.ALL
        return applyDrillCategory(parsed)
    }

    internal fun applyDrillCategory(category: DrillCategory): Map<String, Any> {
        learnerSettings = learnerSettings.copy(drillCategory = category)
        scope.launch { settings.setDrillCategory(category) }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            "Đã chuyển chủ đề luyện âm sang: ${category.getLabel(true)}."
        } else {
            "Drill category switched to: ${category.displayName}."
        }
        announcer.announce(confirmation)

        val sampleSentences = drillSentenceManager.getSampleSentencesForPrompt(
            category = category,
            level = _lesson.value?.level ?: learnerSettings.level,
            topicId = _lesson.value?.topic?.id,
            isCarConnected = _isCarConnected.value
        ).map { it.text }
        val switchMsg = PromptTemplates.drillCategorySwitchMessage(category)

        if (_state.value == ConversationState.ACTIVE) {
            scope.launch {
                runCatching {
                    liveClient.sendText("System: $switchMsg")
                }
            }
        }

        return mapOf(
            "status" to "success",
            "drill_category" to category.name,
            "instruction" to "Confirm warmly in one short sentence to the learner, then continue with: $switchMsg"
        )
    }

    private fun handleSetAiVolume(call: LiveToolCall): Map<String, Any> {
        val volArg = call.args["volume"] as? String
        val parsed = VoiceSettingsTools.parseVolume(volArg, learnerSettings.aiVolume) ?: (learnerSettings.aiVolume - 20).coerceAtLeast(10)
        return applyAiVolume(parsed)
    }

    internal fun applyAiVolume(volume: Int): Map<String, Any> {
        val clamped = volume.coerceIn(10, 100)
        learnerSettings = learnerSettings.copy(aiVolume = clamped)
        scope.launch { settings.setAiVolume(clamped) }
        liveClient.setVolume(clamped / 100f)

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            "Đã điều chỉnh âm lượng giọng nói thành $clamped phần trăm."
        } else {
            "AI voice volume set to $clamped percent."
        }
        announcer.announce(confirmation)

        return mapOf(
            "status" to "success",
            "new_volume" to clamped,
            "instruction" to "AI voice volume set to $clamped%. Confirm warmly in one short sentence to the learner, then naturally continue the conversation."
        )
    }

    private fun handleSetAutoPauseWhenUnfocused(call: LiveToolCall): Map<String, Any> {
        val enabledArg = call.args["enabled"]
        val parsed = VoiceSettingsTools.parseAutoPauseWhenUnfocused(enabledArg) ?: true
        return applyAutoPauseWhenUnfocusedChange(parsed)
    }

    internal fun applyAutoPauseWhenUnfocusedChange(enabled: Boolean): Map<String, Any> {
        learnerSettings = learnerSettings.copy(autoPauseWhenUnfocused = enabled)
        scope.launch { settings.setAutoPauseWhenUnfocused(enabled) }

        val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
        val confirmation = if (isVi) {
            if (enabled) "Đã bật tự động tạm dừng khi rời ứng dụng hoặc tắt màn hình."
            else "Đã tắt tự động tạm dừng khi rời ứng dụng hoặc tắt màn hình."
        } else {
            if (enabled) "Auto-pause when leaving app or turning screen off enabled."
            else "Auto-pause when leaving app or turning screen off disabled."
        }
        announcer.announce(confirmation)

        if (enabled) {
            checkAutoPause()
        }

        return mapOf(
            "status" to "success",
            "auto_pause_when_unfocused" to enabled,
            "instruction" to "Auto-pause when app is unfocused or screen off set to $enabled. Confirm warmly in one short sentence to the learner, then naturally continue the conversation."
        )
    }

    private fun handleSetAppLanguage(call: LiveToolCall): Map<String, Any> {
        val langArg = call.args["language"] as? String
        val parsed = VoiceSettingsTools.parseAppLanguage(langArg)
        if (parsed == null) {
            Log.w(TAG, "Could not parse app language from arg: $langArg")
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
        val parsedLevel = VoiceSettingsTools.parseLevel(levelArg)
        if (parsedLevel == null) {
            Log.w(TAG, "Could not parse difficulty level from arg: $levelArg")
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
        val enabled = call.args["enabled"] as? Boolean ?: true
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
            Log.w(TAG, "Could not parse pronunciation strictness from arg: $strictnessArg")
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
        val enabled = call.args["enabled"] as? Boolean ?: true
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
            Log.w(TAG, "Could not parse voice from arg: $voiceArg")
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
        val enabled = call.args["enabled"] as? Boolean ?: true
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
            Log.w(TAG, "Could not parse storytelling style from arg: $styleArg")
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
        Log.i(TAG, "Learner requested next story tool (reason: $reason)")
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
            Log.w(TAG, "Could not parse story duration from arg: $durationArg")
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
        val enabled = when (enabledArg) {
            is Boolean -> enabledArg
            is String -> VoiceSettingsTools.parseMultiVoice(enabledArg) ?: true
            else -> true
        }
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
        Log.i(TAG, "Learner requested resume story tool")
        resumeStory()
        return mapOf(
            "status" to "success",
            "instruction" to "Resuming the story from where you left off. Welcome the learner back in one short sentence and continue the narrative."
        )
    }

    private fun applyDifficultyChange(newLevel: DifficultyLevel) {
        val currentLesson = _lesson.value ?: return
        if (currentLesson.level == newLevel) return
        _lesson.value = currentLesson.copy(level = newLevel)
        learnerSettings = learnerSettings.copy(level = newLevel)
        scope.launch { settings.setLevel(newLevel) }
        Log.i(TAG, "Switched difficulty level to ${newLevel.displayName} (${newLevel.cefr})")
    }

    private fun applyVietnameseHelpChange(enabled: Boolean) {
        if (learnerSettings.allowVietnameseHelp == enabled) return
        learnerSettings = learnerSettings.copy(allowVietnameseHelp = enabled)
        scope.launch { settings.setAllowVietnameseHelp(enabled) }
        Log.i(TAG, "Switched allowVietnameseHelp to $enabled")
    }

    private fun applyAppLanguageChange(newLanguage: AppLanguage) {
        if (learnerSettings.appLanguage == newLanguage) return
        learnerSettings = learnerSettings.copy(appLanguage = newLanguage)
        scope.launch { settings.setAppLanguage(newLanguage) }
        Log.i(TAG, "Switched appLanguage to ${newLanguage.name} (${newLanguage.code})")
    }

    private fun applyStorytellingStyleChange(newStyle: StorytellingStyle) {
        if (learnerSettings.storytellingStyle == newStyle) return
        learnerSettings = learnerSettings.copy(storytellingStyle = newStyle)
        scope.launch { settings.setStorytellingStyle(newStyle) }
        Log.i(TAG, "Switched storytellingStyle to ${newStyle.displayName}")
    }

    private fun applyPronunciationStrictnessChange(newStrictness: PronunciationStrictness) {
        if (learnerSettings.pronunciationStrictness == newStrictness) return
        learnerSettings = learnerSettings.copy(pronunciationStrictness = newStrictness)
        scope.launch { settings.setPronunciationStrictness(newStrictness) }
        Log.i(TAG, "Switched pronunciationStrictness to ${newStrictness.displayName}")
    }

    private fun applyBargeInChange(enabled: Boolean) {
        if (learnerSettings.allowBargeIn == enabled) return
        learnerSettings = learnerSettings.copy(allowBargeIn = enabled)
        scope.launch { settings.setAllowBargeIn(enabled) }
        Log.i(TAG, "Switched allowBargeIn to $enabled")
    }

    private fun applyVoiceChange(voice: AiVoice) {
        if (learnerSettings.voiceId == voice.id && !learnerSettings.randomVoice) return
        learnerSettings = learnerSettings.copy(voiceId = voice.id, randomVoice = false)
        scope.launch {
            settings.setRandomVoice(false)
            settings.setVoice(voice)
        }
        Log.i(TAG, "Switched preferred voice to ${voice.name} (${voice.id})")
    }

    private fun applyRandomVoiceChange(enabled: Boolean) {
        if (learnerSettings.randomVoice == enabled) return
        learnerSettings = learnerSettings.copy(randomVoice = enabled)
        scope.launch { settings.setRandomVoice(enabled) }
        Log.i(TAG, "Switched randomVoice to $enabled")
    }

    private fun applyStoryDurationChange(newDuration: StoryDuration) {
        if (learnerSettings.storyDuration == newDuration) return
        learnerSettings = learnerSettings.copy(storyDuration = newDuration)
        scope.launch { settings.setStoryDuration(newDuration) }
        Log.i(TAG, "Switched storyDuration to ${newDuration.displayName}")
    }

    private fun applyMultiVoiceChange(enabled: Boolean) {
        if (learnerSettings.multiVoiceStorytelling == enabled) return
        learnerSettings = learnerSettings.copy(multiVoiceStorytelling = enabled)
        scope.launch { settings.setMultiVoiceStorytelling(enabled) }
        Log.i(TAG, "Switched multiVoiceStorytelling to $enabled")
    }

    /**
     * Skips the current story and switches to a recommended one without disconnecting.
     * Can be invoked via voice tool, voice command fallback, steering wheel button, or phone UI.
     */
    fun nextStory(): Boolean {
        val currentLesson = _lesson.value ?: return false
        if (currentLesson.mode != SessionMode.STORY_LISTENING) {
            scope.launch {
                start(LessonRequest(topicId = null, mode = SessionMode.FREE_TALK))
            }
            return true
        }

        val result = performNextStory() ?: return false
        scope.launch {
            runCatching {
                liveClient.sendText(
                    "System: The learner skipped the previous story. Switch immediately to the new story: \"${result.second.titleEn}\" (Topic: ${result.first.titleEn}). " +
                        "Introduce it enthusiastically in one short sentence and begin the opening scene!"
                )
            }.onFailure { Log.w(TAG, "Could not send next story command to Live client", it) }
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
            runCatching {
                liveClient.sendText(
                    "System: The learner requested to replay the story from the beginning. " +
                        "Re-introduce \"$title\" in one short sentence and start narrating Chapter 1 again."
                )
            }.onFailure { Log.w(TAG, "Could not send replay story command to Live client", it) }
        }
        return true
    }

    /**
     * Resumes an unfinished story from a previous session.
     * Can be invoked via voice tool, voice command fallback, Android Auto media action, or phone UI.
     */
    fun resumeStory(): Boolean {
        scope.launch {
            val unfinished = runCatching { sessionStore.latestUnfinishedStorySession() }.getOrNull()
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
        Log.i(TAG, "Live tool skip_drill_sentence invoked (reason=$reason)")
        skipDrillSentence()
        val topic = _lesson.value?.topic?.titleEn ?: "daily situations"
        return mapOf(
            "status" to "success",
            "instruction" to "The learner skipped this sentence. Confirm in one short phrase and immediately introduce a fresh, brand new sentence related to $topic that has not been used yet in this session. Never repeat previous sentences. You MUST ALWAYS start the next sentence with: \"Repeat after me: <sentence>\"."
        )
    }

    private fun handleRepeatDrillSentence(call: LiveToolCall): Map<String, Any> {
        Log.i(TAG, "Live tool repeat_drill_sentence invoked")
        announcer.announce("Đọc lại câu")
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
    fun next(): Boolean {
        val currentLesson = _lesson.value ?: return false
        return when (currentLesson.mode) {
            SessionMode.STORY_LISTENING -> nextStory()
            SessionMode.REPEAT_AFTER_ME -> {
                skipDrillSentence()
                val topic = currentLesson.topic.titleEn
                scope.launch {
                    runCatching {
                        liveClient.sendText(
                            "System: The learner skipped to the next sentence. " +
                                "Confirm in one short phrase, then give a fresh, brand new sentence related to $topic that has not been used yet in this session. " +
                                "Never repeat previous sentences. You MUST ALWAYS start the new sentence with: \"Repeat after me: <sentence>\"."
                        )
                    }.onFailure { Log.w(TAG, "Could not send skip drill sentence to Live client", it) }
                }
                true
            }
            else -> {
                scope.launch {
                    runCatching {
                        liveClient.sendText(
                            "System: The learner clicked 'Next'. Wrap up this thought and move on to the next question or topic."
                        )
                    }.onFailure { Log.w(TAG, "Could not send next command to Live client", it) }
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
    fun repeat(): Boolean {
        val currentLesson = _lesson.value ?: return false
        return when (currentLesson.mode) {
            SessionMode.STORY_LISTENING -> replayStory()
            SessionMode.REPEAT_AFTER_ME -> {
                val target = _drillTarget.value
                announcer.announce("Đọc lại câu")
                scope.launch {
                    runCatching {
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
                    }.onFailure { Log.w(TAG, "Could not send repeat command to Live client", it) }
                }
                true
            }
            else -> {
                scope.launch {
                    runCatching {
                        liveClient.sendText(
                            "System: The learner asked you to repeat what you just said. Please repeat your last sentence clearly."
                        )
                    }.onFailure { Log.w(TAG, "Could not send repeat command to Live client", it) }
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
        _drillTarget.value = null
        awaitingNewDrillTarget = true
        announcer.announce("Chuyển câu tiếp theo")
        return true
    }

    /**
     * Called by the model after each "repeat after me" attempt (on a background thread). The
     * verdict combines the model's judgement with a word-by-word transcript check, and the model
     * is told to follow it, so a wrong attempt is never praised.
     */
    private fun gradeAttempt(call: LiveToolCall): Map<String, Any> {
        val target = (call.args["target_sentence"] as? String)?.takeIf { it.isNotBlank() } ?: _drillTarget.value.orEmpty()
        val modelSaidCorrect = (call.args["verdict"] as? String)?.trim()?.equals("correct", ignoreCase = true) == true
        val problemWords = (call.args["problem_words"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val notes = call.args["problem_notes"] as? String ?: ""
        val attemptNumber = synchronized(attemptCounts) {
            val key = PronunciationDrill.key(target)
            (attemptCounts.getOrElse(key) { 0 } + 1).also { attemptCounts[key] = it }
        }
        val (azure, azureError) = assessWithAzure(target, call.learnerAudio)
        val attempt = PronunciationGrader.grade(
            target = target,
            heard = call.learnerUtterance,
            modelSaidCorrect = modelSaidCorrect,
            modelProblemWords = problemWords,
            modelNotes = notes,
            attemptNumber = attemptNumber,
            timestamp = clock(),
            azure = azure,
            azureError = azureError,
            strictness = learnerSettings.pronunciationStrictness,
            level = _lesson.value?.level ?: learnerSettings.level
        )
        if (attempt.heard.isNotBlank()) _pronunciationAttempts.value = _pronunciationAttempts.value + attempt
        val isDoneWithSentence = attempt.passed || attemptNumber >= PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE
        awaitingNewDrillTarget = isDoneWithSentence
        val shown = _drillTarget.value
        if (shown.isNullOrBlank() || PronunciationDrill.key(shown) == PronunciationDrill.key(target)) {
            _drillTarget.value = target
        }
        Log.d(
            TAG,
            "Attempt $attemptNumber at '$target': passed=${attempt.passed} strictness=${learnerSettings.pronunciationStrictness} " +
                "accuracy=${attempt.accuracyPercent}% azure=${azure?.pronunciationScore ?: azureError ?: "off"}"
        )
        return PronunciationDrill.toolResponse(attempt)
    }

    /**
     * Third, independent judge when switched on in settings. Failures never block the drill:
     * the attempt is then graded by the other two judges and the reason is shown to the learner.
     */
    private fun assessWithAzure(target: String, audio: ByteArray): Pair<AzureAssessment?, String?> {
        if (!learnerSettings.azureEnabled) return null to null
        val config = AzureSpeechConfig(learnerSettings.azureRegion, learnerSettings.azureKey)
        if (!config.isComplete) return null to "Chưa nhập Region/Key Azure trong Cài đặt"
        if (audio.size < MIN_AZURE_AUDIO_BYTES) return null to null
        return runCatching { pronunciationAssessor.assess(target, audio, config) }
            .fold(
                onSuccess = { it to null },
                onFailure = {
                    Log.w(TAG, "Azure assessment failed", it)
                    null to "Azure không phản hồi: ${it.message ?: it::class.simpleName}"
                }
            )
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
        setAiThinking(false)
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
                if (clock() - pausedAt > SAY_WELCOME_BACK_AFTER_MS) {
                    liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
                    setAiThinking(true)
                }
            } else {
                learnerSettings = settings.snapshot()
                connectLive(lesson, recap = true)
                liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
                setAiThinking(true)
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
        setAiThinking(false)
        repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
            if (!connectivity.isOnlineNow()) {
                goOffline()
                return
            }
            try {
                connectLive(lesson, recap = true)
                liveClient.sendText(PromptTemplates.RESUME_MESSAGE)
                setAiThinking(true)
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
        setAiThinking(false)
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
        setAiThinking(false)
        pauseCloseJob?.cancel()
        draftJob?.cancel()
        liveClient.disconnect()
        audioFocus.abandon()

        val turns = accumulator.snapshot()
        val hasLearnerSpeech = turns.any { it.speaker == Speaker.USER }
        val hasStoryContent = lesson.mode == SessionMode.STORY_LISTENING && turns.any { it.speaker == Speaker.AI }
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
                summaryGenerator.summarize(lesson, draft.transcript, draft.pronunciationAttempts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Summary generation failed", e)
                SummaryParser.fallback("Chưa tạo được nhận xét do lỗi kết nối. Nội dung buổi học vẫn được lưu lại.")
            }
            val graded = if (lesson.mode == SessionMode.REPEAT_AFTER_ME) {
                summary.copy(pronunciationScore = PronunciationDrill.score(draft.pronunciationAttempts))
            } else {
                summary
            }
            val isStoryFullyFinished = lesson.mode != SessionMode.STORY_LISTENING ||
                draft.transcript.any {
                    it.text.contains("takeaway", ignoreCase = true) ||
                    it.text.contains("moral", ignoreCase = true) ||
                    it.text.contains("the end", ignoreCase = true)
                }
            val completedStatus = if (lesson.mode == SessionMode.STORY_LISTENING) isStoryFullyFinished else true
            sessionStore.saveSession(draft.copy(summary = graded, isCompleted = completedStatus))
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
        isCompleted = completed,
        pronunciationAttempts = _pronunciationAttempts.value
    )

    /** Saves the transcript every so often so a killed app does not lose the lesson. */
    private fun startDraftSaver() {
        draftJob?.cancel()
        draftJob = scope.launch {
            while (isActive) {
                delay(DRAFT_INTERVAL_MS)
                val lesson = _lesson.value ?: return@launch
                val turns = accumulator.snapshot()
                val shouldSave = turns.any { it.speaker == Speaker.USER } ||
                    (lesson.mode == SessionMode.STORY_LISTENING && turns.any { it.speaker == Speaker.AI })
                if (shouldSave) {
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
            LiveEvent.Interrupted -> {
                speakerResetJob?.cancel()
                _activeSpeaker.value = null
                setAiThinking(false)
            }
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
        if (speaker == Speaker.AI) {
            setAiThinking(false)
            _transcript.value.lastOrNull()?.let { turn ->
                if (_lesson.value?.mode == SessionMode.REPEAT_AFTER_ME) {
                    val current = _drillTarget.value
                    val newTarget = PronunciationDrill.extractTarget(turn.text)
                    if (newTarget != null) {
                        val isExtension = current != null &&
                            newTarget.length > current.length &&
                            newTarget.contains(current.trimEnd('.', '!', '?', ',', ' ', '"', '”'), ignoreCase = true)
                        val norm = { s: String -> PronunciationDrill.key(s) }
                        val isPartialOfCurrent = current != null &&
                            norm(current).startsWith(norm(newTarget)) && norm(newTarget) != norm(current)
                        val explicitCue = turn.text.contains("repeat after me", ignoreCase = true)
                        if (awaitingNewDrillTarget || current == null || isExtension || (explicitCue && !isPartialOfCurrent)) {
                            _drillTarget.value = newTarget
                        }
                    }
                }
                if (!storyFinished && _lesson.value?.mode == SessionMode.STORY_LISTENING &&
                    turn.text.contains(STORY_END_DETECTION_PHRASE, ignoreCase = true)
                ) {
                    Log.i(TAG, "Story finished; podcast auto-continue stopped")
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
            checkVoiceCommandFallback(text)
        }
        _activeSpeaker.value = speaker
        speakerResetJob?.cancel()
        speakerResetJob = scope.launch {
            delay(SPEAKER_IDLE_MS)
            _activeSpeaker.value = null
            if (speaker == Speaker.USER && _state.value == ConversationState.ACTIVE) {
                setAiThinking(true)
            }
        }
    }

    private fun checkVoiceCommandFallback(text: String) {
        val currentLevel = _lesson.value?.level ?: learnerSettings.level
        val levelCmd = VoiceCommandParser.parseDifficultyCommand(text, currentLevel)
        if (levelCmd != null && _lesson.value?.level != levelCmd) {
            Log.i(TAG, "Fallback voice command parser detected level change: $levelCmd")
            applyDifficultyChange(levelCmd)
            scope.launch {
                val sent = runCatching {
                    val promptText = if (_lesson.value?.mode == SessionMode.STORY_LISTENING) {
                        "System: The learner changed story difficulty level to ${levelCmd.displayName} (${levelCmd.cefr}). " +
                            "Confirm warmly in one short sentence (e.g. \"Adapting the story to ${levelCmd.displayName} level!\") and immediately continue narrating the next events of the story adapted to this level."
                    } else {
                        "System: The learner requested to switch difficulty level to ${levelCmd.displayName} (${levelCmd.cefr}). " +
                            "Confirm warmly in one short sentence and adapt your vocabulary and sentence length to this level."
                    }
                    liveClient.sendText(promptText)
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback level update to Live client", sent.exceptionOrNull())
                    announcer.announce("Difficulty level set to ${levelCmd.displayName}")
                }
            }
            return
        }

        val viCmd = VoiceCommandParser.parseVietnameseHelpCommand(text)
        if (viCmd != null && learnerSettings.allowVietnameseHelp != viCmd) {
            Log.i(TAG, "Fallback voice command parser detected Vietnamese help change: $viCmd")
            applyVietnameseHelpChange(viCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to ${if (viCmd) "enable" else "disable"} Vietnamese help. " +
                            "Confirm warmly in one short sentence and continue."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback Vietnamese help update to Live client", sent.exceptionOrNull())
                    announcer.announce(if (viCmd) "Vietnamese help enabled" else "English only mode enabled")
                }
            }
            return
        }

        val langCmd = VoiceCommandParser.parseAppLanguageCommand(text)
        if (langCmd != null && learnerSettings.appLanguage != langCmd) {
            Log.i(TAG, "Fallback voice command parser detected app language change: $langCmd")
            applyAppLanguageChange(langCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to change the app interface and explanation language to ${langCmd.englishName} (${langCmd.nativeName}). " +
                            "Confirm warmly in one short sentence in ${langCmd.englishName} or Vietnamese, and continue the conversation in English."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback app language update to Live client", sent.exceptionOrNull())
                    announcer.announce("App language set to ${langCmd.englishName}")
                }
            }
            return
        }

        val styleCmd = VoiceCommandParser.parseStorytellingStyleCommand(
            text,
            inStorySession = _lesson.value?.mode == SessionMode.STORY_LISTENING
        )
        if (styleCmd != null && learnerSettings.storytellingStyle != styleCmd) {
            Log.i(TAG, "Fallback voice command parser detected storytelling style change: $styleCmd")
            applyStorytellingStyleChange(styleCmd)
            onStorytellingStyleChanged()
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(PromptTemplates.storytellingStyleSwitchMessage(styleCmd))
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback storytelling style update to Live client", sent.exceptionOrNull())
                    announcer.announce("Storytelling mode set to ${styleCmd.displayName}")
                }
            }
            return
        }

        val strictnessCmd = VoiceCommandParser.parsePronunciationStrictnessCommand(text)
        if (strictnessCmd != null && learnerSettings.pronunciationStrictness != strictnessCmd) {
            Log.i(TAG, "Fallback voice command parser detected pronunciation strictness change: $strictnessCmd")
            applyPronunciationStrictnessChange(strictnessCmd)
            scope.launch {
                val currentLevel = _lesson.value?.level ?: learnerSettings.level
                val promptText = if (strictnessCmd == PronunciationStrictness.AUTO) {
                    val resolved = strictnessCmd.resolveForLevel(currentLevel)
                    "System: The learner requested to change pronunciation strictness to Auto by Level (${resolved.displayName} for level ${currentLevel.displayName}). " +
                        "Confirm warmly in one short sentence and adapt your grading accordingly."
                } else {
                    "System: The learner requested to change pronunciation strictness to ${strictnessCmd.displayName} (${strictnessCmd.labelVi}). " +
                        "Confirm warmly in one short sentence and adapt your grading accordingly."
                }
                val sent = runCatching {
                    liveClient.sendText(promptText)
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback strictness update to Live client", sent.exceptionOrNull())
                    val feedbackMsg = if (strictnessCmd == PronunciationStrictness.AUTO) {
                        val resolved = strictnessCmd.resolveForLevel(currentLevel)
                        "Pronunciation strictness set to Auto by Level (${resolved.displayName})"
                    } else {
                        "Pronunciation strictness set to ${strictnessCmd.displayName}"
                    }
                    announcer.announce(feedbackMsg)
                }
            }
            return
        }

        val bargeInCmd = VoiceCommandParser.parseBargeInCommand(text)
        if (bargeInCmd != null && learnerSettings.allowBargeIn != bargeInCmd) {
            Log.i(TAG, "Fallback voice command parser detected barge-in change: $bargeInCmd")
            applyBargeInChange(bargeInCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to ${if (bargeInCmd) "enable" else "disable"} barge-in / interruption mode. " +
                            "Confirm warmly in one short sentence and continue."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback barge-in update to Live client", sent.exceptionOrNull())
                    announcer.announce(if (bargeInCmd) "Barge in enabled" else "Barge in disabled")
                }
            }
            return
        }

        val randomVoiceCmd = VoiceCommandParser.parseRandomVoiceCommand(text)
        if (randomVoiceCmd != null && learnerSettings.randomVoice != randomVoiceCmd) {
            Log.i(TAG, "Fallback voice command parser detected random voice change: $randomVoiceCmd")
            applyRandomVoiceChange(randomVoiceCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to ${if (randomVoiceCmd) "enable" else "disable"} random AI voice selection for lessons. " +
                            "Confirm warmly in one short sentence (e.g. \"${if (randomVoiceCmd) "Đã bật chọn giọng ngẫu nhiên mỗi bài học" else "Đã tắt giọng ngẫu nhiên, giữ giọng hiện tại"}\" or in English) and continue."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback random voice update to Live client", sent.exceptionOrNull())
                    announcer.announce(if (randomVoiceCmd) "Random AI voice enabled" else "Random AI voice disabled")
                }
            }
            return
        }

        val voiceCmd = VoiceCommandParser.parseVoiceCommand(text)
        if (voiceCmd != null && (learnerSettings.voiceId != voiceCmd.id || learnerSettings.randomVoice)) {
            Log.i(TAG, "Fallback voice command parser detected voice change: $voiceCmd")
            applyVoiceChange(voiceCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to change preferred AI voice to ${voiceCmd.name} (${voiceCmd.labelVi}). " +
                            "Confirm warmly in one short sentence that the new voice will be used starting in the next session, and continue."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback voice update to Live client", sent.exceptionOrNull())
                    announcer.announce("AI voice set to ${voiceCmd.name}")
                }
            }
            return
        }

        val storyDurationCmd = VoiceCommandParser.parseStoryDurationCommand(text)
        if (storyDurationCmd != null && learnerSettings.storyDuration != storyDurationCmd) {
            Log.i(TAG, "Fallback voice command parser detected story duration change: $storyDurationCmd")
            applyStoryDurationChange(storyDurationCmd)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to set story duration to ${storyDurationCmd.displayName}. " +
                            "Confirm in one short sentence and adjust the story scope."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback story duration update to Live client", sent.exceptionOrNull())
                    announcer.announce("Story duration set to ${storyDurationCmd.displayName}")
                }
            }
            return
        }

        val multiVoiceCmd = VoiceCommandParser.parseMultiVoiceCommand(text)
        if (multiVoiceCmd != null && learnerSettings.multiVoiceStorytelling != multiVoiceCmd) {
            val enabled: Boolean = multiVoiceCmd
            Log.i(TAG, "Fallback voice command parser detected multi-voice change: $enabled")
            applyMultiVoiceChange(enabled)
            scope.launch {
                val sent = runCatching {
                    liveClient.sendText(
                        "System: The learner requested to ${if (enabled) "enable" else "disable"} multi-voice audio drama mode. " +
                            "Confirm warmly in one short sentence and continue."
                    )
                }
                if (sent.isFailure) {
                    Log.w(TAG, "Could not send fallback multi-voice update to Live client", sent.exceptionOrNull())
                    announcer.announce(if (enabled) "Multi-voice drama enabled" else "Single voice enabled")
                }
            }
            return
        }

        if (VoiceCommandParser.parseResumeStoryCommand(text)) {
            Log.i(TAG, "Fallback voice command parser detected resume story request")
            resumeStory()
            return
        }

        if (_lesson.value?.mode == SessionMode.STORY_LISTENING) {
            if (VoiceCommandParser.parseNextStoryCommand(text)) {
                Log.i(TAG, "Fallback voice command parser detected next story request")
                nextStory()
                return
            }
            if (VoiceCommandParser.parseReplayStoryCommand(text)) {
                Log.i(TAG, "Fallback voice command parser detected replay story request")
                replayStory()
                return
            }
        }

        if (_lesson.value?.mode == SessionMode.REPEAT_AFTER_ME) {
            if (VoiceCommandParser.parseRepeatDrillSentenceCommand(text)) {
                Log.i(TAG, "Fallback voice command parser detected repeat drill sentence request")
                repeat()
                return
            }
            if (VoiceCommandParser.parseSkipDrillSentenceCommand(text)) {
                Log.i(TAG, "Fallback voice command parser detected skip drill sentence request")
                next()
                return
            }
        }

        val drillLengthCmd = VoiceCommandParser.parseDrillSentenceLengthCommand(text)
        if (drillLengthCmd != null && learnerSettings.drillSentenceLength != drillLengthCmd) {
            Log.i(TAG, "Fallback voice command parser detected drill sentence length change: $drillLengthCmd")
            applyDrillSentenceLength(drillLengthCmd)
            return
        }

        val drillCategoryCmd = VoiceCommandParser.parseDrillCategoryCommand(text)
        if (drillCategoryCmd != null && learnerSettings.drillCategory != drillCategoryCmd) {
            Log.i(TAG, "Fallback voice command parser detected drill category change: $drillCategoryCmd")
            applyDrillCategory(drillCategoryCmd)
            return
        }

        val applyRecommendationCmd = VoiceCommandParser.parseApplyRecommendationCommand(text)
        if (applyRecommendationCmd != null) {
            val accept: Boolean = applyRecommendationCmd
            Log.i(TAG, "Fallback voice command parser detected level recommendation action: accept=$accept")
            val currentLevel = learnerSettings.level
            if (!accept) {
                val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
                val msg = if (isVi) "Giữ nguyên cấp độ ${currentLevel.getLabel(true)}" else "Kept current level ${currentLevel.displayName}"
                announcer.announce(msg)
            } else {
                val nextLevel = currentLevel.nextLevel() ?: currentLevel
                scope.launch { settings.setLevel(nextLevel) }
                val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
                val confirmation = if (isVi) {
                    "Đã chuyển cấp độ sang ${nextLevel.getLabel(true)} cho buổi học tiếp theo!"
                } else {
                    "Updated learning level to ${nextLevel.displayName} for your next lesson!"
                }
                announcer.announce(confirmation)
            }
            return
        }

        val adaptiveLevelCmd = VoiceCommandParser.parseAdaptiveLevelCommand(text)
        if (adaptiveLevelCmd != null && learnerSettings.adaptiveLevelRecommendation != adaptiveLevelCmd) {
            val enabled: Boolean = adaptiveLevelCmd
            Log.i(TAG, "Fallback voice command parser detected adaptive level toggle: $enabled")
            scope.launch { settings.setAdaptiveLevelRecommendation(enabled) }
            val isVi = learnerSettings.appLanguage != AppLanguage.ENGLISH
            val confirmation = if (enabled) {
                if (isVi) "Đã bật tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation enabled."
            } else {
                if (isVi) "Đã tắt tự động gợi ý điều chỉnh cấp độ." else "Adaptive level recommendation disabled."
            }
            announcer.announce(confirmation)
            return
        }

        val volumeCmd = VoiceCommandParser.parseVolumeCommand(text, learnerSettings.aiVolume)
        if (volumeCmd != null && learnerSettings.aiVolume != volumeCmd) {
            Log.i(TAG, "Fallback voice command parser detected volume change: $volumeCmd")
            applyAiVolume(volumeCmd)
            return
        }

        val autoPauseCmd = VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand(text)
        if (autoPauseCmd != null && learnerSettings.autoPauseWhenUnfocused != autoPauseCmd) {
            Log.i(TAG, "Fallback voice command parser detected auto pause toggle: $autoPauseCmd")
            applyAutoPauseWhenUnfocusedChange(autoPauseCmd)
            return
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
                val silentFor = clock() - lastActivityAt
                if (isContinuousStoryActive() && storyAutoContinues < MAX_STORY_AUTO_CONTINUES) {
                    // Podcast mode: the learner is just listening, so silence means "keep going",
                    // not "re-engage me with a question".
                    if (silentFor < STORY_AUTO_CONTINUE_AFTER_MS) return@withLock
                    storyAutoContinues++
                    unansweredNudges = 0
                    lastActivityAt = clock()
                    runCatching { liveClient.sendText(PromptTemplates.CONTINUE_STORY_MESSAGE) }
                    return@withLock
                }
                if (silentFor < SILENCE_NUDGE_AFTER_MS) return@withLock
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
        val lower = message.lowercase()
        return when {
            name == "PermissionMissingException" || e is SecurityException -> EngineError.MissingMicPermission
            // The SDK hides the server's close reason behind this message. While online it almost
            // always means App Check rejected the token (close code 1008), e.g. the debug token of
            // this install is not registered in Firebase Console.
            (name == "ServiceConnectionHandshakeFailedException" || "channel was closed by the server" in lower) &&
                connectivity.isOnlineNow() -> EngineError.AiNotConfigured(
                "Server từ chối kết nối – thường do Firebase App Check token không hợp lệ " +
                    "(Debug Token của bản cài này chưa được đăng ký trong Firebase Console)"
            )
            name in setOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException") ||
                "firebaseapp" in lower || "api key" in lower || "api_key" in lower ||
                "permission_denied" in lower || "service_blocked" in lower || "service_disabled" in lower ||
                "app attestation failed" in lower || "403" in lower -> {
                val detail = when {
                    "service_blocked" in lower || "api_key" in lower -> "API Key bị chặn hoặc chưa bật Gemini Developer API trong Firebase/Google Cloud"
                    "app attestation failed" in lower -> "App Check xác thực thất bại (chưa đăng ký Debug Token)"
                    "permission_denied" in lower -> "Quyền truy cập bị từ chối (Permission Denied 403)"
                    else -> message.ifBlank { name }
                }
                EngineError.AiNotConfigured(detail)
            }
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
        const val AI_THINKING_TIMEOUT_MS = 10_000L
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
    }
}
