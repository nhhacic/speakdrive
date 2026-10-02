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
        assertThat(MediaIds.parse(MediaIds.topic("food"))).isEqualTo(MediaTarget.Topic("food", null))
        assertThat(MediaIds.parse(MediaIds.topic("food", DifficultyLevel.ADVANCED)))
            .isEqualTo(MediaTarget.Topic("food", DifficultyLevel.ADVANCED))
        assertThat(MediaIds.parse(MediaIds.scenario("food_order"))).isEqualTo(MediaTarget.Scenario("food_order"))
        assertThat(MediaIds.parse(MediaIds.level(DifficultyLevel.BEGINNER))).isEqualTo(MediaTarget.Level(DifficultyLevel.BEGINNER))
    }

    @Test
    fun `folders are browsable and unknown ids are unknown`() {
        assertThat(MediaIds.parse(MediaIds.ROOT)).isEqualTo(MediaTarget.Browse(MediaIds.ROOT))
        assertThat(MediaIds.parse(MediaIds.roleplayTopic("travel"))).isEqualTo(MediaTarget.Browse("roleplay_topic:travel"))
        assertThat(MediaIds.parse("something-else")).isEqualTo(MediaTarget.Unknown)
        assertThat(MediaIds.parse(null)).isEqualTo(MediaTarget.Unknown)
    }
}
