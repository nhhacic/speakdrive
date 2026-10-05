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
    fun `barge-in drops quiet chunks that are most likely speaker echo`() {
        val guarded = MicGate(tailMs = 350, bargeInRms = 2_000, bargeInChunks = 2, clock = { now })

        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 500)).isEqualTo(Decision.MUTE)
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 1_999)).isEqualTo(Decision.MUTE)
    }

    @Test
    fun `barge-in opens after sustained loud speech and stays open while the AI talks`() {
        val guarded = MicGate(tailMs = 350, bargeInRms = 2_000, bargeInChunks = 2, clock = { now })

        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 5_000)).isEqualTo(Decision.MUTE)
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 5_000)).isEqualTo(Decision.SEND)
        // The learner pauses for a moment: still open, otherwise words would be cut in half.
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 100)).isEqualTo(Decision.SEND)
    }

    @Test
    fun `a single loud blip does not count as barge-in`() {
        val guarded = MicGate(tailMs = 350, bargeInRms = 2_000, bargeInChunks = 2, clock = { now })

        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 5_000)).isEqualTo(Decision.MUTE)
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 100)).isEqualTo(Decision.MUTE)
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 5_000)).isEqualTo(Decision.MUTE)
    }

    @Test
    fun `barge-in still mutes the echo tail after the AI stops unless the learner is talking`() {
        val guarded = MicGate(tailMs = 350, bargeInRms = 2_000, bargeInChunks = 1, clock = { now })

        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 100)).isEqualTo(Decision.MUTE)
        now = 100
        assertThat(guarded.decide(aiAudible = false, allowBargeIn = true, micRms = 100)).isEqualTo(Decision.MUTE)
        now = 500
        assertThat(guarded.decide(aiAudible = false, allowBargeIn = true, micRms = 100))
            .isEqualTo(Decision.SEND_NEW_UTTERANCE)

        now = 1_000
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true, micRms = 5_000)).isEqualTo(Decision.SEND)
        now = 1_050
        // The learner interrupted: the AI stops and their speech is not cut off by the echo tail.
        assertThat(guarded.decide(aiAudible = false, allowBargeIn = true, micRms = 5_000))
            .isEqualTo(Decision.SEND_NEW_UTTERANCE)
    }

    @Test
    fun `tail can be lengthened for slow outputs such as car bluetooth`() {
        gate.tailMs = 800
        gate.decide(aiAudible = true, allowBargeIn = false)
        now = 700
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.MUTE)
        now = 900
        assertThat(gate.decide(aiAudible = false, allowBargeIn = false)).isEqualTo(Decision.SEND_NEW_UTTERANCE)
    }

    @Test
    fun `turning barge-in off while the AI talks mutes immediately`() {
        val guarded = MicGate(tailMs = 350, bargeInRms = 0, bargeInChunks = 1, clock = { now })

        assertThat(guarded.decide(aiAudible = true, allowBargeIn = true)).isEqualTo(Decision.SEND)
        assertThat(guarded.decide(aiAudible = true, allowBargeIn = false)).isEqualTo(Decision.MUTE)
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
