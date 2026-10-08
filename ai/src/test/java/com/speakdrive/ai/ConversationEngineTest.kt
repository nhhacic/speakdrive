package com.speakdrive.ai

import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.audio.ListenResult
import com.speakdrive.ai.offline.OfflineDrillCoach
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveToolCall
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.Correction
import com.speakdrive.ai.model.LearnerMemory
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.MistakeReviewResult
import com.speakdrive.ai.model.ReviewMistake
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val SPEAKER_IDLE_MS = 1_200L
private const val TOOL_GRACE_MS = 2_000L

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
    private val offline = FakeOfflineSpeech()
    private var micGranted = true
    private var engine: ConversationEngine? = null
    private val uncaughtErrors = mutableListOf<Throwable>()

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
            dispatcher = StandardTestDispatcher(testScheduler),
            offlineSpeech = offline
        )
        created.clock = { testScheduler.currentTime }
        created.onUncaughtError = { uncaughtErrors += it }
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
            assertThat(uncaughtErrors).isEmpty()
        } finally {
            engine?.shutdown()
        }
    }

    private fun TestScope.say(speaker: Speaker, text: String) {
        live.emit(if (speaker == Speaker.USER) LiveEvent.UserTranscript(text) else LiveEvent.AiTranscript(text))
        runCurrent()
    }

    /**
     * The learner says [text] and stops talking. Voice commands from the transcript are applied
     * once the utterance is finished and the model had a moment to call the tool itself.
     */
    private fun TestScope.sayCommand(text: String) {
        say(Speaker.USER, text)
        advanceTimeBy(SPEAKER_IDLE_MS + TOOL_GRACE_MS + 1)
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

        sayCommand("chuyển sang cấp độ khó B1-B2")
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

        sayCommand("chuyển sang cấp độ sơ cấp A2")
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

        sayCommand("đổi chuyện khác đi bạn")
        runCurrent()

        assertThat(live.sentTexts.last()).contains("Switch immediately to the new story")
        assertThat(engine.lesson.value?.scenario?.id).isNotEqualTo(initialScenario?.id)
    }

    @Test
    fun `in-lesson fallback voice command storytelling style updates setting`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        assertThat(settings.settings.storytellingStyle).isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)

        sayCommand("chuyển sang chế độ podcast")
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

        sayCommand("switch to interactive mode")
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

        sayCommand("chuyển sang chế độ podcast")
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

        sayCommand("chấm phát âm dễ hơn một chút")
        runCurrent()
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(live.sentTexts.last()).contains("Elementary")

        sayCommand("chấm theo cấp độ đi bạn")
        runCurrent()
        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(live.sentTexts.last()).contains("Auto by Level")

        sayCommand("bật ngắt lời đi bạn")
        runCurrent()
        assertThat(settings.settings.allowBargeIn).isTrue()
        assertThat(live.sentTexts.last()).contains("barge-in")

        sayCommand("đổi sang giọng Puck")
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

        sayCommand("chấm khắt khe")
        runCurrent()

        assertThat(settings.settings.pronunciationStrictness).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(announcer.announcements.last()).contains("Pronunciation strictness set to Upper-Intermediate")

        sayCommand("tắt ngắt lời")
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

        sayCommand("chọn giọng ngẫu nhiên mỗi bài học")
        runCurrent()
        assertThat(settings.settings.randomVoice).isTrue()
        assertThat(live.sentTexts.last()).contains("random AI voice")

        sayCommand("tắt giọng ngẫu nhiên")
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

        sayCommand("chế độ câu ngắn khi lặp lại")
        runCurrent()
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(live.sentTexts.last()).contains("ALWAYS_SHORT")

        sayCommand("tự động rút ngắn câu khi kết nối ô tô")
        runCurrent()
        assertThat(settings.settings.drillSentenceLength).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(live.sentTexts.last()).contains("AUTO_ON_CAR")

        sayCommand("để độ dài câu tiêu chuẩn")
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
        sayCommand("đọc lại câu này")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đọc lại câu")
        assertThat(live.sentTexts.last()).contains("Repeat after me: Where is the nearest station?")

        // User says "câu tiếp theo"
        sayCommand("câu tiếp theo")
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

        advanceTimeBy(6000)
        engine.setAppFocused(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `when app loses focus during startup grace period, lesson does not pause`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        // Lose focus within grace period (less than 5000ms)
        advanceTimeBy(1000)
        engine.setAppFocused(false)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `when lesson was auto-paused by losing focus, regaining focus auto-resumes lesson`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        advanceTimeBy(6000)
        engine.setAppFocused(false)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)

        // Regain focus
        engine.setAppFocused(true)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `when lesson was auto-paused by screen off, turning screen on auto-resumes lesson`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        advanceTimeBy(6000)
        engine.setScreenOn(false)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)

        // Turn screen on
        engine.setScreenOn(true)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `when user manually pauses lesson, gaining focus does not auto-resume`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        // User explicitly pauses
        engine.pause()
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)

        // Gaining focus should NOT resume manual pause
        engine.setAppFocused(false)
        runCurrent()
        engine.setAppFocused(true)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `when screen turns off and not connected to car, active lesson auto-pauses`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)

        advanceTimeBy(6000)
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

        advanceTimeBy(6000)
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

        advanceTimeBy(6000)
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

        advanceTimeBy(6000)
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

        sayCommand("tắt tự động tạm dừng khi tắt màn hình")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đã tắt tự động tạm dừng")

        sayCommand("bật tự động tạm dừng khi rời app")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("Đã bật tự động tạm dừng")
    }

    @Test
    fun `tool call switches session mode to REPEAT_AFTER_ME and announces confirmation`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.FREE_TALK))
        val handler = live.connects.single().toolHandler!!

        val result = handler.handle(
            LiveToolCall(
                VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION,
                mapOf("mode" to "REPEAT_AFTER_ME"),
                learnerUtterance = ""
            )
        )
        assertThat(result["status"]).isEqualTo("switched")
        assertThat(result["new_mode"]).isEqualTo(SessionMode.REPEAT_AFTER_ME.name)
        assertThat(announcer.announcements.last()).contains("chế độ luyện phát âm shadowing")

        runCurrent()
        advanceTimeBy(500)
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    @Test
    fun `fallback voice command switches session mode from FREE_TALK to REPEAT_AFTER_ME`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.FREE_TALK))

        sayCommand("chuyển sang luyện phát âm")
        runCurrent()
        assertThat(announcer.announcements.last()).contains("chế độ luyện phát âm shadowing")

        advanceTimeBy(500)
        runCurrent()
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    // region Voice commands from the transcript: only real commands, applied once

    @Test
    fun `ordinary conversation never changes settings or the lesson mode`(): TestResult = engineTest {
        val before = settings.settings
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel", mode = SessionMode.FREE_TALK))
        val afterStart = settings.settings

        listOf(
            "Tôi đang học tiếng Anh",
            "I use English at work every day",
            "Tôi lái xe 2 tiếng mỗi ngày",
            "Anh trai tôi lớn hơn tôi",
            "I love Korean food",
            "Rõ ràng là trời đẹp",
            "Tôi đồng ý với bạn",
            "Can you tell me a story about your weekend?",
            "Hôm qua tôi học từ vựng rất lâu",
            "Tôi đang lái xe đi làm",
            "Tôi nhớ lại chuyến đi đó"
        ).forEach { sayCommand(it) }

        assertThat(settings.settings).isEqualTo(afterStart)
        assertThat(settings.settings.aiVolume).isEqualTo(before.aiVolume)
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.FREE_TALK)
        assertThat(live.connects).hasSize(1)
    }

    @Test
    fun `repeating a drill sentence that sounds like a command is graded, not obeyed`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.REPEAT_AFTER_ME))
        val sentBefore = live.sentTexts.size
        val levelBefore = engine.lesson.value?.level

        for (sentence in listOf("Could you say that again?", "It is easier said than done.", "Actions always speak louder than words.")) {
            say(Speaker.AI, "Repeat after me: $sentence")
            sayCommand(sentence)
        }

        assertThat(live.sentTexts.drop(sentBefore).none { it.contains("hear the sentence again") }).isTrue()
        assertThat(announcer.announcements).doesNotContain("Đọc lại câu")
        assertThat(settings.settings.aiVolume).isEqualTo(80)
        assertThat(engine.lesson.value?.level).isEqualTo(levelBefore)
    }

    @Test
    fun `a command split over several transcript chunks is applied once`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        say(Speaker.USER, "nói nhỏ")
        say(Speaker.USER, " lại")
        say(Speaker.USER, " một chút")
        assertThat(settings.settings.aiVolume).isEqualTo(80) // not before the learner has finished
        advanceTimeBy(SPEAKER_IDLE_MS + TOOL_GRACE_MS + 1)
        runCurrent()

        assertThat(settings.settings.aiVolume).isEqualTo(60)
    }

    @Test
    fun `the safety net steps aside when the model calls the tool itself`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        say(Speaker.USER, "nói nhỏ lại")
        val response = live.connects.single().toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_AI_VOLUME_FUNCTION, mapOf("volume" to "softer"))
        )
        advanceTimeBy(SPEAKER_IDLE_MS + TOOL_GRACE_MS + 1)
        runCurrent()

        assertThat(response["new_volume"]).isEqualTo(60)
        assertThat(settings.settings.aiVolume).isEqualTo(60) // 80 -> 60 once, not 40
    }

    @Test
    fun `a late tool call for a change the safety net already made is not applied twice`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        sayCommand("nói nhỏ lại")
        assertThat(settings.settings.aiVolume).isEqualTo(60)
        val response = live.connects.single().toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_AI_VOLUME_FUNCTION, mapOf("volume" to "softer"))
        )
        runCurrent()

        assertThat(response["already_applied"]).isEqualTo(true)
        assertThat(settings.settings.aiVolume).isEqualTo(60)
    }

    @Test
    fun `asking to go on while a story plays does not restart the story`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        val sessionId = engine.lesson.value?.sessionId

        sayCommand("kể tiếp đi")
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(engine.lesson.value?.sessionId).isEqualTo(sessionId)
        assertThat(live.connects).hasSize(1)
    }

    @Test
    fun `agreeing applies the level suggested by the last summary, in its direction`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(level = DifficultyLevel.INTERMEDIATE)
        summaries.nextRecommendation = LevelRecommendation(
            LevelAdjustmentDirection.LEVEL_DOWN, DifficultyLevel.PRE_INTERMEDIATE, "Ôn lại nền tảng"
        )
        val engine = createEngine()
        engine.start(LessonRequest())
        sayCommand("đồng ý") // nothing pending yet: just conversation
        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.INTERMEDIATE)
        say(Speaker.USER, "I went to work")
        engine.end()
        runCurrent()

        engine.start(LessonRequest())
        sayCommand("đồng ý")

        assertThat(settings.settings.level).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
    }

    @Test
    fun `unusable tool arguments change nothing`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        val handler = live.connects.single().toolHandler!!

        val mode = handler.handle(LiveToolCall(VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION, mapOf("mode" to "xyz")))
        val help = handler.handle(LiveToolCall(VoiceSettingsTools.SET_VIETNAMESE_HELP_FUNCTION, mapOf("enabled" to "maybe")))
        val volume = handler.handle(LiveToolCall(VoiceSettingsTools.SET_AI_VOLUME_FUNCTION, mapOf("volume" to 70L)))
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(mode["status"]).isEqualTo("error")
        assertThat(help["status"]).isEqualTo("error")
        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.FREE_TALK)
        assertThat(settings.settings.allowVietnameseHelp).isTrue()
        assertThat(volume["new_volume"]).isEqualTo(70) // a number from the model is a number, not "softer"
    }

    @Test
    fun `a tool call after the lesson ended cannot bring it back`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        val handler = live.connects.single().toolHandler!!
        engine.end()

        val response = handler.handle(
            LiveToolCall(VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION, mapOf("level" to "ADVANCED"))
        )

        assertThat(response["status"]).isEqualTo("stale")
        assertThat(engine.lesson.value).isNull()
    }

    // endregion

    // region Pauses, reconnects and ending

    @Test
    fun `focus coming back does not resume a lesson paused because the app went to the background`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        advanceTimeBy(6_000)
        engine.setAppFocused(false)
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)

        focus.state.value = AudioFocusState.LOSS_TRANSIENT_CAN_DUCK // a navigation prompt
        runCurrent()
        focus.state.value = AudioFocusState.GAIN
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
    }

    @Test
    fun `pausing during a phone call keeps the lesson paused after the call`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        focus.state.value = AudioFocusState.LOSS_TRANSIENT
        runCurrent()

        engine.pause()
        focus.state.value = AudioFocusState.GAIN
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
        assertThat(engine.resume()).isTrue()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `a call that starts while offline keeps the AI quiet when the network returns`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        connectivity.online.value = false
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.WAITING_FOR_NETWORK)
        val sentBefore = live.sentTexts.size

        focus.state.value = AudioFocusState.LOSS_TRANSIENT
        runCurrent()
        connectivity.online.value = true
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.PAUSED)
        assertThat(live.sentTexts).hasSize(sentBefore) // no "welcome back" over the call
        assertThat(live.audioPaused).isTrue()

        focus.state.value = AudioFocusState.GAIN
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
        assertThat(live.sentTexts.size).isGreaterThan(sentBefore)
    }

    @Test
    fun `stop works at once while a reconnect hangs`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Hello there")
        live.connectGate = CompletableDeferred()
        live.isConnected = false
        live.emit(LiveEvent.Disconnected(null))
        runCurrent()
        assertThat(engine.state.value).isEqualTo(ConversationState.RECONNECTING)

        val savedId = engine.end()

        assertThat(savedId).isNotNull()
        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
    }

    @Test
    fun `a connection that never answers counts as a failed attempt`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Hello there")
        live.connectGate = CompletableDeferred()
        live.emit(LiveEvent.Disconnected(null))
        advanceTimeBy(120_000)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
        assertThat(engine.error.value).isInstanceOf(EngineError.ConnectionFailed::class.java)
    }

    @Test
    fun `go away followed by a disconnect reconnects only once`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.AI, "Once upon a time")

        live.emit(LiveEvent.GoAway)
        runCurrent()
        live.isConnected = false
        live.emit(LiveEvent.Disconnected(null))
        runCurrent()
        assertThat(live.connects).hasSize(2)

        advanceTimeBy(45_000)
        runCurrent()

        assertThat(live.connects).hasSize(2)
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `a late goodbye does not end the lesson started right after`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        say(Speaker.USER, "Let us stop")
        live.emit(LiveEvent.EndLessonRequested)
        runCurrent()

        engine.start(LessonRequest(topicId = "travel"))
        advanceTimeBy(5_000)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `broken storage does not crash a lesson`(): TestResult = engineTest {
        store.failReads = true
        val engine = createEngine()

        assertThat(engine.start(LessonRequest(mode = SessionMode.VOCAB_REVIEW))).isTrue()
        settings.failWrites = true
        sayCommand("nói nhỏ lại")
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `an idle engine has no endless background work`(): TestResult = engineTest {
        createEngine()
        advanceUntilIdle() // would never return if a watchdog looped without a lesson
    }

    @Test
    fun `a story counts as finished only when the AI tells its ending`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(mode = SessionMode.STORY_LISTENING))
        say(Speaker.AI, "She walked to the end of the street. The moral was not clear yet.")
        engine.end()
        runCurrent()

        assertThat(store.saved.last().isCompleted).isFalse()
    }

    // endregion

    // region Learner memory, mistake review, reminders

    private val goMistake = ReviewMistake(7, "I go there yesterday", "I went there yesterday", "Quá khứ của go là went.")
    private val dontMistake = ReviewMistake(9, "She don't like it", "She doesn't like it", "")

    @Test
    fun `mistake review practises due mistakes and records which were fixed`(): TestResult = engineTest {
        store.dueMistakes = listOf(goMistake, dontMistake)
        // Results for mistakes outside the lesson are ignored.
        summaries.mistakeResults = listOf(
            MistakeReviewResult(7, true),
            MistakeReviewResult(9, false),
            MistakeReviewResult(42, true)
        )
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.MISTAKE_REVIEW))

        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.MISTAKE_REVIEW)
        assertThat(engine.lesson.value?.reviewMistakes).containsExactly(goMistake, dontMistake).inOrder()
        val instruction = live.connects.single().systemInstruction
        assertThat(instruction).contains("MISTAKE REVIEW COACH")
        assertThat(instruction).contains("I went there yesterday")
        assertThat(live.sentTexts.single()).contains("Last time you said: I go there yesterday")

        say(Speaker.USER, "I went there yesterday")
        engine.end()
        runCurrent()

        assertThat(store.mistakeReviews)
            .containsExactly(listOf(MistakeReviewResult(7, true), MistakeReviewResult(9, false)))
        assertThat(summaries.lastLesson?.reviewMistakes).hasSize(2)
    }

    @Test
    fun `mistake review uses recent mistakes when none are due`(): TestResult = engineTest {
        store.recentMistakeList = listOf(dontMistake)
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.MISTAKE_REVIEW))

        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.MISTAKE_REVIEW)
        assertThat(engine.lesson.value?.reviewMistakes).containsExactly(dontMistake)
    }

    @Test
    fun `mistake review without any saved mistake becomes free talk`(): TestResult = engineTest {
        val engine = createEngine()

        engine.start(LessonRequest(mode = SessionMode.MISTAKE_REVIEW))

        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.FREE_TALK)
        assertThat(engine.lesson.value?.reviewMistakes).isEmpty()
    }

    @Test
    fun `the AI remembers the learner from earlier lessons`(): TestResult = engineTest {
        store.memory = LearnerMemory(
            recurringMistakes = listOf(Correction("I goed to Hue", "I went to Hue", "")),
            weakWords = listOf("comfortable"),
            facts = listOf("Works as a nurse in Da Nang")
        )
        val engine = createEngine()

        engine.start(LessonRequest(topicId = "travel"))

        val instruction = live.connects.single().systemInstruction
        assertThat(instruction).contains("WHAT YOU REMEMBER FROM EARLIER LESSONS")
        assertThat(instruction).contains("Works as a nurse in Da Nang")
        assertThat(instruction).contains("I goed to Hue")
        assertThat(instruction).contains("comfortable")
    }

    @Test
    fun `new facts from the summary are kept while memory is on`(): TestResult = engineTest {
        summaries.learnerFacts = listOf("Has two kids")
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "daily"))
        say(Speaker.USER, "I have two kids")

        engine.end()
        runCurrent()

        assertThat(summaries.lastLesson?.learnerMemory).isNotNull()
        assertThat(store.saved.last().summary?.learnerFacts).containsExactly("Has two kids")
    }

    @Test
    fun `with memory off nothing is loaded and no new facts are kept`(): TestResult = engineTest {
        settings.settings = settings.settings.copy(rememberLearner = false)
        store.memory = LearnerMemory(facts = listOf("Works as a nurse in Da Nang"))
        summaries.learnerFacts = listOf("Has two kids")
        val engine = createEngine()

        engine.start(LessonRequest(topicId = "daily"))
        say(Speaker.USER, "I have two kids")
        engine.end()
        runCurrent()

        assertThat(store.memoryReads).isEqualTo(0)
        assertThat(live.connects.single().systemInstruction).doesNotContain("nurse")
        assertThat(summaries.lastLesson?.learnerMemory).isNull()
        assertThat(store.saved.last().summary?.learnerFacts).isEmpty()
    }

    @Test
    fun `a broken memory store does not stop the lesson`(): TestResult = engineTest {
        store.failReads = true
        val engine = createEngine()

        assertThat(engine.start(LessonRequest(topicId = "travel"))).isTrue()
        assertThat(engine.lesson.value?.learnerMemory).isEqualTo(LearnerMemory.EMPTY)
    }

    @Test
    fun `turning memory off during a lesson drops the facts of that lesson`(): TestResult = engineTest {
        summaries.learnerFacts = listOf("Has two kids")
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "daily"))
        val config = live.connects.single()

        val result = config.toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_LEARNER_MEMORY_FUNCTION, mapOf("enabled" to false))
        )
        runCurrent()
        say(Speaker.USER, "I have two kids")
        engine.end()
        runCurrent()

        assertThat(result["status"]).isEqualTo("success")
        assertThat(settings.settings.rememberLearner).isFalse()
        assertThat(announcer.announced.last()).contains("tắt ghi nhớ")
        assertThat(store.saved.last().summary?.learnerFacts).isEmpty()
    }

    @Test
    fun `memory can be turned off and on by voice in Vietnamese and English`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        sayCommand("tắt ghi nhớ")
        assertThat(settings.settings.rememberLearner).isFalse()
        assertThat(engine.lesson.value?.learnerMemory).isNull()

        sayCommand("turn on memory")
        assertThat(settings.settings.rememberLearner).isTrue()
        assertThat(engine.lesson.value?.learnerMemory).isNotNull()
    }

    @Test
    fun `practice reminder can be set by the tool`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        val handler = live.connects.single().toolHandler!!

        val set = handler.handle(
            LiveToolCall(VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION, mapOf("enabled" to true, "time" to "07:30"))
        )
        runCurrent()
        assertThat(set["status"]).isEqualTo("success")
        assertThat(settings.settings.practiceReminderEnabled).isTrue()
        assertThat(settings.settings.practiceReminderMinute).isEqualTo(7 * 60 + 30)
        assertThat(announcer.announced.last()).contains("7:30")

        handler.handle(LiveToolCall(VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION, mapOf("enabled" to true, "time" to "auto")))
        runCurrent()
        assertThat(settings.settings.practiceReminderMinute).isEqualTo(LearnerSettings.REMINDER_AUTO)

        handler.handle(LiveToolCall(VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION, mapOf("enabled" to false)))
        runCurrent()
        assertThat(settings.settings.practiceReminderEnabled).isFalse()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `an unreadable reminder time changes nothing`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        val result = live.connects.single().toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION, mapOf("enabled" to true, "time" to "whenever"))
        )

        assertThat(result["status"]).isEqualTo("error")
        assertThat(settings.settings.practiceReminderMinute).isEqualTo(LearnerSettings.REMINDER_AUTO)
    }

    @Test
    fun `practice reminder can be changed by voice`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        sayCommand("tắt nhắc học")
        assertThat(settings.settings.practiceReminderEnabled).isFalse()

        sayCommand("nhắc học lúc 8 giờ tối")
        assertThat(settings.settings.practiceReminderEnabled).isTrue()
        assertThat(settings.settings.practiceReminderMinute).isEqualTo(20 * 60)

        sayCommand("turn off reminders")
        assertThat(settings.settings.practiceReminderEnabled).isFalse()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
    }

    @Test
    fun `streak freeze can be changed by voice and by the tool`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        sayCommand("tắt bảo toàn chuỗi")
        assertThat(settings.settings.streakFreezeEnabled).isFalse()
        assertThat(announcer.announced.last()).contains("bảo toàn chuỗi")

        // Later than the window in which a tool call repeating the fallback's change is ignored.
        advanceTimeBy(10_000)
        live.connects.single().toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_STREAK_FREEZE_FUNCTION, mapOf("enabled" to true))
        )
        runCurrent()
        assertThat(settings.settings.streakFreezeEnabled).isTrue()
    }

    @Test
    fun `switching to mistake review by voice starts a mistake review lesson`(): TestResult = engineTest {
        store.dueMistakes = listOf(goMistake)
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        say(Speaker.USER, "Hello")

        sayCommand("chuyển sang ôn lỗi sai")
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(engine.lesson.value?.mode).isEqualTo(SessionMode.MISTAKE_REVIEW)
    }

    // endregion

    // region Offline practice

    @Test
    fun `losing the network starts offline practice and the AI takes over again when it is back`(): TestResult = engineTest {
        offline.available = true
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        connectivity.online.value = false
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.WAITING_FOR_NETWORK)
        assertThat(engine.offlinePractice.value).isTrue()
        assertThat(announcer.announcements.none { it.contains("Connection lost") }).isTrue()
        assertThat(offline.spoken.first()).isEqualTo(OfflineDrillCoach.INTRO_VI)
        assertThat(engine.drillTarget.value).isNotNull()
        assertThat(engine.transcript.value.last().text).startsWith("Repeat after me:")

        connectivity.online.value = true
        runCurrent()

        assertThat(engine.offlinePractice.value).isFalse()
        assertThat(offline.releases).isEqualTo(1)
        assertThat(engine.drillTarget.value).isNull()
        assertThat(engine.state.value).isEqualTo(ConversationState.ACTIVE)
        assertThat(live.connects).hasSize(2)
        // The AI hears what was practised offline.
        assertThat(live.connects.last().systemInstruction).contains("Repeat after me:")
    }

    @Test
    fun `offline attempts are kept with the lesson and their time counts`(): TestResult = engineTest {
        offline.available = true
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        connectivity.online.value = false
        runCurrent()
        val target = engine.drillTarget.value!!

        offline.answer(ListenResult.Heard(target))
        runCurrent()
        advanceTimeBy(60_000)
        engine.end()
        runCurrent()

        assertThat(engine.pronunciationAttempts.value.single().passed).isTrue()
        val saved = store.saved.last()
        assertThat(saved.pronunciationAttempts.single().modelNotes).isEqualTo(OfflineDrillCoach.OFFLINE_NOTE)
        assertThat(saved.transcript.map { it.text }).contains(target)
        assertThat(saved.activeDurationMs).isAtLeast(60_000L)
        assertThat(offline.releases).isEqualTo(1)
    }

    @Test
    fun `saying stop during offline practice ends the lesson`(): TestResult = engineTest {
        offline.available = true
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        connectivity.online.value = false
        runCurrent()

        offline.answer(ListenResult.Heard("dừng lại"))
        runCurrent()

        assertThat(engine.state.value).isEqualTo(ConversationState.ENDED)
        assertThat(engine.offlinePractice.value).isFalse()
        assertThat(store.saved).isNotEmpty()
    }

    @Test
    fun `with offline practice turned off the lesson just waits for the network`(): TestResult = engineTest {
        offline.available = true
        settings.settings = settings.settings.copy(offlinePracticeEnabled = false)
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        connectivity.online.value = false
        runCurrent()

        assertThat(engine.offlinePractice.value).isFalse()
        assertThat(offline.spoken).isEmpty()
        assertThat(announcer.announcements.last()).contains("Connection lost")
    }

    @Test
    fun `pausing while offline stops the practice and resuming starts it again`(): TestResult = engineTest {
        offline.available = true
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        connectivity.online.value = false
        runCurrent()

        engine.pause()
        runCurrent()
        assertThat(engine.offlinePractice.value).isFalse()
        assertThat(engine.state.value).isEqualTo(ConversationState.WAITING_FOR_NETWORK)

        assertThat(engine.resume()).isTrue()
        runCurrent()
        assertThat(engine.offlinePractice.value).isTrue()
    }

    @Test
    fun `a phone call while offline stops the practice until the call ends`(): TestResult = engineTest {
        offline.available = true
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))
        connectivity.online.value = false
        runCurrent()

        focus.state.value = AudioFocusState.LOSS_TRANSIENT
        runCurrent()
        assertThat(engine.offlinePractice.value).isFalse()

        focus.state.value = AudioFocusState.GAIN
        runCurrent()
        assertThat(engine.offlinePractice.value).isTrue()
    }

    @Test
    fun `offline practice can be turned off by voice and by the tool`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest(topicId = "travel"))

        sayCommand("tắt luyện offline")
        assertThat(settings.settings.offlinePracticeEnabled).isFalse()
        assertThat(announcer.announced.last()).contains("luyện offline")

        advanceTimeBy(10_000)
        live.connects.single().toolHandler!!.handle(
            LiveToolCall(VoiceSettingsTools.SET_OFFLINE_PRACTICE_FUNCTION, mapOf("enabled" to true))
        )
        runCurrent()
        assertThat(settings.settings.offlinePracticeEnabled).isTrue()
    }

    // endregion

    @Test
    fun `car auto-start, daily goal, Azure and screen settings change by voice tool and confirm out loud`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())
        val handler = live.connects.single().toolHandler!!

        val car = handler.handle(LiveToolCall(VoiceSettingsTools.SET_AUTO_START_IN_CAR_FUNCTION, mapOf("enabled" to false)))
        val goal = handler.handle(LiveToolCall(VoiceSettingsTools.SET_DAILY_GOAL_FUNCTION, mapOf("minutes" to "25")))
        val azure = handler.handle(LiveToolCall(VoiceSettingsTools.SET_AZURE_SCORING_FUNCTION, mapOf("enabled" to true)))
        val screen = handler.handle(LiveToolCall(VoiceSettingsTools.SET_SCREEN_AWAKE_FUNCTION, mapOf("mode" to "FOLLOW_SYSTEM")))
        runCurrent()

        assertThat(car["status"]).isEqualTo("success")
        assertThat(settings.settings.autoStartOnCarConnect).isFalse()
        assertThat(goal["daily_goal_minutes"]).isEqualTo(25)
        assertThat(settings.settings.dailyGoalMinutes).isEqualTo(25)
        assertThat(settings.settings.azureEnabled).isTrue()
        assertThat(azure["azure_key_missing"]).isEqualTo(true) // no key entered yet: the learner is told
        assertThat(settings.settings.screenAwakeMode).isEqualTo(ScreenAwakeMode.FOLLOW_SYSTEM)
        assertThat(screen["status"]).isEqualTo("success")
        assertThat(announcer.announcements.any { it.contains("Android Auto") }).isTrue()
        assertThat(announcer.announcements.any { it.contains("25") }).isTrue()
    }

    @Test
    fun `car auto-start and daily goal change from the transcript when the model does not call the tool`(): TestResult = engineTest {
        val engine = createEngine()
        engine.start(LessonRequest())

        sayCommand("tắt tự động học khi lên xe")
        sayCommand("đặt mục tiêu 30 phút mỗi ngày")
        sayCommand("tắt màn hình sau 2 phút")

        assertThat(settings.settings.autoStartOnCarConnect).isFalse()
        assertThat(settings.settings.dailyGoalMinutes).isEqualTo(30)
        assertThat(settings.settings.screenAwakeMode).isEqualTo(ScreenAwakeMode.AFTER_2_MINUTES)
    }
}
