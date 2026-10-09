package com.speakdrive.ai.session

import com.speakdrive.ai.model.ScreenAwakeMode
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.speakdrive.ai.drill.DrillSentenceManager
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.SessionMode
import org.junit.Test

class VoiceCommandRecognizerTest {

    private fun context(
        mode: SessionMode = SessionMode.FREE_TALK,
        drillTarget: String? = null,
        pendingRecommendation: Boolean = false,
        state: ConversationState = ConversationState.ACTIVE
    ) = CommandContext(
        state = state,
        mode = mode,
        currentLevel = DifficultyLevel.INTERMEDIATE,
        settings = LearnerSettings(),
        drillTarget = drillTarget,
        hasPendingLevelRecommendation = pendingRecommendation
    )

    private fun recognize(text: String, ctx: CommandContext = context()) = VoiceCommandRecognizer.recognize(text, ctx)

    @Test
    fun `clear commands are recognised in Vietnamese with and without diacritics and in English`() {
        assertThat(recognize("nói nhỏ lại")).isEqualTo(VoiceCommand.SetVolume(60))
        assertThat(recognize("noi nho lai")).isEqualTo(VoiceCommand.SetVolume(60))
        assertThat(recognize("speak louder please")).isEqualTo(VoiceCommand.SetVolume(100))
        assertThat(recognize("đổi ngôn ngữ sang tiếng Nhật")).isInstanceOf(VoiceCommand.SetAppLanguage::class.java)
        assertThat(recognize("chuyển sang kể chuyện")).isEqualTo(VoiceCommand.SwitchMode(SessionMode.STORY_LISTENING))
        assertThat(recognize("switch to roleplay")).isEqualTo(VoiceCommand.SwitchMode(SessionMode.ROLEPLAY))
        assertThat(recognize("tắt phụ đề")).isEqualTo(VoiceCommand.SetSubtitles(false))
    }

    @Test
    fun `everyday sentences are not commands`() {
        listOf(
            "Tôi đang học tiếng Anh",
            "I use English at work every day",
            "Tôi lái xe 2 tiếng mỗi ngày",
            "Anh trai tôi lớn hơn tôi",
            "I love Korean food",
            "Rõ ràng là hôm nay trời đẹp",
            "Hôm nay tôi rất vui vẻ",
            "Tôi đang lái xe",
            "Tôi làm việc ở ngân hàng",
            "Tôi thích du lịch",
            "Tôi nhớ lại chuyến đi đó",
            "Tôi đói bụng quá, tiếng Anh nói thế nào",
            "It is easier said than done",
            "Can you tell me a story about your weekend?",
            "Hôm qua tôi học từ vựng rất lâu"
        ).forEach { sentence ->
            assertWithMessage(sentence).that(recognize(sentence)).isNull()
        }
    }

    @Test
    fun `switching modes needs an explicit request`() {
        assertThat(recognize("Can you tell me a story about dragons")).isNull()
        assertThat(recognize("kể chuyện đi")).isEqualTo(VoiceCommand.SwitchMode(SessionMode.STORY_LISTENING))
        assertThat(recognize("chuyển sang luyện phát âm")).isEqualTo(VoiceCommand.SwitchMode(SessionMode.REPEAT_AFTER_ME))
    }

    @Test
    fun `agreeing only answers a pending level recommendation`() {
        assertThat(recognize("đồng ý")).isNull()
        assertThat(recognize("đồng ý", context(pendingRecommendation = true)))
            .isEqualTo(VoiceCommand.AnswerLevelRecommendation(true))
        assertThat(recognize("để sau", context(pendingRecommendation = true)))
            .isEqualTo(VoiceCommand.AnswerLevelRecommendation(false))
    }

    @Test
    fun `going on during a story is not a resume of an old story`() {
        val story = context(mode = SessionMode.STORY_LISTENING)
        assertThat(recognize("kể tiếp đi", story)).isNull()
        assertThat(recognize("kể tiếp chuyện hôm trước")).isEqualTo(VoiceCommand.ResumeStory)
    }

    @Test
    fun `story settings outside a story need a real request`() {
        assertThat(recognize("Long story short, I missed the bus")).isNull()
        assertThat(recognize("chuyện ngắn", context(mode = SessionMode.STORY_LISTENING)))
            .isInstanceOf(VoiceCommand.SetStoryDuration::class.java)
    }

    @Test
    fun `nothing is recognised while the lesson is paused or reconnecting`() {
        assertThat(recognize("nói nhỏ lại", context(state = ConversationState.PAUSED))).isNull()
        assertThat(recognize("nói nhỏ lại", context(state = ConversationState.RECONNECTING))).isNull()
    }

    @Test
    fun `long sentences are conversation, not commands`() {
        assertThat(recognize("make it easier please because I really do not understand the word itinerary at all")).isNull()
    }

