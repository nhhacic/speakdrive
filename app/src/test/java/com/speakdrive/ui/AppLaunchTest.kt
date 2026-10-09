package com.speakdrive.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts the real app (SpeakDriveApplication with Hilt, Room, DataStore and Firebase) and the
 * real MainActivity, then walks through the main screens. Guards against launch crashes such
 * as the manifest pointing at the wrong activity class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "vi-rVN-w411dp-h1400dp")
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts on the home screen and opens settings and progress`() {
        compose.onNodeWithText("SpeakDrive").assertExists()
        compose.onNodeWithText("Chủ đề luyện tập").assertExists()

        compose.onNodeWithContentDescription("Cài đặt").performClick()
        compose.onNodeWithText("Luyện tập & Trình độ").performScrollTo().assertExists()
        compose.onNodeWithText("Giọng nói & Tương tác AI").performScrollTo().assertExists()
        compose.onNodeWithText("Chế độ Lái xe & Màn hình").performScrollTo().assertExists()
        compose.onNodeWithText("Dịch vụ Chấm phát âm Azure").performScrollTo().assertExists()

        compose.onNodeWithText("Thông tin & Hỗ trợ").performScrollTo().performClick()
        compose.onNodeWithText("Giới thiệu ứng dụng & Tác giả").performScrollTo().performClick()
        compose.onNodeWithText("nhhacic").assertExists()
        compose.onNodeWithContentDescription("Quay lại").performClick()

        compose.onNodeWithContentDescription("Quay lại danh mục").performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick()

        compose.onNodeWithContentDescription("Tiến trình").performClick()
        compose.onNodeWithText("7 ngày qua", substring = true).assertExists()
    }
}
