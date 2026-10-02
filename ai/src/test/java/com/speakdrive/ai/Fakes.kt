package com.speakdrive.ai

import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.network.ConnectivityObserver
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.ai.summary.SummaryGenerator
import com.speakdrive.audio.AudioFocus
import com.speakdrive.audio.AudioFocusState
import com.speakdrive.audio.VoiceAnnouncer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeLiveClient : LiveConversationClient {
    override val events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    override var isConnected = false

    val connects = mutableListOf<LiveSessionConfig>()
    val sentTexts = mutableListOf<String>()
    var failNextConnects = 0
    var audioPaused = false
    var disconnects = 0

    override suspend fun connect(config: LiveSessionConfig) {
        if (failNextConnects > 0) {
            failNextConnects--
            throw IllegalStateException("connect failed")
        }
        connects += config
        isConnected = true
        audioPaused = false
    }

    override suspend fun sendText(text: String) {
        sentTexts += text
    }

    override suspend fun pauseAudio() {
        audioPaused = true
    }

    override suspend fun resumeAudio() {
        audioPaused = false
    }

    override suspend fun disconnect() {
        if (isConnected) disconnects++
        isConnected = false
    }

    fun emit(event: LiveEvent) {
        check(events.tryEmit(event))
    }
}

class FakeSummaryGenerator : SummaryGenerator {
    var fail = false
    var calls = 0
    val summary = SessionSummary(7, 6, 8, emptyList(), emptyList(), "Tốt lắm", "Luyện thêm thì quá khứ")

    override suspend fun summarize(lesson: ActiveLesson, transcript: List<TranscriptTurn>): SessionSummary {
        calls++
        if (fail) error("network down")
        return summary
    }
}

class FakeSessionStore : SessionStore {
    val saved = mutableListOf<CompletedSession>()
    val reviewed = mutableListOf<List<String>>()
    var recent = listOf<String>()
    var due = listOf<ReviewWord>()

    override suspend fun saveSession(session: CompletedSession) {
        saved += session
    }

    override suspend fun recentTopicIds(limit: Int) = recent.take(limit)
    override suspend fun wordsDueForReview(limit: Int) = due.take(limit)
    override suspend fun markWordsReviewed(words: List<String>) {
        reviewed += words
    }
}

class FakeSettings(var settings: LearnerSettings = LearnerSettings()) : LearningSettings {
    override suspend fun snapshot() = settings
    override suspend fun setLevel(level: DifficultyLevel) {
        settings = settings.copy(level = level)
    }

    override suspend fun setLastTopicId(topicId: String) {
        settings = settings.copy(lastTopicId = topicId)
    }
}

class FakeAudioFocus : AudioFocus {
    override val state = MutableStateFlow(AudioFocusState.NONE)
    var grant = true
    var requests = 0

    override fun request(): Boolean {
        requests++
        if (grant) state.value = AudioFocusState.GAIN
        return grant
    }

    override fun abandon() {
        state.value = AudioFocusState.NONE
    }
}

class FakeConnectivity(online: Boolean = true) : ConnectivityObserver {
    val online = MutableStateFlow(online)
    override val isOnline: StateFlow<Boolean> = this.online
    override fun isOnlineNow() = online.value
}

class FakeAnnouncer : VoiceAnnouncer {
    val announcements = mutableListOf<String>()
    override fun announce(text: String) {
        announcements += text
    }

    override fun shutdown() = Unit
}
