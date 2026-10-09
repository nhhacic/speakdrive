package com.speakdrive.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.ui.components.NowPlayingBar
import com.speakdrive.ui.screens.HomeContent
import com.speakdrive.ui.screens.HomeUiState
import com.speakdrive.ui.screens.HomeVoiceUi
import com.speakdrive.ui.screens.TopicFilter
import com.speakdrive.ui.screens.TopicProgressUi
import com.speakdrive.ui.screens.TopicsContent
import com.speakdrive.ui.screens.filterTopics
import com.speakdrive.ui.screens.recommendTopics
import com.speakdrive.ui.theme.SpeakDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The home screen layout of v1.6: first screen content, voice mic, practice modes, topic list, wide screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "vi-rVN-w411dp-h820dp")
class HomeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val topicManager = TopicManager()
    private val conversationTopics = topicManager.getConversationTopics()

    private fun progress(vararg counts: Pair<String, Int>): List<TopicProgressUi> {
        val byId = counts.toMap()
        return conversationTopics.map { TopicProgressUi(it, byId[it.id] ?: 0) }
    }

    private fun setHome(
        state: HomeUiState,
        fontScale: Float = 1f,
        voice: HomeVoiceUi = HomeVoiceUi.Idle,
        onStartLesson: (String) -> Unit = {},
        onOpenAllTopics: () -> Unit = {},
        onToggleVoice: () -> Unit = {}
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpeakDriveTheme {
                    HomeContent(
                        state = state,
                        onStartLesson = onStartLesson,
                        onOpenCurrentLesson = {},
                        onOpenProgress = {},
                        onOpenAllTopics = onOpenAllTopics,
                        voice = voice,
                        onToggleVoice = onToggleVoice
                    )
                }
            }
        }
    }

    @Test
    fun `suggested topics keep the last and most practised topics first, then new ones`() {
        val topics = progress("food" to 1, "work" to 5, "travel" to 2, "tech_it" to 3)
        val suggested = recommendTopics(topics, lastTopicId = "food").map { it.topic.id }
        assertThat(suggested).hasSize(5)
        assertThat(suggested.take(3)).containsExactly("food", "work", "tech_it").inOrder()
        // The rest are topics the learner has not tried yet.
        assertThat(topics.filter { it.topic.id in suggested.drop(3) }.all { it.completedLessons == 0 }).isTrue()
    }

    @Test
    fun `topic filter matches category and ignores accents`() {
        val topics = progress()
        val work = filterTopics(topics, TopicFilter.WORK, "").map { it.topic.id }
        assertThat(work).containsExactly(
            "work", "interview", "startup_tech", "tech_it", "medical_expert", "civil_engineering", "transport_engineering",
            "personal_finance", "ecommerce_global_trade", "public_speaking_debate"
        )
        assertThat(filterTopics(topics, TopicFilter.ALL, "du lich").map { it.topic.id }).contains("travel")
        assertThat(filterTopics(topics, TopicFilter.ALL, "Du lịch").map { it.topic.id }).contains("travel")
        assertThat(filterTopics(topics, TopicFilter.WORK, "du lich")).isEmpty()
    }

    @Test
    fun `with a 150 percent font the main actions are on the first screen`() {
        setHome(
            state = HomeUiState(
                stats = ProgressStats(streakDays = 3, minutesToday = 108, wordsToday = 29, dueWordCount = 12, dueMistakeCount = 3),
                dailyGoalMinutes = 30,
                lastTopic = topicManager.getTopicById("work"),
                topics = progress("work" to 2),
                isLoading = false
            ),
            fontScale = 1.5f
        )
        compose.onNodeWithText("Tiếp tục").assertIsDisplayed()
        compose.onNodeWithText("Nói lệnh").assertIsDisplayed()
        compose.onNodeWithText("12 từ vựng đến hạn ôn").assertIsDisplayed()
        compose.onNodeWithText("3 lỗi sai cần sửa").assertIsDisplayed()
        compose.onNodeWithText("Nghe truyện").assertIsDisplayed()
    }

    @Test
    fun `practice modes start IELTS and open roleplay with the learner's scenarios`() {
        val started = mutableListOf<String>()
        val custom = CustomScenario("my_shop", "Mở cửa hàng", "Open a shop", "landlord", "tenant", "context")
        setHome(
            state = HomeUiState(
                lastTopic = topicManager.getTopicById("food"),
                topics = progress(),
                customScenarios = listOf(custom),
                isLoading = false
            ),
            onStartLesson = { started += it }
        )
        compose.onNodeWithText("IELTS Speaking").performScrollTo().performClick()
        compose.onNodeWithText("1 kịch bản riêng").assertExists()
        compose.onNodeWithText("Nhập vai").performScrollTo().performClick()
        compose.onNodeWithText("Mở cửa hàng").assertExists()
        compose.onNodeWithText("🎲 Khám phá tình huống AI mới").performClick()
        assertThat(started).containsExactly(
            MediaIds.IELTS,
            MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}explore_food")
        ).inOrder()
    }

    @Test
    fun `home lists five suggested topics and links to all of them`() {
        var openedAll = false
        setHome(
            state = HomeUiState(topics = progress(), isLoading = false),
            onOpenAllTopics = { openedAll = true }
        )
        compose.onNodeWithText("Tất cả ${conversationTopics.size}").performScrollTo().performClick()
        assertThat(openedAll).isTrue()
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText(conversationTopics[4].titleVi))
        compose.onNodeWithText(conversationTopics[4].titleVi).assertIsDisplayed()
        compose.onNodeWithText(conversationTopics[5].titleVi).assertDoesNotExist()
    }

    @Test
    fun `mic button toggles listening and shows what to say and what was heard`() {
        var toggled = 0
        setHome(
            state = HomeUiState(topics = progress(), isLoading = false),
            voice = HomeVoiceUi.Listening(partial = ""),
            onToggleVoice = { toggled++ }
        )
        compose.onNodeWithText("Hãy nói, ví dụ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Đang nghe…").performClick()
        assertThat(toggled).isEqualTo(1)
    }

    @Test
    fun `heard text is shown while listening`() {
        setHome(
            state = HomeUiState(topics = progress(), isLoading = false),
            voice = HomeVoiceUi.Listening(partial = "ôn từ vựng")
        )
        compose.onNodeWithText("“ôn từ vựng”").assertIsDisplayed()
    }

    @Test
    fun `a paused lesson says so on the main card`() {
        val lesson = ActiveLesson("s", topicManager.getTopicById("work")!!, null, DifficultyLevel.PRE_INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0, emptyList())
        setHome(
            state = HomeUiState(
                topics = progress(),
                currentLesson = lesson,
                lessonState = ConversationState.PAUSED,
                isLoading = false
            )
        )
        compose.onNodeWithText("Đang tạm dừng").assertIsDisplayed()
        compose.onNodeWithText("Ngẫu nhiên").assertDoesNotExist()
    }

    @Test
    fun `car banner tells the driver to use the car screen`() {
        setHome(state = HomeUiState(topics = progress(), isCarConnected = true, isLoading = false))
        compose.onNodeWithText("Đã kết nối Android Auto").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "vi-rVN-w800dp-h900dp")
    fun `wide screens show every topic next to the main card`() {
        setHome(state = HomeUiState(topics = progress(), isLoading = false))
        compose.onNodeWithText("Tìm chủ đề").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục bài", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Bắt đầu").assertIsDisplayed()
        compose.onNodeWithText("Du lịch & Đi lại").assertIsDisplayed()
        compose.onNodeWithText("Chủ đề gợi ý").assertDoesNotExist()
    }

    @Test
    fun `topic list filters by chip and by search`() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            SpeakDriveTheme {
                TopicsContent(topics = progress(), isVi = true, onTopicClick = { clicked += it.id })
            }
        }
        compose.onNodeWithText("Công việc & Chuyên môn").performScrollTo().performClick()
        compose.onNodeWithText("Công việc & Kinh doanh").assertIsDisplayed()
        compose.onNodeWithText("Du lịch & Đi lại").assertDoesNotExist()
        compose.onNodeWithText("Tất cả").performClick()
        compose.onNodeWithText("Tìm chủ đề").performTextInput("mua sam")
        compose.onNodeWithText("Mua sắm").performClick()
        compose.onNodeWithText("Du lịch & Đi lại").assertDoesNotExist()
        assertThat(clicked).containsExactly("shopping")
    }

    @Test
    fun `now playing bar opens the lesson and resumes it`() {
        var opened = false
        var toggled = false
        val lesson = ActiveLesson("s", topicManager.getTopicById("travel")!!, null, DifficultyLevel.BEGINNER, SessionMode.FREE_TALK, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                NowPlayingBar(
                    lesson = lesson,
                    state = ConversationState.PAUSED,
                    onOpen = { opened = true },
                    onTogglePause = { toggled = true }
                )
            }
        }
        compose.onNodeWithText("Đang tạm dừng").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tiếp tục").performClick()
        assertThat(toggled).isTrue()
        compose.onNodeWithText(lesson.getTitle(true)).performClick()
        assertThat(opened).isTrue()
    }
}
