package com.speakdrive.auto

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.session.LearningSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * A [Player] whose "playback" is a live conversation. Android Auto, the steering-wheel buttons,
 * the notification and the phone UI all control lessons through this player:
 *
 * - play / pause → start or resume / pause the lesson
 * - stop → end the lesson (it is summarised and saved)
 * - next → switch to another topic
 * - choosing an item in the browse tree → start that lesson
 */
@UnstableApi
class SpeakDrivePlayer(
    looper: Looper,
    private val engine: ConversationEngine,
    private val settings: LearningSettings,
    private val contentProvider: MediaContentProvider,
    private val scope: CoroutineScope
) : SimpleBasePlayer(looper) {

    /** What the learner picked last, shown before the lesson connects and after it ends. */
    private var selectedItem: MediaItem? = null

    init {
        scope.launch {
            combine(
                listOf(
                    engine.state,
                    engine.lesson,
                    engine.error,
                    engine.transcript,
                    engine.drillTarget,
                    engine.drillTargetTranslation
                )
            ) { }.collect { invalidateState() }
        }
    }

    private fun isSameAsCurrentLesson(item: MediaItem?): Boolean {
        if (item == null) return false
        val lesson = engine.lesson.value ?: return false
        val target = MediaIds.parse(item.mediaId)
        return when (target) {
            MediaTarget.Resume -> true
            is MediaTarget.Pronunciation ->
                lesson.mode == SessionMode.REPEAT_AFTER_ME && (target.topicId == null || target.topicId == lesson.topic.id)
            is MediaTarget.Topic ->
                lesson.topic.id == target.topicId
            is MediaTarget.Scenario ->
                lesson.mode == SessionMode.ROLEPLAY && lesson.scenario?.id == target.scenarioId
            is MediaTarget.Story ->
                lesson.mode == SessionMode.STORY_LISTENING && (target.topicId == null || target.topicId == lesson.topic.id)
            MediaTarget.StoryRecommended, MediaTarget.StoryResume ->
                lesson.mode == SessionMode.STORY_LISTENING
            MediaTarget.Review ->
                lesson.mode == SessionMode.VOCAB_REVIEW
            MediaTarget.Mistakes ->
                lesson.mode == SessionMode.MISTAKE_REVIEW
            else -> false
        }
    }

    override fun getState(): State {
        val engineState = engine.state.value
        val lesson = engine.lesson.value
        if (lesson != null && isSameAsCurrentLesson(selectedItem)) {
            selectedItem = null
        }
        if (engineState == ConversationState.ENDED) {
            selectedItem = null
        }
        val isSwitching = engineState.isInLesson && selectedItem != null && !isSameAsCurrentLesson(selectedItem)
        val item = lesson?.let {
            val transcript = engine.transcript.value
            val lastAiText = transcript.lastOrNull { turn -> turn.speaker == Speaker.AI }?.text
            val drillTarget = engine.drillTarget.value
            val drillTargetTranslation = engine.drillTargetTranslation.value
            contentProvider.lessonItem(it, engineState, lastAiText, drillTarget, drillTargetTranslation)
        } ?: selectedItem ?: contentProvider.standbyItem(justEnded = engineState == ConversationState.ENDED)
        val error = engine.error.value

        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(if (isSwitching) true else engineState.wantsToPlay(), Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        builder.setPlaylist(
            listOf(
                MediaItemData.Builder(item.mediaId)
                    .setMediaItem(item)
                    .setMediaMetadata(item.mediaMetadata)
                    .setIsSeekable(false)
                    .setDurationUs(C.TIME_UNSET)
                    // A conversation has no timeline; marking it live hides the seek bar.
                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                    // Dynamic: without it Media3 drops "next" on a single-item playlist and the
                    // steering-wheel Next button never reaches handleSeek.
                    .setIsDynamic(true)
                    .build()
            )
        )
        builder.setCurrentMediaItemIndex(0)

        if (engineState == ConversationState.ERROR && error != null) {
            builder.setPlaybackState(Player.STATE_IDLE)
            builder.setPlayerError(PlaybackException(error.messageVi, null, PlaybackException.ERROR_CODE_UNSPECIFIED))
        } else if (isSwitching && engineState == ConversationState.ENDED) {
            builder.setPlaybackState(Player.STATE_BUFFERING)
        } else {
            builder.setPlaybackState(engineState.toPlaybackState())
        }
        return builder.build()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val item = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
            ?: return Futures.immediateVoidFuture()
        selectedItem = item
        // While a lesson is running or when already playing, start immediately
        if (engine.state.value.isInLesson || getState().playWhenReady) {
            return scope.future { startFromItem(item) }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = scope.future {
        val state = engine.state.value
        val itemToStart = selectedItem
        if (!playWhenReady) {
            // Keep any error on screen: the driver should still see why the lesson stopped.
            if (startingMediaId == null && !engine.isWithinStartupGrace()) {
                engine.pause()
            }
            return@future
        }
        engine.clearError()
        when {
            itemToStart != null && !isSameAsCurrentLesson(itemToStart) -> startFromItem(itemToStart)
            state == ConversationState.PAUSED -> engine.resume()
            state.isInLesson -> Unit
            else -> startFromItem(itemToStart)
        }
    }

    override fun handleStop(): ListenableFuture<*> = scope.future {
        selectedItem = null
        engine.end()
        Unit
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                val currentLesson = engine.lesson.value
                if (currentLesson?.mode == SessionMode.STORY_LISTENING) {
                    // "Next" on steering wheel while listening to stories: switch to next recommended story
                    return scope.future {
                        engine.nextStory()
                        Unit
                    }
                }
                if (engine.state.value.isInLesson) {
                    // "Next" during lesson: advance to next sentence or prompt AI to move forward
                    return scope.future {
                        engine.next()
                        Unit
                    }
                }
                // "Next" when idle or outside lesson: move on to a different topic.
                val currentTopic = currentLesson?.topic?.id
                return scope.future {
                    selectedItem = null
                    engine.start(LessonRequest(topicId = null, mode = SessionMode.FREE_TALK).avoiding(currentTopic))
                    Unit
                }
            }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                if (engine.state.value.isInLesson) {
                    // "Previous" / "Repeat" on steering wheel or car screen: repeat current sentence
                    return scope.future {
                        engine.repeat()
                        Unit
                    }
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private var startingMediaId: String? = null

    private suspend fun startFromItem(item: MediaItem?) {
        val mediaId = item?.mediaId ?: MediaIds.RESUME
        if (startingMediaId == mediaId && engine.state.value.isInLesson) {
            return
        }
        startingMediaId = mediaId
        try {
            if (mediaId == MediaIds.STORY_RESUME || MediaIds.parse(mediaId) is MediaTarget.StoryResume) {
                engine.resumeStory()
                return
            }
            val request = requestFor(mediaId)
            if (request == null) {
                // Nothing playable (e.g. a folder): do not stay stuck in "switching".
                selectedItem = null
                invalidateState()
                return
            }
            engine.start(request)
        } finally {
            startingMediaId = null
        }
    }

    /** Turns a media id from the browse tree or voice search into a lesson request. */
    private suspend fun requestFor(mediaId: String): LessonRequest? = when (val target = MediaIds.parse(mediaId)) {
        MediaTarget.Resume, MediaTarget.Unknown -> {
            val snapshot = settings.snapshot()
            val topicId = snapshot.lastTopicId
            when (snapshot.lastSessionMode) {
                SessionMode.REPEAT_AFTER_ME -> LessonRequest(topicId = topicId, mode = SessionMode.REPEAT_AFTER_ME)
                SessionMode.ROLEPLAY -> LessonRequest(topicId = topicId, scenarioId = snapshot.lastScenarioId, mode = SessionMode.ROLEPLAY)
                SessionMode.VOCAB_REVIEW -> LessonRequest(topicId = topicId, mode = SessionMode.VOCAB_REVIEW)
                SessionMode.MISTAKE_REVIEW -> LessonRequest(topicId = topicId, mode = SessionMode.MISTAKE_REVIEW)
                SessionMode.STORY_LISTENING -> LessonRequest(topicId = topicId, scenarioId = snapshot.lastScenarioId, mode = SessionMode.STORY_LISTENING)
                SessionMode.FREE_TALK -> LessonRequest(topicId = topicId, mode = SessionMode.FREE_TALK)
            }
        }
        MediaTarget.Random -> LessonRequest(topicId = null)
        MediaTarget.Review -> LessonRequest(mode = SessionMode.VOCAB_REVIEW, topicId = settings.snapshot().lastTopicId)
        MediaTarget.Mistakes -> LessonRequest(mode = SessionMode.MISTAKE_REVIEW, topicId = settings.snapshot().lastTopicId)
        is MediaTarget.Vocab -> {
            val words = if (!target.word.isNullOrBlank()) {
                listOf(com.speakdrive.ai.model.ReviewWord(target.word, ""))
            } else {
                emptyList()
            }
            LessonRequest(
                mode = SessionMode.VOCAB_REVIEW,
                topicId = settings.snapshot().lastTopicId,
                targetWords = words
            )
        }
        MediaTarget.StoryRecommended -> LessonRequest(mode = SessionMode.STORY_LISTENING)
        MediaTarget.StoryResume -> null
        is MediaTarget.Story -> LessonRequest(mode = SessionMode.STORY_LISTENING, topicId = target.topicId, scenarioId = target.scenarioId)
        is MediaTarget.Pronunciation ->
            LessonRequest(mode = SessionMode.REPEAT_AFTER_ME, topicId = target.topicId ?: settings.snapshot().lastTopicId)
        is MediaTarget.Topic -> {
            target.level?.let { settings.setLevel(it) }
            val preferredMode = if (settings.snapshot().lastSessionMode == SessionMode.REPEAT_AFTER_ME) {
                SessionMode.REPEAT_AFTER_ME
            } else {
                SessionMode.FREE_TALK
            }
            LessonRequest(topicId = target.topicId, level = target.level, mode = preferredMode)
        }
        is MediaTarget.Scenario -> LessonRequest(mode = SessionMode.ROLEPLAY, scenarioId = target.scenarioId)
        is MediaTarget.Level -> {
            settings.setLevel(target.level)
            LessonRequest(topicId = settings.snapshot().lastTopicId, level = target.level)
        }
        is MediaTarget.Browse -> null
    }

    /** The engine picks a random topic when [LessonRequest.topicId] is null; this keeps it from repeating. */
    private fun LessonRequest.avoiding(topicId: String?): LessonRequest =
        if (topicId == null) this else copy(topicId = contentProvider.randomTopicIdExcept(topicId))

    private fun ConversationState.wantsToPlay(): Boolean = when (this) {
        ConversationState.CONNECTING,
        ConversationState.ACTIVE,
        ConversationState.RECONNECTING,
        ConversationState.WAITING_FOR_NETWORK -> true
        else -> false
    }

    private fun ConversationState.toPlaybackState(): Int = when (this) {
        ConversationState.ACTIVE, ConversationState.PAUSED -> Player.STATE_READY
        ConversationState.CONNECTING,
        ConversationState.RECONNECTING,
        ConversationState.WAITING_FOR_NETWORK,
        ConversationState.ENDING -> Player.STATE_BUFFERING
        ConversationState.ENDED -> Player.STATE_ENDED
        ConversationState.IDLE, ConversationState.ERROR -> Player.STATE_IDLE
    }

    private companion object {
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE
            )
            .build()
    }
}
