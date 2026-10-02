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
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
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
    private var interruptionsEnabled = false
    private var watchdog: Job? = null

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
            tools = listOf(Tool.functionDeclarations(listOf(endLessonDeclaration))),
            systemInstruction = content { text(config.systemInstruction) }
        )
        val newSession = model.connect()
        session = newSession
        interruptionsEnabled = config.enableInterruptions
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
                enableInterruptions = interruptionsEnabled
                transcriptHandler = { input, output ->
                    input?.text?.takeIf { it.isNotEmpty() }?.let { _events.tryEmit(LiveEvent.UserTranscript(it)) }
                    output?.text?.takeIf { it.isNotEmpty() }?.let { _events.tryEmit(LiveEvent.AiTranscript(it)) }
                }
                goAwayHandler = { _events.tryEmit(LiveEvent.GoAway) }
                functionCallHandler = ::handleFunctionCall
            }
        )
    }

    private fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        if (call.name == PromptTemplates.END_LESSON_FUNCTION) {
            _events.tryEmit(LiveEvent.EndLessonRequested)
        } else {
            Log.w(TAG, "Unknown function call: ${call.name}")
        }
        return FunctionResponsePart(
            name = call.name,
            response = JsonObject(mapOf("status" to JsonPrimitive("ok"))),
            id = call.id
        )
    }

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
