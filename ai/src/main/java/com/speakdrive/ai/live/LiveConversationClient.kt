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

    /**
     * Adds a note to the conversation without asking for a new answer (used when the AI is already
     * replying, so it does not answer twice). Clients without that ability fall back to [sendText].
     */
    suspend fun sendContext(text: String) = sendText(text)

    /**
     * False when the session had to be opened without the settings tools (Live API limits), so the
     * app's own voice command recognition is the only way settings can change by voice.
     */
    val settingsToolsActive: Boolean get() = true

    /** Stops the microphone and speaker but keeps the session open. */
    suspend fun pauseAudio()

    /**
     * Like [pauseAudio], for a short interruption such as a navigation prompt: the voice-call route
     * stays up for a few seconds, so a Bluetooth headset or car is not hung up and dialled again.
     */
    suspend fun pauseAudioBriefly() = pauseAudio()

    suspend fun resumeAudio()

    suspend fun disconnect()

    /** Updates whether interruptions (barge-in) are enabled for the current live session. */
    fun updateInterruptions(enabled: Boolean) = Unit

    /** Sets the output volume gain for AI playback, from 0.0 (mute) to 1.0 (full). */
    fun setVolume(volumeFraction: Float) = Unit

    /** Informs client whether car / Android Auto connection is active. */
    fun setCarConnected(connected: Boolean) = Unit
}

data class LiveSessionConfig(
    val systemInstruction: String,
    val voiceId: String,
    /** When false the microphone is muted while the AI speaks, so it cannot hear its own echo. */
    val enableInterruptions: Boolean = false,
    /** Tools the model may call besides ending the lesson. */
    val tools: List<LiveTool> = emptyList(),
    /** Answers calls to [tools]. Called from a background coroutine; the handler picks its own thread. */
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
data class LiveToolCall(
    val name: String,
    val args: Map<String, Any?>,
    val learnerUtterance: String = "",
    val learnerAudio: ByteArray = ByteArray(0)
)

fun interface LiveToolHandler {
    /** Returns the tool's result (strings, numbers, booleans and lists of those). */
    suspend fun handle(call: LiveToolCall): Map<String, Any>
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

    /** The AI's spoken turn was interrupted by learner speech. */
    data object Interrupted : LiveEvent
}
