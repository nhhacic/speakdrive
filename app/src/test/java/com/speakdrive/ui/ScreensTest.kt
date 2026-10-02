package com.speakdrive.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.SessionDetail
import com.speakdrive.ui.components.MicState
import com.speakdrive.ui.screens.ConversationContent
import com.speakdrive.ui.screens.ConversationUiState
import com.speakdrive.ui.screens.DrillUiState
import com.speakdrive.ai.pronunciation.PronunciationGrader
import com.speakdrive.ui.screens.HomeContent
import com.speakdrive.ui.screens.HomeUiState
import com.speakdrive.ui.screens.SummaryContent
import com.speakdrive.ui.screens.SummaryUiState
import com.speakdrive.ui.screens.TopicProgressUi
import com.speakdrive.ui.theme.SpeakDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val topics = TopicManager()

    @Test
    fun `home shows stats and starts lessons`() {
        val started = mutableListOf<String>()
        compose.setContent {
            SpeakDriveTheme {
                HomeContent(
                    state = HomeUiState(
                        stats = ProgressStats(streakDays = 4, minutesToday = 9, wordsToday = 3, dueWordCount = 2),
                        lastTopic = topics.getTopicById("food"),
                        topics = topics.getAllTopics().map { TopicProgressUi(it, 1) },
                        isLoading = false
                    ),
                    onStartLesson = { started += it },
                    onOpenCurrentLesson = {},
                    onOpenSettings = {},
                    onOpenProgress = {}
                )
            }
        }

        compose.onNodeWithContentDescription("🔥 4 ngày liên tục").assertIsDisplayed()
        compose.onNodeWithText("Lần trước: 🍽️ Ăn uống & Nhà hàng").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục").performClick()
        compose.onNodeWithText("Ôn tập từ vựng").performClick()
        assertThat(started).containsExactly(MediaIds.RESUME, MediaIds.REVIEW).inOrder()
    }

    @Test
    fun `choosing a roleplay scenario from a topic`() {
        val started = mutableListOf<String>()
        compose.setContent {
            SpeakDriveTheme {
                HomeContent(
                    state = HomeUiState(topics = topics.getAllTopics().map { TopicProgressUi(it, 0) }, isLoading = false),
                    onStartLesson = { started += it },
                    onOpenCurrentLesson = {},
                    onOpenSettings = {},
                    onOpenProgress = {}
                )
            }
        }

        compose.onNodeWithText("Du lịch & Đi lại").performClick()
        compose.onNodeWithText("Nhận phòng khách sạn").performClick()
        assertThat(started).containsExactly(MediaIds.scenario("travel_hotel"))
    }

    @Test
    fun `conversation shows the transcript and ends on request`() {
        var ended = false
        val lesson = ActiveLesson("s", topics.getTopicById("work")!!, null, DifficultyLevel.ADVANCED, SessionMode.FREE_TALK, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        transcript = listOf(
                            TranscriptTurn(1, Speaker.AI, "What do you do for a living?", 0),
                            TranscriptTurn(2, Speaker.USER, "I am a engineer", 0)
                        ),
                        micState = MicState.LISTENING,
                        statusText = "Đến lượt bạn",
                        elapsed = "01:05"
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = { ended = true },
                    onToggle = {},
                    onRetry = {},
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        compose.onNodeWithText("Advanced • 01:05").assertIsDisplayed()
        compose.onNodeWithText("I am a engineer").assertIsDisplayed()
        compose.onNodeWithText("Kết thúc").performClick()
        assertThat(ended).isTrue()
    }

    @Test
    fun `drill card shows the sentence and an honest grade`() {
        val attempt = PronunciationGrader.grade("I need three tickets", "I need tree tickets", true, emptyList(), "", 1, 0)
        val lesson = ActiveLesson("s", topics.getTopicById("travel")!!, null, DifficultyLevel.BEGINNER, SessionMode.REPEAT_AFTER_ME, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        micState = MicState.LISTENING,
                        drill = DrillUiState(target = "I need three tickets", lastAttempt = attempt, passedSentences = 0, sentences = 1)
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        compose.onNodeWithText("I need three tickets").assertIsDisplayed()
        compose.onNodeWithText("✗ Lần 1: CHƯA ĐẠT • app nghe đúng 75% số từ").assertIsDisplayed()
        compose.onNodeWithText("Đạt 0/1 câu").assertIsDisplayed()
    }

    @Test
    fun `conversation explains a missing microphone permission`() {
        var asked = false
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(state = ConversationState.ERROR, error = EngineError.MissingMicPermission),
                    permissionDenied = true,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onRequestPermission = { asked = true },
                    onOpenAppSettings = {}
                )
            }
        }

        compose.onNodeWithText("Cần quyền micro").assertIsDisplayed()
        compose.onNodeWithText("Cấp quyền").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun `summary shows scores, words and corrections`() {
        val session = SessionEntity(
            id = "s", topicId = "travel", scenarioId = null, level = "BEGINNER", mode = "FREE_TALK",
            startedAt = 0, endedAt = 1, activeDurationMs = 600_000, fluencyScore = 8, grammarScore = 6, vocabularyScore = 7,
            encouragement = "Bạn nói rất tự tin!", nextSuggestion = "Luyện thì quá khứ", isCompleted = true
        )
        val detail = SessionDetail(
            session = session,
            messages = emptyList(),
            corrections = listOf(CorrectionEntity(1, "s", "I goed", "I went", "Quá khứ của go")),
            words = listOf(LearnedWordEntity(1, "itinerary", "itinerary", "lịch trình", "My itinerary is busy.", "s", 0, 0, 0))
        )
        compose.setContent {
            SpeakDriveTheme {
                SummaryContent(
                    state = SummaryUiState(isLoading = false, detail = detail, title = "🏖️ Du lịch & Đi lại", levelLabel = "Beginner", durationLabel = "10 phút 0 giây"),
                    onPracticeAgain = {},
                    onHome = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("8/10").assertIsDisplayed()
        compose.onNodeWithText("Bạn nói rất tự tin!").assertIsDisplayed()
        compose.onNodeWithText("itinerary").assertIsDisplayed()
        compose.onNode(hasText("✓ I went")).assertExists()
    }

    @Test
    fun `summary shows progress while the AI is still grading`() {
        val session = SessionEntity("s", "travel", null, "BEGINNER", "FREE_TALK", 0, 1, 1000, null, null, null, null, null, false)
        compose.setContent {
            SpeakDriveTheme {
                SummaryContent(
                    state = SummaryUiState(isLoading = false, isSummarizing = true, detail = SessionDetail(session, emptyList(), emptyList(), emptyList())),
                    onPracticeAgain = {},
                    onHome = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("AI đang chấm điểm và tổng kết buổi học…").assertIsDisplayed()
        assertThat(compose.onAllNodesWithText("Đánh giá").fetchSemanticsNodes()).isEmpty()
    }
}
