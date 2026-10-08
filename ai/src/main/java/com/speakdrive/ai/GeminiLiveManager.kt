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
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
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

    override suspend fun connect(config: LiveSessionConfig) = lock.withLock {
        closeLocked()
        this.config = config
        gate.reset()
        utterance.reset()

        // The Live API preview may refuse a session because of the tool declarations (and the reason it
        // gives is not always recognisable), so fall back to fewer tools on any failure that is not about
        // authentication, the network or a timeout: lessons still work, settings then go by voice parser.
        val toolSets = listOf(
            config.tools,
            config.tools.filter { it.name == PronunciationDrill.CHECK_ATTEMPT_FUNCTION },
            emptyList()
        ).distinct()
        var firstError: Exception? = null
        var newSession: LiveSession? = null
        var usedDeclarations = 0
        for ((index, tools) in toolSets.withIndex()) {
            val declarations = listOf(endLessonDeclaration) + tools.map(::toDeclaration)
            try {
                newSession = connectWithTimeout(config, declarations)
                usedDeclarations = declarations.size
                toolsActive = index == 0
                if (index > 0) Log.w(TAG, "Connected with ${declarations.size} tools only; settings tools are off")
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live connection with ${declarations.size} tools failed: ${e.message}", e)
                firstError?.addSuppressed(e) ?: run { firstError = e }
                if (!canRetryWithFewerTools(e)) break
            }
        }
        if (newSession == null) throw firstError ?: IllegalStateException("Live connection failed")
        session = newSession
        resetAiTurn()
        startPipeline(newSession)
        startWatchdog(newSession)
        // The caller sends a kick-off / resume message next and the AI answers out loud.
        expectAiResponse()
        Log.i(TAG, "Connected to ${BuildConfig.LIVE_MODEL} with $usedDeclarations tools (half-duplex MicGate active)")
        Unit
    }

    /** One connection attempt that cannot hang forever on a dead network. */
    private suspend fun connectWithTimeout(config: LiveSessionConfig, declarations: List<FunctionDeclaration>): LiveSession =
        withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) { createLiveModel(config, declarations).connect() }
            ?: throw IllegalStateException("Live connection timed out after ${CONNECT_ATTEMPT_TIMEOUT_MS / 1000} s")

    private fun createLiveModel(
        config: LiveSessionConfig,
        declarations: List<FunctionDeclaration>
    ) = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
        modelName = BuildConfig.LIVE_MODEL,
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
        val current = session ?: return
        // Keep the mic shut until the AI has answered, so noise or the learner's chatter cannot
        // start a second, competing turn (the "greets twice" problem).
        expectAiResponse()
        try {
            current.send(content(role = "user") { text(text) }, turnComplete = true)
        } catch (e: Exception) {
            awaitingAiSince = 0L
            throw e
        }
    }

    override suspend fun sendContext(text: String) {
        val current = session ?: return
        // A note while the AI is already answering: no new turn, so it does not answer twice and the
        // learner's microphone stays open.
        current.send(content(role = "user") { text(text) }, turnComplete = false)
    }

    override suspend fun pauseAudio() = lock.withLock {
        audioPaused = true
        audio.stopCapture()
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

    override suspend fun disconnect() = lock.withLock { closeLocked() }

    override fun updateInterruptions(enabled: Boolean) {
        val current = config ?: return
        // DataStore re-emits on every unrelated preference change; only react to a real toggle,
        // otherwise the echo tail would be forgotten and the AI could hear the end of its sentence.
        if (current.enableInterruptions == enabled) return
        config = current.copy(enableInterruptions = enabled)
        // Takes effect on the very next mic chunk; drop anything held back for the old mode.
        gate.reset()
        clearPreRoll()
        Log.i(TAG, "Interruptions (barge-in) ${if (enabled) "enabled" else "disabled"}")
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
                    Log.w(TAG, "sendAudioRealtime failed", it)
                }
            }
        }
        receiveJob = scope.launch {
            try {
                target.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> onContent(message)
                        is LiveServerToolCall -> message.functionCalls.forEach { call -> answerToolCall(target, call) }
                        is LiveServerGoAway -> _events.tryEmit(LiveEvent.GoAway)
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Receive loop ended", e)
                if (session === target) _events.tryEmit(LiveEvent.Disconnected(e))
            }
        }
        startCaptureFor(target)
    }

    /** A microphone that dies mid-lesson is handled like a dropped connection: the engine reconnects. */
    private fun startCaptureFor(target: LiveSession?) {
        audio.startCapture(::onMicChunk) { error ->
            if (target != null && session === target) _events.tryEmit(LiveEvent.Disconnected(error))
        }
    }

    private fun onContent(message: LiveServerContent) {
        AudioDiagnostics.onServerMessage()
        if (message.interrupted) {
            synchronized(transcriptLock) { pendingAiTranscript.clear() }
            // The AI's turn ended early either way. Only cut the speaker when the learner allowed
            // interruptions; otherwise this is the server reacting to leaked echo/noise and the
            // learner never asked to be talked over.
            endAiTurn()
            if (config?.enableInterruptions == true) {
                audio.flushPlayback()
                _events.tryEmit(LiveEvent.Interrupted)
            } else {
                Log.d(TAG, "Ignoring server interruption: barge-in is off")
            }
        }

        val aiTextChunk = message.outputTranscription?.text?.takeIf { it.isNotEmpty() }
        if (aiTextChunk != null) {
            synchronized(transcriptLock) {
                pendingAiTranscript.append(aiTextChunk)
            }
        }

        if (!audioPaused) {
            val audioParts = message.content?.parts?.filterIsInstance<InlineDataPart>()
                ?.filter { it.mimeType.startsWith("audio") } ?: emptyList()
            if (audioParts.isNotEmpty()) {
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
                    Log.d(TAG, "AI speech (text-only): $text")
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
                Log.d(TAG, "AI speech (turnComplete flush): $remaining")
                _events.tryEmit(LiveEvent.AiTranscript(remaining))
            }
            endAiTurn()
        }

        message.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            utterance.appendText(it)
            AudioDiagnostics.onUserTranscript()
            Log.d(TAG, "User speech recognized: $it")
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
        Log.w(TAG, "Mic muted for ${mutedFor}ms (speakerPlaying=$speakerPlaying, sinceAiAudio=${sinceAiAudio}ms); forcing it open")
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

    /**
     * False when fewer tools cannot help: App Check / API key refusals, no network, or a timeout (which
     * would only make the learner wait twice as long).
     */
    private fun canRetryWithFewerTools(e: Exception): Boolean {
        if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.NoRouteToHostException) return false
        val text = (e.message.orEmpty() + " " + e.cause?.message.orEmpty()).lowercase()
        return listOf(
            "timed out", "app check", "appcheck", "attestation", "too many attempts", "unauthenticated",
            "permission", "api key", "api_key", "service_blocked", "service_disabled", "403"
        ).none { it in text }
    }

    private fun answerToolCall(target: LiveSession, call: FunctionCallPart) {
        scope.launch {
            val response = handleFunctionCall(call)
            runCatching { target.sendFunctionResponse(listOf(response)) }
                .onSuccess { expectAiResponse() } // The model now speaks its confirmation / feedback.
                .onFailure { Log.w(TAG, "Could not send tool response", it) }
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
                    withTimeoutOrNull(TOOL_TIMEOUT_MS) { handler?.handle(toolCall) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Tool ${call.name} failed", e)
                    null
                } ?: mapOf("status" to "error")
            }
            else -> {
                Log.w(TAG, "Unknown function call: ${call.name}")
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

    /** The SDK has no "closed" callback, so poll for a connection that dropped on its own. */
    private fun startWatchdog(target: LiveSession) {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (session !== target) return@launch
                if (target.isClosed()) {
                    Log.w(TAG, "Live session closed unexpectedly")
                    _events.tryEmit(LiveEvent.Disconnected(null))
                    return@launch
                }
            }
        }
    }

    private suspend fun closeLocked() {
        val current = session
        session = null
        watchdog?.cancel()
        watchdog = null
        audio.stopCapture()
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
            runCatching { current.close() }.onFailure { Log.w(TAG, "Error while closing session", it) }
        }
    }

    private fun newOutgoingChannel() =
        Channel<ByteArray>(capacity = OUTGOING_CHUNKS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "GeminiLiveManager"
        const val WATCHDOG_INTERVAL_MS = 1_500L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 15_000L

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
