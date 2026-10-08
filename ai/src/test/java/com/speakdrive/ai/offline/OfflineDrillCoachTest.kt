package com.speakdrive.ai.offline

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.FakeOfflineSpeech
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.audio.ListenResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineDrillCoachTest {

    private val speech = FakeOfflineSpeech()
    private val targets = mutableListOf<String?>()
    private val turns = mutableListOf<Pair<Speaker, String>>()
    private val attempts = mutableListOf<PronunciationAttempt>()
    private var endRequested = false

    private val listener = object : OfflineDrillListener {
        override fun onTarget(target: String?) {
            targets += target
        }

        override fun onTurn(speaker: Speaker, text: String) {
            turns += speaker to text
        }

        override fun onAttempt(attempt: PronunciationAttempt) {
            attempts += attempt
        }

        override fun onEndRequested() {
            endRequested = true
        }
    }

    private val coach = OfflineDrillCoach(speech, listener) { 0L }

    private fun TestScope.runCoach(vararg sentences: String) = launch { coach.run(sentences.toList()) }

    @Test
    fun `a correct repetition passes and moves on to the next sentence`() = runTest {
        speech.answer(ListenResult.Heard("I need three tickets"))
        val job = runCoach("I need three tickets.", "Where is the station?")
        runCurrent()

        assertThat(speech.spoken.first()).isEqualTo(OfflineDrillCoach.INTRO_VI)
        assertThat(attempts.single().passed).isTrue()
        assertThat(attempts.single().modelNotes).isEqualTo(OfflineDrillCoach.OFFLINE_NOTE)
        assertThat(targets).containsExactly("I need three tickets.", "Where is the station?").inOrder()
        assertThat(speech.spoken).contains("Repeat after me: Where is the station?")
        assertThat(turns).contains(Speaker.USER to "I need three tickets")
        job.cancel()
    }

    @Test
    fun `a wrong repetition gets one more try with the problem word, then moves on`() = runTest {
        speech.answer(ListenResult.Heard("I need tree tickets"))
        speech.answer(ListenResult.Heard("I need tree tickets"))
        val job = runCoach("I need three tickets.", "Where is the station?")
        runCurrent()

        assertThat(attempts.map { it.attemptNumber to it.passed }).containsExactly(1 to false, 2 to false).inOrder()
        assertThat(speech.spoken).contains("Almost. Watch three. Again: I need three tickets.")
        assertThat(speech.spoken).contains("Good try. Let's move on.")
        assertThat(targets.last()).isEqualTo("Where is the station?")
        job.cancel()
    }

    @Test
    fun `without offline recognition it becomes shadowing`() = runTest {
        speech.listenWorks = false
        val job = runCoach("Hello there.", "Nice to meet you.")
        runCurrent()

        assertThat(speech.spoken.first()).contains(OfflineDrillCoach.SHADOW_ONLY_VI)
        assertThat(speech.listens).isEqualTo(0)
        assertThat(targets).containsExactly("Hello there.")

        advanceTimeBy(OfflineDrillCoach.SHADOW_PAUSE_MS + 2 * OfflineDrillCoach.SHADOW_MS_PER_WORD + 1)
        runCurrent()
        assertThat(targets).containsExactly("Hello there.", "Nice to meet you.").inOrder()
        assertThat(attempts).isEmpty()
        job.cancel()
    }

    @Test
    fun `recognition that stops working switches to shadowing`() = runTest {
        speech.answer(ListenResult.Unavailable)
        val job = runCoach("Hello there.")
        runCurrent()

        assertThat(speech.spoken).contains(OfflineDrillCoach.CANNOT_LISTEN_VI)
        assertThat(speech.listens).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `silence twice moves on to another sentence`() = runTest {
        speech.answer(ListenResult.Silence)
        speech.answer(ListenResult.Silence)
        val job = runCoach("Hello there.", "Nice to meet you.")
        runCurrent()

        assertThat(speech.spoken).contains("Take your time. Again: Hello there.")
        assertThat(speech.spoken).contains("Let's try another one.")
        assertThat(targets.last()).isEqualTo("Nice to meet you.")
        job.cancel()
    }

    @Test
    fun `saying stop ends the lesson and skip moves on without grading`() = runTest {
        speech.answer(ListenResult.Heard("bỏ qua"))
        speech.answer(ListenResult.Heard("dừng lại"))
        val job = runCoach("Hello there.", "Nice to meet you.")
        runCurrent()

        assertThat(attempts).isEmpty()
        assertThat(targets).containsExactly("Hello there.", "Nice to meet you.").inOrder()
        assertThat(endRequested).isTrue()
        assertThat(job.isCompleted).isTrue()
    }

    @Test
    fun `a drill sentence that contains a command word is practised, not obeyed`() = runTest {
        speech.answer(ListenResult.Heard("Stop the car here"))
        val job = runCoach("Stop the car here.")
        runCurrent()

        assertThat(endRequested).isFalse()
        assertThat(attempts.single().passed).isTrue()
        job.cancel()
    }

    @Test
    fun `English instructions when the app speaks English`() = runTest {
        val job = launch { coach.run(listOf("Hello there."), OfflineDrillCoach.Options(vietnamese = false)) }
        runCurrent()

        assertThat(speech.spoken.first()).isEqualTo(OfflineDrillCoach.INTRO_EN)
        job.cancel()
    }
}
