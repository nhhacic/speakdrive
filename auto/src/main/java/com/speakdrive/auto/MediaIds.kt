package com.speakdrive.auto

import com.speakdrive.ai.model.DifficultyLevel

/**
 * Media ids of the Android Auto browse tree:
 *
 * ```
 * root
 * ├── home      Bắt đầu       → resume, random, review
 * ├── topics    Chủ đề         → topic:<id>
 * ├── roleplay  Nhập vai       → roleplay_topic:<id> → scenario:<id>
 * └── levels    Độ khó         → level:<LEVEL>
 * ```
 * Android Auto shows the four browsable root children as tabs.
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

    /** The item shown while a lesson is playing. */
    const val LESSON = "lesson"

    private const val TOPIC_PREFIX = "topic:"
    private const val ROLEPLAY_TOPIC_PREFIX = "roleplay_topic:"
    private const val SCENARIO_PREFIX = "scenario:"
    private const val LEVEL_PREFIX = "level:"
    private const val LEVEL_SEPARATOR = "@"

    fun topic(topicId: String, level: DifficultyLevel? = null) =
        TOPIC_PREFIX + topicId + (level?.let { LEVEL_SEPARATOR + it.name } ?: "")

    fun roleplayTopic(topicId: String) = ROLEPLAY_TOPIC_PREFIX + topicId
    fun scenario(scenarioId: String) = SCENARIO_PREFIX + scenarioId
    fun level(level: DifficultyLevel) = LEVEL_PREFIX + level.name

    fun parse(mediaId: String?): MediaTarget = when {
        mediaId == null -> MediaTarget.Unknown
        mediaId == RESUME -> MediaTarget.Resume
        mediaId == RANDOM -> MediaTarget.Random
        mediaId == REVIEW -> MediaTarget.Review
        mediaId in setOf(ROOT, HOME, TOPICS, ROLEPLAY, LEVELS) -> MediaTarget.Browse(mediaId)
        mediaId.startsWith(ROLEPLAY_TOPIC_PREFIX) -> MediaTarget.Browse(mediaId)
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
    data class Topic(val topicId: String, val level: DifficultyLevel?) : MediaTarget
    data class Scenario(val scenarioId: String) : MediaTarget
    data class Level(val level: DifficultyLevel) : MediaTarget
    data class Browse(val mediaId: String) : MediaTarget
    data object Unknown : MediaTarget
}
