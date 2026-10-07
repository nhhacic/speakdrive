package com.speakdrive.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.data.repository.ProgressStats
import com.speakdrive.data.repository.SessionDetail
import com.speakdrive.data.repository.UserPreferences
import com.speakdrive.ui.components.MicState
import com.speakdrive.ui.screens.AboutScreen
import com.speakdrive.ui.screens.AzureTestState
import com.speakdrive.ui.screens.ConversationContent
import com.speakdrive.ui.screens.ConversationUiState
import com.speakdrive.ui.screens.DrillUiState
import com.speakdrive.ui.screens.SettingsContent
import com.speakdrive.ai.pronunciation.AzureAssessment
import com.speakdrive.ai.pronunciation.AzurePhoneme
import com.speakdrive.ai.pronunciation.AzureWord
import com.speakdrive.ai.pronunciation.PronunciationGrader
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ui.screens.HomeContent
import com.speakdrive.ui.screens.HomeUiState
import com.speakdrive.ui.screens.ProgressContent
import com.speakdrive.ui.screens.ProgressUiState
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
@Config(sdk = [35], application = Application::class, qualifiers = "vi-rVN-w411dp-h1400dp")
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

        compose.onNodeWithContentDescription("🔥 4 ngày streak").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục bài: 🍽️ Ăn uống & Nhà hàng").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục").performClick()
        compose.onNodeWithText("Ôn tập từ vựng", substring = true).performScrollTo().performClick()
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

        compose.onNodeWithText(lesson.level.getLabel(true)).assertIsDisplayed()
        compose.onNodeWithText("01:05").assertIsDisplayed()
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
        compose.onNodeWithText("✗ Lần 1: CHƯA ĐẠT (nghe đúng 75% từ)").assertIsDisplayed()
        compose.onNodeWithText("Đạt 0/1 câu").assertIsDisplayed()
    }

    @Test
    fun `drill card shows azure scores and weak sounds`() {
        val azure = AzureAssessment(
            pronunciationScore = 62, accuracyScore = 58, fluencyScore = 90, completenessScore = 100, recognizedText = "",
            words = listOf(
                AzureWord("I", 100, "None", emptyList()),
                AzureWord("need", 100, "None", emptyList()),
                AzureWord("three", 20, "Mispronunciation", listOf(AzurePhoneme("th", 15))),
                AzureWord("tickets", 95, "None", emptyList())
            )
        )
        val attempt = PronunciationGrader.grade("I need three tickets", "I need three tickets", true, emptyList(), "", 1, 0, azure)
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

        compose.onNodeWithText("✗ Lần 1: CHƯA ĐẠT (nghe đúng 75% từ)").assertIsDisplayed()
        compose.onNodeWithText("Azure: 62/100 • chính xác 58 • trôi chảy 90 • đầy đủ 100").assertIsDisplayed()
        compose.onNodeWithText("Âm cần sửa: three (th 15)").assertIsDisplayed()
    }

    @Test
    fun `drill card shows clarity hint when attempt failed but all words recognized`() {
        val attempt = PronunciationAttempt(
            target = "Can you provide an update on the budget status?",
            heard = "Can you provide an update on the budget status",
            words = emptyList(),
            accuracyPercent = 100,
            modelSaidCorrect = false,
            modelProblemWords = emptyList(),
            modelNotes = "Mumbled slightly",
            attemptNumber = 1,
            passed = false,
            timestamp = 0
        )
        val lesson = ActiveLesson("s", topics.getTopicById("travel")!!, null, DifficultyLevel.BEGINNER, SessionMode.REPEAT_AFTER_ME, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        micState = MicState.LISTENING,
                        drill = DrillUiState(target = "Can you provide an update on the budget status?", lastAttempt = attempt, passedSentences = 0, sentences = 1)
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

        compose.onNodeWithText("✗ Lần 1: CHƯA ĐẠT (cần phát âm rõ hơn)").assertIsDisplayed()
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
                    state = SummaryUiState(
                        isLoading = false,
                        detail = detail,
                        topicTitleVi = "Du lịch & Đi lại",
                        topicTitleEn = "Travel",
                        topicEmoji = "🏖️",
                        level = DifficultyLevel.BEGINNER,
                        durationMs = 600_000L
                    ),
                    onPracticeAgain = {},
                    onHome = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("8/10").assertExists()
        compose.onNodeWithText("Bạn nói rất tự tin!", substring = true).assertExists()
        compose.onNodeWithText("itinerary").assertExists()
        compose.onNodeWithText("I went", substring = true).assertExists()
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

        compose.onNodeWithText("AI đang phân tích và tổng kết buổi học…").assertIsDisplayed()
        assertThat(compose.onAllNodesWithText("Đánh giá").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `settings shows drill sentence length options and changes selection`() {
        var selectedLength: DrillSentenceLength? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetDrillSentenceLength = { selectedLength = it },
                    onSetAzureEnabled = {},
                    onSaveAndTestAzure = { _, _ -> },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onNodeWithText("Độ dài câu luyện nói (An toàn lái xe)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tự động khi lái xe").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Luôn ngắn gọn").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tiêu chuẩn theo cấp độ").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Luôn ngắn gọn").performScrollTo().performClick()
        assertThat(selectedLength).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)

        compose.onNodeWithText("Tiêu chuẩn theo cấp độ").performScrollTo().performClick()
        assertThat(selectedLength).isEqualTo(DrillSentenceLength.STANDARD)
    }

    @Test
    fun `settings shows pronunciation strictness options and changes selection`() {
        var selectedStrictness: PronunciationStrictness? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetPronunciationStrictness = { selectedStrictness = it },
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetAzureEnabled = {},
                    onSaveAndTestAzure = { _, _ -> },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onNodeWithText("Độ khắt khe khi chấm phát âm").assertIsDisplayed()
        compose.onNodeWithText("Theo cấp độ").assertIsDisplayed()
        compose.onNodeWithText("A1 - Rất nhẹ").assertIsDisplayed()
        compose.onNodeWithText("A2 - Dễ chịu").assertIsDisplayed()
        compose.onNodeWithText("B1 - Tiêu chuẩn").assertIsDisplayed()
        compose.onNodeWithText("B2 - Khắt khe").assertIsDisplayed()
        compose.onNodeWithText("C1–C2 - Chuẩn bản xứ").assertIsDisplayed()

        compose.onNodeWithText("A2 - Dễ chịu").performClick()
        assertThat(selectedStrictness).isEqualTo(PronunciationStrictness.ELEMENTARY)

        compose.onNodeWithText("B2 - Khắt khe").performClick()
        assertThat(selectedStrictness).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)

        compose.onNodeWithText("Theo cấp độ").performClick()
        assertThat(selectedStrictness).isEqualTo(PronunciationStrictness.AUTO)
    }

    @Test
    fun `settings shows random voice option and toggles it`() {
        var randomVoiceToggled: Boolean? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(learner = LearnerSettings(randomVoice = false)),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetRandomVoice = { randomVoiceToggled = it },
                    onSetPronunciationStrictness = {},
                    onSetStorytellingStyle = {},
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetAzureEnabled = {},
                    onSaveAndTestAzure = { _, _ -> },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onNodeWithText("Ngẫu nhiên mỗi phiên học").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ngẫu nhiên mỗi phiên học").performScrollTo().performClick()
        assertThat(randomVoiceToggled).isTrue()
    }

    @Test
    fun `settings shows storytelling style option and toggles it`() {
        var selectedStyle: StorytellingStyle? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(learner = LearnerSettings(storytellingStyle = StorytellingStyle.INTERACTIVE)),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetRandomVoice = {},
                    onSetPronunciationStrictness = {},
                    onSetStorytellingStyle = { selectedStyle = it },
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetAzureEnabled = {},
                    onSaveAndTestAzure = { _, _ -> },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onNodeWithText("Tương tác từng đoạn").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Podcast liền mạch").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Podcast liền mạch").performClick()
        assertThat(selectedStyle).isEqualTo(StorytellingStyle.CONTINUOUS)
    }

    @Test
    fun `settings shows azure test button and triggers test connection`() {
        var testTriggered = false
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(),
                    azureTest = AzureTestState.Ok,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetAllowVietnameseHelp = {},
                    onSetAllowBargeIn = {},
                    onSetAzureEnabled = {},
                    onTestAzure = { testTriggered = true },
                    onSetDailyGoal = {}
                )
            }
        }

        compose.onNodeWithText("Kiểm tra kết nối Azure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("✓ Kết nối Azure thành công").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kiểm tra kết nối Azure").performClick()
        assertThat(testTriggered).isTrue()
    }

    @Test
    fun `conversation displays active lesson with context-aware screen behavior`() {
        val lesson = ActiveLesson("s", topics.getTopicById("travel")!!, null, DifficultyLevel.BEGINNER, SessionMode.FREE_TALK, 0, emptyList())
        var userInteracted = false
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        isCarConnected = false,
                        micState = MicState.LISTENING
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onUserInteraction = { userInteracted = true }
                )
            }
        }

        compose.onNodeWithText(lesson.level.getLabel(true)).assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        // Click on the transcript area or mic area
        compose.onNodeWithContentDescription("Đang đợi bạn nói — chạm để tạm dừng").performClick()
        assertThat(userInteracted).isTrue()
    }

    @Test
    fun `about screen displays developer info and handles navigation`() {
        var backed = false
        var privacyOpened = false
        compose.setContent {
            SpeakDriveTheme {
                AboutScreen(
                    onBack = { backed = true },
                    onOpenPrivacy = { privacyOpened = true }
                )
            }
        }

        compose.onNodeWithText("Thông tin ứng dụng").assertIsDisplayed()
        compose.onNodeWithText("SpeakDrive").assertIsDisplayed()
        compose.onNodeWithText("nhhacic").assertIsDisplayed()
        compose.onNodeWithText("Tác giả & Nhà phát triển chính").assertIsDisplayed()
        compose.onNodeWithText("Gia sư AI Gemini Live").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Chính sách bảo mật").performScrollTo().performClick()
        assertThat(privacyOpened).isTrue()

        compose.onNodeWithContentDescription("Quay lại").performClick()
        assertThat(backed).isTrue()
    }

    @Test
    fun `settings shows app language options and changes selection`() {
        var selectedLang: AppLanguage? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetAppLanguage = { selectedLang = it },
                    onSetLevel = {},
                    onSetVoice = {},
                    onSetDailyGoal = {}
                )
            }
        }

        val viText = "${AppLanguage.VIETNAMESE.flagEmoji} ${AppLanguage.VIETNAMESE.nativeName}"
        val enText = "${AppLanguage.ENGLISH.flagEmoji} ${AppLanguage.ENGLISH.nativeName}"

        compose.onNodeWithText(viText).assertIsDisplayed()
        compose.onNodeWithText(enText).assertIsDisplayed()
        compose.onNodeWithText(viText).performClick()
        assertThat(selectedLang).isEqualTo(AppLanguage.VIETNAMESE)
    }

    @Test
    fun `settings shows adaptive level recommendation toggle and handles change`() {
        var adaptiveEnabled: Boolean? = null
        compose.setContent {
            SpeakDriveTheme {
                SettingsContent(
                    prefs = UserPreferences(),
                    azureTest = AzureTestState.Idle,
                    onBack = {},
                    onOpenProgress = {},
                    onOpenVocabulary = {},
                    onOpenPrivacy = {},
                    onSetAdaptiveLevelRecommendation = { adaptiveEnabled = it }
                )
            }
        }

        compose.onNodeWithText("Gợi ý cấp độ học thích ứng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gợi ý cấp độ học thích ứng").performClick()
        assertThat(adaptiveEnabled).isFalse() // Default was true in UserPreferences -> toggle to false
    }

    @Test
    fun `progress shows cefr roadmap card with level recommendation and apply button`() {
        var appliedLevel: DifficultyLevel? = null
        val testRec = LevelRecommendation(
            direction = LevelAdjustmentDirection.LEVEL_UP,
            targetLevel = DifficultyLevel.UPPER_INTERMEDIATE,
            reasonVi = "Bạn đã thể hiện xuất sắc ở B1! Hãy thử thách với B2.",
            reasonEn = "Excellent progress at B1! Advance to B2."
        )

        compose.setContent {
            SpeakDriveTheme {
                ProgressContent(
                    state = ProgressUiState(
                        currentLevel = DifficultyLevel.INTERMEDIATE,
                        roadmapRecommendation = testRec,
                        isAdaptiveEnabled = true
                    ),
                    isVi = true,
                    onBack = {},
                    onOpenSession = {},
                    onOpenVocabulary = {},
                    onApplyRecommendedLevel = { appliedLevel = it }
                )
            }
        }

        compose.onNodeWithText("Lộ trình học & Đánh giá năng lực").assertIsDisplayed()
        compose.onNodeWithText("🚀 Sẵn sàng thăng cấp").assertIsDisplayed()
        compose.onNodeWithText("Bạn đã thể hiện xuất sắc ở B1! Hãy thử thách với B2.").assertIsDisplayed()
        compose.onNodeWithText("Tăng lên B2 ngay").performClick()
        assertThat(appliedLevel).isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
    }

    @Test
    fun `conversation screen displays and toggles barge-in state`() {
        var bargeInToggled = false
        val lesson = ActiveLesson("s", topics.getTopicById("work")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.FREE_TALK, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        isBargeInEnabled = false,
                        micState = MicState.LISTENING
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onToggleBargeIn = { bargeInToggled = true },
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Ngắt lời: Tắt").assertIsDisplayed().performClick()
        assertThat(bargeInToggled).isTrue()
    }

    @Test
    fun `conversation screen displays and triggers next and repeat buttons`() {
        var repeatClicked = false
        var nextClicked = false
        val lesson = ActiveLesson("s", topics.getTopicById("work")!!, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0, emptyList())
        compose.setContent {
            SpeakDriveTheme {
                ConversationContent(
                    state = ConversationUiState(
                        lesson = lesson,
                        state = ConversationState.ACTIVE,
                        isBargeInEnabled = false,
                        micState = MicState.LISTENING
                    ),
                    permissionDenied = false,
                    onBack = {},
                    onEnd = {},
                    onToggle = {},
                    onRetry = {},
                    onRepeat = { repeatClicked = true },
                    onNext = { nextClicked = true },
                    onToggleBargeIn = {},
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Lặp lại").assertIsDisplayed().performClick()
        assertThat(repeatClicked).isTrue()

        compose.onNodeWithContentDescription("Tiếp theo").assertIsDisplayed().performClick()
        assertThat(nextClicked).isTrue()
    }
}

