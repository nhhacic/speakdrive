package com.speakdrive.playback

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaybackConnectionTest {

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
        var current = LearnerSettings(
            level = DifficultyLevel.INTERMEDIATE,
            lastTopicId = "daily",
            lastSessionMode = SessionMode.FREE_TALK,
            lastScenarioId = null
        )
        override suspend fun snapshot(): LearnerSettings = current
        override suspend fun setLevel(level: DifficultyLevel) { current = current.copy(level = level) }
        override suspend fun setLastTopicId(topicId: String) { current = current.copy(lastTopicId = topicId) }
        override suspend fun setAllowVietnameseHelp(allowed: Boolean) {}
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

    private fun createFakeEngine(
        onStart: (LessonRequest) -> Unit = {},
        onEnd: () -> Unit = {}
    ): ConversationEngine {
        return object : ConversationEngine(
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
            dispatcher = StandardTestDispatcher()
        ) {
            override suspend fun start(request: LessonRequest): Boolean {
                onStart(request)
                return true
            }

            override suspend fun end(): String? {
                onEnd()
                return "session-123"
            }
        }
    }

    @Test
    fun `when media controller connection fails, fallback starts engine directly`() = runTest {
        var startedRequest: LessonRequest? = null
        val fakeEngine = createFakeEngine(onStart = { startedRequest = it })
        val settings = DummySettings()

        val connection = PlaybackConnection(
            context = null,
            engine = fakeEngine,
            settings = settings,
            forTesting = false
        )

        connection.play(MediaIds.topic("travel"))

        assertThat(startedRequest).isNotNull()
        assertThat(startedRequest!!.topicId).isEqualTo("travel")
        assertThat(startedRequest!!.mode).isEqualTo(SessionMode.FREE_TALK)
    }

    @Test
    fun `stop stops engine cleanly`() = runTest {
        var endCalled = false
        val fakeEngine = createFakeEngine(onEnd = { endCalled = true })
        val settings = DummySettings()

        val connection = PlaybackConnection(
            context = null,
            engine = fakeEngine,
            settings = settings,
            forTesting = false
        )

        connection.stop()

        assertThat(endCalled).isTrue()
    }
}
