package com.speakdrive.ai

import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.live.LiveEvent
import com.speakdrive.ai.live.LiveSessionConfig
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.pronunciation.AzureAssessment
import com.speakdrive.ai.pronunciation.AzureSpeechConfig
import com.speakdrive.ai.pronunciation.PronunciationAssessor
import com.speakdrive.ai.pronunciation.PronunciationAttempt
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
    val sentContexts = mutableListOf<String>()
    var failNextConnects = 0
    /** What a failing connect() throws. */
    var connectFailure: Exception = IllegalStateException("connect failed")
    var audioPaused = false
    var disconnects = 0
    override var settingsToolsActive = true

    /** When set, connect() suspends until it is completed (a connection that hangs). */
    var connectGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** When true, the next connects that ask to resume continue the server-side conversation. */
    var resumeWorks = false
    override var lastConnectResumed = false
    override var connectedModel: String? = null

    override suspend fun connect(config: LiveSessionConfig) {
        connectGate?.await()
        if (failNextConnects > 0) {
            failNextConnects--
            throw connectFailure
        }
        connects += config
        isConnected = true
        audioPaused = false
        lastConnectResumed = resumeWorks && config.resume
        connectedModel = config.models.firstOrNull()
    }

    var sendTextFailsWith: Throwable? = null

    override suspend fun sendText(text: String) {
        sendTextFailsWith?.let { throw it }
        sentTexts += text
    }

    override suspend fun sendContext(text: String) {
        sendTextFailsWith?.let { throw it }
        sentContexts += text
    }

    override suspend fun pauseAudio() {
        audioPaused = true
        audioPausedBriefly = false
    }

    /** True when the last pause kept the call route up (a navigation prompt, a phone call). */
    var audioPausedBriefly = false

    override suspend fun pauseAudioBriefly() {
        audioPaused = true
        audioPausedBriefly = true
    }

    override suspend fun resumeAudio() {
        audioPaused = false
    }

    var interruptionsEnabled = false

    override fun updateInterruptions(enabled: Boolean) {
        interruptionsEnabled = enabled
    }

    var currentVolume = 1.0f

    override fun setVolume(volumeFraction: Float) {
        currentVolume = volumeFraction
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
    var nextRecommendation: com.speakdrive.ai.model.LevelRecommendation? = null
    var learnerFacts = listOf<String>()
    var mistakeResults = listOf<com.speakdrive.ai.model.MistakeReviewResult>()
    var lastLesson: ActiveLesson? = null
    val summary: SessionSummary
        get() = SessionSummary(
            7, 6, 8, emptyList(), emptyList(), "Tốt lắm", "Luyện thêm thì quá khứ",
            levelRecommendation = nextRecommendation,
            learnerFacts = learnerFacts,
            mistakeResults = mistakeResults
        )

    override suspend fun summarize(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        attempts: List<PronunciationAttempt>
    ): SessionSummary {
        calls++
        lastLesson = lesson
        if (fail) error("network down")
        return summary
    }
}

class FakeSessionStore : SessionStore {
    val saved = mutableListOf<CompletedSession>()
    val reviewed = mutableListOf<List<String>>()
    var recent = listOf<String>()
    var due = listOf<ReviewWord>()
    /** Simulates a broken database for the read paths used when a lesson starts. */
    var failReads = false

    override suspend fun saveSession(session: CompletedSession) {
        saved += session
    }

    override suspend fun recentTopicIds(limit: Int): List<String> {
        if (failReads) error("database unavailable")
        return recent.take(limit)
    }

    override suspend fun wordsDueForReview(limit: Int): List<ReviewWord> {
        if (failReads) error("database unavailable")
        return due.take(limit)
    }
    override suspend fun markWordsReviewed(words: List<String>) {
        reviewed += words
    }

    var dueMistakes = listOf<com.speakdrive.ai.model.ReviewMistake>()
    var recentMistakeList = listOf<com.speakdrive.ai.model.ReviewMistake>()
    val mistakeReviews = mutableListOf<List<com.speakdrive.ai.model.MistakeReviewResult>>()
    var memory = com.speakdrive.ai.model.LearnerMemory.EMPTY
    var memoryReads = 0

    override suspend fun mistakesDueForReview(limit: Int) = dueMistakes.take(limit)

    override suspend fun recentMistakes(limit: Int) = recentMistakeList.take(limit)

    override suspend fun recordMistakeReviews(results: List<com.speakdrive.ai.model.MistakeReviewResult>) {
        mistakeReviews += results
    }

    override suspend fun learnerMemory(): com.speakdrive.ai.model.LearnerMemory {
        memoryReads++
        if (failReads) error("database unavailable")
        return memory
    }
}

class FakeSettings(settings: LearnerSettings = LearnerSettings()) : LearningSettings {
    val flow = MutableStateFlow(settings)
    var settings: LearnerSettings
        get() = flow.value
        set(value) {
            if (failWrites) error("disk full")
            flow.value = value
        }

    /** Simulates a DataStore that cannot be written. */
    var failWrites = false

    override suspend fun snapshot() = settings
    override fun observeLearnerSettings(): kotlinx.coroutines.flow.Flow<LearnerSettings> = flow

    override suspend fun setLevel(level: DifficultyLevel) {
        settings = settings.copy(level = level)
    }

    override suspend fun setLastTopicId(topicId: String) {
        settings = settings.copy(lastTopicId = topicId)
    }

