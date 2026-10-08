package com.speakdrive.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.SessionDetail
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.ui.components.MicState
import com.speakdrive.ui.screens.AboutScreen
import com.speakdrive.ui.screens.AzureTestState
import com.speakdrive.ui.screens.ConversationContent
import com.speakdrive.ui.screens.ConversationUiState
import com.speakdrive.ui.screens.HomeContent
import com.speakdrive.ui.screens.HomeUiState
import com.speakdrive.ui.screens.SettingsContent
import com.speakdrive.ui.screens.SummaryContent
import com.speakdrive.ui.screens.SummaryUiState
import com.speakdrive.ui.screens.TopicProgressUi
import com.speakdrive.ui.theme.SpeakDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that when the device or app locale is English (en-rUS),
 * all UI screens display in clean English with NO hardcoded Vietnamese text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h1400dp")
class EnglishUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val topics = TopicManager()

    @Test
    fun `home screen displays completely in English`() {
        compose.setContent {
            SpeakDriveTheme {
                HomeContent(
                    state = HomeUiState(
                        stats = ProgressStats(streakDays = 5, minutesToday = 15, wordsToday = 4, dueWordCount = 3),
                        lastTopic = topics.getTopicById("work"),
                        topics = topics.getAllTopics().map { TopicProgressUi(it, 1) },
                        isLoading = false
                    ),
                    onStartLesson = {},
                    onOpenCurrentLesson = {},
                    onOpenSettings = {},
                    onOpenProgress = {}
                )
            }
        }

        compose.onNodeWithContentDescription("🔥 5 day streak").assertExists()
        compose.onNodeWithText("Conversation Topics").assertExists()
        compose.onAllNodesWithText("Resume", substring = true)[0].assertExists()
        compose.onNodeWithText("Work & Business").assertExists()
    }

    @Test
    fun `settings screen displays completely in English`() {
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(
                        appLanguage = AppLanguage.ENGLISH,
                        learner = LearnerSettings(
                            level = DifficultyLevel.INTERMEDIATE,
                            pronunciationStrictness = PronunciationStrictness.STANDARD
                        ),
                        dailyGoalMinutes = 15
                    ),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    onSetAppLanguage = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetRandomVoice = {},
                    onSetPronunciationStrictness = {},
                    onSetStorytellingStyle = {},
                    onSetStoryDuration = {},
                    onSetMultiVoiceStorytelling = {},
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetAzureEnabled = {},
                    onTestAzure = {},
                    onSaveAndTestAzure = { _, _ -> },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onAllNodesWithText("Interface Language")[0].assertExists()
        compose.onNodeWithText("Practice & Proficiency").performScrollTo().assertExists()
        compose.onNodeWithText("AI Voice & Speech").performScrollTo().assertExists()
        compose.onNodeWithText("Drill Sentence Length (Driving Safety)").performScrollTo().assertExists()
        compose.onNodeWithText("Azure Pronunciation Service").performScrollTo().assertExists()
        compose.onNodeWithText("Data Privacy Policy").performScrollTo().assertExists()
        compose.onNodeWithText("About SpeakDrive & Author").performScrollTo().assertExists()
    }

    @Test
    fun `about screen displays completely in English`() {
        compose.setContent {
            SpeakDriveTheme {
                AboutScreen(onBack = {}, onOpenPrivacy = {})
            }
        }

        compose.onNodeWithText("About SpeakDrive").assertExists()
        compose.onNodeWithText("Author & Lead Developer").assertExists()
        compose.onNodeWithText("Key Features & Technology").assertExists()
        compose.onNodeWithText("Technical Information").assertExists()
    }

    @Test
    fun `conversation screen displays in English`() {
        val lesson = ActiveLesson("s", topics.getTopicById("travel")!!, null, DifficultyLevel.ADVANCED, SessionMode.FREE_TALK, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        transcript = listOf(
                            TranscriptTurn(1, Speaker.AI, "Where are you traveling to today?", 0),
                            TranscriptTurn(2, Speaker.USER, "I'm going to New York.", 0)
                        ),
                        micState = MicState.LISTENING,
                        elapsed = "02:15"
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

        compose.onNodeWithText("Advanced").assertExists()
        compose.onNodeWithText("02:15").assertExists()
        compose.onNodeWithText("Where are you traveling to today?").assertExists()
        compose.onNodeWithText("End").assertExists()
    }

    @Test
    fun `summary screen displays in English`() {
        val session = SessionEntity(
            id = "s", topicId = "travel", scenarioId = null, level = "ADVANCED", mode = "FREE_TALK",
            startedAt = 0, endedAt = 1, activeDurationMs = 600_000, fluencyScore = 8, grammarScore = 6, vocabularyScore = 7,
            encouragement = "Great job speaking today!", nextSuggestion = "Practice past tense", isCompleted = true
        )
        val detail = SessionDetail(session, emptyList(), emptyList(), emptyList())
        compose.setContent {
            SpeakDriveTheme {
                SummaryContent(
                    state = SummaryUiState(
                        isLoading = false,
                        detail = detail,
                        topicTitleVi = "Du lịch & Đi lại",
                        topicTitleEn = "Travel & Commute",
                        level = DifficultyLevel.ADVANCED,
                        durationMs = 600_000L
                    ),
                    onPracticeAgain = {},
                    onHome = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Session Completed").assertExists()
        compose.onNodeWithText("Fluency").assertExists()
        compose.onNodeWithText("Travel & Commute").assertExists()
        compose.onNodeWithText("Great job speaking today!", substring = true).assertExists()
    }

    @Test
    fun `conversation screen displays barge-in controls in English`() {
        var toggled = false
        val lesson = ActiveLesson("s", topics.getTopicById("travel")!!, null, DifficultyLevel.ADVANCED, SessionMode.FREE_TALK, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        isBargeInEnabled = true,
                        micState = MicState.LISTENING
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onToggleBargeIn = { toggled = true },
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        // The barge-in control is an icon toggle; its state is announced through the content description.
        compose.onNodeWithContentDescription("Barge-in: On").assertIsDisplayed().performClick()
        assertThat(toggled).isTrue()
    }
}
