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
        override suspend fun setAllowVietnameseHelp(allowed: Boolean) = Unit
    }
    private val provider = MediaContentProvider(topics, store, settings)

    @Test
    fun `root has five browsable tabs including stories`() = runTest {
        val root = provider.children(MediaIds.ROOT)

        assertThat(root.map { it.mediaId })
            .containsExactly(MediaIds.HOME, MediaIds.STORIES, MediaIds.TOPICS, MediaIds.ROLEPLAY, MediaIds.LEVELS).inOrder()
        assertThat(root.all { it.mediaMetadata.isBrowsable == true }).isTrue()
    }

    @Test
    fun `home tab offers resume, stories, random, pronunciation and review with live subtitles`() = runTest {
        val home = provider.children(MediaIds.HOME)

        assertThat(home.map { it.mediaId })
            .containsExactly(MediaIds.RESUME, MediaIds.STORY_RECOMMENDED, MediaIds.RANDOM, MediaIds.PRONUNCIATION, MediaIds.REVIEW).inOrder()
        assertThat(home.all { it.mediaMetadata.isPlayable == true }).isTrue()
        assertThat(home[0].mediaMetadata.subtitle.toString()).contains("Ăn uống")
        assertThat(home[1].mediaMetadata.title.toString()).contains("Luyện nghe kể chuyện")
        assertThat(home[3].mediaMetadata.subtitle.toString()).contains("Ăn uống")
        assertThat(home[4].mediaMetadata.subtitle.toString()).contains("2 từ")
    }

    @Test
    fun `stories tab offers recommended, random, and story topics`() = runTest {
        val stories = provider.children(MediaIds.STORIES)

        assertThat(stories.map { it.mediaId }).containsAtLeast(
            MediaIds.STORY_RECOMMENDED,
            MediaIds.STORY_RANDOM,
            MediaIds.storyTopic("story_famous"),
            MediaIds.storyTopic("story_science"),
            MediaIds.storyTopic("story_history")
        )

        val famousScenarios = provider.children(MediaIds.storyTopic("story_famous"))
        assertThat(famousScenarios).hasSize(5) // 1 dynamic ai recommendation + 4 static scenarios
        assertThat(famousScenarios.first().mediaId).contains("dynamic_story_recommended")
    }

    @Test
    fun `pronunciation items resolve with their topic`() = runTest {
        assertThat(provider.item(MediaIds.pronunciation("interview"))?.mediaMetadata?.subtitle.toString()).contains("Phỏng vấn")
        assertThat(provider.item(MediaIds.PRONUNCIATION)?.mediaMetadata?.subtitle.toString()).contains("Ăn uống")
    }

    @Test
    fun `topics and roleplay scenarios are listed`() = runTest {
        assertThat(provider.children(MediaIds.TOPICS)).hasSize(15)
        val roleplayTopics = provider.children(MediaIds.ROLEPLAY)
        assertThat(roleplayTopics).hasSize(15)
        val scenarios = provider.children(roleplayTopics.first().mediaId)
        assertThat(scenarios).hasSize(9)
        assertThat(MediaIds.parse(scenarios.first().mediaId)).isInstanceOf(MediaTarget.Scenario::class.java)
        assertThat(scenarios.first().mediaId).contains("dynamic_explore")
    }

    @Test
    fun `levels mark the current one`() = runTest {
        val levels = provider.children(MediaIds.LEVELS)

        assertThat(levels).hasSize(DifficultyLevel.entries.size)
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

    @Test
    fun `lesson item shows AI text and repeat target on Android Auto`() {
        val lesson = ActiveLesson("id", topics.getTopicById("travel")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: I'd like a window seat, please.",
            drillTarget = "I'd like a window seat, please."
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("🎯 Lặp lại theo AI")
        assertThat(item.mediaMetadata.subtitle.toString()).contains("Du lịch")
        assertThat(item.mediaMetadata.artist.toString()).contains("Intermediate")
        assertThat(item.mediaMetadata.artworkData).isNotNull()
        assertThat(item.mediaMetadata.artworkData!!.isNotEmpty()).isTrue()
    }

    @Test
    fun `lesson item shows AI text when free talking without repeat target`() {
        val lesson = ActiveLesson("id", topics.getTopicById("travel")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.FREE_TALK, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Where would you like to travel next?",
            drillTarget = null
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("🤖 Where would you like to travel next?")
        assertThat(item.mediaMetadata.subtitle.toString()).isEqualTo("Đang trò chuyện")
        assertThat(item.mediaMetadata.artworkData).isNotNull()
    }

    @Test
    fun `standby item is playable and shows clear prompt for driver`() {
        val standby = provider.standbyItem(justEnded = false)
        assertThat(standby.mediaId).isEqualTo(MediaIds.RESUME)
        assertThat(standby.mediaMetadata.isPlayable).isTrue()
        assertThat(standby.mediaMetadata.title.toString()).isEqualTo("Luyện nói tiếng Anh")

        val completed = provider.standbyItem(justEnded = true)
        assertThat(completed.mediaId).isEqualTo(MediaIds.RESUME)
        assertThat(completed.mediaMetadata.isPlayable).isTrue()
        assertThat(completed.mediaMetadata.title.toString()).contains("Đã hoàn thành")
    }

    @Test
    fun `lesson item shows story metadata and next story cue for story listening`() {
        val storyTopic = topics.getTopicById("story_science")!!
        val lesson = ActiveLesson("id", storyTopic, null, DifficultyLevel.INTERMEDIATE, SessionMode.STORY_LISTENING, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Once upon a time in a small laboratory...",
            drillTarget = null
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).contains(lesson.titleVi)
        assertThat(item.mediaMetadata.subtitle.toString()).contains("Bấm Next để đổi truyện")
        assertThat(item.mediaMetadata.artworkData).isNotNull()
    }
}
