package com.speakdrive.audio

import kotlin.math.sqrt

/** Loudness helpers for little-endian signed 16-bit mono PCM. */
object PcmLevel {

    /** Root-mean-square amplitude (0..32768) of the first [length] bytes of [pcm]. */
    fun rms(pcm: ByteArray, length: Int = pcm.size): Int {
        val samples = minOf(length, pcm.size) / 2
        if (samples == 0) return 0
        var sumSquares = 0.0
        for (i in 0 until samples) {
            val s = sample(pcm, i)
            sumSquares += s.toDouble() * s
        }
        return sqrt(sumSquares / samples).toInt()
    }

    /**
     * True when every sample is exactly zero. A real microphone always has some noise, so this
     * means the audio HAL is muting the chosen source and another one should be tried.
     */
    fun isDigitalSilence(pcm: ByteArray, length: Int = pcm.size): Boolean {
        val samples = minOf(length, pcm.size) / 2
        for (i in 0 until samples) {
            if (sample(pcm, i) != 0) return false
        }
        return true
    }

    private fun sample(pcm: ByteArray, index: Int): Int {
        val lo = pcm[index * 2].toInt() and 0xFF
        val hi = pcm[index * 2 + 1].toInt() // sign-extended
        return (hi shl 8) or lo
    }
}
