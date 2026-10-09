package com.speakdrive.auto

import com.speakdrive.ai.model.DifficultyLevel

/**
 * Media ids of the Android Auto browse tree:
 *
 * ```
 * root
 * ├── home      Bắt đầu       → resume, story_recommended, random, pronunciation, review,
 * │                             mistakes, ielts, levels (Độ khó → level:<LEVEL>)
 * ├── stories   Luyện nghe kể chuyện → story_resume, story_recommended, story_random,
 * │                             story_topic:<id> → story:<topic>/<scenario>, stories_recent
 * ├── topics    Chủ đề         → topic:<id>
 * └── roleplay  Nhập vai       → roleplay_topic:<id> → scenario:<id>
 * ```
 * Android Auto shows at most four tabs, so the level picker lives at the end of "Bắt đầu".
 */
object MediaIds {
    const val ROOT = "root"
    const val HOME = "home"
    const val TOPICS = "topics"
    const val ROLEPLAY = "roleplay"
    const val LEVELS = "levels"

    const val RESUME = "resume"
    const val RANDOM = "random"
    const val REVIEW = "review"

    /** Mistake review: the learner's own earlier mistakes, said correctly this time. */
    const val MISTAKES = "mistakes"

    /** IELTS speaking exam simulation with 3 parts and estimated band score. */
    const val IELTS = "ielts"

    /** Custom user-created scenarios saved in local database. */
    const val CUSTOM_SCENARIOS = "custom_scenarios"

    /** Repeat-after-me drill on the last topic; [pronunciation] targets a specific topic. */
    const val PRONUNCIATION = "pronunciation"

    /** Story listening constants. */
    const val STORIES = "stories"
    const val STORY_RECOMMENDED = "story_recommended"
    const val STORY_RESUME = "story_resume"
    const val STORY_RANDOM = "story_random"
    const val STORIES_RECENT = "stories_recent"

    const val LESSON = "lesson"
    const val VOCAB_PREFIX = "vocab:"

    private const val TOPIC_PREFIX = "topic:"
    private const val ROLEPLAY_TOPIC_PREFIX = "roleplay_topic:"
    private const val STORY_PREFIX = "story:"
    private const val STORY_TOPIC_PREFIX = "story_topic:"
    private const val SCENARIO_PREFIX = "scenario:"
    private const val LEVEL_PREFIX = "level:"
    private const val PRONUNCIATION_PREFIX = "pronunciation:"
    private const val LEVEL_SEPARATOR = "@"

    fun vocabWord(word: String) = VOCAB_PREFIX + "word:" + word
    fun vocabMode(mode: String) = VOCAB_PREFIX + "mode:" + mode

    fun topic(topicId: String, level: DifficultyLevel? = null) =
        TOPIC_PREFIX + topicId + (level?.let { LEVEL_SEPARATOR + it.name } ?: "")

    fun roleplayTopic(topicId: String) = ROLEPLAY_TOPIC_PREFIX + topicId
    fun storyTopic(topicId: String) = STORY_TOPIC_PREFIX + topicId
    fun pronunciation(topicId: String? = null) = if (topicId == null) PRONUNCIATION else PRONUNCIATION_PREFIX + topicId
    fun scenario(scenarioId: String) = SCENARIO_PREFIX + scenarioId
    fun level(level: DifficultyLevel) = LEVEL_PREFIX + level.name
    fun browse(category: String): String = category

    fun story(topicId: String? = null, scenarioId: String? = null): String = when {
        topicId != null && scenarioId != null -> STORY_PREFIX + topicId + "/" + scenarioId
        scenarioId != null -> STORY_PREFIX + "scenario:" + scenarioId
        topicId != null -> STORY_PREFIX + topicId
        else -> STORY_RECOMMENDED
    }

