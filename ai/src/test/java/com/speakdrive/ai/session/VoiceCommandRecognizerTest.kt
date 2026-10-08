package com.speakdrive.ai.session

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
}
