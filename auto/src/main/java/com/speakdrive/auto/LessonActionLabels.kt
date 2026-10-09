package com.speakdrive.auto

import androidx.annotation.StringRes
import com.speakdrive.ai.model.SessionMode

/**
 * Names of the two custom buttons on the Android Auto Now Playing screen and the media
 * notification. They call [com.speakdrive.ai.ConversationEngine.repeat] and
 * [com.speakdrive.ai.ConversationEngine.next], whose effect depends on the lesson mode
 * (in a story "next" changes the story and "repeat" restarts it), so the names follow the mode.
 */
data class LessonActionLabels(@param:StringRes val repeat: Int, @param:StringRes val next: Int) {

    companion object {
        /** Labels for [mode]; `null` (no lesson running) keeps the generic sentence labels. */
        fun forMode(mode: SessionMode?): LessonActionLabels = when (mode) {
            null -> LessonActionLabels(R.string.action_repeat, R.string.action_next)
            SessionMode.REPEAT_AFTER_ME -> LessonActionLabels(R.string.action_repeat_drill, R.string.action_next)
            SessionMode.MISTAKE_REVIEW -> LessonActionLabels(R.string.action_repeat_ai, R.string.action_next)
            SessionMode.VOCAB_REVIEW -> LessonActionLabels(R.string.action_repeat_ai, R.string.action_next_word)
            SessionMode.FREE_TALK -> LessonActionLabels(R.string.action_repeat_ai, R.string.action_next_question)
            SessionMode.IELTS_SPEAKING -> LessonActionLabels(R.string.action_repeat_ai, R.string.action_next_ielts_question)
            SessionMode.ROLEPLAY -> LessonActionLabels(R.string.action_repeat_ai, R.string.action_next_scene)
            SessionMode.STORY_LISTENING -> LessonActionLabels(R.string.action_repeat_story, R.string.action_next_story)
        }
    }
}
