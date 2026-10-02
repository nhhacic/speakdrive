package com.speakdrive.ai.live

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.live.MicGate.Decision
import org.junit.Test

class MicGateTest {

    private var now = 0L
    private val gate = MicGate(tailMs = 350, clock = { now })

    @Test
    fun `mutes while the AI is audible and for a short tail after`() {
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.SEND)

        now = 1_000
        assertThat(gate.decide(aiAudible = true, allowBargeIn = false)).isEqualTo(Decision.MUTE)
        now = 1_200
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.MUTE) // echo tail
        now = 1_400
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.SEND_NEW_UTTERANCE)
        now = 1_500
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.SEND)
    }

    @Test
    fun `barge-in keeps the microphone open but still starts a new utterance after the AI`() {
        assertThat(gate.decide(aiAudible = true, allowBargeIn = true)).isEqualTo(Decision.SEND)
        now = 100
        assertThat(gate.decide(aiAudible = false, allowBargeIn = true)).isEqualTo(Decision.SEND_NEW_UTTERANCE)
    }

    @Test
    fun `utterance buffer keeps text and the latest audio`() {
        val buffer = UtteranceBuffer(maxAudioBytes = 4)
        buffer.appendText("I need ")
        buffer.appendText("three tickets")
        buffer.appendAudio(byteArrayOf(1, 2, 3))
        buffer.appendAudio(byteArrayOf(4, 5, 6))

        assertThat(buffer.text()).isEqualTo("I need three tickets")
        assertThat(buffer.audio().toList()).containsExactly(3.toByte(), 4.toByte(), 5.toByte(), 6.toByte()).inOrder()

        buffer.reset()
        assertThat(buffer.text()).isEmpty()
        assertThat(buffer.audio()).isEmpty()
    }
}
