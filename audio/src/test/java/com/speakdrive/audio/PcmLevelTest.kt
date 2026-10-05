package com.speakdrive.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PcmLevelTest {

    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `rms of silence is zero and empty input is safe`() {
        assertThat(PcmLevel.rms(pcm(0, 0, 0, 0))).isEqualTo(0)
        assertThat(PcmLevel.rms(ByteArray(0))).isEqualTo(0)
    }

    @Test
    fun `rms handles positive and negative samples`() {
        assertThat(PcmLevel.rms(pcm(1000, -1000, 1000, -1000))).isEqualTo(1000)
        assertThat(PcmLevel.rms(pcm(-32768, -32768))).isEqualTo(32768)
    }

    @Test
    fun `rms only looks at the requested length`() {
        assertThat(PcmLevel.rms(pcm(500, 500, 30000, 30000), length = 4)).isEqualTo(500)
    }

    @Test
    fun `digital silence is only all zero samples`() {
        assertThat(PcmLevel.isDigitalSilence(pcm(0, 0, 0))).isTrue()
        assertThat(PcmLevel.isDigitalSilence(pcm(0, 0, 1))).isFalse()
        assertThat(PcmLevel.isDigitalSilence(pcm(0, -1, 0))).isFalse()
        assertThat(PcmLevel.isDigitalSilence(pcm(0, 0, 7), length = 4)).isTrue()
    }
}
