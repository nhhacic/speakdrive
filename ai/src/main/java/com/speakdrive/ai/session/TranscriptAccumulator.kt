package com.speakdrive.ai.session

import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn

/**
 * Turns the streaming transcription chunks from the Live API into conversation turns.
 * Consecutive chunks from the same speaker are joined; a change of speaker starts a new turn.
 */
class TranscriptAccumulator(private val clock: () -> Long = System::currentTimeMillis) {

    private val turns = mutableListOf<TranscriptTurn>()
    private var nextId = 1L

    /** Appends a chunk and returns the updated, immutable list of turns. */
    @Synchronized
    fun append(speaker: Speaker, chunk: String): List<TranscriptTurn> {
        if (chunk.isBlank() && turns.lastOrNull()?.speaker != speaker) return snapshot()
        val last = turns.lastOrNull()
        if (last != null && last.speaker == speaker) {
            turns[turns.lastIndex] = last.copy(text = join(last.text, chunk))
        } else {
            turns += TranscriptTurn(id = nextId++, speaker = speaker, text = chunk.trim(), timestamp = clock())
        }
        return snapshot()
    }

    @Synchronized
    fun snapshot(): List<TranscriptTurn> = turns.map { it.copy(text = normalizeSpaces(it.text)) }

    @Synchronized
    fun clear() {
        turns.clear()
        nextId = 1L
    }

    private fun join(current: String, chunk: String): String = when {
        current.isEmpty() -> chunk.trimStart()
        // The API usually sends chunks with their own leading space; add one only when two words would merge.
        chunk.firstOrNull()?.isWhitespace() == true || current.last().isWhitespace() -> current + chunk
        chunk.first() in NO_SPACE_BEFORE -> current + chunk
        else -> "$current $chunk"
    }

    private fun normalizeSpaces(text: String) = text.replace(WHITESPACE, " ").trim()

    private companion object {
        val NO_SPACE_BEFORE = setOf('.', ',', '!', '?', ';', ':', '\'', ')', '’')
        val WHITESPACE = Regex("\\s+")
    }
}
