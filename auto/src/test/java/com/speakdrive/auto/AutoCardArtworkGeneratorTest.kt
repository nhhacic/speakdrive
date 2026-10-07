package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoCardArtworkGeneratorTest {

    private val topics = TopicManager()
    private val generator = AutoCardArtworkGenerator()

    @Test
    fun `generates non-empty png byte array for pronunciation drill`() {
        val topic = topics.getTopicById("travel")!!
        val lesson = ActiveLesson("session_1", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: I would like a window seat, please.",
            drillTarget = "I would like a window seat, please."
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `generates card for short repeat target safely`() {
        val topic = topics.getTopicById("greetings") ?: topics.getTopicById("daily") ?: topics.getAllTopics().first()
        val lesson = ActiveLesson("session_short", topic, null, DifficultyLevel.BEGINNER, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: Hello, how are you?",
            drillTarget = "Hello, how are you?"
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `generates card for very long repeat target sentence safely`() {
        val topic = topics.getTopicById("travel")!!
        val lesson = ActiveLesson("session_long", topic, null, DifficultyLevel.ADVANCED, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val longSentence = "Could you please tell me how much it costs to take a taxi from the international airport to the central railway station during rush hour?"
        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: $longSentence",
            drillTarget = longSentence
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `caches byte array when called with identical parameters`() {
        val topic = topics.getTopicById("work")!!
        val lesson = ActiveLesson("session_2", topic, null, DifficultyLevel.ADVANCED, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val first = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: Let's schedule a meeting.",
            drillTarget = "Let's schedule a meeting."
        )
        val second = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: Let's schedule a meeting.",
            drillTarget = "Let's schedule a meeting."
        )

        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `handles free talk mode without repeat target safely`() {
        val topic = topics.getTopicById("food")!!
        val lesson = ActiveLesson("session_3", topic, null, DifficultyLevel.BEGINNER, SessionMode.FREE_TALK, 0L, emptyList())

        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "What kind of food do you like?",
            drillTarget = null
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `generates card artwork for story listening mode safely`() {
        val topic = topics.getTopicById("story_science")!!
        val lesson = ActiveLesson("session_story", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.STORY_LISTENING, 0L, emptyList())

        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Albert Einstein was working in the Swiss patent office...",
            drillTarget = null
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `handles paused and reconnecting states smoothly`() {
        val topic = topics.getTopicById("travel")!!
        val lesson = ActiveLesson("session_paused", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val pausedBytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.PAUSED,
            lastAiText = "Repeat after me: Where is the gate?",
            drillTarget = "Where is the gate?"
        )
        assertThat(pausedBytes).isNotNull()

        val reconnectingBytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.RECONNECTING,
            lastAiText = "Repeat after me: Where is the gate?",
            drillTarget = "Where is the gate?"
        )
        assertThat(reconnectingBytes).isNotNull()
    }

    @Test
    fun `generates card with high-contrast target text and translation subtitle`() {
        val topic = topics.getTopicById("work") ?: topics.getAllTopics().first()
        val lesson = ActiveLesson("session_sub", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val bytes = generator.generateCard(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: We need a better plan.",
            drillTarget = "We need a better plan.",
            drillTargetTranslation = "Chúng ta cần một kế hoạch tốt hơn."
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }
}
