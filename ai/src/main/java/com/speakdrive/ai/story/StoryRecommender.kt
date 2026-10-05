package com.speakdrive.ai.story

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.Scenario
import com.speakdrive.ai.model.Topic
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates learner preferences from past story listening sessions
 * and recommends topics and stories tailored to their taste.
 */
@Singleton
class StoryRecommender @Inject constructor(
    private val topicManager: TopicManager
) {
    /**
     * Recommends a story scenario based on session history, skipped items, and learner CEFR level.
     * [recentSessions]: list of past story sessions, newest first.
     * [excludeScenarioIds]: scenarios to avoid (e.g. current story just skipped).
     * [learnerLevel]: the learner's current CEFR difficulty level.
     */
    fun recommendStory(
        recentSessions: List<CompletedSession>,
        excludeScenarioIds: Set<String> = emptySet(),
        learnerLevel: com.speakdrive.ai.model.DifficultyLevel = com.speakdrive.ai.model.DifficultyLevel.INTERMEDIATE
    ): Pair<Topic, Scenario> {
        val storyTopics = topicManager.getStoryTopics().ifEmpty { topicManager.getAllTopics() }

        // Compute topic affinity scores based on completion, active duration, and CEFR level affinity
        val topicScores = mutableMapOf<String, Int>()
        for (session in recentSessions) {
            val scoreDelta = if (session.isCompleted) 3 else -2
            val durationBonus = (session.activeDurationMs / 30_000L).coerceAtMost(3).toInt()
            val totalScore = scoreDelta + durationBonus
            topicScores[session.topicId] = (topicScores[session.topicId] ?: 0) + totalScore
        }

        // Apply gentle level-appropriate affinity bias
        for (topic in storyTopics) {
            val levelBonus = when {
                learnerLevel <= com.speakdrive.ai.model.DifficultyLevel.ELEMENTARY &&
                    (topic.id == "story_adventure" || topic.id == "story_famous") -> 2
                learnerLevel >= com.speakdrive.ai.model.DifficultyLevel.UPPER_INTERMEDIATE &&
                    (topic.id == "story_classic_mystery" || topic.id == "story_sci_fi_tales") -> 2
                else -> 0
            }
            topicScores[topic.id] = (topicScores[topic.id] ?: 0) + levelBonus
        }

        // Rank story topics by score
        val sortedTopics = storyTopics.sortedByDescending { topicScores[it.id] ?: 0 }

        // Find best scenario not recently visited or excluded
        val visitedScenarioIds = recentSessions.mapNotNull { it.scenarioId }.toSet() + excludeScenarioIds

        for (topic in sortedTopics) {
            val available = topic.scenarios.filterNot { it.id in visitedScenarioIds }
            if (available.isNotEmpty()) {
                return topic to available.random()
            }
        }

        // If all standard scenarios were visited, fallback to dynamic personalized recommendation
        val fallbackTopic = sortedTopics.firstOrNull() ?: storyTopics.first()
        val dynamicScenario = Scenario(
            id = "${TopicManager.DYNAMIC_PREFIX}story_recommended_${fallbackTopic.id}",
            titleVi = "Chuyện AI gợi ý theo trình độ ${learnerLevel.labelVi}",
            titleEn = "AI Story for ${learnerLevel.displayName}",
            aiRole = "a captivating storyteller tailoring an engaging tale to the listener's favorite themes in ${fallbackTopic.titleEn} adapted for ${learnerLevel.displayName} level",
            learnerRole = "the listener enjoying a fresh personalized story",
            customContext = "The learner (${learnerLevel.displayName} / CEFR ${learnerLevel.cefr}) enjoys stories in ${fallbackTopic.titleEn} (${fallbackTopic.titleVi}). Narrate an intriguing, fresh, and unexpected story with rich narrative suspense, adapted specifically in vocabulary and pacing for ${learnerLevel.displayName} learners."
        )
        return fallbackTopic to dynamicScenario
    }

    /**
     * Builds a personalization hint for Gemini's system instruction.
     */
    fun buildTasteSummaryPrompt(recentSessions: List<CompletedSession>): String {
        if (recentSessions.isEmpty()) return ""
        val completedTopics = recentSessions.filter { it.isCompleted }.map { it.topicId }
        val skippedTopics = recentSessions.filterNot { it.isCompleted }.map { it.topicId }

        val favoriteNames = completedTopics.distinct().mapNotNull { topicManager.getTopicById(it)?.titleEn }
        val avoidedNames = skippedTopics.distinct().filterNot { it in completedTopics }.mapNotNull { topicManager.getTopicById(it)?.titleEn }

        return buildString {
            if (favoriteNames.isNotEmpty()) {
                append("Learner taste: enjoys themes like ${favoriteNames.joinToString(", ")}. ")
            }
            if (avoidedNames.isNotEmpty()) {
                append("Tends to skip or dislike: ${avoidedNames.joinToString(", ")}. Keep the story dynamic, engaging, and avoid sluggish pacing. ")
            }
        }.trim()
    }
}
