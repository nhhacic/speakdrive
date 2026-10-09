package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.SessionMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LessonActionLabelsTest {

    private fun labels(mode: SessionMode?): Pair<String, String> {
        val context = RuntimeEnvironment.getApplication()
        val ids = LessonActionLabels.forMode(mode)
        return context.getString(ids.repeat) to context.getString(ids.next)
    }

    @Test
    fun `every mode gets two different labels`() {
        (SessionMode.entries + null).forEach { mode ->
            val ids = LessonActionLabels.forMode(mode)
            assertThat(ids.repeat).isNotEqualTo(ids.next)
        }
    }

    @Test
    @Config(qualifiers = "vi")
    fun `vietnamese labels follow the lesson mode`() {
        assertThat(labels(null)).isEqualTo("Lặp lại câu" to "Câu tiếp theo")
        assertThat(labels(SessionMode.STORY_LISTENING)).isEqualTo("Kể lại từ đầu" to "Đổi truyện")
        assertThat(labels(SessionMode.REPEAT_AFTER_ME)).isEqualTo("Đọc lại câu" to "Câu tiếp theo")
        assertThat(labels(SessionMode.FREE_TALK)).isEqualTo("AI nói lại" to "Câu hỏi khác")
        assertThat(labels(SessionMode.ROLEPLAY)).isEqualTo("AI nói lại" to "Tình tiết tiếp")
        assertThat(labels(SessionMode.VOCAB_REVIEW)).isEqualTo("AI nói lại" to "Từ tiếp theo")
        assertThat(labels(SessionMode.MISTAKE_REVIEW)).isEqualTo("AI nói lại" to "Câu tiếp theo")
        assertThat(labels(SessionMode.IELTS_SPEAKING)).isEqualTo("AI nói lại" to "Câu hỏi tiếp")
    }

    @Test
    @Config(qualifiers = "en")
    fun `english labels follow the lesson mode`() {
        assertThat(labels(null)).isEqualTo("Repeat sentence" to "Next sentence")
        assertThat(labels(SessionMode.STORY_LISTENING)).isEqualTo("Restart story" to "Change story")
        assertThat(labels(SessionMode.REPEAT_AFTER_ME)).isEqualTo("Hear it again" to "Next sentence")
        assertThat(labels(SessionMode.FREE_TALK)).isEqualTo("Say that again" to "Another question")
        assertThat(labels(SessionMode.ROLEPLAY)).isEqualTo("Say that again" to "Move the scene on")
        assertThat(labels(SessionMode.VOCAB_REVIEW)).isEqualTo("Say that again" to "Next word")
        assertThat(labels(SessionMode.MISTAKE_REVIEW)).isEqualTo("Say that again" to "Next sentence")
        assertThat(labels(SessionMode.IELTS_SPEAKING)).isEqualTo("Say that again" to "Next question")
    }
}
