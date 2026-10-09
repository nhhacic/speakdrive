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

        if (q.hasAny(VOCAB_PRONUNCIATION_WORDS)) return MediaIds.vocabMode("pronunciation")
        if (q.hasAny(VOCAB_SENTENCE_WORDS)) return MediaIds.vocabMode("sentence")
        // Before vocabulary review: "review my mistakes" also contains "review".
        if (q.hasAny(MISTAKE_WORDS)) return MediaIds.MISTAKES
        if (q.hasAny(IELTS_WORDS)) return MediaIds.IELTS
        if (q.hasAny(REVIEW_WORDS)) return MediaIds.REVIEW
        if (q.hasAny(RESUME_STORY_WORDS)) return MediaIds.STORY_RESUME
        if (q.hasAny(RANDOM_WORDS) && !q.hasAny(STORY_WORDS)) return MediaIds.RANDOM

        val level = levelIn(q)
        val topic = topicManager.findTopicByQuery(stripFillers(q))

        if (q.hasAny(STORY_WORDS)) {
            if (q.hasAny(RESUME_STORY_WORDS)) return MediaIds.STORY_RESUME
            if (q.hasAny(RANDOM_WORDS)) return MediaIds.STORY_RANDOM
            val hasSearchIntent = q.contains(" ve ") || q.contains(" about ") ||
                q.contains(" tim ") || q.contains(" search ")
            val stripped = stripStoryWordsAndFillers(q)

            if (hasSearchIntent && stripped.length >= 3) {
                val (searchTopic, searchScenario) = topicManager.searchWebStory(stripped)
                return MediaIds.story(searchTopic.id, searchScenario.id)
            }

            val matchedTopic = if (stripped.isNotBlank()) topicManager.findTopicByQuery(stripped) else null
            val finalTopic = matchedTopic ?: if (!hasSearchIntent) topic else null
            if (finalTopic != null && topicManager.isStoryTopic(finalTopic.id)) {
                return MediaIds.story(finalTopic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${finalTopic.id}")
            }
            if (stripped.length >= 3) {
                val (searchTopic, searchScenario) = topicManager.searchWebStory(stripped)
                return MediaIds.story(searchTopic.id, searchScenario.id)
            }
            return MediaIds.STORY_RECOMMENDED
        }

        if (q.hasAny(PRONUNCIATION_WORDS)) return MediaIds.pronunciation(topic?.id)

        if (q.hasAny(ROLEPLAY_WORDS)) {
            if (topic != null) {
                val matchedStatic = topic.scenarios.firstOrNull { scenario ->
                    val sTitleVi = TopicManager.normalize(scenario.titleVi)
                    val sTitleEn = TopicManager.normalize(scenario.titleEn)
                    (sTitleVi.length >= 4 && q.contains(sTitleVi)) || (sTitleEn.length >= 4 && q.contains(sTitleEn))
                }
                if (matchedStatic != null) return MediaIds.scenario(matchedStatic.id)

                val stripped = stripRoleplayAndFillers(q, topic)
                if (stripped.length >= 3) {
                    val safeSlug = stripped.replace(' ', '_').take(30)
                    return MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}custom_${topic.id}_$safeSlug")
                }
                return topic.scenarios.randomOrNull()?.let { MediaIds.scenario(it.id) } ?: MediaIds.topic(topic.id, level)
            }
            val scenarios = topicManager.getAllTopics().flatMap { it.scenarios }
            return scenarios.randomOrNull()?.let { MediaIds.scenario(it.id) } ?: MediaIds.RESUME
        }
        return when {
            topic != null -> MediaIds.topic(topic.id, level)
            level != null -> MediaIds.level(level)
            else -> MediaIds.RESUME
        }
    }

    private fun levelIn(q: String): DifficultyLevel? = when {
        q.hasAny(PRE_INTERMEDIATE_WORDS) -> DifficultyLevel.PRE_INTERMEDIATE
        q.hasAny(UPPER_INTERMEDIATE_WORDS) -> DifficultyLevel.UPPER_INTERMEDIATE
        q.hasAny(ELEMENTARY_WORDS) -> DifficultyLevel.ELEMENTARY
        q.hasAny(BEGINNER_WORDS) -> DifficultyLevel.BEGINNER
        q.hasAny(ADVANCED_WORDS) -> DifficultyLevel.ADVANCED
        q.hasAny(INTERMEDIATE_WORDS) -> DifficultyLevel.INTERMEDIATE
        else -> null
    }

    /** Removes words like "english lesson on speakdrive" so they do not get in the way of topic matching. */
    private fun stripFillers(q: String): String =
        q.split(' ').filterNot { it in FILLER_WORDS }.joinToString(" ")

    private fun stripStoryWordsAndFillers(q: String): String {
        val storyAndActionWords = setOf(
            "ke", "nghe", "chuyen", "truyen", "doc", "cau", "story", "stories", "tell", "listen",
            "play", "start", "speakdrive", "please", "tieng", "anh", "english", "me", "a", "an", "the", "one",
            "cho", "toi", "minh", "ve", "mot", "nhung", "to", "about", "with"
        )
        return q.split(' ').filterNot { it.isBlank() || it in storyAndActionWords }.joinToString(" ").trim()
    }

    private fun stripRoleplayAndFillers(q: String, topic: com.speakdrive.ai.model.Topic): String {
        val ignoreWords = FILLER_WORDS + ROLEPLAY_WORDS + setOf(topic.id.lowercase()) +
                TopicManager.normalize(topic.titleVi).split(' ') +
                TopicManager.normalize(topic.titleEn).split(' ')
        return q.split(' ').filterNot { word ->
            word.isBlank() || word in ignoreWords
        }.joinToString(" ").trim()
    }

    private fun String.hasAny(phrases: List<String>): Boolean {
        val padded = " $this "
        return phrases.any { padded.contains(" $it ") }
    }

    private companion object {
        val VOCAB_PRONUNCIATION_WORDS = listOf(
            "luyen phat am tu vung", "phat am tu vung", "luyen phat am tu moi", "phat am tu moi",
            "vocab pronunciation", "pronounce vocabulary", "pronounce words", "practice word pronunciation"
        )
        val VOCAB_SENTENCE_WORDS = listOf(
            "luyen dat cau", "tap dat cau", "thu thach dat cau", "dat cau tu vung", "dat cau voi tu",
            "sentence making", "sentence practice", "practice making sentences", "sentence challenge", "make sentences"
        )
        val MISTAKE_WORDS = listOf(
            "mistake", "mistakes", "my errors", "loi sai", "on loi", "on lai loi", "loi cu", "cau sai"
        )
        val IELTS_WORDS = listOf(
            "ielts", "thi ielts", "luyen thi ielts", "luyen ielts", "ielts speaking", "practice ielts", "ielts practice", "test ielts"
        )
        val REVIEW_WORDS = listOf(
            "review", "vocabulary", "vocab", "words", "on tap", "tu vung",
            "so tu vung", "hoc tu vung", "luyen tu vung", "on tu vung", "hoc tu moi",
            "practice vocabulary", "study vocabulary", "vocabulary notebook"
        )
        val RANDOM_WORDS = listOf("random", "anything", "surprise me", "ngau nhien", "bat ky")
        val RESUME_STORY_WORDS = listOf(
            "tiep tuc cau chuyen", "tiep tuc truyen", "ke tiep chuyen", "ke tiep truyen",
            "bat tiep chuyen", "bat tiep truyen", "nghe tiep truyen", "nghe tiep chuyen",
            "chuyen dang nghe do", "truyen dang nghe do", "chuyen nghe do", "truyen nghe do",
            "resume story", "continue story", "continue last story", "resume unfinished story"
        )
        val STORY_WORDS = listOf(
            "story", "stories", "storytelling", "tell a story", "tell me a story", "listen to a story",
            "ke chuyen", "nghe chuyen", "cau chuyen", "chuyen ke", "doc truyen", "nghe truyen", "truyen"
        )
        val PRONUNCIATION_WORDS = listOf(
            "pronunciation", "pronounce", "repeat after me", "shadowing", "phat am", "nhac lai", "doc theo", "noi theo",
            "cau ngan", "short repetition", "short drill", "lap lai", "luyen cau ngan",
            "luyen shadowing", "chuyen sang shadowing", "chuyen qua shadowing", "tap phat am", "chuyen sang phat am",
            "chuyen sang luyen phat am", "chuyen qua phat am", "luyen noi theo", "tap noi theo", "doc theo ban",
            "nhac lai theo ban", "chuyen sang tap phat am", "shadowing mode", "practice pronunciation"
        )
        val ROLEPLAY_WORDS = listOf("roleplay", "role play", "role playing", "nhap vai", "dong vai")
        // "dễ" is left out: without diacritics it collides with the very common "để".
        val ELEMENTARY_WORDS = listOf("elementary", "so cap")
        val PRE_INTERMEDIATE_WORDS = listOf("pre intermediate", "pre-intermediate", "preintermediate", "tien trung cap", "so trung cap")
        val UPPER_INTERMEDIATE_WORDS = listOf("upper intermediate", "upper-intermediate", "upperintermediate", "trung cap tren", "trung cap cao", "trung cap nang cao")
        val BEGINNER_WORDS = listOf("beginner", "easy", "simple", "basic", "co ban", "moi bat dau")
        val INTERMEDIATE_WORDS = listOf("intermediate", "medium", "trung cap", "trung binh")
        val ADVANCED_WORDS = listOf("advanced", "hard", "difficult", "kho", "nang cao")
        val FILLER_WORDS = setOf(
            "play", "start", "practice", "practise", "lesson", "lessons", "english", "on", "in", "the", "a", "an",
            "my", "some", "speakdrive", "speak", "drive", "please", "conversation", "about", "bai", "hoc", "tieng", "anh",
            "luyen", "noi", "mo", "phat", "am", "pronunciation", "repeat", "after", "me", "easy", "hard",
            "beginner", "elementary", "pre", "pre-intermediate", "intermediate", "upper", "upper-intermediate", "advanced",
            "so", "cap", "tien", "trung"
        )
    }
}