    fun parse(mediaId: String?): MediaTarget = when {
        mediaId == null -> MediaTarget.Unknown
        mediaId == RESUME -> MediaTarget.Resume
        mediaId == RANDOM -> MediaTarget.Random
        mediaId == REVIEW -> MediaTarget.Review
        mediaId == MISTAKES -> MediaTarget.Mistakes
        mediaId == IELTS -> MediaTarget.Ielts
        mediaId.startsWith(VOCAB_PREFIX) -> {
            val rest = mediaId.removePrefix(VOCAB_PREFIX)
            when {
                rest.startsWith("word:") -> MediaTarget.Vocab(word = rest.removePrefix("word:"), mode = null)
                rest.startsWith("mode:") -> MediaTarget.Vocab(word = null, mode = rest.removePrefix("mode:"))
                else -> MediaTarget.Vocab(word = rest.takeIf { it.isNotBlank() }, mode = null)
            }
        }
        mediaId == STORY_RECOMMENDED -> MediaTarget.StoryRecommended
        mediaId == STORY_RESUME -> MediaTarget.StoryResume
        mediaId == STORY_RANDOM -> MediaTarget.Story("story_famous", "dynamic_story_random")
        mediaId == PRONUNCIATION -> MediaTarget.Pronunciation(null)
        mediaId.startsWith(PRONUNCIATION_PREFIX) -> MediaTarget.Pronunciation(mediaId.removePrefix(PRONUNCIATION_PREFIX))
        mediaId.startsWith(STORY_PREFIX) -> {
            val rest = mediaId.removePrefix(STORY_PREFIX)
            when {
                rest.contains("/") -> {
                    val parts = rest.split("/", limit = 2)
                    MediaTarget.Story(parts[0], parts[1])
                }
                rest.startsWith("scenario:") -> MediaTarget.Story(null, rest.removePrefix("scenario:"))
                else -> MediaTarget.Story(rest, null)
            }
        }
        mediaId in setOf(ROOT, HOME, TOPICS, ROLEPLAY, LEVELS, STORIES, STORIES_RECENT, CUSTOM_SCENARIOS) -> MediaTarget.Browse(mediaId)
        mediaId.startsWith(ROLEPLAY_TOPIC_PREFIX) -> MediaTarget.Browse(mediaId)
        mediaId.startsWith(STORY_TOPIC_PREFIX) -> MediaTarget.Browse(mediaId)
        mediaId.startsWith(TOPIC_PREFIX) -> {
            val rest = mediaId.removePrefix(TOPIC_PREFIX)
            val topicId = rest.substringBefore(LEVEL_SEPARATOR)
            val level = rest.substringAfter(LEVEL_SEPARATOR, "").takeIf { it.isNotEmpty() }?.let(DifficultyLevel::fromStored)
            MediaTarget.Topic(topicId, level)
        }
        mediaId.startsWith(SCENARIO_PREFIX) -> MediaTarget.Scenario(mediaId.removePrefix(SCENARIO_PREFIX))
        mediaId.startsWith(LEVEL_PREFIX) -> MediaTarget.Level(DifficultyLevel.fromStored(mediaId.removePrefix(LEVEL_PREFIX)))
        else -> MediaTarget.Unknown
    }
}

sealed interface MediaTarget {
    data object Resume : MediaTarget
    data object Random : MediaTarget
    data object Review : MediaTarget
    data object Mistakes : MediaTarget
    data object Ielts : MediaTarget
    data class Vocab(val word: String?, val mode: String?) : MediaTarget
    data object StoryRecommended : MediaTarget
    data object StoryResume : MediaTarget
    data class Story(val topicId: String?, val scenarioId: String?) : MediaTarget
    data class Pronunciation(val topicId: String?) : MediaTarget
    data class Topic(val topicId: String, val level: DifficultyLevel?) : MediaTarget
    data class Scenario(val scenarioId: String) : MediaTarget
    data class Level(val level: DifficultyLevel) : MediaTarget
    data class Browse(val mediaId: String) : MediaTarget
    data object Unknown : MediaTarget
}
