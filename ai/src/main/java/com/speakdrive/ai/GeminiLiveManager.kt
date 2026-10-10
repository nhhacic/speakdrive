package com.speakdrive.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveServerUnknownMessage
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.speakdrive.ai.diagnostics.LiveLog
import com.speakdrive.ai.live.LiveConnectPlanner
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.live.LiveTool
import com.speakdrive.ai.live.LiveToolCall
import com.speakdrive.ai.live.LiveToolParam
import com.speakdrive.ai.live.MicGate
import com.speakdrive.ai.live.UtteranceBuffer
import com.speakdrive.audio.AudioDiagnostics
import com.speakdrive.audio.LiveAudio
import com.speakdrive.audio.PcmLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech-to-speech conversation over the Gemini Live API (Firebase AI Logic).
 *
 * Captures microphone with [LiveAudio] and gates audio with [MicGate] during AI speech playback
 * to completely eliminate speaker-to-mic acoustic feedback loops (AI hearing itself), while
 * seamlessly streaming learner speech to Gemini over realtime WebSockets.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class GeminiLiveManager @Inject constructor(
    private val audio: LiveAudio
) : LiveConversationClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val gate = MicGate(
        tailMs = TAIL_PHONE_SPEAKER_MS,
        bargeInRms = BARGE_IN_RMS,
        bargeInChunks = BARGE_IN_CHUNKS
    )
    private val utterance = UtteranceBuffer()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events: Flow<LiveEvent> = _events.asSharedFlow()

    @Volatile
    private var session: LiveSession? = null
    @Volatile
    private var config: LiveSessionConfig? = null
    private var watchdog: Job? = null
    private var receiveJob: Job? = null
    private var sendJob: Job? = null
    @Volatile
    private var outgoing = newOutgoingChannel()
    @Volatile
    private var audioPaused = false

    /**
     * The AI's turn is "live" from the moment we ask it to speak (greeting, nudge, tool result)
     * until the server says the turn is complete. Gemini streams audio faster than real time and
     * the speaker output can stall on a slow network, so the speaker alone cannot tell us the AI
     * is done: without this the mic opens in a gap mid-sentence and the AI hears itself.
     */
    @Volatile
    private var aiTurnActive = false
    @Volatile
    private var lastAiAudioAt = 0L
    @Volatile
    private var awaitingAiSince = 0L
    @Volatile
    private var lastTailRefreshAt = 0L

    /** Mic chunks held back while barge-in is deciding if they are speech or echo. */
    private val preRoll = ArrayDeque<ByteArray>()

    private val pendingAiTranscript = StringBuilder()
    private val transcriptLock = Any()

    override val isConnected: Boolean
        get() = session?.isClosed() == false

    @Volatile
    private var toolsActive = true

    override val settingsToolsActive: Boolean
        get() = toolsActive

    /** Latest server handle for continuing the lesson's conversation on a new connection. */
    private class ResumeHandle(val key: String, val model: String, val handle: String, val receivedAt: Long)

    @Volatile
    private var resumeHandle: ResumeHandle? = null

    /** Learned on the first connect of this app run: does the server accept session resumption? */
    @Volatile
    private var resumption = LiveConnectPlanner.ResumptionSupport.UNKNOWN

    /** Models the server refused in this app run (with the reason); later connects skip them. */
    private val refusedModels = ConcurrentHashMap<String, String>()

    @Volatile
    private var resumed = false

    @Volatile
    private var model: String? = null

    override val lastConnectResumed: Boolean
        get() = resumed

    override val connectedModel: String?
        get() = model

    override suspend fun connect(config: LiveSessionConfig) = lock.withLock {
        // A reconnect keeps the call route, so a Bluetooth headset is not hung up and dialled again.
        closeLocked(holdAudioRoute = true)
        this.config = config
        gate.reset()
        utterance.reset()
        resetTurnLog()
        val startedAt = System.currentTimeMillis()

        val wanted = config.models.ifEmpty { listOf(BuildConfig.LIVE_MODEL) }
        wanted.filter { refusedModels.containsKey(it) }.forEach {
            LiveLog.i(TAG, "Skipping $it: refused earlier in this app run (${refusedModels[it]})")
        }
        // Never end up with nothing to try: the last model gets another chance.
        val models = wanted.filterNot { refusedModels.containsKey(it) }.ifEmpty { listOf(wanted.last()) }
        // The Live API preview may refuse a session because of the tool declarations (and the reason it
        // gives is not always recognisable), so fall back to fewer tools on any failure that is not about
        // authentication, the network or a timeout: lessons still work, settings then go by voice parser.
        val toolSets = listOf(
            config.tools,
            config.tools.filter { it.name == PronunciationDrill.CHECK_ATTEMPT_FUNCTION },
            emptyList()
        ).distinct()
        val handle = usableHandle(config)
        LiveLog.i(
            TAG,
            "Connecting: models=$models tools=${config.tools.size} [${config.tools.joinToString(",") { it.name }}] " +
                "resume=${config.resume} handle=${handle?.let { "yes, ${(startedAt - it.receivedAt) / 1000} s old" } ?: "no"} " +
                "resumption=$resumption instruction=${config.systemInstruction.length} chars bargeIn=${config.enableInterruptions} " +
                "voice=${config.voiceId}"
        )

        val planner = LiveConnectPlanner(
            models = models,
            toolSetCount = toolSets.size,
            hasHandle = { handle != null && handle.model == it },
            resumption = resumption
        )
        var firstError: Exception? = null
        var connected: Pair<LiveSession, LiveConnectPlanner.Attempt>? = null
        while (connected == null) {
            val attempt = planner.next() ?: break
            val declarations = listOf(endLessonDeclaration) + toolSets[attempt.toolSet].map(::toDeclaration)
            val resumeConfig = when (attempt.resume) {
                LiveConnectPlanner.Resume.HANDLE -> handle?.let { SessionResumptionConfig(it.handle) }
                LiveConnectPlanner.Resume.FRESH -> SessionResumptionConfig()
                LiveConnectPlanner.Resume.OFF -> null
            }
            val attemptStartedAt = System.currentTimeMillis()
            val described = "${attempt.model} with ${declarations.size} tools, resume=${attempt.resume}"
            try {
                connected = connectWithTimeout(config, attempt.model, declarations, resumeConfig) to attempt
                LiveLog.i(TAG, "Connect attempt ok: $described in ${System.currentTimeMillis() - attemptStartedAt} ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = LiveConnectPlanner.classify(e, lastModel = attempt.model == models.last())
                LiveLog.w(
                    TAG,
                    "Connect attempt failed ($failure): $described after ${System.currentTimeMillis() - attemptStartedAt} ms",
                    e
                )
                if (failure == LiveConnectPlanner.Failure.MODEL_REFUSED) {
                    refusedModels[attempt.model] = (e.message ?: e::class.java.simpleName).take(200)
                    LiveLog.w(TAG, "Model ${attempt.model} is not available to this app; using the next model until restart")
                }
                planner.failed(attempt, failure)
                firstError?.addSuppressed(e) ?: run { firstError = e }
            }
        }
        val (newSession, attempt) = connected ?: run {
            LiveLog.e(TAG, "Live connection failed after ${System.currentTimeMillis() - startedAt} ms", firstError)
            throw firstError ?: IllegalStateException("Live connection failed")
        }
        val learned = planner.resumptionAfter(attempt)
        if (learned != resumption) LiveLog.i(TAG, "Session resumption on this server: $learned")
        resumption = learned
        resumed = attempt.resume == LiveConnectPlanner.Resume.HANDLE
        // A new server-side session makes the old handle useless; this one sends its own.
        if (!resumed) resumeHandle = null
        toolsActive = attempt.toolSet == 0
        model = attempt.model
        session = newSession
        resetAiTurn()
        startPipeline(newSession)
        startWatchdog(newSession)
        LiveLog.i(
            TAG,
            "Connected to ${attempt.model} with ${toolSets[attempt.toolSet].size + 1} tools, " +
                "${if (resumed) "continuing the lesson's server-side conversation" else "new server-side conversation"}, " +
                "in ${System.currentTimeMillis() - startedAt} ms (half-duplex MicGate active); audio: " +
                "echoCancelled=${audio.isEchoCancelled()} headset=${audio.isHeadsetConnected()} " +
                "carAudio=${audio.isCarAudioConnected()} androidAuto=${audio.isCarConnected()}"
        )
        if (!toolsActive) LiveLog.w(TAG, "Settings tools are off for this connection; settings go by the voice parser")
        Unit
    }

    private fun usableHandle(config: LiveSessionConfig): ResumeHandle? {
        val current = resumeHandle ?: return null
        if (!config.resume || config.resumeKey == null || current.key != config.resumeKey) return null
        if (System.currentTimeMillis() - current.receivedAt > RESUME_HANDLE_MAX_AGE_MS) return null
        return current
    }

    /** One connection attempt that cannot hang forever on a dead network. */
    private suspend fun connectWithTimeout(
        config: LiveSessionConfig,
        modelName: String,
        declarations: List<FunctionDeclaration>,
        resumeConfig: SessionResumptionConfig?
    ): LiveSession =
        withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) {
            val liveModel = createLiveModel(config, modelName, declarations)
            if (resumeConfig != null) liveModel.connect(resumeConfig) else liveModel.connect()
        } ?: throw IllegalStateException("Live connection timed out after ${CONNECT_ATTEMPT_TIMEOUT_MS / 1000} s")

    private fun createLiveModel(
        config: LiveSessionConfig,
        modelName: String,
        declarations: List<FunctionDeclaration>
    ) = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
        modelName = modelName,
        generationConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(voice = Voice(config.voiceId))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            // Lets audio sessions run past the 15-minute context limit by sliding the window.
            contextWindowCompression = ContextWindowCompressionConfig(slidingWindow = SlidingWindow())
        },
        tools = listOf(Tool.functionDeclarations(declarations)),
        systemInstruction = content { text(config.systemInstruction) }
    )

    override suspend fun sendText(text: String) {
        val current = session ?: run {
            LiveLog.w(TAG, "Request dropped, no connection: ${text.take(LOG_TEXT_CHARS)}")
            return
        }
        // Keep the mic shut until the AI has answered, so noise or the learner's chatter cannot
        // start a second, competing turn (the "greets twice" problem).
        expectAiResponse()
        lastRequestAt = System.currentTimeMillis()
        LiveLog.i(TAG, "To model (new turn): ${text.take(LOG_TEXT_CHARS)}")
        try {
            current.send(content(role = "user") { text(text) }, turnComplete = true)
        } catch (e: Exception) {
            awaitingAiSince = 0L
            LiveLog.w(TAG, "Sending the request failed", e)
            throw e
        }
    }

    override suspend fun sendContext(text: String) {
        val current = session ?: return
        // A note while the AI is already answering: no new turn, so it does not answer twice and the
        // learner's microphone stays open.
        LiveLog.i(TAG, "To model (note): ${text.take(LOG_TEXT_CHARS)}")
        current.send(content(role = "user") { text(text) }, turnComplete = false)
    }

    override suspend fun pauseAudio() = pauseAudio(holdRoute = false)

    override suspend fun pauseAudioBriefly() = pauseAudio(holdRoute = true)

    private suspend fun pauseAudio(holdRoute: Boolean) = lock.withLock {
        audioPaused = true
        audio.stopCapture(holdRoute)
        audio.flushPlayback()
        resetAiTurn()
    }

    override suspend fun resumeAudio() = lock.withLock {
        if (session == null) return@withLock
        audioPaused = false
        gate.reset()
        resetAiTurn()
        startCaptureFor(session)
    }

    override suspend fun disconnect() = lock.withLock { closeLocked(holdAudioRoute = false) }

    override fun updateInterruptions(enabled: Boolean) {
        val current = config ?: return
        // DataStore re-emits on every unrelated preference change; only react to a real toggle,
        // otherwise the echo tail would be forgotten and the AI could hear the end of its sentence.
        if (current.enableInterruptions == enabled) return
        config = current.copy(enableInterruptions = enabled)
        // Takes effect on the very next mic chunk; drop anything held back for the old mode.
        gate.reset()
        clearPreRoll()
        LiveLog.i(TAG, "Interruptions (barge-in) ${if (enabled) "enabled" else "disabled"}")
    }

    override fun setVolume(volumeFraction: Float) {
        audio.setVolume(volumeFraction)
    }

    override fun setCarConnected(connected: Boolean) {
        audio.setCarConnected(connected)
    }

    private fun startPipeline(target: LiveSession) {
        audioPaused = false
        outgoing = newOutgoingChannel()
        val channel = outgoing
        sendJob = scope.launch {
            for (chunk in channel) {
                runCatching {
                    // The Live API needs the sample rate in the MIME type; without it the server may
                    // guess a different rate and transcribe the learner's speech as garbage or nothing.
                    target.sendAudioRealtime(InlineData(chunk, INPUT_AUDIO_MIME))
                }.onFailure {
                    if (it is CancellationException) throw it
                    sendFailures++
                    // Once per burst is enough to see it in the log; every chunk would flood it.
                    if (sendFailures == 1 || sendFailures % 50 == 0) LiveLog.w(TAG, "sendAudioRealtime failed ($sendFailures)", it)
                }
            }
        }
        receiveJob = scope.launch {
            try {
                target.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> onContent(message)
                        is LiveServerToolCall -> {
                            AudioDiagnostics.onServerMessage()
                            noteAiTurnStarted("tool call")
                            message.functionCalls.forEach { call -> answerToolCall(target, call) }
                        }
                        is LiveServerToolCallCancellation -> {
                            AudioDiagnostics.onServerMessage()
                            LiveLog.w(TAG, "Model cancelled tool call(s) ${message.functionIds}")
                        }
                        is LiveSessionResumptionUpdate -> onResumptionUpdate(target, message)
                        is LiveServerGoAway -> {
                            LiveLog.i(TAG, "Server will close this connection in ${message.timeLeft}")
                            _events.tryEmit(LiveEvent.GoAway)
                        }
                        is LiveServerSetupComplete -> LiveLog.d(TAG, "Server setup complete")
                        // gemini-3.8-live sends many messages this SDK does not know (empty ones, voiceActivity);
                        // they carry nothing for us, so they are only counted in the diag line.
                        is LiveServerUnknownMessage -> ignoredMessages++
                        else -> LiveLog.d(TAG, "Unhandled server message ${message::class.simpleName}")
                    }
                }
                if (session === target) LiveLog.w(TAG, "Server stream ended (connection closed by the server)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveLog.w(TAG, "Receive loop ended", e)
                if (session === target) _events.tryEmit(LiveEvent.Disconnected(e))
            }
        }
        startCaptureFor(target)
    }

    /** A microphone that dies mid-lesson is handled like a dropped connection: the engine reconnects. */
    private fun startCaptureFor(target: LiveSession?) {
        audio.startCapture(::onMicChunk) { error ->
            LiveLog.w(TAG, "Microphone stopped", error)
            if (target != null && session === target) _events.tryEmit(LiveEvent.Disconnected(error))
        }
    }

    private fun onResumptionUpdate(target: LiveSession, update: LiveSessionResumptionUpdate) {
        val handle = update.newHandle
        val key = config?.resumeKey
        val current = model
        if (update.resumable == true && !handle.isNullOrEmpty() && key != null && current != null && session === target) {
            val first = resumeHandle == null
            resumeHandle = ResumeHandle(key, current, handle, System.currentTimeMillis())
            if (first) LiveLog.i(TAG, "Session resumption handle received: a reconnect can continue this conversation")
        } else {
            LiveLog.d(TAG, "Session resumption update: resumable=${update.resumable} handle=${!handle.isNullOrEmpty()}")
        }
    }

    // region Turn log: what the AI said and how long it took, one line per turn.

    @Volatile
    private var lastRequestAt = 0L
    @Volatile
    private var lastLearnerWordsAt = 0L
    private var turnStarted = false
    private var generationDone = false
    private val turnText = StringBuilder()
    @Volatile
    private var sendFailures = 0
    @Volatile
    private var ignoredMessages = 0

    private fun resetTurnLog() {
        turnStarted = false
        generationDone = false
        turnText.setLength(0)
        lastRequestAt = 0L
        lastLearnerWordsAt = 0L
        sendFailures = 0
        ignoredMessages = 0
    }

    /** First sign of an AI answer (audio, words or a tool call): log how long the learner waited. */
    private fun noteAiTurnStarted(what: String) {
        if (turnStarted) return
        turnStarted = true
        val now = System.currentTimeMillis()
        val afterLearner = lastLearnerWordsAt.takeIf { it > 0 }?.let { "${now - it} ms after the learner's last words" }
        val afterRequest = lastRequestAt.takeIf { it > 0 }?.let { "${now - it} ms after our request" }
        LiveLog.i(TAG, "AI answering ($what): " + (listOfNotNull(afterLearner, afterRequest).joinToString(", ").ifEmpty { "unprompted" }))
    }

    private fun endTurnLog(how: String) {
        if (turnStarted || turnText.isNotEmpty()) {
            LiveLog.i(TAG, "AI turn $how: \"${turnText.toString().trim().take(LOG_TEXT_CHARS)}\"")
        }
        turnStarted = false
        generationDone = false
        turnText.setLength(0)
        lastRequestAt = 0L
        lastLearnerWordsAt = 0L
    }

    // endregion

    private fun onContent(message: LiveServerContent) {
        AudioDiagnostics.onServerMessage()
        if (message.interrupted) {
            synchronized(transcriptLock) { pendingAiTranscript.clear() }
            endTurnLog("interrupted")
            // The AI's turn ended early either way. Only cut the speaker when the learner allowed
            // interruptions; otherwise this is the server reacting to leaked echo/noise and the
            // learner never asked to be talked over.
            endAiTurn()
            if (config?.enableInterruptions == true) {
                audio.flushPlayback()
                _events.tryEmit(LiveEvent.Interrupted)
            } else {
                LiveLog.d(TAG, "Ignoring server interruption: barge-in is off")
            }
        }

        val aiTextChunk = message.outputTranscription?.text?.takeIf { it.isNotEmpty() }
        if (aiTextChunk != null) {
            noteAiTurnStarted("words")
            turnText.append(aiTextChunk)
            synchronized(transcriptLock) {
                pendingAiTranscript.append(aiTextChunk)
            }
        }

        if (!audioPaused) {
            val audioParts = message.content?.parts?.filterIsInstance<InlineDataPart>()
                ?.filter { it.mimeType.startsWith("audio") } ?: emptyList()
            if (audioParts.isNotEmpty()) {
                noteAiTurnStarted("audio")
                audioParts.forEachIndexed { index, part ->
                    val textToEmit = if (index == 0) {
                        synchronized(transcriptLock) {
                            if (pendingAiTranscript.isNotEmpty()) {
                                val text = pendingAiTranscript.toString()
                                pendingAiTranscript.clear()
                                text
                            } else null
                        }
                    } else null

                    if (textToEmit != null) {
                        audio.play(part.inlineData) {
                            Log.d(TAG, "AI speech playing at speaker: $textToEmit")
                            _events.tryEmit(LiveEvent.AiTranscript(textToEmit))
                        }
                    } else {
                        audio.play(part.inlineData)
                    }
                    lastAiAudioAt = System.currentTimeMillis()
                    aiTurnActive = true
                    awaitingAiSince = 0L
                }
            } else if (aiTextChunk != null && !audio.isPlaying()) {
                // If message has text but no audio parts and speaker is idle, emit immediately
                val text = synchronized(transcriptLock) {
                    val t = pendingAiTranscript.toString()
                    pendingAiTranscript.clear()
                    t
                }
                if (text.isNotEmpty()) {
                    LiveLog.d(TAG, "AI speech (text-only): $text")
                    _events.tryEmit(LiveEvent.AiTranscript(text))
                }
            }
        }

        if (message.turnComplete) {
            val remaining = synchronized(transcriptLock) {
                val t = pendingAiTranscript.toString()
                pendingAiTranscript.clear()
                t
            }
            if (remaining.isNotEmpty()) {
                LiveLog.d(TAG, "AI speech (turnComplete flush): $remaining")
                _events.tryEmit(LiveEvent.AiTranscript(remaining))
            }
            endTurnLog(if (message.generationComplete || generationDone) "complete" else "complete (generation not complete)")
            endAiTurn()
        } else if (message.generationComplete) {
            generationDone = true
            LiveLog.d(TAG, "AI generation complete; audio still playing")
        }

        message.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            utterance.appendText(it)
            AudioDiagnostics.onUserTranscript()
            lastLearnerWordsAt = System.currentTimeMillis()
            LiveLog.i(TAG, "Learner: $it")
            _events.tryEmit(LiveEvent.UserTranscript(it))
        }
    }

    /** Runs on the microphone thread for every ~100 ms chunk of audio. */
    private fun onMicChunk(chunk: ByteArray) {
        if (audioPaused) return
        val now = System.currentTimeMillis()
        refreshTail(now)
        val allowBargeIn = config?.enableInterruptions ?: false
        val speakerPlaying = audio.isPlaying()
        val turnLive = aiTurnLive(now)
        val aiAudible = speakerPlaying || turnLive
        // Loudness only matters for telling the learner apart from speaker echo during barge-in.
        val level = if (allowBargeIn && aiAudible) PcmLevel.rms(chunk) else Int.MAX_VALUE
        when (gate.decide(aiAudible = aiAudible, allowBargeIn = allowBargeIn, micRms = level)) {
            MicGate.Decision.MUTE -> {
                val reason = when {
                    speakerPlaying -> "speaker"
                    awaitingAiSince != 0L -> "waitAI"
                    turnLive -> "aiTurn"
                    else -> "tail"
                }
                AudioDiagnostics.onMuted(reason, speakerPlaying)
                if (mutedSince == 0L) mutedSince = now
                if (unstickIfNeeded(now, speakerPlaying)) return
                // Drop microphone audio while the AI talks so it cannot hear itself.
                if (allowBargeIn) holdPreRoll(chunk) else clearPreRoll()
                return
            }
            MicGate.Decision.SEND_NEW_UTTERANCE -> {
                utterance.reset()
                clearPreRoll()
            }
            MicGate.Decision.SEND -> if (aiAudible) sendPreRoll() else clearPreRoll()
        }
        mutedSince = 0L
        AudioDiagnostics.onSent()
        utterance.appendAudio(chunk)
        outgoing.trySend(chunk)
    }

    @Volatile
    private var mutedSince = 0L

    /**
     * Last line of defence: whatever goes wrong with turn tracking or the speaker, the learner must
     * never be left talking to a deaf AI. Turn state is cleared after a long mute with no new AI
     * audio; a speaker that claims to play long after the last AI audio arrived is flushed.
     * @return true if the mic was forced open (the chunk is then dropped, the next one goes through).
     */
    private fun unstickIfNeeded(now: Long, speakerPlaying: Boolean): Boolean {
        val mutedFor = now - mutedSince
        if (mutedFor < MAX_CONTINUOUS_MUTE_MS) return false
        val sinceAiAudio = if (lastAiAudioAt == 0L) Long.MAX_VALUE else now - lastAiAudioAt
        val stuck = if (speakerPlaying) sinceAiAudio > SPEAKER_STUCK_MS else sinceAiAudio > TURN_STUCK_MS
        if (!stuck) return false
        LiveLog.w(TAG, "Mic muted for ${mutedFor}ms (speakerPlaying=$speakerPlaying, sinceAiAudio=${sinceAiAudio}ms); forcing it open")
        if (speakerPlaying) audio.flushPlayback()
        resetAiTurn()
        gate.reset()
        mutedSince = 0L
        AudioDiagnostics.onForcedUnmute()
        return true
    }

    /** True while the AI is (or is about to be) talking, as far as the server's messages say. */
    private fun aiTurnLive(now: Long): Boolean {
        val waitingSince = awaitingAiSince
        if (waitingSince != 0L) {
            if (now - waitingSince < AWAIT_AI_RESPONSE_MS) return true
            awaitingAiSince = 0L // The AI never answered; do not leave the learner unheard.
        }
        return aiTurnActive && now - lastAiAudioAt < AI_TURN_STALE_MS
    }

    private fun expectAiResponse() {
        awaitingAiSince = System.currentTimeMillis()
    }

    private fun endAiTurn() {
        aiTurnActive = false
        awaitingAiSince = 0L
    }

    private fun resetAiTurn() {
        endAiTurn()
        lastAiAudioAt = 0L
        lastTailRefreshAt = 0L
        // A mute left over from the previous connection or pause would look endless and force the
        // mic open at once, right while the AI is about to greet.
        mutedSince = 0L
        clearPreRoll()
    }

    /**
     * Echo takes longer to die out on slow outputs: Bluetooth/car audio lags the playback position
     * the app can see by a few hundred milliseconds, so the tail must be longer there.
     * On the voice-call route the phone / car cancels the echo in hardware, so the tail can be
     * short and a normal speaking voice is enough to interrupt the AI.
     */
    private fun refreshTail(now: Long) {
        if (now - lastTailRefreshAt < TAIL_REFRESH_MS) return
        lastTailRefreshAt = now
        val echoCancelled = audio.isEchoCancelled()
        gate.tailMs = when {
            echoCancelled -> TAIL_ECHO_CANCELLED_MS
            audio.isCarAudioConnected() -> TAIL_CAR_MS
            audio.isHeadsetConnected() -> TAIL_HEADSET_MS
            else -> TAIL_PHONE_SPEAKER_MS
        }
        gate.bargeInRms = if (echoCancelled) BARGE_IN_RMS_ECHO_CANCELLED else BARGE_IN_RMS
    }

    private fun holdPreRoll(chunk: ByteArray) = synchronized(preRoll) {
        preRoll.addLast(chunk)
        while (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
    }

    private fun clearPreRoll() = synchronized(preRoll) { preRoll.clear() }

    /** Barge-in just opened: send what was held back so the first word is not clipped. */
    private fun sendPreRoll() {
        val held = synchronized(preRoll) { preRoll.toList().also { preRoll.clear() } }
        held.forEach {
            utterance.appendAudio(it)
            outgoing.trySend(it)
        }
    }

    private fun answerToolCall(target: LiveSession, call: FunctionCallPart) {
        LiveLog.i(TAG, "Tool call ${call.name} (id=${call.id}) args=${call.args.toString().take(LOG_TEXT_CHARS)}")
        scope.launch {
            val startedAt = System.currentTimeMillis()
            val response = handleFunctionCall(call)
            val took = System.currentTimeMillis() - startedAt
            runCatching { target.sendFunctionResponse(listOf(response)) }
                .onSuccess {
                    LiveLog.i(TAG, "Tool ${call.name} answered after $took ms: ${response.response.toString().take(LOG_TEXT_CHARS)}")
                    lastRequestAt = System.currentTimeMillis()
                    expectAiResponse() // The model now speaks its confirmation / feedback.
                }
                .onFailure { LiveLog.w(TAG, "Could not send the ${call.name} tool response after $took ms", it) }
        }
    }

    private suspend fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        val result: Map<String, Any> = when {
            call.name == PromptTemplates.END_LESSON_FUNCTION -> {
                _events.tryEmit(LiveEvent.EndLessonRequested)
                mapOf("status" to "ok")
            }
            config?.tools?.any { it.name == call.name } == true -> {
                val handler = config?.toolHandler
                val toolCall = LiveToolCall(
                    name = call.name,
                    args = call.args.mapValues { (_, v) -> v.toPlain() },
                    learnerUtterance = utterance.text(),
                    learnerAudio = utterance.audio()
                )
                try {
                    // The model waits for this answer; never leave it waiting forever.
                    val answer = withTimeoutOrNull(TOOL_TIMEOUT_MS) { handler?.handle(toolCall) }
                    if (answer == null) LiveLog.w(TAG, "Tool ${call.name} gave no result within $TOOL_TIMEOUT_MS ms")
                    answer
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LiveLog.e(TAG, "Tool ${call.name} failed", e)
                    null
                } ?: mapOf("status" to "error")
            }
            else -> {
                LiveLog.w(TAG, "Unknown function call: ${call.name}")
                mapOf("status" to "unknown function")
            }
        }
        return FunctionResponsePart(name = call.name, response = result.toJsonObject(), id = call.id)
    }

    private fun toDeclaration(tool: LiveTool): FunctionDeclaration = FunctionDeclaration(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters.associate { param ->
            param.name to when (param.type) {
                LiveToolParam.Type.STRING -> Schema.string(description = param.description)
                LiveToolParam.Type.BOOLEAN -> Schema.boolean(description = param.description)
                LiveToolParam.Type.STRING_LIST -> Schema.array(Schema.string(), description = param.description)
            }
        },
        optionalParameters = tool.parameters.filter { it.optional }.map { it.name }
    )

    /**
     * The SDK has no "closed" callback, so poll for a connection that dropped on its own. Also writes
     * the microphone counters to the lesson log now and then (sent / muted / server messages).
     */
    private fun startWatchdog(target: LiveSession) {
        watchdog?.cancel()
        watchdog = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (session !== target) return@launch
                if (target.isClosed()) {
                    LiveLog.w(TAG, "Live session closed unexpectedly")
                    _events.tryEmit(LiveEvent.Disconnected(null))
                    return@launch
                }
                if (++ticks % DIAG_LOG_EVERY_TICKS == 0) {
                    LiveLog.d(TAG, "diag ${AudioDiagnostics.state.value.summary()} paused=$audioPaused ignoredMsgs=$ignoredMessages")
                }
            }
        }
    }

    private suspend fun closeLocked(holdAudioRoute: Boolean) {
        val current = session
        if (current != null) LiveLog.i(TAG, "Closing the connection to $model")
        session = null
        watchdog?.cancel()
        watchdog = null
        audio.stopCapture(holdAudioRoute)
        audio.stopPlayback()
        synchronized(transcriptLock) { pendingAiTranscript.clear() }
        resetAiTurn()
        outgoing.close()
        receiveJob?.cancel()
        sendJob?.cancel()
        receiveJob = null
        sendJob = null
        if (current != null) {
            runCatching { current.stopReceiving() }
            runCatching { current.close() }.onFailure { LiveLog.w(TAG, "Error while closing session", it) }
        }
    }

    private fun newOutgoingChannel() =
        Channel<ByteArray>(capacity = OUTGOING_CHUNKS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "GeminiLiveManager"
        const val WATCHDOG_INTERVAL_MS = 1_500L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 15_000L

        /** Microphone counters go to the lesson log every 7 watchdog ticks (about 10 s). */
        const val DIAG_LOG_EVERY_TICKS = 7

        /** Resumption handles live 2 hours after a connection ends; stay well inside that. */
        const val RESUME_HANDLE_MAX_AGE_MS = 100 * 60_000L

        /** Requests, tool payloads and AI turns are cut to this length in the log. */
        const val LOG_TEXT_CHARS = 400

        /** Azure pronunciation grading can take two 10 s HTTP timeouts. */
        const val TOOL_TIMEOUT_MS = 25_000L
        const val OUTGOING_CHUNKS = 50
        const val INPUT_AUDIO_MIME = "audio/pcm;rate=16000"

        /** Echo tail (mic stays shut this long after the AI's audio ends) per output route. */
        const val TAIL_PHONE_SPEAKER_MS = 450L
        const val TAIL_HEADSET_MS = 250L
        const val TAIL_CAR_MS = 700L
        const val TAIL_ECHO_CANCELLED_MS = 200L
        const val TAIL_REFRESH_MS = 3_000L

        /** During barge-in, only speech clearly louder than the echo (RMS of 16-bit PCM) counts. */
        const val BARGE_IN_RMS = 2_500

        /** With hardware echo cancellation only a faint residue is left, so normal speech is enough. */
        const val BARGE_IN_RMS_ECHO_CANCELLED = 900
        const val BARGE_IN_CHUNKS = 2
        const val PRE_ROLL_CHUNKS = 2

        /** After we ask the AI to speak, how long we wait for its first audio before giving up. */
        const val AWAIT_AI_RESPONSE_MS = 6_000L

        /** No AI audio for this long without a "turn complete" means the turn is over anyway. */
        const val AI_TURN_STALE_MS = 4_000L

        /** Safety valve: a mute this long is suspicious ... */
        const val MAX_CONTINUOUS_MUTE_MS = 15_000L

        /** ... and is treated as stuck if no AI audio arrived for this long (turn state only) ... */
        const val TURN_STUCK_MS = 3_000L

        /** ... or, when the speaker still claims to play, for longer than any real AI reply. */
        const val SPEAKER_STUCK_MS = 60_000L

        val endLessonDeclaration = FunctionDeclaration(
            name = PromptTemplates.END_LESSON_FUNCTION,
            description = PromptTemplates.END_LESSON_DESCRIPTION,
            parameters = emptyMap()
        )
    }
}

/** Turns JSON tool arguments into plain Kotlin values (String, Boolean, Number, List). */
private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: content.toLongOrNull() ?: content.toDoubleOrNull() ?: contentOrNull
    is JsonArray -> map { it.toPlain() }
    is JsonObject -> mapValues { (_, v) -> v.toPlain() }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

private fun Map<String, Any>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, v) -> v.toJsonElement() })
