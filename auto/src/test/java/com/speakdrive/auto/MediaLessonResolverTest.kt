package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.session.LearningSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaLessonResolverTest {

    private class FakeLearningSettings : LearningSettings {
        var current = LearnerSettings(
            level = DifficultyLevel.INTERMEDIATE,
            lastTopicId = "daily",
            lastSessionMode = SessionMode.FREE_TALK,
            lastScenarioId = null
        )

        override suspend fun snapshot(): LearnerSettings = current
        override suspend fun setLevel(level: DifficultyLevel) {
            current = current.copy(level = level)
        }
        override suspend fun setLastTopicId(topicId: String) {
            current = current.copy(lastTopicId = topicId)
        }
        override suspend fun setAllowVietnameseHelp(allowed: Boolean) {}
    }

    @Test
    fun `resolves topic id into lesson request`() = runTest {
        val settings = FakeLearningSettings()
        val request = MediaLessonResolver.resolve(MediaIds.topic("travel"), settings)
        assertThat(request).isNotNull()
        assertThat(request!!.topicId).isEqualTo("travel")
        assertThat(request.mode).isEqualTo(SessionMode.FREE_TALK)
    }

    @Test
    fun `resolves pronunciation id into repeat after me request`() = runTest {
        val settings = FakeLearningSettings()
        val request = MediaLessonResolver.resolve(MediaIds.pronunciation("travel"), settings)
        assertThat(request).isNotNull()
        assertThat(request!!.topicId).isEqualTo("travel")
        assertThat(request.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }

    @Test
    fun `resolves scenario id into roleplay request`() = runTest {
        val settings = FakeLearningSettings()
        val request = MediaLessonResolver.resolve(MediaIds.scenario("hotel_checkin"), settings)
        assertThat(request).isNotNull()
        assertThat(request!!.scenarioId).isEqualTo("hotel_checkin")
        assertThat(request.mode).isEqualTo(SessionMode.ROLEPLAY)
    }

    @Test
    fun `resolves review and mistakes into correct modes`() = runTest {
        val settings = FakeLearningSettings()
        val reviewReq = MediaLessonResolver.resolve(MediaIds.REVIEW, settings)
        assertThat(reviewReq!!.mode).isEqualTo(SessionMode.VOCAB_REVIEW)

        val mistakeReq = MediaLessonResolver.resolve(MediaIds.MISTAKES, settings)
        assertThat(mistakeReq!!.mode).isEqualTo(SessionMode.MISTAKE_REVIEW)
    }

    @Test
    fun `resolves resume based on last session mode`() = runTest {
        val settings = FakeLearningSettings().apply {
            current = LearnerSettings(
                level = DifficultyLevel.ADVANCED,
                lastTopicId = "business",
                lastSessionMode = SessionMode.REPEAT_AFTER_ME,
                lastScenarioId = null
            )
        }
        val request = MediaLessonResolver.resolve(MediaIds.RESUME, settings)
        assertThat(request).isNotNull()
        assertThat(request!!.topicId).isEqualTo("business")
        assertThat(request.mode).isEqualTo(SessionMode.REPEAT_AFTER_ME)
    }
}
