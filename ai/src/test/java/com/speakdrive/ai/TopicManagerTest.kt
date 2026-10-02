package com.speakdrive.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicManagerTest {

    private val topics = TopicManager()

    @Test
    fun `has eight topics with unique ids and four scenarios each`() {
        val all = topics.getAllTopics()
        assertThat(all).hasSize(8)
        assertThat(all.map { it.id }.toSet()).hasSize(8)
        all.forEach { assertThat(it.scenarios).hasSize(4) }
        val scenarioIds = all.flatMap { topic -> topic.scenarios.map { it.id } }
        assertThat(scenarioIds.toSet()).hasSize(scenarioIds.size)
    }

    @Test
    fun `normalize strips Vietnamese diacritics`() {
        assertThat(TopicManager.normalize("Phỏng vấn xin việc")).isEqualTo("phong van xin viec")
        assertThat(TopicManager.normalize("Đi lại!")).isEqualTo("di lai")
    }

    @Test
    fun `finds topics from English and Vietnamese queries`() {
        assertThat(topics.findTopicByQuery("travel english")?.id).isEqualTo("travel")
        assertThat(topics.findTopicByQuery("Phỏng vấn")?.id).isEqualTo("interview")
        assertThat(topics.findTopicByQuery("job interview practice")?.id).isEqualTo("interview")
        assertThat(topics.findTopicByQuery("movies")?.id).isEqualTo("entertainment")
        assertThat(topics.findTopicByQuery("ăn uống")?.id).isEqualTo("food")
    }

    @Test
    fun `does not match partial words`() {
        assertThat(topics.findTopicByQuery("network")).isNull()
        assertThat(topics.findTopicByQuery("")).isNull()
    }

    @Test
    fun `finds the topic of a scenario`() {
        val (topic, scenario) = topics.getScenario("interview_salary")!!
        assertThat(topic.id).isEqualTo("interview")
        assertThat(scenario.titleVi).isEqualTo("Đàm phán lương")
        assertThat(topics.getScenario("nope")).isNull()
    }

    @Test
    fun `suggests a topic that was not practised recently`() {
        val recent = topics.getAllTopics().map { it.id }.filterNot { it == "shopping" }
        repeat(20) { assertThat(topics.suggestTopic(recent).id).isEqualTo("shopping") }
    }
}