    @Test
    fun `repeating any curated drill sentence never triggers a command`() {
        val sentences = DrillSentenceManager().getAllSentences().map { it.text }
        assertThat(sentences).isNotEmpty()
        sentences.forEach { sentence ->
            val ctx = context(mode = SessionMode.REPEAT_AFTER_ME, drillTarget = sentence)
            assertWithMessage(sentence).that(recognize(sentence, ctx)).isNull()
        }
    }

    @Test
    fun `drill navigation works on short requests during a drill`() {
        val drill = context(mode = SessionMode.REPEAT_AFTER_ME, drillTarget = "Where is the nearest station?")
        assertThat(recognize("đọc lại câu này", drill)).isEqualTo(VoiceCommand.RepeatDrillSentence)
        assertThat(recognize("bỏ qua", drill)).isEqualTo(VoiceCommand.SkipDrillSentence)
        assertThat(recognize("bỏ qua", context())).isNull()
    }

    @Test
    fun `every settings tool has a category so the safety net can step aside`() {
        VoiceSettingsTools.allTools.forEach { tool ->
            assertWithMessage(tool.name).that(CommandCategory.forTool(tool.name)).isNotNull()
        }
    }

    @Test
    fun `memory, reminder and streak freeze commands are recognised`() {
        assertThat(recognize("tắt ghi nhớ")).isEqualTo(VoiceCommand.SetLearnerMemory(false))
        assertThat(recognize("turn off memory")).isEqualTo(VoiceCommand.SetLearnerMemory(false))
        assertThat(recognize("tắt nhắc học")).isEqualTo(VoiceCommand.SetPracticeReminder(false, null))
        assertThat(recognize("nhắc học lúc 7 giờ sáng")).isEqualTo(VoiceCommand.SetPracticeReminder(true, 7 * 60))
        assertThat(recognize("remind me to practice at 6 pm")).isEqualTo(VoiceCommand.SetPracticeReminder(true, 18 * 60))
        assertThat(recognize("tắt bảo toàn chuỗi")).isEqualTo(VoiceCommand.SetStreakFreeze(false))
        assertThat(recognize("chuyển sang ôn lỗi sai")).isEqualTo(VoiceCommand.SwitchMode(SessionMode.MISTAKE_REVIEW))
    }

    @Test
    fun `settings that are already in place are not changed again`() {
        // Defaults: memory on, reminder on and automatic, streak freeze on.
        assertThat(recognize("bật ghi nhớ")).isNull()
        assertThat(recognize("bật nhắc học")).isNull()
        assertThat(recognize("nhắc học tự động")).isNull()
        assertThat(recognize("bật bảo toàn chuỗi")).isNull()
    }

    @Test
    fun `conversation about reminders and memories is not a command`() {
        listOf(
            "Can you remind me at 7 to call my mom",
            "Tôi nhớ lại chuyến đi đó",
            "Anh ấy vẫn còn nhớ về tôi",
            "I made a mistake at work yesterday"
        ).forEach { sentence ->
            assertWithMessage(sentence).that(recognize(sentence)).isNull()
        }
    }

    @Test
    fun `offline practice toggle is recognised`() {
        assertThat(recognize("tắt luyện offline")).isEqualTo(VoiceCommand.SetOfflinePractice(false))
        assertThat(recognize("turn off offline practice")).isEqualTo(VoiceCommand.SetOfflinePractice(false))
        assertThat(recognize("bật luyện offline")).isNull()
    }

    @Test
    fun `car, goal, azure and screen settings are recognised as commands`() {
        assertThat(recognize("tắt tự động học khi lên xe")).isEqualTo(VoiceCommand.SetAutoStartInCar(false))
        assertThat(recognize("đặt mục tiêu 20 phút mỗi ngày")).isEqualTo(VoiceCommand.SetDailyGoal(20))
        assertThat(recognize("bật chấm điểm Azure")).isEqualTo(VoiceCommand.SetAzureScoring(true))
        assertThat(recognize("tắt màn hình sau 1 phút")).isEqualTo(VoiceCommand.SetScreenAwake(ScreenAwakeMode.AFTER_1_MINUTE))
        // Already the current value: nothing to do.
        assertThat(recognize("luôn sáng màn hình")).isNull()
        // Statements, not requests.
        assertThat(recognize("hôm nay tôi học 20 phút trên đường đi làm")).isNull()
    }

    @Test
    fun `weekly digest requests are recognised as commands`() {
        assertThat(recognize("báo cáo tuần")).isEqualTo(VoiceCommand.GetWeeklyDigest)
        assertThat(recognize("tóm tắt tuần qua")).isEqualTo(VoiceCommand.GetWeeklyDigest)
        assertThat(recognize("weekly digest")).isEqualTo(VoiceCommand.GetWeeklyDigest)
        assertThat(recognize("tắt báo cáo tuần")).isEqualTo(VoiceCommand.SetWeeklyDigest(false))
        assertThat(recognize("turn off weekly digest")).isEqualTo(VoiceCommand.SetWeeklyDigest(false))
    }
}
