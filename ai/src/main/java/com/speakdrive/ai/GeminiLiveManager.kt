package com.speakdrive.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.Schema
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.live.LiveTool
import com.speakdrive.ai.live.LiveToolCall
import com.speakdrive.ai.live.LiveToolParam
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech-to-speech conversation over the Gemini Live API (Firebase AI Logic).
 *
 * The SDK's audio conversation records the microphone (VOICE_COMMUNICATION source with
 * acoustic echo cancellation, so the AI does not hear itself through the car speakers),
 * streams it to Gemini, plays the 24 kHz reply on the media stream and supports barge-in.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class GeminiLiveManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LiveConversationClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events: Flow<LiveEvent> = _events.asSharedFlow()

    @Volatile
    private var session: LiveSession? = null
    @Volatile
    private var config: LiveSessionConfig? = null
    private var watchdog: Job? = null

    /** What the learner has said since the AI last spoke; handed to tools such as the pronunciation check. */
    private val learnerUtterance = StringBuilder()
    private var aiSpokeSinceLearner = false

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
        resetUtterance()
        startAudio(newSession)
        startWatchdog(newSession)
        Log.d(TAG, "Connected to ${BuildConfig.LIVE_MODEL}")
        Unit
    }

    override suspend fun sendText(text: String) {
        val current = session ?: return
        current.send(content(role = "user") { text(text) }, turnComplete = true)
    }

    override suspend fun pauseAudio() = lock.withLock {
        session?.takeIf { it.isAudioConversationActive() }?.stopAudioConversation()
        Unit
    }

    override suspend fun resumeAudio() = lock.withLock {
        val current = session ?: return@withLock
        if (!current.isAudioConversationActive()) startAudio(current)
    }

    override suspend fun disconnect() = lock.withLock { closeLocked() }

    private suspend fun startAudio(target: LiveSession) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }
        target.startAudioConversation(
            liveAudioConversationConfig {
                // Without interruptions the SDK pauses the microphone while the AI's audio plays.
                enableInterruptions = config?.enableInterruptions ?: false
                transcriptHandler = { input, output ->
                    input?.text?.takeIf { it.isNotEmpty() }?.let {
                        trackLearner(it)
                        _events.tryEmit(LiveEvent.UserTranscript(it))
                    }
                    output?.text?.takeIf { it.isNotEmpty() }?.let {
                        trackAi()
                        _events.tryEmit(LiveEvent.AiTranscript(it))
                    }
                }
                goAwayHandler = { _events.tryEmit(LiveEvent.GoAway) }
                functionCallHandler = ::handleFunctionCall
            }
        )
    }

    private fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        val result: Map<String, Any> = when {
            call.name == PromptTemplates.END_LESSON_FUNCTION -> {
                _events.tryEmit(LiveEvent.EndLessonRequested)
                mapOf("status" to "ok")
            }
            config?.tools?.any { it.name == call.name } == true -> {
                val handler = config?.toolHandler
                val utterance = synchronized(learnerUtterance) { learnerUtterance.toString().trim() }
                runCatching {
                    handler?.handle(LiveToolCall(call.name, call.args.mapValues { (_, v) -> v.toPlain() }, utterance))
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

    private fun trackLearner(text: String) = synchronized(learnerUtterance) {
        if (aiSpokeSinceLearner) {
            learnerUtterance.setLength(0)
            aiSpokeSinceLearner = false
        }
        learnerUtterance.append(text)
    }

    private fun trackAi() = synchronized(learnerUtterance) { aiSpokeSinceLearner = true }

    private fun resetUtterance() = synchronized(learnerUtterance) {
        learnerUtterance.setLength(0)
        aiSpokeSinceLearner = false
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
        watchdog?.cancel()
        watchdog = null
        val current = session ?: return
        session = null
        runCatching { if (current.isAudioConversationActive()) current.stopAudioConversation() }
        runCatching { current.close() }.onFailure { Log.w(TAG, "Error while closing session", it) }
    }

    private companion object {
        const val TAG = "GeminiLiveManager"
        const val WATCHDOG_INTERVAL_MS = 1_500L

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
