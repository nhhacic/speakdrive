package com.speakdrive.auto

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import javax.inject.Inject

/**
 * Maps a Google Assistant voice query to a media id, e.g.
 * "Hey Google, play travel English on SpeakDrive" → `topic:travel`,
 * "play easy job interview practice" → `topic:interview@BEGINNER`,
 * "play on SpeakDrive" (no query) → `resume`.
 */
class VoiceCommandHandler @Inject constructor(
    private val topicManager: TopicManager
) {
    fun resolve(query: String?): String {
        val q = TopicManager.normalize(query.orEmpty())
        if (q.isBlank()) return MediaIds.RESUME

        if (q.hasAny(REVIEW_WORDS)) return MediaIds.REVIEW
        if (q.hasAny(RANDOM_WORDS)) return MediaIds.RANDOM

        val level = levelIn(q)
        val topic = topicManager.findTopicByQuery(stripFillers(q))

        if (q.hasAny(PRONUNCIATION_WORDS)) return MediaIds.pronunciation(topic?.id)

        if (q.hasAny(ROLEPLAY_WORDS)) {
            val scenarios = topic?.scenarios ?: topicManager.getAllTopics().flatMap { it.scenarios }
            return MediaIds.scenario(scenarios.random().id)
        }
        return when {
            topic != null -> MediaIds.topic(topic.id, level)
            level != null -> MediaIds.level(level)
            else -> MediaIds.RESUME
        }
    }

    private fun levelIn(q: String): DifficultyLevel? = when {
        q.hasAny(BEGINNER_WORDS) -> DifficultyLevel.BEGINNER
        q.hasAny(ADVANCED_WORDS) -> DifficultyLevel.ADVANCED
        q.hasAny(INTERMEDIATE_WORDS) -> DifficultyLevel.INTERMEDIATE
        else -> null
    }

    /** Removes words like "english lesson on speakdrive" so they do not get in the way of topic matching. */
    private fun stripFillers(q: String): String =
        q.split(' ').filterNot { it in FILLER_WORDS }.joinToString(" ")

    private fun String.hasAny(phrases: List<String>): Boolean {
        val padded = " $this "
        return phrases.any { padded.contains(" $it ") }
    }

    private companion object {
        val REVIEW_WORDS = listOf("review", "vocabulary", "vocab", "words", "on tap", "tu vung")
        val RANDOM_WORDS = listOf("random", "anything", "surprise me", "ngau nhien", "bat ky")
        val PRONUNCIATION_WORDS = listOf(
            "pronunciation", "pronounce", "repeat after me", "shadowing", "phat am", "nhac lai", "doc theo", "noi theo"
        )
        val ROLEPLAY_WORDS = listOf("roleplay", "role play", "role playing", "nhap vai", "dong vai")
        // "dễ" is left out: without diacritics it collides with the very common "để".
        val BEGINNER_WORDS = listOf("beginner", "easy", "simple", "basic", "co ban", "moi bat dau")
        val INTERMEDIATE_WORDS = listOf("intermediate", "medium", "trung cap", "trung binh")
        val ADVANCED_WORDS = listOf("advanced", "hard", "difficult", "kho", "nang cao")
        val FILLER_WORDS = setOf(
            "play", "start", "practice", "practise", "lesson", "lessons", "english", "on", "in", "the", "a", "an",
            "my", "some", "speakdrive", "speak", "drive", "please", "conversation", "about", "bai", "hoc", "tieng", "anh",
            "luyen", "noi", "mo", "phat", "am", "pronunciation", "repeat", "after", "me", "easy", "hard", "beginner", "advanced", "intermediate"
        )
    }
}
