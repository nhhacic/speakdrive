package com.speakdrive.playback

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.network.ConnectivityObserver
import com.speakdrive.ai.pronunciation.AzureAssessment
import com.speakdrive.ai.pronunciation.AzureSpeechConfig
import com.speakdrive.ai.pronunciation.PronunciationAssessor
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.ai.summary.SummaryGenerator
import com.speakdrive.audio.AudioFocus
import com.speakdrive.audio.VoiceAnnouncer
import com.speakdrive.auto.MediaIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarConnectionAutoStarterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val carFlow = MutableSharedFlow<Boolean>()
    private var playedMediaId: String? = null
    private var autoStartEnabled = true
    private var engineCarConnected: Boolean = false
    private val engineStateFlow = MutableStateFlow(ConversationState.IDLE)

    private val fakeCarConnection = object : CarConnectionObserver() {
        override val isConnectedToCar = carFlow
    }

    private val fakePlaybackConnection = object : PlaybackConnection() {
        override suspend fun play(mediaId: String) {
            playedMediaId = mediaId
        }
    }

    private class DummyLiveClient : LiveConversationClient {
        override val isConnected: Boolean = false
        override val events: Flow<LiveEvent> = emptyFlow()
        override suspend fun connect(config: LiveSessionConfig) {}
        override suspend fun sendText(text: String) {}
        override suspend fun pauseAudio() {}
        override suspend fun resumeAudio() {}
        override suspend fun disconnect() {}
    }

    private class DummySummaryGenerator : SummaryGenerator {
        override suspend fun summarize(
            lesson: ActiveLesson,
            transcript: List<TranscriptTurn>,
            attempts: List<PronunciationAttempt>
        ): SessionSummary = SessionSummary(null, null, null, emptyList(), emptyList(), "", "")
    }

    private class DummySessionStore : SessionStore {
        override suspend fun saveSession(session: CompletedSession) {}
        override suspend fun recentTopicIds(limit: Int): List<String> = emptyList()
        override suspend fun wordsDueForReview(limit: Int): List<ReviewWord> = emptyList()
        override suspend fun markWordsReviewed(words: List<String>) {}
    }

    private class DummySettings : LearningSettings {
        override suspend fun snapshot(): LearnerSettings = LearnerSettings()
        override suspend fun setLevel(level: DifficultyLevel) {}
        override suspend fun setVoice(voice: AiVoice) {}
        override suspend fun setRandomVoice(enabled: Boolean) {}
        override suspend fun setAllowVietnameseHelp(allowed: Boolean) {}
        override suspend fun setAllowBargeIn(allowed: Boolean) {}
        override suspend fun setPronunciationStrictness(strictness: PronunciationStrictness) {}
        override suspend fun setStorytellingStyle(style: StorytellingStyle) {}
        override suspend fun setStoryDuration(duration: StoryDuration) {}
        override suspend fun setMultiVoiceStorytelling(enabled: Boolean) {}
        override suspend fun setAppLanguage(language: AppLanguage) {}
        override suspend fun setLastTopicId(topicId: String) {}
        override suspend fun setAdaptiveLevelRecommendation(enabled: Boolean) {}
        override suspend fun setDrillSentenceLength(length: DrillSentenceLength) {}
    }

    private class DummyConnectivity : ConnectivityObserver {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
        override fun isOnlineNow(): Boolean = true
    }

    private class DummyAudioFocus : AudioFocus {
        override val state: StateFlow<com.speakdrive.audio.AudioFocusState> = MutableStateFlow(com.speakdrive.audio.AudioFocusState.GAIN)
        override fun request(): Boolean = true
        override fun abandon() {}
    }

    private class DummyAnnouncer : VoiceAnnouncer {
        override fun announce(text: String) {}
        override fun shutdown() {}
    }

    private class DummyPronunciationAssessor : PronunciationAssessor {
        override fun assess(referenceText: String, pcm16kMono: ByteArray, config: AzureSpeechConfig): AzureAssessment =
            AzureAssessment(0, 0, 0, 0, "", emptyList())
        override fun testConnection(config: AzureSpeechConfig): Result<Unit> = Result.success(Unit)
    }

    private val fakeEngine = object : ConversationEngine(
        liveClient = DummyLiveClient(),
        summaryGenerator = DummySummaryGenerator(),
        topicManager = TopicManager(),
        sessionStore = DummySessionStore(),
        settings = DummySettings(),
        audioFocus = DummyAudioFocus(),
        connectivity = DummyConnectivity(),
        announcer = DummyAnnouncer(),
        micPermission = MicPermissionChecker { true },
        pronunciationAssessor = DummyPronunciationAssessor(),
        // The engine's own coroutines (settings collector, watchdogs) must not share the scheduler the
        // test advances: advanceUntilIdle() would otherwise spin forever on the engine's periodic loops.
        dispatcher = StandardTestDispatcher()
    ) {
        var resumeCalled: Boolean = false
        override val state: StateFlow<ConversationState> = engineStateFlow
        override fun setCarConnected(connected: Boolean) {
            engineCarConnected = connected
        }
        override suspend fun resume(): Boolean {
            resumeCalled = true
            return true
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when car connects and idle, automatically starts resume playback`() = testScope.runTest {
        val starter = CarConnectionAutoStarter(
            carConnection = fakeCarConnection,
            playbackConnection = fakePlaybackConnection,
            engine = fakeEngine,
            autoStartPolicy = { autoStartEnabled }
        )
        starter.start()
        testScheduler.advanceUntilIdle()

        // Initially disconnected
        carFlow.emit(false)
        testScheduler.advanceUntilIdle()
        assertThat(engineCarConnected).isFalse()
        assertThat(playedMediaId).isNull()

        // Car connects
        carFlow.emit(true)
        testScheduler.advanceUntilIdle()
        assertThat(engineCarConnected).isTrue()
        assertThat(playedMediaId).isEqualTo(MediaIds.RESUME)
    }

    @Test
    fun `when car connects but already in active lesson, does not start new playback`() = testScope.runTest {
        val starter = CarConnectionAutoStarter(
            carConnection = fakeCarConnection,
            playbackConnection = fakePlaybackConnection,
            engine = fakeEngine,
            autoStartPolicy = { autoStartEnabled }
        )
        starter.start()
        testScheduler.advanceUntilIdle()

        // Active lesson is already running
        engineStateFlow.value = ConversationState.ACTIVE

        carFlow.emit(true)
        testScheduler.advanceUntilIdle()
        assertThat(engineCarConnected).isTrue()
        assertThat(playedMediaId).isNull()
    }

    @Test
    fun `when car disconnects, does not trigger playback`() = testScope.runTest {
        val starter = CarConnectionAutoStarter(
            carConnection = fakeCarConnection,
            playbackConnection = fakePlaybackConnection,
            engine = fakeEngine,
            autoStartPolicy = { autoStartEnabled }
        )
        starter.start()
        testScheduler.advanceUntilIdle()

        // Connect first
        carFlow.emit(true)
        testScheduler.advanceUntilIdle()
        playedMediaId = null

        // Disconnect
        carFlow.emit(false)
        testScheduler.advanceUntilIdle()
        assertThat(engineCarConnected).isFalse()
        assertThat(playedMediaId).isNull()
    }

    @Test
    fun `when car connects and lesson is PAUSED, automatically resumes paused lesson`() = testScope.runTest {
        val starter = CarConnectionAutoStarter(
            carConnection = fakeCarConnection,
            playbackConnection = fakePlaybackConnection,
            engine = fakeEngine,
            autoStartPolicy = { autoStartEnabled }
        )
        starter.start()
        testScheduler.advanceUntilIdle()

        // Lesson is paused (e.g. user stepped out of the car)
        engineStateFlow.value = ConversationState.PAUSED
        fakeEngine.resumeCalled = false
        playedMediaId = null

        // User gets back in car -> car connects
        carFlow.emit(true)
        testScheduler.advanceUntilIdle()

        assertThat(engineCarConnected).isTrue()
        assertThat(fakeEngine.resumeCalled).isTrue()
        assertThat(playedMediaId).isNull() // Should resume directly, not start fresh from MediaIds.RESUME
    }

    @Test
    fun `when auto-start is turned off in settings, connecting the car starts nothing`() = testScope.runTest {
        autoStartEnabled = false
        val starter = CarConnectionAutoStarter(
            carConnection = fakeCarConnection,
            playbackConnection = fakePlaybackConnection,
            engine = fakeEngine,
            autoStartPolicy = { autoStartEnabled }
        )
        starter.start()
        testScheduler.advanceUntilIdle()

        carFlow.emit(true)
        testScheduler.advanceUntilIdle()

        assertThat(engineCarConnected).isTrue()
        assertThat(playedMediaId).isNull()
    }
}