    override suspend fun setLastSession(topicId: String, mode: SessionMode, scenarioId: String?) {
        settings = settings.copy(lastTopicId = topicId, lastSessionMode = mode, lastScenarioId = scenarioId)
    }

    override suspend fun setAllowVietnameseHelp(allowed: Boolean) {
        settings = settings.copy(allowVietnameseHelp = allowed)
    }

    override suspend fun setPronunciationStrictness(strictness: PronunciationStrictness) {
        settings = settings.copy(pronunciationStrictness = strictness)
    }

    override suspend fun setStorytellingStyle(style: StorytellingStyle) {
        settings = settings.copy(storytellingStyle = style)
    }

    override suspend fun setAllowBargeIn(allowed: Boolean) {
        settings = settings.copy(allowBargeIn = allowed)
    }

    override suspend fun setVoice(voice: com.speakdrive.ai.model.AiVoice) {
        settings = settings.copy(voiceId = voice.id)
    }

    override suspend fun setRandomVoice(enabled: Boolean) {
        settings = settings.copy(randomVoice = enabled)
    }

    override suspend fun setDrillSentenceLength(length: com.speakdrive.ai.model.DrillSentenceLength) {
        settings = settings.copy(drillSentenceLength = length)
    }

    override suspend fun setDrillCategory(category: com.speakdrive.ai.model.DrillCategory) {
        settings = settings.copy(drillCategory = category)
    }

    override suspend fun setAiVolume(volume: Int) {
        settings = settings.copy(aiVolume = volume)
    }

    override suspend fun setAutoPauseWhenUnfocused(enabled: Boolean) {
        settings = settings.copy(autoPauseWhenUnfocused = enabled)
    }

    override suspend fun setAppLanguage(language: com.speakdrive.ai.model.AppLanguage) {
        settings = settings.copy(appLanguage = language)
    }

    override suspend fun setStoryDuration(duration: com.speakdrive.ai.model.StoryDuration) {
        settings = settings.copy(storyDuration = duration)
    }

    override suspend fun setMultiVoiceStorytelling(enabled: Boolean) {
        settings = settings.copy(multiVoiceStorytelling = enabled)
    }

    override suspend fun setAdaptiveLevelRecommendation(enabled: Boolean) {
        settings = settings.copy(adaptiveLevelRecommendation = enabled)
    }

    override suspend fun setShowTranslationSubtitle(enabled: Boolean) {
        settings = settings.copy(showTranslationSubtitle = enabled)
    }

    override suspend fun setAzureEnabled(enabled: Boolean) {
        settings = settings.copy(azureEnabled = enabled)
    }

    override suspend fun setAutoStartOnCarConnect(enabled: Boolean) {
        settings = settings.copy(autoStartOnCarConnect = enabled)
    }

    override suspend fun setDailyGoalMinutes(minutes: Int) {
        settings = settings.copy(dailyGoalMinutes = minutes)
    }

    override suspend fun setScreenAwakeMode(mode: com.speakdrive.ai.model.ScreenAwakeMode) {
        settings = settings.copy(screenAwakeMode = mode)
    }

    override suspend fun setRememberLearner(enabled: Boolean) {
        settings = settings.copy(rememberLearner = enabled)
    }

    override suspend fun setPracticeReminder(enabled: Boolean, minuteOfDay: Int?) {
        settings = settings.copy(
            practiceReminderEnabled = enabled,
            practiceReminderMinute = minuteOfDay ?: settings.practiceReminderMinute
        )
    }

    override suspend fun setStreakFreeze(enabled: Boolean) {
        settings = settings.copy(streakFreezeEnabled = enabled)
    }

    override suspend fun setOfflinePractice(enabled: Boolean) {
        settings = settings.copy(offlinePracticeEnabled = enabled)
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

class FakeAssessor : PronunciationAssessor {
    var result: AzureAssessment? = null
    var failure: Exception? = null
    val calls = mutableListOf<Pair<String, Int>>()

    override fun assess(referenceText: String, pcm16kMono: ByteArray, config: AzureSpeechConfig): AzureAssessment {
        calls += referenceText to pcm16kMono.size
        failure?.let { throw it }
        return result ?: error("no fake result")
    }

    override fun testConnection(config: AzureSpeechConfig): Result<Unit> = Result.success(Unit)
}

class FakeAnnouncer : VoiceAnnouncer {
    val announcements = mutableListOf<String>()
    val announced: List<String> get() = announcements
    override fun announce(text: String) {
        announcements += text
    }

    override fun shutdown() = Unit
}

/** Scripted offline speech: [answer] queues what the learner "says"; listening waits until there is one. */
class FakeOfflineSpeech : com.speakdrive.audio.OfflineSpeech {
    var available = false
    var listenWorks = true
    val spoken = mutableListOf<String>()
    private val answers = kotlinx.coroutines.channels.Channel<com.speakdrive.audio.ListenResult>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    var listens = 0
    var releases = 0

    fun answer(result: com.speakdrive.audio.ListenResult) {
        answers.trySend(result)
    }

    override val isAvailable: Boolean get() = available

    override suspend fun speak(text: String, vietnamese: Boolean) {
        spoken += text
    }

    override suspend fun canListen() = listenWorks

    override suspend fun listen(timeoutMs: Long): com.speakdrive.audio.ListenResult {
        listens++
        return answers.receive()
    }

    override fun release() {
        releases++
    }
}
