package com.speakdrive.auto

import com.speakdrive.ai.model.LessonRequest
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.session.LearningSettings

/**
 * Resolves a MediaId into a [LessonRequest] for starting a lesson.
 * Shared between [SpeakDrivePlayer] (Media3 playback) and [PlaybackConnection] (fallback direct start).
 */
object MediaLessonResolver {

    suspend fun resolve(mediaId: String, settings: LearningSettings): LessonRequest? = when (val target = MediaIds.parse(mediaId)) {
        MediaTarget.Resume, MediaTarget.Unknown -> {
            val snapshot = settings.snapshot()
            val topicId = snapshot.lastTopicId
            when (snapshot.lastSessionMode) {
                SessionMode.REPEAT_AFTER_ME -> LessonRequest(topicId = topicId, mode = SessionMode.REPEAT_AFTER_ME)
                SessionMode.ROLEPLAY -> LessonRequest(topicId = topicId, scenarioId = snapshot.lastScenarioId, mode = SessionMode.ROLEPLAY)
                SessionMode.VOCAB_REVIEW -> LessonRequest(topicId = topicId, mode = SessionMode.VOCAB_REVIEW)
                SessionMode.MISTAKE_REVIEW -> LessonRequest(topicId = topicId, mode = SessionMode.MISTAKE_REVIEW)
                SessionMode.STORY_LISTENING -> LessonRequest(topicId = topicId, scenarioId = snapshot.lastScenarioId, mode = SessionMode.STORY_LISTENING)
                SessionMode.IELTS_SPEAKING -> LessonRequest(topicId = topicId, scenarioId = snapshot.lastScenarioId, mode = SessionMode.IELTS_SPEAKING)
                SessionMode.FREE_TALK -> LessonRequest(topicId = topicId, mode = SessionMode.FREE_TALK)
            }
        }
        MediaTarget.Random -> LessonRequest(topicId = null)
        MediaTarget.Review -> LessonRequest(mode = SessionMode.VOCAB_REVIEW, topicId = settings.snapshot().lastTopicId)
        MediaTarget.Mistakes -> LessonRequest(mode = SessionMode.MISTAKE_REVIEW, topicId = settings.snapshot().lastTopicId)
        MediaTarget.Ielts -> LessonRequest(mode = SessionMode.IELTS_SPEAKING, topicId = settings.snapshot().lastTopicId)
        is MediaTarget.Vocab -> {
            val words = if (!target.word.isNullOrBlank()) {
                listOf(ReviewWord(target.word, ""))
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
}
