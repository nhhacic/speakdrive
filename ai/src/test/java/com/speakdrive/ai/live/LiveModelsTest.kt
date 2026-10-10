package com.speakdrive.ai.live

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.SessionMode
import org.junit.Test

class LiveModelsTest {

    @Test
    fun `conversation lessons try the newer model first, the drill keeps the older one`() {
        SessionMode.entries.filter { it != SessionMode.REPEAT_AFTER_ME }.forEach { mode ->
            assertThat(LiveModels.forMode(mode, conversationModel = "new", drillModel = "old")).containsExactly("new", "old").inOrder()
        }
        assertThat(LiveModels.forMode(SessionMode.REPEAT_AFTER_ME, conversationModel = "new", drillModel = "old"))
            .containsExactly("old")
    }

    @Test
    fun `the same model is listed once`() {
        assertThat(LiveModels.forMode(SessionMode.FREE_TALK, conversationModel = "old", drillModel = "old")).containsExactly("old")
    }
}
