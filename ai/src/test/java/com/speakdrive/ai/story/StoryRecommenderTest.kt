package com.speakdrive.ai.story

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import org.junit.Test

class StoryRecommenderTest {

    private val topicManager = TopicManager()
    private val recommender = StoryRecommender(topicManager)

    @Test
    fun `recommends a story scenario when no history exists`() {
        val (topic, scenario) = recommender.recommendStory(emptyList())
        assertThat(topicManager.isStoryTopic(topic.id)).isTrue()
        assertThat(scenario.titleVi).isNotEmpty()
    }

    @Test
    fun `excludes specified scenario id when skipping story`() {
        val allScenarios = topicManager.getStoryTopics().flatMap { it.scenarios }.map { it.id }
        val excludedId = allScenarios.first()

        repeat(10) {
            val (_, scenario) = recommender.recommendStory(
                recentSessions = emptyList(),
                excludeScenarioIds = setOf(excludedId)
            )
            assertThat(scenario.id).isNotEqualTo(excludedId)
        }
    }

    @Test
    fun `prioritizes topics with higher completion and engagement score`() {
        // User loves science stories (completed multiple times with long duration)
        val scienceSessions = listOf(
            fakeSession("story_science", "science_relativity", isCompleted = true, durationMs = 120_000L),
            fakeSession("story_science", "science_marie_curie", isCompleted = true, durationMs = 90_000L)
        )
        // User disliked famous people (skipped quickly)
        val famousSessions = listOf(
            fakeSession("story_famous", "famous_steve_jobs", isCompleted = false, durationMs = 5_000L)
        )

        val history = scienceSessions + famousSessions
        val (topic, scenario) = recommender.recommendStory(history)

        // Must recommend another science story that hasn't been visited yet
        assertThat(topic.id).isEqualTo("story_science")
        assertThat(scenario.id).isNotIn(listOf("science_relativity", "science_marie_curie"))
    }

    @Test
    fun `falls back to dynamic personalized recommendation when all standard scenarios visited`() {
        val visitedSessions = topicManager.getStoryTopics().flatMap { topic ->
            topic.scenarios.map { scenario ->
                fakeSession(topic.id, scenario.id, isCompleted = true, durationMs = 60_000L)
            }
        }

        val (topic, scenario) = recommender.recommendStory(visitedSessions)
        assertThat(topicManager.isStoryTopic(topic.id)).isTrue()
        assertThat(scenario.id).startsWith(TopicManager.DYNAMIC_PREFIX)
    }

    @Test
    fun `builds taste summary prompt for Gemini instruction`() {
        val history = listOf(
            fakeSession("story_science", "science_relativity", isCompleted = true, durationMs = 120_000L),
            fakeSession("story_famous", "famous_steve_jobs", isCompleted = false, durationMs = 10_000L)
        )

        val prompt = recommender.buildTasteSummaryPrompt(history)
        assertThat(prompt).contains("Learner taste: enjoys themes like")
        assertThat(prompt).contains("Tends to skip or dislike:")
    }

    @Test
    fun `recommends dynamic personalized story with matching learner level in context`() {
        val visitedSessions = topicManager.getStoryTopics().flatMap { topic ->
            topic.scenarios.map { scenario ->
                fakeSession(topic.id, scenario.id, isCompleted = true, durationMs = 60_000L)
            }
        }

        val (topic, scenario) = recommender.recommendStory(visitedSessions, learnerLevel = DifficultyLevel.BEGINNER)
        assertThat(topicManager.isStoryTopic(topic.id)).isTrue()
        assertThat(scenario.titleVi).contains("Người mới bắt đầu")
        assertThat(scenario.customContext).contains("Beginner")
        assertThat(scenario.customContext).contains("A1")
    }

    @Test
    fun `biases recommendation towards beginner-friendly topics for beginner level`() {
        // Without history, Beginner should favor adventure or famous people
        val (topic, _) = recommender.recommendStory(emptyList(), learnerLevel = DifficultyLevel.BEGINNER)
        assertThat(topic.id).isIn(listOf("story_adventure", "story_famous"))
    }

    private fun fakeSession(
        topicId: String,
        scenarioId: String,
        isCompleted: Boolean,
        durationMs: Long
    ) = CompletedSession(
        id = "test-session-${System.nanoTime()}",
        topicId = topicId,
        scenarioId = scenarioId,
        level = DifficultyLevel.INTERMEDIATE,
        mode = SessionMode.STORY_LISTENING,
        startedAt = 1000L,
        endedAt = 1000L + durationMs,
        activeDurationMs = durationMs,
        summary = null,
        transcript = emptyList(),
        isCompleted = isCompleted
    )
}
