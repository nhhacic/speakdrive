package com.speakdrive.ai

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.audio.AudioFocusState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineTest {

    private val live = FakeLiveClient()
    private val summaries = FakeSummaryGenerator()
    private val store = FakeSessionStore()
    private val settings = FakeSettings()
    private val focus = FakeAudioFocus()
    private val connectivity = FakeConnectivity()
    private val announcer = FakeAnnouncer()
    private var micGranted = true
    private var engine: ConversationEngine? = null

    private fun TestScope.createEngine(): ConversationEngine {
        val created = ConversationEngine(
            liveClient = live,
            summaryGenerator = summaries,
            topicManager = TopicManager(),
            sessionStore = store,
            settings = settings,
            audioFocus = focus,
            connectivity = connectivity,
            announcer = announcer,
            micPermission = MicPermissionChecker { micGranted },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        created.clock = { testScheduler.currentTime }
        engine = created
        runCurrent()
        return created
    }

    /**
     * The engine runs endless background loops (silence watchdog) on the test scheduler,
     * so it must be shut down before runTest drains the scheduler.
     */
    private fun engineTest(block: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            block()
        } finally {
            engine?.shutdown()
        }
    }

    private fun TestScope.say(speaker: Speaker, text: String) {
        live.emit(if (speaker == Speaker.USER) LiveEvent.UserTranscript(text) else LiveEvent.AiTranscript(text))
        runCurrent()
    }

    @Test
    fun `start connects, speaks first and becomes active`(): TestResult = engineTest {
        val engine = createEngine()

        val started = engine.start(LessonRequest(topicId = "travel"))

        assertThat(started).isTrue()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
        assertThat(live.connects).hasSize(1)
        assertThat(live.connects.single().systemInstruction).contains("Travel & Getting Around")
        assertThat(live.connects.single().voiceId).isEqualTo(settings.settings.voiceId)
        assertThat(live.sentTexts.single()).contains("Start the lesson")
        assertThat(focus.requests).isEqualTo(1)
        assertThat(settings.settings.lastTopicId).isEqualTo("travel")
    }

    @Test
    fun `start without microphone permission reports it`(): TestResult = engineTest {
        micGranted = false
        val engine = createEngine()

        assertThat(engine.start(LessonRequest())).isFalse()

        assertThat(engine.state.value).isEqualTo(ConversationState.ERROR)
        assertThat(engine.error.value).isEqualTo(EngineError.MissingMicPermission)
        assertThat(live.connects).isEmpty()
    }

    @Test
    fun `start while offline reports it and announces`(): TestResult = engineTest {
        connectivity.online.value = false
        val engine = createEngine()

        assertThat(engine.start(LessonRequest())).isFalse()

        assertThat(engine.error.value).isEqualTo(EngineError.NoNetwork)
        assertThat(announcer.announcements).isNotEmpty()
    }

    @Test
    fun `failed connection releases audio focus`(): TestResult = engineTest {
        live.failNextConnects = 1
        val engine = createEngine()

        assertThat(engine.start(LessonRequest())).isFalse()

        assertThat(engine.state.value).isEqualTo(ConversationState.ERROR)
        assertThat(engine.error.value).isInstanceOf(EngineError.ConnectionFailed::class.java)
        assertThat(focus.state.value).isEqualTo(AudioFocusState.NONE)
        assertThat(engine.lesson.value).isNull()
    }

    @Test
    fun `transcript chunks become turns`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "food"))

        say(Speaker.AI, "Hi! What did")
        say(Speaker.AI, " you eat today?")
        say(Speaker.USER, "I eated pho")

        val turns = engine.transcript.value
        assertThat(turns.map { it.speaker }).containsExactly(Speaker.AI, Speaker.USER).inOrder()
        assertThat(turns.first().text).isEqualTo("Hi! What did you eat today?")
        assertThat(engine.activeSpeaker.value).isEqualTo(Speaker.USER)
    }

    @Test
    fun `phone call pauses the lesson and it resumes afterwards`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        focus.state.value = AudioFocusState.LOSS_TRANSIENT
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
        assertThat(live.audioPaused).isTrue()

        focus.state.value = AudioFocusState.GAIN
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
        assertThat(live.audioPaused).isFalse()
    }

    @Test
    fun `navigation prompt pauses instead of ducking`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        focus.state.value = AudioFocusState.LOSS_TRANSIENT_CAN_DUCK
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `resuming after a long pause reconnects with the conversation so far`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "work"))
        say(Speaker.USER, "I work in a bank")

        engine.pause()
        assertThat(focus.state.value).isEqualTo(AudioFocusState.NONE)
        advanceTimeBy(2 * 60_000L + 1)
        runCurrent()
        assertThat(live.isConnected).isFalse()

        assertThat(engine.resume()).isTrue()

        assertThat(live.connects).hasSize(2)
        assertThat(live.connects.last().systemInstruction).contains("CONVERSATION SO FAR")
        assertThat(live.connects.last().systemInstruction).contains("I work in a bank")
        assertThat(live.sentTexts.last()).isEqualTo(PromptTemplates.RESUME_MESSAGE)
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `go away from the server swaps the connection once the AI is quiet`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Tell me more")
        say(Speaker.AI, "Sure, so")

        live.emit(LiveEvent.GoAway)
        runCurrent()
        assertThat(live.connects).hasSize(1) // still talking

        advanceTimeBy(2_000)
        runCurrent()

        assertThat(live.connects).hasSize(2)
        assertThat(live.connects.last().systemInstruction).contains("Tell me more")
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `network loss waits and continues when back online`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        connectivity.online.value = false
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.WAITING_FOR_NETWORK)
        assertThat(announcer.announcements.last()).contains("Connection lost")

        connectivity.online.value = true
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
        assertThat(live.connects).hasSize(2)
    }

    @Test
    fun `unexpected disconnect reconnects`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        live.isConnected = false
        live.emit(LiveEvent.Disconnected(null))
        runCurrent()

        assertThat(live.connects).hasSize(2)
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `gives up after repeated reconnect failures and saves the lesson`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Hello there")
        live.failNextConnects = 3

        live.emit(LiveEvent.Disconnected(null))
        advanceTimeBy(30_000)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
        assertThat(engine.error.value).isInstanceOf(EngineError.ConnectionFailed::class.java)
        assertThat(store.saved).isNotEmpty()
    }

    @Test
    fun `ending saves the transcript first and the summary afterwards`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "health"))
        say(Speaker.AI, "How are you feeling?")
        say(Speaker.USER, "I have a headache")

        val id = engine.end()

        assertThat(id).isNotNull()
        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
        assertThat(store.saved.first().isCompleted).isFalse()
        assertThat(store.saved.first().transcript).hasSize(2)

        runCurrent()
        val final = store.saved.last()
        assertThat(final.isCompleted).isTrue()
        assertThat(final.summary).isEqualTo(summaries.summary)
        assertThat(final.topicId).isEqualTo("health")
        assertThat(engine.summarizingSessionIds.value).isEmpty()
        assertThat(live.isConnected).isFalse()
        assertThat(focus.state.value).isEqualTo(AudioFocusState.NONE)
    }

    @Test
    fun `a lesson where the learner never spoke is not saved`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.AI, "Hello! Are you there?")

        assertThat(engine.end()).isNull()
        runCurrent()

        assertThat(store.saved).isEmpty()
        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
    }

    @Test
    fun `summary failure stores a fallback summary`(): TestResult = engineTest {
        summaries.fail = true
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Good morning teacher")

        engine.end()
        runCurrent()

        val final = store.saved.last()
        assertThat(final.isCompleted).isTrue()
        assertThat(final.summary?.fluencyScore).isNull()
        assertThat(final.summary?.encouragement).isNotEmpty()
    }

    @Test
    fun `saying end the lesson ends it after the goodbye`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Let's stop the lesson")

        live.emit(LiveEvent.EndLessonRequested)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        advanceTimeBy(4_001)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
    }

    @Test
    fun `a silent learner is nudged and the lesson pauses after three nudges`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        advanceTimeBy(31_000)
        runCurrent()
        assertThat(live.sentTexts).contains(PromptTemplates.SILENCE_NUDGE)

        advanceTimeBy(3 * 31_000L)
        runCurrent()
        assertThat(live.sentTexts.count { it == PromptTemplates.SILENCE_NUDGE }).isEqualTo(3)
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
        assertThat(announcer.announcements.last()).contains("pause")
    }

    @Test
    fun `vocabulary review quizzes due words and reschedules them`(): TestResult = engineTest {
        store.due = listOf(ReviewWord("commute", "đi làm hằng ngày"))
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.VOCAB_REVIEW))
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.VOCAB_REVIEW)
        assertThat(live.connects.single().systemInstruction).contains("commute")

        say(Speaker.USER, "My commute is long")
        engine.end()
        runCurrent()

        assertThat(store.reviewed).containsExactly(listOf("commute"))
    }

    @Test
    fun `vocabulary review without due words becomes free talk`(): TestResult = engineTest {
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.VOCAB_REVIEW))

        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.FREE_TALK)
    }

    @Test
    fun `roleplay uses the chosen scenario`(): TestResult = engineTest {
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.ROLEPLAY, scenarioId = "travel_hotel"))

        assertThat(engine.lesson.value?.scenario?.id).isEqualTo("travel_hotel")
        assertThat(live.connects.single().systemInstruction).contains("hotel receptionist")
    }

    @Test
    fun `starting another lesson ends and saves the current one`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        say(Speaker.USER, "I love Da Nang")

        engine.start(LessonRequest(topicId = "work"))

        assertThat(store.saved.first().topicId).isEqualTo("travel")
        assertThat(engine.lesson.value?.topic?.id).isEqualTo("work")
        assertThat(engine.transcript.value).isEmpty()
    }

    @Test
    fun `active time does not count pauses`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        advanceTimeBy(20_000)
        engine.pause()
        advanceTimeBy(60_000)
        engine.resume()
        advanceTimeBy(10_000)

        assertThat(engine.activeDurationMs()).isEqualTo(30_000)
    }
}
