package com.speakdrive.ai.live

import kotlinx.coroutines.flow.Flow

/**
 * A realtime speech-to-speech connection to the AI. While connected, the client captures the
 * microphone, plays the AI's voice and reports transcripts through [events].
 */
interface LiveConversationClient {
    val events: Flow<LiveEvent>

    val isConnected: Boolean

    /** Opens a session and starts the audio conversation. Throws on failure. */
    suspend fun connect(config: LiveSessionConfig)

    /** Sends a text instruction that the AI answers out loud. */
    suspend fun sendText(text: String)

    /** Stops the microphone and speaker but keeps the session open. */
    suspend fun pauseAudio()

    suspend fun resumeAudio()

    suspend fun disconnect()
}

data class LiveSessionConfig(
    val systemInstruction: String,
    val voiceId: String,
    /** When false the microphone is muted while the AI speaks, so it cannot hear its own echo. */
    val enableInterruptions: Boolean = false,
    /** Tools the model may call besides ending the lesson. */
    val tools: List<LiveTool> = emptyList(),
    /** Answers calls to [tools]. Runs on a background thread and must return quickly. */
    val toolHandler: LiveToolHandler? = null
)

data class LiveTool(
    val name: String,
    val description: String,
    val parameters: List<LiveToolParam>
)

data class LiveToolParam(
    val name: String,
    val type: Type,
    val description: String,
    val optional: Boolean = false
) {
    enum class Type { STRING, BOOLEAN, STRING_LIST }
}

/**
 * A tool call from the model. [learnerUtterance] (transcript) and [learnerAudio] (16 kHz mono
 * PCM16) are what the learner said since the AI last spoke, so a tool can judge exactly the
 * attempt the model is reacting to.
 */
class LiveToolCall(
    val name: String,
    val args: Map<String, Any?>,
    val learnerUtterance: String,
    val learnerAudio: ByteArray = ByteArray(0)
)

fun interface LiveToolHandler {
    /** Returns the tool's result (strings, numbers, booleans and lists of those). */
    fun handle(call: LiveToolCall): Map<String, Any>
}

sealed interface LiveEvent {
    /** A piece of what the learner said. */
    data class UserTranscript(val text: String) : LiveEvent

    /** A piece of what the AI said. */
    data class AiTranscript(val text: String) : LiveEvent

    /** The server will close the connection soon (Live API connections last about 10 minutes). */
    data object GoAway : LiveEvent

    /** The model asked to end the lesson (the learner said "stop the lesson"). */
    data object EndLessonRequested : LiveEvent

    /** The connection closed without us asking. */
    data class Disconnected(val cause: Throwable?) : LiveEvent
}
