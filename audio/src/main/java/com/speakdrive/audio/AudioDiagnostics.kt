package com.speakdrive.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live counters for the microphone pipeline, so "the AI does not hear me" can be pinned down on a
 * real phone: is the mic delivering sound, is the gate letting it through, does the server
 * transcribe it? Shown on the conversation screen in debug builds and logged under [TAG].
 */
object AudioDiagnostics {
    const val TAG = "SpeakDriveDiag"

    data class Snapshot(
        val micSource: String = "-",
        val route: String = "-",
        val micRms: Int = 0,
        val micPeakRms: Int = 0,
        val captured: Long = 0,
        val sent: Long = 0,
        val muted: Long = 0,
        val muteReason: String = "",
        val aiPlaying: Boolean = false,
        val userTranscripts: Long = 0,
        val serverMessages: Long = 0,
        val forcedUnmutes: Long = 0
    ) {
        fun summary(): String =
            "mic=$micSource route=$route rms=$micRms peak=$micPeakRms cap=$captured sent=$sent mute=$muted" +
                (if (muteReason.isNotEmpty()) "($muteReason)" else "") +
                " ai=${if (aiPlaying) "on" else "off"} srv=$serverMessages heard=$userTranscripts" +
                (if (forcedUnmutes > 0) " unstuck=$forcedUnmutes" else "")
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    @Volatile private var lastLogAt = 0L

    fun reset(source: String, route: String = "-") {
        _state.value = Snapshot(micSource = source, route = route)
    }

    fun onCaptured(rms: Int) = update {
        it.copy(captured = it.captured + 1, micRms = rms, micPeakRms = maxOf(it.micPeakRms, rms))
    }

    fun onSent() = update { it.copy(sent = it.sent + 1, muteReason = "") }

    fun onMuted(reason: String, aiPlaying: Boolean) = update {
        it.copy(muted = it.muted + 1, muteReason = reason, aiPlaying = aiPlaying)
    }

    fun onAiPlaying(playing: Boolean) = update { it.copy(aiPlaying = playing) }

    fun onServerMessage() = update { it.copy(serverMessages = it.serverMessages + 1) }

    fun onUserTranscript() = update { it.copy(userTranscripts = it.userTranscripts + 1) }

    fun onForcedUnmute() = update { it.copy(forcedUnmutes = it.forcedUnmutes + 1) }

    private inline fun update(change: (Snapshot) -> Snapshot) {
        val next = change(_state.value)
        _state.value = next
        val now = System.currentTimeMillis()
        if (now - lastLogAt >= LOG_INTERVAL_MS) {
            lastLogAt = now
            runCatching { Log.i(TAG, next.summary()) }
        }
    }

    private const val LOG_INTERVAL_MS = 2_000L
}
