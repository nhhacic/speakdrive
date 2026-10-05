package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.DifficultyLevel
import org.junit.Test

class MediaIdsTest {

    @Test
    fun `round-trips every kind of id`() {
        assertThat(MediaIds.parse(MediaIds.RESUME)).isEqualTo(MediaTarget.Resume)
        assertThat(MediaIds.parse(MediaIds.RANDOM)).isEqualTo(MediaTarget.Random)
        assertThat(MediaIds.parse(MediaIds.REVIEW)).isEqualTo(MediaTarget.Review)
        assertThat(MediaIds.parse(MediaIds.PRONUNCIATION)).isEqualTo(MediaTarget.Pronunciation(null))
        assertThat(MediaIds.parse(MediaIds.pronunciation("food"))).isEqualTo(MediaTarget.Pronunciation("food"))
        assertThat(MediaIds.parse(MediaIds.topic("food"))).isEqualTo(MediaTarget.Topic("food", null))
        assertThat(MediaIds.parse(MediaIds.topic("food", DifficultyLevel.ADVANCED)))
            .isEqualTo(MediaTarget.Topic("food", DifficultyLevel.ADVANCED))
        assertThat(MediaIds.parse(MediaIds.scenario("food_order"))).isEqualTo(MediaTarget.Scenario("food_order"))
        assertThat(MediaIds.parse(MediaIds.level(DifficultyLevel.BEGINNER))).isEqualTo(MediaTarget.Level(DifficultyLevel.BEGINNER))
        assertThat(MediaIds.parse(MediaIds.vocabWord("appreciate"))).isEqualTo(MediaTarget.Vocab(word = "appreciate", mode = null))
        assertThat(MediaIds.parse(MediaIds.vocabMode("pronunciation"))).isEqualTo(MediaTarget.Vocab(word = null, mode = "pronunciation"))
        assertThat(MediaIds.parse(MediaIds.STORY_RECOMMENDED)).isEqualTo(MediaTarget.StoryRecommended)
        assertThat(MediaIds.parse(MediaIds.STORY_RANDOM)).isEqualTo(MediaTarget.Story("story_famous", "dynamic_story_random"))
        assertThat(MediaIds.parse(MediaIds.story("story_science", "science_relativity")))
            .isEqualTo(MediaTarget.Story("story_science", "science_relativity"))
    }

    @Test
    fun `folders are browsable and unknown ids are unknown`() {
        assertThat(MediaIds.parse(MediaIds.ROOT)).isEqualTo(MediaTarget.Browse(MediaIds.ROOT))
        assertThat(MediaIds.parse(MediaIds.STORIES)).isEqualTo(MediaTarget.Browse(MediaIds.STORIES))
        assertThat(MediaIds.parse(MediaIds.storyTopic("story_science"))).isEqualTo(MediaTarget.Browse("story_topic:story_science"))
        assertThat(MediaIds.parse(MediaIds.roleplayTopic("travel"))).isEqualTo(MediaTarget.Browse("roleplay_topic:travel"))
        assertThat(MediaIds.parse("something-else")).isEqualTo(MediaTarget.Unknown)
        assertThat(MediaIds.parse(null)).isEqualTo(MediaTarget.Unknown)
    }
}
