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
    val enableInterruptions: Boolean = false
)

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
