package com.speakdrive.ai

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveToolCall
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.session.VoiceSettingsTools
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.pronunciation.AzureAssessment
import com.speakdrive.ai.pronunciation.AzurePhoneme
import com.speakdrive.ai.pronunciation.AzureWord
import com.speakdrive.ai.pronunciation.PronunciationDrill
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
    private val assessor = FakeAssessor()
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
            pronunciationAssessor = assessor,
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
    fun `microphone is muted while the AI speaks unless barge-in is allowed`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(live.connects.last().enableInterruptions).isFalse()

        settings.settings = settings.settings.copy(allowBargeIn = true)
        engine.start(LessonRequest())
        assertThat(live.connects.last().enableInterruptions).isTrue()
    }

    @Test
    fun `barge-in setting change during active lesson updates live client immediately`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(live.interruptionsEnabled).isFalse()

        settings.settings = settings.settings.copy(allowBargeIn = true)
        runCurrent()

        assertThat(live.interruptionsEnabled).isTrue()
    }

    @Test
    fun `AI interrupted event clears active speaker immediately`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        say(Speaker.AI, "Hello there, let us talk about...")
        assertThat(engine.activeSpeaker.value).isEqualTo(Speaker.AI)

        live.emit(LiveEvent.Interrupted)
        runCurrent()

        assertThat(engine.activeSpeaker.value).isNull()
    }

    @Test
    fun `pronunciation drill grades every attempt strictly and stores the results`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(allowBargeIn = true)
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))

        val config = live.connects.single()
        assertThat(config.tools.map { it.name }).contains(PronunciationDrill.CHECK_ATTEMPT_FUNCTION)
        assertThat(config.enableInterruptions).isFalse()
        assertThat(config.systemInstruction).contains("NEVER say an attempt was correct")

        say(Speaker.AI, "Repeat after me: I need three tickets.")
        assertThat(engine.drillTarget.value).isEqualTo("I need three tickets.")

        // The model says "correct", but the learner said "tree": the app's verdict wins.
        say(Speaker.USER, "I need tree tickets")
        val first = config.toolHandler!!.handle(
            LiveToolCall(
                PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
                mapOf("target_sentence" to "I need three tickets.", "verdict" to "correct"),
                learnerUtterance = "I need tree tickets"
            )
        )
        assertThat(first["final_verdict"]).isEqualTo("needs_work")

        val second = config.toolHandler.handle(
            LiveToolCall(
                PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
                mapOf("target_sentence" to "I need three tickets.", "verdict" to "correct", "problem_words" to emptyList<String>()),
                learnerUtterance = "I need three tickets"
            )
        )
        assertThat(second["final_verdict"]).isEqualTo("correct")
        assertThat(engine.pronunciationAttempts.value.map { it.attemptNumber to it.passed })
            .containsExactly(1 to false, 2 to true).inOrder()

        engine.end()
        runCurrent()
        val saved = store.saved.last()
        assertThat(saved.pronunciationAttempts).hasSize(2)
        assertThat(saved.summary?.pronunciationScore).isEqualTo(100)
    }

    @Test
    fun `new repeat-after-me sentence replaces pinned card even when user transcript arrives after grading`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))
        val config = live.connects.single()
        say(Speaker.AI, "Repeat after me: To be honest, that makes sense.")
        config.toolHandler!!.handle(
            LiveToolCall(
                PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
                mapOf("target_sentence" to "To be honest, that makes sense.", "verdict" to "correct"),
                learnerUtterance = "To be honest that makes sense"
            )
        )
        say(Speaker.USER, "To be honest, that makes sense.") // late transcript
        say(Speaker.AI, "Correct. Repeat after me: That rings a bell to me.")
        assertThat(engine.drillTarget.value).isEqualTo("That rings a bell to me.")
    }

    @Test
    fun `drill target sentence remains strictly unchanged during AI pronunciation feedback until next sentence is delivered`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))
        val config = live.connects.single()

        // 1. AI introduces sentence 1
        say(Speaker.AI, "Welcome! Repeat after me: She walked across the street.")
        assertThat(engine.drillTarget.value).isEqualTo("She walked across the street.")

        // 2. Learner fails attempt 1
        say(Speaker.USER, "She walk acros the street")
        val first = config.toolHandler!!.handle(
            LiveToolCall(
                PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
                mapOf("target_sentence" to "She walked across the street.", "verdict" to "needs_work"),
                learnerUtterance = "She walk acros the street"
            )
        )
        assertThat(first["final_verdict"]).isEqualTo("needs_work")

        // 3. AI gives coaching feedback on attempt 1.
        // Even with quotes and fragments like "And in" in feedback, target MUST stay unchanged!
        say(Speaker.AI, "You're making progress! Let's tighten them up. For \"she,\" round your lips for a strong \"shish\" sound. And for \"across,\" make that \"o\" more rounded, like...")
        assertThat(engine.drillTarget.value).isEqualTo("She walked across the street.")

        // 4. Learner repeats and passes on attempt 2
        say(Speaker.USER, "She walked across the street.")
        val second = config.toolHandler.handle(
            LiveToolCall(
                PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
                mapOf("target_sentence" to "She walked across the street.", "verdict" to "correct"),
                learnerUtterance = "She walked across the street."
            )
        )
        assertThat(second["final_verdict"]).isEqualTo("correct")

        // 5. AI introduces Sentence 2
        say(Speaker.AI, "Clear and accurate! Next sentence: Repeat after me: I'd like a window seat, please.")
        assertThat(engine.drillTarget.value).isEqualTo("I'd like a window seat, please.")
    }

    @Test
    fun `drill target updates correctly when AI streams sentence incrementally across pauses`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "science", mode = SessionMode.REPEAT_AFTER_ME))

        // First chunk arrives: intro + partial sentence
        say(Speaker.AI, "I'll say a sentence, and you repeat it back just like I said it. Repeat after me: The spacecraft will")
        assertThat(engine.drillTarget.value).isEqualTo("The spacecraft will")

        // AI pauses for more than SPEAKER_IDLE_MS
        advanceTimeBy(2_000)
        runCurrent()

        // Second chunk arrives: completing the sentence
        say(Speaker.AI, " orbit the planet carefully.")
        assertThat(engine.drillTarget.value).isEqualTo("The spacecraft will orbit the planet carefully.")
    }

    private fun drillCall(heard: String, verdict: String = "correct") = LiveToolCall(
        PronunciationDrill.CHECK_ATTEMPT_FUNCTION,
        mapOf("target_sentence" to "I need three tickets", "verdict" to verdict),
        learnerUtterance = heard,
        learnerAudio = ByteArray(32_000)
    )

    private fun azureResult(score: Int, threeAccuracy: Int, error: String = "None") = AzureAssessment(
        pronunciationScore = score, accuracyScore = score, fluencyScore = 95, completenessScore = 100,
        recognizedText = "I need three tickets",
        words = listOf(
            AzureWord("i", 100, "None", emptyList()),
            AzureWord("need", 100, "None", emptyList()),
            AzureWord("three", threeAccuracy, error, listOf(AzurePhoneme("th", threeAccuracy))),
            AzureWord("tickets", 100, "None", emptyList())
        )
    )

    @Test
    fun `azure grades drill attempts when switched on`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(azureEnabled = true, azureRegion = "southeastasia", azureKey = "k")
        assessor.result = azureResult(score = 62, threeAccuracy = 20, error = "Mispronunciation")
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))
        val handler = live.connects.single().toolHandler!!

        // AI and transcript are both satisfied; Azure hears a bad "th".
        val response = handler.handle(drillCall("I need three tickets"))

        assertThat(assessor.calls).containsExactly("I need three tickets" to 32_000)
        assertThat(response["final_verdict"]).isEqualTo("needs_work")
        assertThat(response["azure_weak_sounds"] as List<*>).containsExactly("three (th 20)")
        val attempt = engine.pronunciationAttempts.value.single()
        assertThat(attempt.azure?.pronunciationScore).isEqualTo(62)
        assertThat(attempt.passed).isFalse()

        assessor.result = azureResult(score = 92, threeAccuracy = 90)
        assertThat(handler.handle(drillCall("I need three tickets"))["final_verdict"]).isEqualTo("correct")
    }

    @Test
    fun `azure failure falls back to the other judges and says why`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(azureEnabled = true, azureRegion = "southeastasia", azureKey = "k")
        assessor.failure = java.io.IOException("timeout")
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))

        val response = live.connects.single().toolHandler!!.handle(drillCall("I need three tickets"))

        assertThat(response["final_verdict"]).isEqualTo("correct")
        val attempt = engine.pronunciationAttempts.value.single()
        assertThat(attempt.azure).isNull()
        assertThat(attempt.azureError).contains("timeout")
    }

    @Test
    fun `azure is not called when switched off or not configured`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))
        live.connects.single().toolHandler!!.handle(drillCall("I need three tickets"))
        assertThat(assessor.calls).isEmpty()
        assertThat(engine.pronunciationAttempts.value.single().azureError).isNull()

        settings.settings = settings.settings.copy(azureEnabled = true)
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))
        live.connects.last().toolHandler!!.handle(drillCall("I need three tickets"))
        assertThat(assessor.calls).isEmpty()
        assertThat(engine.pronunciationAttempts.value.single().azureError).contains("Region/Key")
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
    fun `resume during REPEAT_AFTER_ME sends drill-mode resume message and preserves mode in settings`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.REPEAT_AFTER_ME))

        assertThat(settings.settings.lastTopicId).isEqualTo("travel")
        assertThat(settings.settings.lastSessionMode).isEqualTo(SessionMode.REPEAT_AFTER_ME)

        // Set a target sentence
        say(Speaker.AI, "Please repeat: I would like to check in.")
        runCurrent()

        // Pause lesson
        engine.pause()
        advanceTimeBy(2 * 60_000L + 1)
        runCurrent()
        assertThat(live.isConnected).isFalse()

        // Resume lesson
        assertThat(engine.resume()).isTrue()

        val lastResumeText = live.sentTexts.last()
        assertThat(lastResumeText).contains("Repeat-After-Me / Pronunciation Shadowing drill mode")
        assertThat(lastResumeText).contains("Do NOT switch to open conversation")
        assertThat(lastResumeText).doesNotContain(PromptTemplates.RESUME_MESSAGE)
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

    @Test
    fun `in-lesson tool call set_difficulty_level updates level and saves setting`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(level = DifficultyLevel.BEGINNER)
        val engine = createEngine()
        engine.start(LessonRequest(level = DifficultyLevel.BEGINNER))
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.BEGINNER)

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION,
                args = mapOf("level" to "INTERMEDIATE"),
                learnerUtterance = "chuyển sang cấp độ khó B1-B2"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_level"]).isEqualTo("Intermediate")
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.INTERMEDIATE)
    }

    @Test
    fun `in-lesson fallback voice command updates difficulty level and notifies Live client`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(level = DifficultyLevel.BEGINNER)
        val engine = createEngine()
        engine.start(LessonRequest(level = DifficultyLevel.BEGINNER))
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.BEGINNER)

        say(Speaker.USER, "chuyển sang cấp độ khó B1-B2")
        runCurrent()

        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(live.sentTexts.last()).contains("Intermediate")
    }

    @Test
    fun `in-lesson tool call set_difficulty_level can switch to pre-intermediate`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(level = DifficultyLevel.BEGINNER)
        val engine = createEngine()
        engine.start(LessonRequest(level = DifficultyLevel.BEGINNER))
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.BEGINNER)

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION,
                args = mapOf("level" to "PRE_INTERMEDIATE"),
                learnerUtterance = "đổi sang tiền trung cấp A2-B1"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_level"]).isEqualTo("Pre-Intermediate")
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
    }

    @Test
    fun `in-lesson fallback voice command updates difficulty level to elementary`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(level = DifficultyLevel.BEGINNER)
        val engine = createEngine()
        engine.start(LessonRequest(level = DifficultyLevel.BEGINNER))
        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.BEGINNER)

        say(Speaker.USER, "chuyển sang cấp độ sơ cấp A2")
        runCurrent()

        assertThat(engine.lesson.value?.level).isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(live.sentTexts.last()).contains("Elementary")
    }

    @Test
    fun `in-lesson tool call set_vietnamese_help updates setting`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.allowVietnameseHelp).isTrue()

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_VIETNAMESE_HELP_FUNCTION,
                args = mapOf("enabled" to false),
                learnerUtterance = "English only please"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["vietnamese_help"]).isEqualTo(false)
        assertThat(settings.settings.allowVietnameseHelp).isFalse()
    }

    @Test
    fun `start story listening lesson sets mode and generates engaging story kickoff`(): TestResult = engineTest {
        val engine = createEngine()
        val started = engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        assertThat(started).isTrue()
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(live.connects.single().systemInstruction).contains("STORY LISTENING PRACTICE")
        assertThat(live.sentTexts.single()).contains("Start the story listening session now")
    }

    @Test
    fun `in-lesson tool call set_storytelling_style updates storytelling style and saves setting`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_STORYTELLING_STYLE_FUNCTION,
                args = mapOf("style" to "CONTINUOUS"),
                learnerUtterance = "chuyển sang chế độ podcast"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["storytelling_style"]).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS.displayName)
        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
    }

    @Test
    fun `in-lesson tool call next_story switches to a fresh story`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING, topicId = "story_science"))
        val initialScenario = engine.lesson.value?.scenario

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.NEXT_STORY_FUNCTION,
                args = emptyMap(),
                learnerUtterance = "next story please"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_story"]).isNotNull()
        // Scenario should be changed to a fresh one
        assertThat(engine.lesson.value?.scenario?.id).isNotEqualTo(initialScenario?.id)
    }

    @Test
    fun `in-lesson tool call replay_story restarts the current story`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING, topicId = "story_famous"))

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.REPLAY_STORY_FUNCTION,
                args = emptyMap(),
                learnerUtterance = "kể lại từ đầu"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["instruction"].toString()).contains("Restart")
    }

    @Test
    fun `in-lesson fallback voice command next story switches story when Live tool call not triggered`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        val initialScenario = engine.lesson.value?.scenario

        say(Speaker.USER, "đổi chuyện khác đi bạn")
        runCurrent()

        assertThat(live.sentTexts.last()).contains("Switch immediately to the new story")
        assertThat(engine.lesson.value?.scenario?.id).isNotEqualTo(initialScenario?.id)
    }

    @Test
    fun `in-lesson fallback voice command storytelling style updates setting`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)

        say(Speaker.USER, "chuyển sang chế độ podcast")
        runCurrent()

        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        assertThat(live.sentTexts.last()).contains("Continuous")
        assertThat(live.sentTexts.last()).contains("NEVER stop to ask")
    }

    @Test
    fun `english fallback voice command switches back to interactive storytelling`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(storytellingStyle = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        say(Speaker.USER, "switch to interactive mode")
        runCurrent()

        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)
        assertThat(live.sentTexts.last()).contains("Interactive (INTERACTIVE)")
    }

    @Test
    fun `continuous story system instruction overrides the short reply question rule`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(storytellingStyle = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        val instruction = live.connects.single().systemInstruction
        assertThat(instruction).contains("CONTINUOUS PODCAST STORYTELLING MODE (CURRENT MODE)")
        assertThat(instruction).contains("STORYTELLING MODE rules below override this rule")
        assertThat(instruction).contains("NEVER ask the learner anything mid-story")
        assertThat(instruction).contains(PromptTemplates.STORY_END_MARKER)
    }

    @Test
    fun `tool call switching to podcast returns an explicit no-questions instruction`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        val response = live.connects.single().toolHandler!!.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_STORYTELLING_STYLE_FUNCTION,
                args = mapOf("style" to "CONTINUOUS"),
                learnerUtterance = "podcast mode please"
            )
        )
        runCurrent()

        assertThat(response["instruction"].toString()).contains("replaces the Interactive mode")
        assertThat(response["instruction"].toString()).contains("NEVER stop to ask")
    }

    @Test
    fun `podcast mode auto-continues the story instead of nudging with questions or pausing`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(storytellingStyle = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        advanceTimeBy(11_000)
        assertThat(live.sentTexts).contains(PromptTemplates.CONTINUE_STORY_MESSAGE)

        advanceTimeBy(5 * 31_000L)
        assertThat(live.sentTexts).doesNotContain(PromptTemplates.SILENCE_NUDGE)
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `podcast auto-continue stops once the AI says the end of the story`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(storytellingStyle = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        say(Speaker.AI, "They sailed home at last. ${PromptTemplates.STORY_END_MARKER}")
        advanceTimeBy(20_000)
        assertThat(live.sentTexts).doesNotContain(PromptTemplates.CONTINUE_STORY_MESSAGE)

        advanceTimeBy(15_000)
        assertThat(live.sentTexts).contains(PromptTemplates.SILENCE_NUDGE)
    }

    @Test
    fun `interactive story mode keeps the normal silence nudge`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        advanceTimeBy(31_000)

        assertThat(live.sentTexts).doesNotContain(PromptTemplates.CONTINUE_STORY_MESSAGE)
        assertThat(live.sentTexts).contains(PromptTemplates.SILENCE_NUDGE)
    }

    @Test
    fun `switching to podcast on the settings screen mid-story tells the live model`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        // Simulates SettingsViewModel writing DataStore while the story is playing.
        settings.settings = settings.settings.copy(storytellingStyle = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        runCurrent()

        assertThat(live.sentTexts.last()).isEqualTo(
            PromptTemplates.storytellingStyleSwitchMessage(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        )
        assertThat(live.sentTexts.count { it.startsWith("System: The storytelling mode is now") }).isEqualTo(1)
    }

    @Test
    fun `voice style switch is not announced twice when the setting is persisted`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))

        say(Speaker.USER, "chuyển sang chế độ podcast")
        runCurrent()

        assertThat(live.sentTexts.count { it.startsWith("System: The storytelling mode is now") }).isEqualTo(1)
    }

    @Test
    fun `in-lesson tool call set_pronunciation_strictness updates setting and instructs voice confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.AUTO)

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_PRONUNCIATION_STRICTNESS_FUNCTION,
                args = mapOf("strictness" to "AUTO"),
                learnerUtterance = "chấm theo cấp độ học viên"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_strictness"]).isEqualTo("Auto (By Level)")
        assertThat(response["instruction"].toString()).contains("Auto by Level")
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.AUTO)
    }

    @Test
    fun `in-lesson tool call set_pronunciation_strictness to specific level updates setting`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_PRONUNCIATION_STRICTNESS_FUNCTION,
                args = mapOf("strictness" to "ADVANCED"),
                learnerUtterance = "chấm chuẩn bản xứ"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_strictness"]).isEqualTo("Advanced (C1–C2)")
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.ADVANCED)
    }

    @Test
    fun `in-lesson tool call set_barge_in updates setting and instructs voice confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.allowBargeIn).isFalse()

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_BARGE_IN_FUNCTION,
                args = mapOf("enabled" to true),
                learnerUtterance = "bật ngắt lời"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["barge_in"]).isEqualTo(true)
        assertThat(response["instruction"].toString()).contains("Confirm warmly")
        assertThat(settings.settings.allowBargeIn).isTrue()
    }

    @Test
    fun `in-lesson tool call set_ai_voice updates setting and instructs voice confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_VOICE_FUNCTION,
                args = mapOf("voice" to "Charon"),
                learnerUtterance = "đổi giọng Charon"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_voice"]).isEqualTo("Charon")
        assertThat(response["instruction"].toString()).contains("Confirm warmly")
        assertThat(settings.settings.voiceId).isEqualTo("Charon")
    }

    @Test
    fun `in-lesson fallback voice command strictness and barge_in and voice update settings and notify live client`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        say(Speaker.USER, "chấm phát âm dễ hơn một chút")
        runCurrent()
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(live.sentTexts.last()).contains("Elementary")

        say(Speaker.USER, "chấm theo cấp độ đi bạn")
        runCurrent()
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(live.sentTexts.last()).contains("Auto by Level")

        say(Speaker.USER, "bật ngắt lời đi bạn")
        runCurrent()
        assertThat(settings.settings.allowBargeIn).isTrue()
        assertThat(live.sentTexts.last()).contains("barge-in")

        say(Speaker.USER, "đổi sang giọng Puck")
        runCurrent()
        assertThat(settings.settings.voiceId).isEqualTo(AiVoice.PUCK.id)
        assertThat(live.sentTexts.last()).contains("PUCK")
    }

    @Test
    fun `in-lesson fallback voice command uses on-device TTS announcer when Live client fails`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(allowBargeIn = true)
        val engine = createEngine()
        engine.start(LessonRequest())
        live.sendTextFailsWith = RuntimeException("Live client disconnected")

        say(Speaker.USER, "chấm khắt khe")
        runCurrent()

        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(announcer.announcements.last()).contains("Pronunciation strictness set to Upper-Intermediate")

        say(Speaker.USER, "tắt ngắt lời")
        runCurrent()
        assertThat(settings.settings.allowBargeIn).isFalse()
        assertThat(announcer.announcements.last()).contains("Barge in disabled")
    }

    @Test
    fun `lesson start with randomVoice enabled selects an AI voice and keeps it during lesson`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(randomVoice = true)
        val engine = createEngine()
        engine.start(LessonRequest())

        val lessonVoiceId = engine.lesson.value?.voiceId
        assertThat(lessonVoiceId).isNotNull()
        assertThat(AiVoice.entries.map { it.id }).contains(lessonVoiceId)
        assertThat(live.connects.single().voiceId).isEqualTo(lessonVoiceId)
    }

    @Test
    fun `in-lesson tool call set_random_voice updates setting and instructs voice confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.randomVoice).isFalse()

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_RANDOM_VOICE_FUNCTION,
                args = mapOf("enabled" to true),
                learnerUtterance = "bật giọng ngẫu nhiên"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["random_voice"]).isEqualTo(true)
        assertThat(response["instruction"].toString()).contains("Confirm warmly")
        assertThat(settings.settings.randomVoice).isTrue()
    }

    @Test
    fun `in-lesson fallback voice command random voice updates setting and notifies live client`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.randomVoice).isFalse()

        say(Speaker.USER, "chọn giọng ngẫu nhiên mỗi bài học")
        runCurrent()
        assertThat(settings.settings.randomVoice).isTrue()
        assertThat(live.sentTexts.last()).contains("random AI voice")

        say(Speaker.USER, "tắt giọng ngẫu nhiên")
        runCurrent()
        assertThat(settings.settings.randomVoice).isFalse()
        assertThat(live.sentTexts.last()).contains("disable")
    }

    @Test
    fun `in-lesson tool call set_drill_sentence_length updates setting and instructs voice confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        val handler = live.connects.single().toolHandler!!
        val response = handler.handle(
            LiveToolCall(
                name = VoiceSettingsTools.SET_DRILL_SENTENCE_LENGTH_FUNCTION,
                args = mapOf("length" to "ALWAYS_SHORT"),
                learnerUtterance = "rút ngắn câu khi lặp lại"
            )
        )
        runCurrent()

        assertThat(response["status"]).isEqualTo("success")
        assertThat(response["new_length"]).isEqualTo("ALWAYS_SHORT")
        assertThat(response["instruction"].toString()).contains("Confirm warmly")
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
    }

    @Test
    fun `in-lesson fallback voice command drill sentence length updates setting and notifies live client`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        say(Speaker.USER, "chế độ câu ngắn khi lặp lại")
        runCurrent()
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(live.sentTexts.last()).contains("ALWAYS_SHORT")

        say(Speaker.USER, "tự động rút ngắn câu khi kết nối ô tô")
        runCurrent()
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(live.sentTexts.last()).contains("AUTO_ON_CAR")

        say(Speaker.USER, "để độ dài câu tiêu chuẩn")
        runCurrent()
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(live.sentTexts.last()).contains("STANDARD")
    }

    @Test
    fun `car connection changes notify live client during REPEAT_AFTER_ME lesson`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))

        engine.setCarConnected(true)
        runCurrent()
        assertThat(live.sentTexts.last()).contains("connected via Android Auto")

        engine.setCarConnected(false)
        runCurrent()
        assertThat(live.sentTexts.last()).contains("Disconnected from car")
    }

    @Test
    fun `start REPEAT_AFTER_ME lesson while connected to car configures prompt for short repetition sentences`(): TestResult = engineTest {
        val engine = createEngine()
        engine.setCarConnected(true)
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))

        val prompt = live.connects.single().systemInstruction
        assertThat(prompt).contains("CRITICAL DRIVING SAFETY CONSTRAINTS (SHORT REPETITION SENTENCES)")
        assertThat(prompt).contains("5 to 8 words maximum")
    }

    @Test
    fun `isAiThinking is active on kickoff and after learner speech, inactive when AI speaks or paused`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        // After kickoff message is sent, AI is thinking before first speech
        assertThat(engine.isAiThinking.value).isTrue()

        // AI starts speaking -> thinking is false, activeSpeaker is AI
        say(Speaker.AI, "Hello! Where are you going today?")
        runCurrent()
        assertThat(engine.isAiThinking.value).isFalse()
        assertThat(engine.activeSpeaker.value).isEqualTo(Speaker.AI)

        // Learner speaks -> thinking is false, activeSpeaker is USER
        say(Speaker.USER, "I am going to Paris.")
        runCurrent()
        assertThat(engine.isAiThinking.value).isFalse()
        assertThat(engine.activeSpeaker.value).isEqualTo(Speaker.USER)

        // Learner finishes speaking (idle delay expires) -> activeSpeaker becomes null, AI starts thinking
        advanceTimeBy(1_300L)
        runCurrent()
        assertThat(engine.activeSpeaker.value).isNull()
        assertThat(engine.isAiThinking.value).isTrue()

        // AI responds -> thinking becomes false, activeSpeaker is AI
        say(Speaker.AI, "Paris is a wonderful city!")
        runCurrent()
        assertThat(engine.isAiThinking.value).isFalse()
        assertThat(engine.activeSpeaker.value).isEqualTo(Speaker.AI)

        // Pause lesson -> thinking becomes false
        engine.pause()
        runCurrent()
        assertThat(engine.isAiThinking.value).isFalse()
    }

    @Test
    fun `repeat in REPEAT_AFTER_ME mode announces and instructs AI to repeat current drill sentence`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        say(Speaker.AI, "Repeat after me: I would like a cup of coffee.")
        runCurrent()
        assertThat(engine.drillTarget.value).isEqualTo("I would like a cup of coffee.")

        engine.repeat()
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đọc lại câu")
        assertThat(live.sentTexts.last()).contains("Repeat after me: I would like a cup of coffee.")
    }

    @Test
    fun `next in REPEAT_AFTER_ME mode clears target and requests next sentence`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        say(Speaker.AI, "Repeat after me: I would like a cup of coffee.")
        runCurrent()
        assertThat(engine.drillTarget.value).isEqualTo("I would like a cup of coffee.")

        engine.next()
        runCurrent()
        assertThat(engine.drillTarget.value).isNull()
        assertThat(announcer.announcements.last()).contains("Chuyển câu tiếp theo")
        assertThat(live.sentTexts.last()).contains("The learner skipped to the next sentence")
    }

    @Test
    fun `fallback voice command triggers repeat and skip in REPEAT_AFTER_ME`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        say(Speaker.AI, "Repeat after me: Where is the nearest station?")
        runCurrent()

        // User says "đọc lại câu này"
        say(Speaker.USER, "đọc lại câu này")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đọc lại câu")
        assertThat(live.sentTexts.last()).contains("Repeat after me: Where is the nearest station?")

        // User says "câu tiếp theo"
        say(Speaker.USER, "câu tiếp theo")
        runCurrent()
        assertThat(engine.drillTarget.value).isNull()
        assertThat(live.sentTexts.last()).contains("The learner skipped to the next sentence")
    }

    @Test
    fun `handle repeat_drill_sentence live tool call returns repeat instruction`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        say(Speaker.AI, "Repeat after me: Keep your hands on the wheel.")
        runCurrent()

        val config = live.connects.single()
        val result = config.toolHandler!!.handle(
            LiveToolCall(
                VoiceSettingsTools.REPEAT_DRILL_SENTENCE_FUNCTION,
                emptyMap(),
                learnerUtterance = ""
            )
        )
        assertThat(result["status"]).isEqualTo("success")
        assertThat(result["instruction"].toString()).contains("Repeat after me: Keep your hands on the wheel.")
        assertThat(announcer.announcements.last()).contains("Đọc lại câu")
    }

    @Test
    fun `when app loses focus and not connected to car, active lesson auto-pauses`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        engine.setAppFocused(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `when screen turns off and not connected to car, active lesson auto-pauses`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        engine.setScreenOn(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `when connected to Android Auto, losing app focus or screen off does not pause`(): TestResult = engineTest {
        val engine = createEngine()
        engine.setCarConnected(true)
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        engine.setAppFocused(false)
        engine.setScreenOn(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `when autoPauseWhenUnfocused is disabled, losing focus does not pause`(): TestResult = engineTest {
        settings.setAutoPauseWhenUnfocused(false)
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        engine.setAppFocused(false)
        engine.setScreenOn(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `disconnecting from car while phone screen is off pauses active lesson`(): TestResult = engineTest {
        val engine = createEngine()
        engine.setCarConnected(true)
        engine.setScreenOn(false)
        engine.setAppFocused(false)
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        engine.setCarConnected(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `voice setting tool set_auto_pause_when_unfocused updates settings and announces confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        val config = live.connects.single()

        val resultDisable = config.toolHandler!!.handle(
            LiveToolCall(
                VoiceSettingsTools.SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION,
                mapOf("enabled" to false),
                learnerUtterance = ""
            )
        )
        assertThat(resultDisable["status"]).isEqualTo("success")
        assertThat(resultDisable["auto_pause_when_unfocused"]).isEqualTo(false)
        assertThat(announcer.announcements.last()).contains("Đã tắt tự động tạm dừng")

        val resultEnable = config.toolHandler!!.handle(
            LiveToolCall(
                VoiceSettingsTools.SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION,
                mapOf("enabled" to true),
                learnerUtterance = ""
            )
        )
        assertThat(resultEnable["status"]).isEqualTo("success")
        assertThat(resultEnable["auto_pause_when_unfocused"]).isEqualTo(true)
        assertThat(announcer.announcements.last()).contains("Đã bật tự động tạm dừng")
    }

    @Test
    fun `fallback voice command enables and disables auto pause`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        say(Speaker.USER, "tắt tự động tạm dừng khi tắt màn hình")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đã tắt tự động tạm dừng")

        say(Speaker.USER, "bật tự động tạm dừng khi rời app")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đã bật tự động tạm dừng")
    }
}

