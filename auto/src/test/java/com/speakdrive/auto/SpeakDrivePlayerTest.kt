package com.speakdrive.auto

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
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
import com.speakdrive.audio.AudioFocusState
import com.speakdrive.audio.VoiceAnnouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeakDrivePlayerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + testDispatcher)

    private val stateFlow = MutableStateFlow(ConversationState.IDLE)
    private val lessonFlow = MutableStateFlow<ActiveLesson?>(null)
    private val transcriptFlow = MutableStateFlow<List<TranscriptTurn>>(emptyList())
    private val drillTargetFlow = MutableStateFlow<String?>(null)

    private val topics = TopicManager()
    private val store = object : SessionStore {
        override suspend fun saveSession(session: CompletedSession) = Unit
        override suspend fun recentTopicIds(limit: Int) = emptyList<String>()
        override suspend fun wordsDueForReview(limit: Int) = emptyList<ReviewWord>()
        override suspend fun markWordsReviewed(words: List<String>) = Unit
    }
    private val settings = object : LearningSettings {
        var current = LearnerSettings(level = DifficultyLevel.BEGINNER, lastTopicId = "travel")
        override suspend fun snapshot() = current
        override suspend fun setLevel(level: DifficultyLevel) = Unit
        override suspend fun setVoice(voice: AiVoice) = Unit
        override suspend fun setRandomVoice(enabled: Boolean) = Unit
        override suspend fun setAllowVietnameseHelp(allowed: Boolean) = Unit
        override suspend fun setAllowBargeIn(allowed: Boolean) = Unit
        override suspend fun setPronunciationStrictness(strictness: PronunciationStrictness) = Unit
        override suspend fun setStorytellingStyle(style: StorytellingStyle) = Unit
        override suspend fun setStoryDuration(duration: StoryDuration) = Unit
        override suspend fun setMultiVoiceStorytelling(enabled: Boolean) = Unit
        override suspend fun setAppLanguage(language: AppLanguage) = Unit
        override suspend fun setLastTopicId(topicId: String) = Unit
        override suspend fun setLastSession(topicId: String, mode: SessionMode, scenarioId: String?) {
            current = current.copy(lastTopicId = topicId, lastSessionMode = mode, lastScenarioId = scenarioId)
        }
        override suspend fun setAdaptiveLevelRecommendation(enabled: Boolean) = Unit
    }
    private val contentProvider = MediaContentProvider(topics, store, settings)

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

    private class TestEngine(
        liveClient: LiveConversationClient,
        summaryGenerator: SummaryGenerator,
        topicManager: TopicManager,
        sessionStore: SessionStore,
        settings: LearningSettings,
        private val stateOverride: StateFlow<ConversationState>,
        private val lessonOverride: StateFlow<ActiveLesson?>,
        private val transcriptOverride: StateFlow<List<TranscriptTurn>>,
        private val drillTargetOverride: StateFlow<String?>
    ) : ConversationEngine(
        liveClient = liveClient,
        summaryGenerator = summaryGenerator,
        topicManager = topicManager,
        sessionStore = sessionStore,
        settings = settings,
        audioFocus = object : AudioFocus {
            override val state: StateFlow<AudioFocusState> = MutableStateFlow(AudioFocusState.GAIN)
            override fun request(): Boolean = true
            override fun abandon() {}
        },
        connectivity = object : ConnectivityObserver {
            override val isOnline: Flow<Boolean> = emptyFlow()
            override fun isOnlineNow(): Boolean = true
        },
        announcer = object : VoiceAnnouncer {
            override fun announce(text: String) {}
            override fun shutdown() {}
        },
        micPermission = MicPermissionChecker { true },
        pronunciationAssessor = object : PronunciationAssessor {
            override fun assess(referenceText: String, pcm16kMono: ByteArray, config: AzureSpeechConfig): AzureAssessment =
                AzureAssessment(100, 100, 100, 100, referenceText, emptyList())
            override fun testConnection(config: AzureSpeechConfig): Result<Unit> = Result.success(Unit)
        },
        dispatcher = Dispatchers.Unconfined
    ) {
        var lastStartRequest: LessonRequest? = null
        var resumeCallCount = 0

        override suspend fun start(request: LessonRequest): Boolean {
            lastStartRequest = request
            return true
        }

        override suspend fun resume(): Boolean {
            resumeCallCount++
            return true
        }

        override val state: StateFlow<ConversationState> get() = stateOverride
        override val lesson: StateFlow<ActiveLesson?> get() = lessonOverride
        override val transcript: StateFlow<List<TranscriptTurn>> get() = transcriptOverride
        override val drillTarget: StateFlow<String?> get() = drillTargetOverride
    }

    private lateinit var testEngine: TestEngine
    private lateinit var player: SpeakDrivePlayer

    @Before
    fun setUp() {
        testEngine = TestEngine(
            DummyLiveClient(),
            DummySummaryGenerator(),
            topics,
            store,
            settings,
            stateFlow,
            lessonFlow,
            transcriptFlow,
            drillTargetFlow
        )
        player = SpeakDrivePlayer(
            looper = Looper.getMainLooper(),
            engine = testEngine,
            settings = settings,
            contentProvider = contentProvider,
            scope = scope
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `playbackState is STATE_IDLE when conversation is IDLE`() = runTest(testDispatcher) {
        stateFlow.value = ConversationState.IDLE
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `playbackState is STATE_READY and playWhenReady is true when ACTIVE`() = runTest(testDispatcher) {
        stateFlow.value = ConversationState.ACTIVE
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(player.playbackState).isEqualTo(Player.STATE_READY)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `playbackState is STATE_READY and playWhenReady is false when PAUSED`() = runTest(testDispatcher) {
        stateFlow.value = ConversationState.PAUSED
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(player.playbackState).isEqualTo(Player.STATE_READY)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `playbackState is STATE_ENDED and playWhenReady is false when ENDED`() = runTest(testDispatcher) {
        stateFlow.value = ConversationState.ENDED
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(player.playbackState).isEqualTo(Player.STATE_ENDED)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `resets selectedItem and shows completed standby title when lesson ends`() = runTest(testDispatcher) {
        // Learner starts pronunciation drill
        val pronunciationItem = MediaItem.Builder()
            .setMediaId(MediaIds.PRONUNCIATION)
            .build()
        player.setMediaItem(pronunciationItem)
        shadowOf(Looper.getMainLooper()).idle()

        // Lesson is active
        val travelTopic = topics.getTopicById("travel")!!
        val activeLesson = ActiveLesson("session_123", travelTopic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())
        lessonFlow.value = activeLesson
        stateFlow.value = ConversationState.ACTIVE
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(player.currentMediaItem?.mediaMetadata?.title.toString()).contains("Luyện phát âm")

        // Lesson ends
        lessonFlow.value = null
        stateFlow.value = ConversationState.ENDED
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // Selected item must be cleared and metadata must show completed standby item, NOT old "Luyện phát âm"
        val currentTitle = player.currentMediaItem?.mediaMetadata?.title.toString()
        assertThat(currentTitle).contains("Đã hoàn thành")
        assertThat(currentTitle).doesNotContain("Luyện phát âm")
        assertThat(player.playbackState).isEqualTo(Player.STATE_ENDED)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `resuming playback with MediaIds RESUME preserves lastSessionMode REPEAT_AFTER_ME`() = runTest(testDispatcher) {
        settings.current = settings.current.copy(
            lastTopicId = "travel",
            lastSessionMode = SessionMode.REPEAT_AFTER_ME
        )

        // Set resume media item (e.g. from CarConnectionAutoStarter or Android Auto resume)
        val resumeItem = MediaItem.Builder().setMediaId(MediaIds.RESUME).build()
        player.setMediaItem(resumeItem)
        player.prepare()
        player.play()
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(testEngine.lastStartRequest?.topicId).isEqualTo("travel")
    }

    @Test
    fun `resuming playback with MediaIds RESUME preserves lastSessionMode ROLEPLAY and scenarioId`() = runTest(testDispatcher) {
        settings.current = settings.current.copy(
            lastTopicId = "work",
            lastSessionMode = SessionMode.ROLEPLAY,
            lastScenarioId = "interview"
        )

        val resumeItem = MediaItem.Builder().setMediaId(MediaIds.RESUME).build()
        player.setMediaItem(resumeItem)
        player.prepare()
        player.play()
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.ROLEPLAY)
        assertThat(testEngine.lastStartRequest?.scenarioId).isEqualTo("interview")
    }

    @Test
    fun `picking pronunciation item while lesson is PAUSED starts pronunciation and does not resume old lesson`() = runTest(testDispatcher) {
        val topic = topics.getTopicById("travel")!!
        lessonFlow.value = ActiveLesson(
            sessionId = "s1",
            topic = topic,
            scenario = null,
            level = DifficultyLevel.INTERMEDIATE,
            mode = SessionMode.FREE_TALK,
            startedAt = 0L,
            reviewWords = emptyList()
        )
        stateFlow.value = ConversationState.PAUSED
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // User picks "Luyện phát âm" from car screen while paused
        val pronItem = MediaItem.Builder().setMediaId(MediaIds.PRONUNCIATION).build()
        player.setMediaItem(pronItem)
        player.prepare()
        player.play()
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // Must start REPEAT_AFTER_ME, NOT resume the old FREE_TALK lesson
        assertThat(testEngine.resumeCallCount).isEqualTo(0)
        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    @Test
    fun `picking pronunciation item while lesson is ACTIVE starts pronunciation immediately`() = runTest(testDispatcher) {
        val topic = topics.getTopicById("travel")!!
        lessonFlow.value = ActiveLesson(
            sessionId = "s1",
            topic = topic,
            scenario = null,
            level = DifficultyLevel.INTERMEDIATE,
            mode = SessionMode.FREE_TALK,
            startedAt = 0L,
            reviewWords = emptyList()
        )
        stateFlow.value = ConversationState.ACTIVE
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // User picks "Luyện phát âm" from car screen while active
        val pronItem = MediaItem.Builder().setMediaId(MediaIds.PRONUNCIATION).build()
        player.setMediaItem(pronItem)
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    @Test
    fun `picking topic item preserves REPEAT_AFTER_ME mode when lastSessionMode was REPEAT_AFTER_ME`() = runTest(testDispatcher) {
        settings.current = settings.current.copy(
            lastTopicId = "travel",
            lastSessionMode = SessionMode.REPEAT_AFTER_ME
        )
        val topicItem = MediaItem.Builder().setMediaId(MediaIds.topic("interview")).build()
        player.setMediaItem(topicItem)
        player.prepare()
        player.play()
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.topicId).isEqualTo("interview")
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    @Test
    fun `picking topic item uses FREE_TALK when lastSessionMode was FREE_TALK`() = runTest(testDispatcher) {
        settings.current = settings.current.copy(
            lastTopicId = "travel",
            lastSessionMode = SessionMode.FREE_TALK
        )
        val topicItem = MediaItem.Builder().setMediaId(MediaIds.topic("shopping")).build()
        player.setMediaItem(topicItem)
        player.prepare()
        player.play()
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(testEngine.lastStartRequest).isNotNull()
        assertThat(testEngine.lastStartRequest?.topicId).isEqualTo("shopping")
        assertThat(testEngine.lastStartRequest?.mode).isEqualTo(SessionMode.FREE_TALK)
    }
}

