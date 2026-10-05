package com.speakdrive.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ui.components.MicState
import com.speakdrive.ui.components.VoiceMicButton
import com.speakdrive.ui.theme.SpeakDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "vi-rVN-w411dp-h1400dp")
class VoiceMicButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `mic button shows AI speaking state and responds to click`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.AI_SPEAKING,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("AI đang nói — chạm để tạm dừng")
        node.assertIsDisplayed()
        node.performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `mic button shows listening state waiting for learner to speak`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.LISTENING,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("Đang đợi bạn nói — chạm để tạm dừng")
        node.assertIsDisplayed()
        node.performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `mic button shows user speaking state when learner talks`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.USER_SPEAKING,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("Đang nghe bạn nói — chạm để tạm dừng")
        node.assertIsDisplayed()
        node.performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `mic button shows AI thinking state while AI processes reply`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.AI_THINKING,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("AI đang suy nghĩ — chạm để tạm dừng")
        node.assertIsDisplayed()
        node.performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `mic button shows paused state and can resume`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.PAUSED,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("Tiếp tục bài học")
        node.assertIsDisplayed()
        node.performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `mic button shows busy state and is disabled`() {
        var clicked = false
        compose.setContent {
            SpeakDriveTheme {
                VoiceMicButton(
                    state = MicState.BUSY,
                    onClick = { clicked = true }
                )
            }
        }

        val node = compose.onNodeWithContentDescription("Đang kết nối")
        node.assertIsDisplayed()
        node.assertIsNotEnabled()
        assertThat(clicked).isFalse()
    }
}
