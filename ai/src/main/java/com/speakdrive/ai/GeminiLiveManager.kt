package com.speakdrive.ai

import android.util.Log
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
import com.speakdrive.audio.LiveAudio
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
 * The app drives the audio itself ([LiveAudio]) instead of the SDK's built-in audio conversation:
 * - the microphone is muted exactly while the AI's voice is audible (plus a short tail), so the
 *   AI never hears its own echo through a phone or car speaker ([MicGate]);
 * - the learner's raw audio is kept per utterance ([UtteranceBuffer]) so tools such as the
 *   pronunciation check can send it to Azure.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class GeminiLiveManager @Inject constructor(
    private val audio: LiveAudio
) : LiveConversationClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events: Flow<LiveEvent> = _events.asSharedFlow()

    @Volatile private var session: LiveSession? = null
    @Volatile private var config: LiveSessionConfig? = null
    @Volatile private var audioPaused = false
    private var receiveJob: Job? = null
    private var sendJob: Job? = null
    private var watchdog: Job? = null

    private val gate = MicGate()
    private val utterance = UtteranceBuffer()
    private var outgoing = newOutgoingChannel()

    override val isConnected: Boolean
        get() = session?.isClosed() == false

    override suspend fun connect(config: LiveSessionConfig) = lock.withLock {
        closeLocked()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = BuildConfig.LIVE_MODEL,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(config.voiceId))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
                // Lets audio sessions run past the 15-minute context limit by sliding the window.
                contextWindowCompression = ContextWindowCompressionConfig(slidingWindow = SlidingWindow())
            },
            tools = listOf(Tool.functionDeclarations(listOf(endLessonDeclaration) + config.tools.map(::toDeclaration))),
            systemInstruction = content { text(config.systemInstruction) }
        )
        val newSession = model.connect()
        session = newSession
        this.config = config
        gate.reset()
        utterance.reset()
        startPipeline(newSession)
        startWatchdog(newSession)
        Log.d(TAG, "Connected to ${BuildConfig.LIVE_MODEL}")
        Unit
    }

    override suspend fun sendText(text: String) {
        val current = session ?: return
        current.send(content(role = "user") { text(text) }, turnComplete = true)
    }

    override suspend fun pauseAudio() = lock.withLock {
        audioPaused = true
        audio.stopCapture()
        audio.flushPlayback()
    }

    override suspend fun resumeAudio() = lock.withLock {
        if (session == null) return@withLock
        audioPaused = false
        gate.reset()
        audio.startCapture(::onMicChunk)
    }

    override suspend fun disconnect() = lock.withLock { closeLocked() }

    private fun startPipeline(target: LiveSession) {
        audioPaused = false
        outgoing = newOutgoingChannel()
        val channel = outgoing
        sendJob = scope.launch {
            for (chunk in channel) {
                runCatching { target.sendAudioRealtime(InlineData(chunk, "audio/pcm;rate=16000")) }
                    .onFailure { if (it is CancellationException) throw it }
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
        audio.startCapture(::onMicChunk)
    }

    private fun onContent(message: LiveServerContent) {
        if (message.interrupted) audio.flushPlayback()
        if (!audioPaused) {
            message.content?.parts?.filterIsInstance<InlineDataPart>()?.forEach { part ->
                if (part.mimeType.startsWith("audio")) audio.play(part.inlineData)
            }
        }
        message.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            utterance.appendText(it)
            _events.tryEmit(LiveEvent.UserTranscript(it))
        }
        message.outputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            _events.tryEmit(LiveEvent.AiTranscript(it))
        }
    }

    /** Runs on the microphone thread for every ~100 ms of audio. */
    private fun onMicChunk(chunk: ByteArray) {
        if (audioPaused) return
        val allowBargeIn = config?.enableInterruptions ?: false
        when (gate.decide(aiAudible = audio.isPlaying(), allowBargeIn = allowBargeIn)) {
            MicGate.Decision.MUTE -> return
            MicGate.Decision.SEND_NEW_UTTERANCE -> utterance.reset()
            MicGate.Decision.SEND -> Unit
        }
        utterance.appendAudio(chunk)
        outgoing.trySend(chunk)
    }

    private fun answerToolCall(target: LiveSession, call: FunctionCallPart) {
        // Tools may do network work (Azure); answer off the receive loop so it keeps reading.
        scope.launch {
            val response = handleFunctionCall(call)
            runCatching { target.sendFunctionResponse(listOf(response)) }
                .onFailure { Log.w(TAG, "Could not send tool response", it) }
        }
    }

    private fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        val result: Map<String, Any> = when {
            call.name == PromptTemplates.END_LESSON_FUNCTION -> {
                _events.tryEmit(LiveEvent.EndLessonRequested)
                mapOf("status" to "ok")
            }
            config?.tools?.any { it.name == call.name } == true -> {
                val handler = config?.toolHandler
                runCatching {
                    handler?.handle(
                        LiveToolCall(
                            name = call.name,
                            args = call.args.mapValues { (_, v) -> v.toPlain() },
                            learnerUtterance = utterance.text(),
                            learnerAudio = utterance.audio()
                        )
                    )
                }.onFailure { Log.e(TAG, "Tool ${call.name} failed", it) }.getOrNull()
                    ?: mapOf("status" to "error")
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

    /** Catches connections that close without the receive loop noticing. */
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
        audio.flushPlayback()
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

        /** About 5 s of microphone audio waiting for a slow network. */
        const val OUTGOING_CHUNKS = 50

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
