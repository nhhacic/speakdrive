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
    fun `root has the four tabs Android Auto can show, levels moved into home`() = runTest {
        val root = provider.children(MediaIds.ROOT)

        assertThat(root.map { it.mediaId })
            .containsExactly(MediaIds.HOME, MediaIds.STORIES, MediaIds.TOPICS, MediaIds.ROLEPLAY).inOrder()
        assertThat(root.all { it.mediaMetadata.isBrowsable == true }).isTrue()
        assertThat(provider.children(MediaIds.HOME).map { it.mediaId }).contains(MediaIds.LEVELS)
    }

    @Test
    fun `home tab offers resume, stories, random, pronunciation, reviews and the level picker`() = runTest {
        val home = provider.children(MediaIds.HOME)

        assertThat(home.map { it.mediaId })
            .containsExactly(
                MediaIds.RESUME, MediaIds.STORY_RECOMMENDED, MediaIds.RANDOM, MediaIds.PRONUNCIATION, MediaIds.REVIEW,
                MediaIds.MISTAKES, MediaIds.IELTS, MediaIds.LEVELS
            )
            .inOrder()
        assertThat(home.dropLast(1).all { it.mediaMetadata.isPlayable == true }).isTrue()
        assertThat(home.last().mediaMetadata.isBrowsable).isTrue()
        assertThat(home[0].mediaMetadata.subtitle.toString()).contains("Ăn uống")
        assertThat(home[1].mediaMetadata.title.toString()).contains("Luyện nghe kể chuyện")
        assertThat(home[3].mediaMetadata.subtitle.toString()).contains("Ăn uống")
        assertThat(home[4].mediaMetadata.subtitle.toString()).contains("2 từ")
    }

    @Test
    fun `home tab resume subtitle reflects shadowing mode when lastSessionMode is REPEAT_AFTER_ME`() = runTest {
        settings.current = settings.current.copy(
            lastTopicId = "travel",
            lastSessionMode = SessionMode.REPEAT_AFTER_ME
        )

        val home = provider.children(MediaIds.HOME)
        val resumeItem = home.first { it.mediaId == MediaIds.RESUME }
        assertThat(resumeItem.mediaMetadata.subtitle.toString()).contains("Luyện phát âm (Shadowing)")
        assertThat(resumeItem.mediaMetadata.subtitle.toString()).contains("Du lịch")
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
    fun `custom scenarios browse and play when present`() = runTest {
        val customStore = object : SessionStore by store {
            override suspend fun customScenarios(): List<com.speakdrive.ai.model.CustomScenario> = listOf(
                com.speakdrive.ai.model.CustomScenario(
                    id = "custom_logistics",
                    titleVi = "Phỏng vấn Logistics",
                    titleEn = "Logistics Interview",
                    aiRole = "Interviewer",
                    learnerRole = "Candidate",
                    customContext = "Logistics job interview",
                    missionObjective = "Pass the interview"
                )
            )
        }
        val customProvider = MediaContentProvider(topics, customStore, settings)
        val roleplayItems = customProvider.children(MediaIds.ROLEPLAY)
        assertThat(roleplayItems.first().mediaId).isEqualTo(MediaIds.CUSTOM_SCENARIOS)

        val customItems = customProvider.children(MediaIds.CUSTOM_SCENARIOS)
        assertThat(customItems).hasSize(1)
        assertThat(customItems.first().mediaId).isEqualTo(MediaIds.scenario("custom_logistics"))
        assertThat(customItems.first().mediaMetadata.title.toString()).isEqualTo("Phỏng vấn Logistics")
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
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("I'd like a window seat, please.")
        assertThat(item.mediaMetadata.subtitle.toString()).isEqualTo("🗣️ Nhắc lại theo AI")
        assertThat(item.mediaMetadata.artist.toString()).contains("Intermediate")
        assertThat(item.mediaMetadata.artworkData).isNotNull()
        assertThat(item.mediaMetadata.artworkData!!.isNotEmpty()).isTrue()
    }

    @Test
    fun `lesson item displays translation subtitle when provided`() {
        val lesson = ActiveLesson("id", topics.getTopicById("travel")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Repeat after me: I'd like a window seat, please.",
            drillTarget = "I'd like a window seat, please.",
            drillTargetTranslation = "Tôi muốn một chỗ ngồi cạnh cửa sổ, làm ơn."
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("I'd like a window seat, please.")
        assertThat(item.mediaMetadata.subtitle.toString()).isEqualTo("🇻🇳 Tôi muốn một chỗ ngồi cạnh cửa sổ, làm ơn.")
        assertThat(item.mediaMetadata.artworkData).isNotNull()
    }

    @Test
    fun `lesson item in repeat after me mode without drill target shows topic and waiting prompt`() {
        val lesson = ActiveLesson("id", topics.getTopicById("travel")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = null,
            drillTarget = null
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).contains("Du lịch")
        assertThat(item.mediaMetadata.subtitle.toString()).contains("Hãy nghe và nhắc lại")
    }

    @Test
    fun `lesson item keeps the topic as title instead of the changing AI text`() {
        val lesson = ActiveLesson("id", topics.getTopicById("travel")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.FREE_TALK, 0, emptyList())

        val item = provider.lessonItem(
            lesson = lesson,
            state = ConversationState.ACTIVE,
            lastAiText = "Where would you like to travel next?",
            drillTarget = null
        )

        assertThat(item.mediaId).isEqualTo(MediaIds.LESSON)
        assertThat(item.mediaMetadata.title.toString()).doesNotContain("Where would you like")
        assertThat(item.mediaMetadata.subtitle.toString()).startsWith("Đang trò chuyện")
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
