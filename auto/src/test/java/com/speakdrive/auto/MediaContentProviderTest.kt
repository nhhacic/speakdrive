package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaContentProviderTest {

    private val topics = TopicManager()
    private val store = object : SessionStore {
        var due = listOf(ReviewWord("commute", "đi làm"), ReviewWord("itinerary", "lịch trình"))
        override suspend fun saveSession(session: CompletedSession) = Unit
        override suspend fun recentTopicIds(limit: Int) = emptyList<String>()
        override suspend fun wordsDueForReview(limit: Int) = due.take(limit)
        override suspend fun markWordsReviewed(words: List<String>) = Unit
    }
    private val settings = object : LearningSettings {
        var current = LearnerSettings(level = DifficultyLevel.BEGINNER, lastTopicId = "food")
        override suspend fun snapshot() = current
        override suspend fun setLevel(level: DifficultyLevel) = Unit
        override suspend fun setLastTopicId(topicId: String) = Unit
    }
    private val provider = MediaContentProvider(topics, store, settings)

    @Test
    fun `root has four browsable tabs`() = runTest {
        val root = provider.children(MediaIds.ROOT)

        assertThat(root.map { it.mediaId }).containsExactly(MediaIds.HOME, MediaIds.TOPICS, MediaIds.ROLEPLAY, MediaIds.LEVELS).inOrder()
        assertThat(root.all { it.mediaMetadata.isBrowsable == true }).isTrue()
    }

    @Test
    fun `home tab offers resume, random and review with live subtitles`() = runTest {
        val home = provider.children(MediaIds.HOME)

        assertThat(home.map { it.mediaId }).containsExactly(MediaIds.RESUME, MediaIds.RANDOM, MediaIds.REVIEW).inOrder()
        assertThat(home.all { it.mediaMetadata.isPlayable == true }).isTrue()
        assertThat(home[0].mediaMetadata.subtitle.toString()).contains("Ăn uống")
        assertThat(home[2].mediaMetadata.subtitle.toString()).contains("2 từ")
    }

    @Test
    fun `topics and roleplay scenarios are listed`() = runTest {
        assertThat(provider.children(MediaIds.TOPICS)).hasSize(8)
        val roleplayTopics = provider.children(MediaIds.ROLEPLAY)
        assertThat(roleplayTopics).hasSize(8)
        val scenarios = provider.children(roleplayTopics.first().mediaId)
        assertThat(scenarios).hasSize(4)
        assertThat(MediaIds.parse(scenarios.first().mediaId)).isInstanceOf(MediaTarget.Scenario::class.java)
    }

    @Test
    fun `levels mark the current one`() = runTest {
        val levels = provider.children(MediaIds.LEVELS)

        assertThat(levels).hasSize(3)
        assertThat(levels.first().mediaMetadata.title.toString()).startsWith("✓")
    }

    @Test
    fun `items resolve from ids sent back by controllers`() = runTest {
        assertThat(provider.item(MediaIds.topic("travel", DifficultyLevel.ADVANCED))?.mediaMetadata?.subtitle.toString())
            .contains("Advanced")
        assertThat(provider.item(MediaIds.scenario("health_doctor"))?.mediaMetadata?.title.toString()).isEqualTo("Khám bệnh")
        assertThat(provider.item("bogus")).isNull()
    }

    @Test
    fun `search finds topics and scenarios`() {
        assertThat(provider.search("interview").map { it.mediaId }).contains(MediaIds.topic("interview"))
        assertThat(provider.search("taxi").map { it.mediaId }).containsExactly(MediaIds.scenario("travel_taxi"))
        assertThat(provider.search("   ")).isEmpty()
    }

    @Test
    fun `lesson item shows the state to the driver`() {
        val lesson = ActiveLesson("id", topics.getTopicById("work")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.FREE_TALK, 0, emptyList())

        val item = provider.lessonItem(lesson, ConversationState.PAUSED)

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.artist.toString()).isEqualTo("Intermediate • Tạm dừng")
    }
}
