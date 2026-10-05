package com.speakdrive.ai.live

/**
 * Decides, for every microphone chunk, whether it may be sent to the AI.
 *
 * Half-duplex (the default): the microphone is shut while the AI's voice is audible and for a
 * short tail afterwards, so the AI never hears its own echo. The first chunk after the AI goes
 * quiet starts a new learner utterance.
 *
 * Barge-in: the microphone may stay open while the AI talks, but a chunk only gets through once
 * the learner has been clearly louder than the speaker echo ([bargeInRms]) for [bargeInChunks]
 * chunks in a row. Without this the AI hears its own voice, "interrupts" itself and restarts
 * its greeting. The echo tail after the AI stops is muted in this mode too, unless the learner
 * is already talking.
 */
class MicGate(
    @Volatile var tailMs: Long = DEFAULT_TAIL_MS,
    @Volatile var bargeInRms: Int = 0,
    private val bargeInChunks: Int = 1,
    private val clock: () -> Long = System::currentTimeMillis
) {
    enum class Decision {
        /** Drop the chunk (the AI is talking). */
        MUTE,

        /** Send it as part of the current utterance. */
        SEND,

        /** Send it, and start a new utterance: the AI has just finished speaking. */
        SEND_NEW_UTTERANCE
    }

    private var lastAiAudibleAt = Long.MIN_VALUE
    private var aiSpokeSinceLastSend = false
    private var loudStreak = 0
    private var bargeInOpen = false

    /**
     * @param micRms loudness of this chunk (see `PcmLevel.rms`); only used to tell a real
     * barge-in from speaker echo while the AI is audible.
     */
    fun decide(aiAudible: Boolean, allowBargeIn: Boolean, micRms: Int = Int.MAX_VALUE): Decision {
        val now = clock()
        if (aiAudible) {
            lastAiAudibleAt = now
            aiSpokeSinceLastSend = true
            if (!allowBargeIn) {
                loudStreak = 0
                bargeInOpen = false
                return Decision.MUTE
            }
            if (!bargeInOpen) {
                loudStreak = if (micRms >= bargeInRms) loudStreak + 1 else 0
                if (loudStreak < bargeInChunks) return Decision.MUTE
                bargeInOpen = true
            }
            return Decision.SEND
        }
        loudStreak = 0
        val learnerAlreadyTalking = bargeInOpen
        bargeInOpen = false
        if (!learnerAlreadyTalking && lastAiAudibleAt != Long.MIN_VALUE && now - lastAiAudibleAt < tailMs) {
            return Decision.MUTE
        }
        if (aiSpokeSinceLastSend) {
            aiSpokeSinceLastSend = false
            return Decision.SEND_NEW_UTTERANCE
        }
        return Decision.SEND
    }

    fun reset() {
        lastAiAudibleAt = Long.MIN_VALUE
        aiSpokeSinceLastSend = false
        loudStreak = 0
        bargeInOpen = false
    }

    companion object {
        /** Room echo usually dies out within a few hundred milliseconds. */
        const val DEFAULT_TAIL_MS = 350L
    }
}

/** What the learner has said since the AI last spoke: transcript text and raw 16 kHz PCM. */
class UtteranceBuffer(private val maxAudioBytes: Int = MAX_AUDIO_BYTES) {
    private val text = StringBuilder()
    private var audio = ByteArray(0)
    private var audioSize = 0

    @Synchronized
    fun reset() {
        text.setLength(0)
        audioSize = 0
    }

    @Synchronized
    fun appendText(chunk: String) {
        text.append(chunk)
    }

    @Synchronized
    fun appendAudio(chunk: ByteArray) {
        if (audioSize + chunk.size > audio.size) {
            audio = audio.copyOf(maxOf(audio.size * 2, audioSize + chunk.size, 32_000))
        }
        System.arraycopy(chunk, 0, audio, audioSize, chunk.size)
        audioSize += chunk.size
        // Keep only the most recent audio (pronunciation assessment accepts up to 30 s).
        if (audioSize > maxAudioBytes) {
            val drop = audioSize - maxAudioBytes
            System.arraycopy(audio, drop, audio, 0, maxAudioBytes)
            audioSize = maxAudioBytes
        }
    }

    @Synchronized
    fun text(): String = text.toString().trim()

    @Synchronized
    fun audio(): ByteArray = audio.copyOf(audioSize)

    companion object {
        /** 30 s of 16 kHz mono PCM16. */
        const val MAX_AUDIO_BYTES = 30 * 16_000 * 2
    }
}
