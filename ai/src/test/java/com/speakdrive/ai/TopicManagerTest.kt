package com.speakdrive.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicManagerTest {

    private val topics = TopicManager()

    @Test
    fun `has all topics with unique ids and four scenarios each`() {
        val all = topics.getAllTopics()
        assertThat(all).hasSize(24)
        assertThat(all.map { it.id }.toSet()).hasSize(24)
        all.forEach { assertThat(it.scenarios.size).isAtLeast(4) }
        val scenarioIds = all.flatMap { topic -> topic.scenarios.map { it.id } }
        assertThat(scenarioIds.toSet()).hasSize(scenarioIds.size)
        // Verify roleplay conversation topics all have non-blank mission objectives
        topics.getConversationTopics().flatMap { it.scenarios }.forEach { scenario ->
            assertThat(scenario.missionObjective).isNotNull()
            assertThat(scenario.missionObjective).isNotEmpty()
        }
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
        assertThat(topics.findTopicByQuery("bác sĩ y khoa")?.id).isEqualTo("medical_expert")
        assertThat(topics.findTopicByQuery("phẫu thuật lâm sàng")?.id).isEqualTo("medical_expert")
        assertThat(topics.findTopicByQuery("lập trình phần mềm")?.id).isEqualTo("tech_it")
        assertThat(topics.findTopicByQuery("kubernetes cloud architecture")?.id).isEqualTo("tech_it")
        assertThat(topics.findTopicByQuery("công trình xây dựng")?.id).isEqualTo("civil_engineering")
        assertThat(topics.findTopicByQuery("giao thông hạ tầng")?.id).isEqualTo("transport_engineering")
        assertThat(topics.findTopicByQuery("cầu đường cao tốc")?.id).isEqualTo("transport_engineering")
        assertThat(topics.findTopicByQuery("cứu hộ giao thông")?.id).isEqualTo("driving_emergency")
        assertThat(topics.findTopicByQuery("hẹn hò kết bạn")?.id).isEqualTo("social_dating")
        assertThat(topics.findTopicByQuery("startup ai kỳ lân")?.id).isEqualTo("startup_tech")
        assertThat(topics.findTopicByQuery("vụ cướp thế kỷ")?.id).isEqualTo("story_detective_heists")
        assertThat(topics.findTopicByQuery("sinh tồn kỷ lục")?.id).isEqualTo("story_extreme_survival")
        assertThat(topics.findTopicByQuery("sốc văn hóa")?.id).isEqualTo("story_comedy_misadventures")
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
    fun `resolves dynamic explore and custom scenarios`() {
        val (exploreTopic, exploreScenario) = topics.getScenario("dynamic_explore_medical_expert")!!
        assertThat(exploreTopic.id).isEqualTo("medical_expert")
        assertThat(exploreScenario.titleVi).contains("Khám phá tình huống AI")

        val (customTopic, customScenario) = topics.getScenario("dynamic_custom_tech_it_kubernetes_cloud")!!
        assertThat(customTopic.id).isEqualTo("tech_it")
        assertThat(customScenario.customContext).contains("kubernetes cloud")
    }

    @Test
    fun `separates conversation topics and story topics`() {
        val conversationTopics = topics.getConversationTopics()
        val storyTopics = topics.getStoryTopics()

        assertThat(conversationTopics).hasSize(15)
        assertThat(storyTopics).hasSize(9)
        assertThat(storyTopics.map { it.id }).containsExactly(
            "story_adventure",
            "story_classic_mystery",
            "story_sci_fi_tales",
            "story_famous",
            "story_science",
            "story_history",
            "story_detective_heists",
            "story_extreme_survival",
            "story_comedy_misadventures"
        )

        assertThat(topics.isStoryTopic("story_adventure")).isTrue()
        assertThat(topics.isStoryTopic("story_famous")).isTrue()
        assertThat(topics.isStoryTopic("story_science")).isTrue()
        assertThat(topics.isStoryTopic("story_detective_heists")).isTrue()
        assertThat(topics.isStoryTopic("story_extreme_survival")).isTrue()
        assertThat(topics.isStoryTopic("travel")).isFalse()
    }

    @Test
    fun `resolves dynamic story scenarios`() {
        val (randomTopic, randomScenario) = topics.getScenario("dynamic_story_random")!!
        assertThat(topics.isStoryTopic(randomTopic.id)).isTrue()
        assertThat(randomScenario.titleVi).contains("Ngẫu nhiên")

        val (customTopic, customScenario) = topics.getScenario("dynamic_story_custom_steve_jobs")!!
        assertThat(customTopic.id).isEqualTo("story_famous")
        assertThat(customScenario.customContext).contains("steve jobs")

        val (recTopic, recScenario) = topics.getScenario("dynamic_story_recommended_story_science")!!
        assertThat(recTopic.id).isEqualTo("story_science")
        assertThat(recScenario.titleVi).contains("Chuyện AI gợi ý")
    }

    @Test
    fun `suggests a topic that was not practised recently`() {
        val recent = topics.getAllTopics().map { it.id }.filterNot { it == "shopping" }
        repeat(20) { assertThat(topics.suggestTopic(recent).id).isEqualTo("shopping") }
    }
}
