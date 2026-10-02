package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import org.junit.Test

class VoiceCommandHandlerTest {

    private val topics = TopicManager()
    private val handler = VoiceCommandHandler(topics)

    @Test
    fun `no query resumes the last lesson`() {
        assertThat(handler.resolve(null)).isEqualTo(MediaIds.RESUME)
        assertThat(handler.resolve("")).isEqualTo(MediaIds.RESUME)
        assertThat(handler.resolve("English practice")).isEqualTo(MediaIds.RESUME)
    }

    @Test
    fun `topic queries pick the topic`() {
        assertThat(handler.resolve("travel English")).isEqualTo(MediaIds.topic("travel"))
        assertThat(handler.resolve("play job interview practice on SpeakDrive")).isEqualTo(MediaIds.topic("interview"))
        assertThat(handler.resolve("bài học phỏng vấn")).isEqualTo(MediaIds.topic("interview"))
    }

    @Test
    fun `level words are kept with the topic`() {
        assertThat(handler.resolve("easy travel English")).isEqualTo(MediaIds.topic("travel", DifficultyLevel.BEGINNER))
        assertThat(handler.resolve("advanced business lesson")).isEqualTo(MediaIds.topic("work", DifficultyLevel.ADVANCED))
    }

    @Test
    fun `a level alone selects the level`() {
        assertThat(handler.resolve("hard English lesson")).isEqualTo(MediaIds.level(DifficultyLevel.ADVANCED))
    }

    @Test
    fun `review and random`() {
        assertThat(handler.resolve("review my vocabulary")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("ôn tập từ vựng")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("random topic")).isEqualTo(MediaIds.RANDOM)
    }

    @Test
    fun `pronunciation practice, with or without a topic`() {
        assertThat(handler.resolve("pronunciation practice")).isEqualTo(MediaIds.PRONUNCIATION)
        assertThat(handler.resolve("repeat after me travel English")).isEqualTo(MediaIds.pronunciation("travel"))
        assertThat(handler.resolve("luyện phát âm phỏng vấn")).isEqualTo(MediaIds.pronunciation("interview"))
    }

    @Test
    fun `roleplay picks a scenario of the topic`() {
        val target = MediaIds.parse(handler.resolve("restaurant role play"))
        assertThat(target).isInstanceOf(MediaTarget.Scenario::class.java)
        val scenarioId = (target as MediaTarget.Scenario).scenarioId
        assertThat(topics.getScenario(scenarioId)?.first?.id).isEqualTo("food")
    }
}
