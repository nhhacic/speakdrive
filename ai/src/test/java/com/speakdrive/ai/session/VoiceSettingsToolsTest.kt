package com.speakdrive.ai.session

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.StorytellingStyle
import org.junit.Test

class VoiceSettingsToolsTest {

    @Test
    fun `allTools contains all registered settings tools`() {
        val toolNames = VoiceSettingsTools.allTools.map { it.name }
        assertThat(toolNames).containsExactly(
            VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION,
            VoiceSettingsTools.SET_VIETNAMESE_HELP_FUNCTION,
            VoiceSettingsTools.SET_APP_LANGUAGE_FUNCTION,
            VoiceSettingsTools.SET_STORYTELLING_STYLE_FUNCTION,
            VoiceSettingsTools.SET_STORY_DURATION_FUNCTION,
            VoiceSettingsTools.SET_MULTI_VOICE_FUNCTION,
            VoiceSettingsTools.SET_PRONUNCIATION_STRICTNESS_FUNCTION,
            VoiceSettingsTools.SET_BARGE_IN_FUNCTION,
            VoiceSettingsTools.SET_VOICE_FUNCTION,
            VoiceSettingsTools.SET_RANDOM_VOICE_FUNCTION,
            VoiceSettingsTools.NEXT_STORY_FUNCTION,
            VoiceSettingsTools.REPLAY_STORY_FUNCTION,
            VoiceSettingsTools.RESUME_STORY_FUNCTION,
            VoiceSettingsTools.SKIP_DRILL_SENTENCE_FUNCTION,
            VoiceSettingsTools.REPEAT_DRILL_SENTENCE_FUNCTION,
            VoiceSettingsTools.APPLY_LEVEL_RECOMMENDATION_FUNCTION,
            VoiceSettingsTools.SET_ADAPTIVE_LEVEL_FUNCTION,
            VoiceSettingsTools.SET_DRILL_SENTENCE_LENGTH_FUNCTION,
            VoiceSettingsTools.SET_DRILL_CATEGORY_FUNCTION,
            VoiceSettingsTools.SET_AI_VOLUME_FUNCTION,
            VoiceSettingsTools.SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION,
            VoiceSettingsTools.SET_TRANSLATION_SUBTITLES_FUNCTION,
            VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION
        )
    }

    @Test
    fun `checkAttemptTool can be converted to FunctionDeclaration with string parameters`() {
        val tool = com.speakdrive.ai.pronunciation.PronunciationDrill.checkAttemptTool
        val decl = com.google.firebase.ai.type.FunctionDeclaration(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters.associate { param ->
                param.name to when (param.type) {
                    com.speakdrive.ai.live.LiveToolParam.Type.STRING -> com.google.firebase.ai.type.Schema.string(description = param.description)
                    com.speakdrive.ai.live.LiveToolParam.Type.BOOLEAN -> com.google.firebase.ai.type.Schema.boolean(description = param.description)
                    com.speakdrive.ai.live.LiveToolParam.Type.STRING_LIST -> com.google.firebase.ai.type.Schema.array(com.google.firebase.ai.type.Schema.string(), description = param.description)
                }
            },
            optionalParameters = tool.parameters.filter { it.optional }.map { it.name }
        )
        assertThat(decl).isNotNull()
        // Confirm problem_words parameter is now primitive STRING for Live API compatibility
        val problemWordsParam = tool.parameters.first { it.name == "problem_words" }
        assertThat(problemWordsParam.type).isEqualTo(com.speakdrive.ai.live.LiveToolParam.Type.STRING)
    }

    @Test
    fun `parseAutoPauseWhenUnfocused resolves boolean and string inputs`() {
        // Boolean inputs
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused(true)).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused(false)).isFalse()

        // String inputs - True
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("true")).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("enable")).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("bật")).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("on")).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("có")).isTrue()

        // String inputs - False
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("false")).isFalse()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("disable")).isFalse()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("tắt")).isFalse()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("off")).isFalse()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("không")).isFalse()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("đừng")).isFalse()

        // Null / Unknown
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused(null)).isNull()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("xyz")).isNull()
    }

    @Test
    fun `parseVolume resolves English and Vietnamese phrases and percentages`() {
        // Explicit percentages and numbers
        assertThat(VoiceSettingsTools.parseVolume("50%", 80)).isEqualTo(50)
        assertThat(VoiceSettingsTools.parseVolume("âm lượng 70%", 80)).isEqualTo(70)
        assertThat(VoiceSettingsTools.parseVolume("volume 60", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("30", 80)).isEqualTo(30)
        assertThat(VoiceSettingsTools.parseVolume("120%", 80)).isEqualTo(100)
        assertThat(VoiceSettingsTools.parseVolume("5%", 80)).isEqualTo(10)

        // Relative down / softer / quieter
        assertThat(VoiceSettingsTools.parseVolume("nói nhỏ lại", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("cho nhỏ tiếng", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("giảm âm lượng", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("bé lại", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("speak softer", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("quieter", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("volume down", 80)).isEqualTo(60)
        assertThat(VoiceSettingsTools.parseVolume("lower volume", 80)).isEqualTo(60)

        // Relative up / louder
        assertThat(VoiceSettingsTools.parseVolume("nói to lên", 60)).isEqualTo(80)
        assertThat(VoiceSettingsTools.parseVolume("tăng âm lượng", 60)).isEqualTo(80)
        assertThat(VoiceSettingsTools.parseVolume("cho to tiếng", 60)).isEqualTo(80)
        assertThat(VoiceSettingsTools.parseVolume("speak louder", 60)).isEqualTo(80)
        assertThat(VoiceSettingsTools.parseVolume("volume up", 60)).isEqualTo(80)
        assertThat(VoiceSettingsTools.parseVolume("make it louder", 60)).isEqualTo(80)

        // Max / Min
        assertThat(VoiceSettingsTools.parseVolume("âm lượng tối đa", 60)).isEqualTo(100)
        assertThat(VoiceSettingsTools.parseVolume("max volume", 60)).isEqualTo(100)
        assertThat(VoiceSettingsTools.parseVolume("nhỏ nhất", 60)).isEqualTo(10)
        assertThat(VoiceSettingsTools.parseVolume("min volume", 60)).isEqualTo(10)
    }

    @Test
    fun `parseStrictness resolves English and Vietnamese phrases`() {
        // Auto / by level
        assertThat(VoiceSettingsTools.parseStrictness("auto")).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(VoiceSettingsTools.parseStrictness("tự động")).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(VoiceSettingsTools.parseStrictness("theo cấp độ")).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(VoiceSettingsTools.parseStrictness("by level")).isEqualTo(PronunciationStrictness.AUTO)
        assertThat(VoiceSettingsTools.parseStrictness("theo trình độ")).isEqualTo(PronunciationStrictness.AUTO)

        // Beginner (A1)
        assertThat(VoiceSettingsTools.parseStrictness("beginner")).isEqualTo(PronunciationStrictness.BEGINNER)
        assertThat(VoiceSettingsTools.parseStrictness("A1")).isEqualTo(PronunciationStrictness.BEGINNER)
        assertThat(VoiceSettingsTools.parseStrictness("rất nhẹ")).isEqualTo(PronunciationStrictness.BEGINNER)

        // Elementary (A2 / relaxed)
        assertThat(VoiceSettingsTools.parseStrictness("elementary")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("A2")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("relaxed")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("lenient")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("dễ tính")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("dễ chịu")).isEqualTo(PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseStrictness("nhẹ tay")).isEqualTo(PronunciationStrictness.ELEMENTARY)

        // Pre-Intermediate (A2–B1)
        assertThat(VoiceSettingsTools.parseStrictness("pre-intermediate")).isEqualTo(PronunciationStrictness.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("tiền trung cấp")).isEqualTo(PronunciationStrictness.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("A2-B1")).isEqualTo(PronunciationStrictness.PRE_INTERMEDIATE)

        // Intermediate (B1 / standard)
        assertThat(VoiceSettingsTools.parseStrictness("intermediate")).isEqualTo(PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("B1")).isEqualTo(PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("standard")).isEqualTo(PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("tiêu chuẩn")).isEqualTo(PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("bình thường")).isEqualTo(PronunciationStrictness.INTERMEDIATE)

        // Upper-Intermediate (B2 / strict)
        assertThat(VoiceSettingsTools.parseStrictness("upper-intermediate")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("trung cấp trên")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("B2")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("strict")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("khắt khe")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseStrictness("nghiêm khắc")).isEqualTo(PronunciationStrictness.UPPER_INTERMEDIATE)

        // Advanced (C1–C2 / native)
        assertThat(VoiceSettingsTools.parseStrictness("advanced")).isEqualTo(PronunciationStrictness.ADVANCED)
        assertThat(VoiceSettingsTools.parseStrictness("C1")).isEqualTo(PronunciationStrictness.ADVANCED)
        assertThat(VoiceSettingsTools.parseStrictness("C2")).isEqualTo(PronunciationStrictness.ADVANCED)
        assertThat(VoiceSettingsTools.parseStrictness("chuẩn bản xứ")).isEqualTo(PronunciationStrictness.ADVANCED)
        assertThat(VoiceSettingsTools.parseStrictness("native")).isEqualTo(PronunciationStrictness.ADVANCED)
    }

    @Test
    fun `parseVoice resolves AI voice names and descriptions`() {
        assertThat(VoiceSettingsTools.parseVoice("Puck")).isEqualTo(AiVoice.PUCK)
        assertThat(VoiceSettingsTools.parseVoice("Charon")).isEqualTo(AiVoice.CHARON)
        assertThat(VoiceSettingsTools.parseVoice("Aoede")).isEqualTo(AiVoice.AOEDE)
        assertThat(VoiceSettingsTools.parseVoice("Kore")).isEqualTo(AiVoice.KORE)

        assertThat(VoiceSettingsTools.parseVoice("giọng nam vui vẻ")).isEqualTo(AiVoice.PUCK)
        assertThat(VoiceSettingsTools.parseVoice("giọng nam trầm ấm")).isEqualTo(AiVoice.CHARON)
        assertThat(VoiceSettingsTools.parseVoice("giọng nữ nhẹ nhàng")).isEqualTo(AiVoice.AOEDE)
        assertThat(VoiceSettingsTools.parseVoice("nữ rõ ràng")).isEqualTo(AiVoice.KORE)
    }

    @Test
    fun `parseLevel resolves correctly`() {
        assertThat(VoiceSettingsTools.parseLevel("A1")).isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceSettingsTools.parseLevel("A2")).isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseLevel("A2-B1")).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("B1")).isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("B2")).isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("C1")).isEqualTo(DifficultyLevel.ADVANCED)

        assertThat(VoiceSettingsTools.parseLevel("cơ bản")).isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceSettingsTools.parseLevel("sơ cấp")).isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseLevel("tiền trung cấp")).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("trung cấp")).isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("trung cấp trên")).isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("nâng cao")).isEqualTo(DifficultyLevel.ADVANCED)

        assertThat(VoiceSettingsTools.parseLevel("elementary")).isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceSettingsTools.parseLevel("pre-intermediate")).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("upper-intermediate")).isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
    }

    @Test
    fun `isRandomVoiceRequested detects English and Vietnamese phrases`() {
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("random")).isTrue()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("ngẫu nhiên")).isTrue()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("ngau nhien")).isTrue()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("bất kỳ")).isTrue()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("bat ky")).isTrue()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("Puck")).isFalse()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested("")).isFalse()
        assertThat(VoiceSettingsTools.isRandomVoiceRequested(null)).isFalse()
    }

    @Test
    fun `parseStoryDuration resolves 5 min and 10 to 30 min in English and Vietnamese`() {
        assertThat(VoiceSettingsTools.parseStoryDuration("5 minutes")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("5 phút")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("5 phut")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("ngắn thôi")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("chuyện ngắn")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("short story")).isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)

        assertThat(VoiceSettingsTools.parseStoryDuration("15 minutes")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("10 phút")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("30 phút")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("chuyện dài đầy đủ")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("full length story")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceSettingsTools.parseStoryDuration("extended")).isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
    }

    @Test
    fun `parseMultiVoice resolves enable and disable in English and Vietnamese`() {
        assertThat(VoiceSettingsTools.parseMultiVoice("bật đa giọng")).isTrue()
        assertThat(VoiceSettingsTools.parseMultiVoice("kịch truyền thanh")).isTrue()
        assertThat(VoiceSettingsTools.parseMultiVoice("nhiều giọng đọc")).isTrue()
        assertThat(VoiceSettingsTools.parseMultiVoice("enable multi voice")).isTrue()
        assertThat(VoiceSettingsTools.parseMultiVoice("audio drama")).isTrue()

        assertThat(VoiceSettingsTools.parseMultiVoice("tắt đa giọng")).isFalse()
        assertThat(VoiceSettingsTools.parseMultiVoice("một giọng thôi")).isFalse()
        assertThat(VoiceSettingsTools.parseMultiVoice("đơn giọng")).isFalse()
        assertThat(VoiceSettingsTools.parseMultiVoice("single voice")).isFalse()
        assertThat(VoiceSettingsTools.parseMultiVoice("disable multi voice")).isFalse()
    }

    @Test
    fun `parseAppLanguage resolves codes, English names, and native or Vietnamese names`() {
        assertThat(VoiceSettingsTools.parseAppLanguage("vi")).isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("tiếng việt")).isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("vietnamese")).isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)

        assertThat(VoiceSettingsTools.parseAppLanguage("en")).isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)
        assertThat(VoiceSettingsTools.parseAppLanguage("english")).isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)
        assertThat(VoiceSettingsTools.parseAppLanguage("tiếng anh")).isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)

        assertThat(VoiceSettingsTools.parseAppLanguage("ja")).isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("japanese")).isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("tiếng nhật")).isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("日本語")).isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)

        assertThat(VoiceSettingsTools.parseAppLanguage("es")).isEqualTo(com.speakdrive.ai.model.AppLanguage.SPANISH)
        assertThat(VoiceSettingsTools.parseAppLanguage("español")).isEqualTo(com.speakdrive.ai.model.AppLanguage.SPANISH)
        assertThat(VoiceSettingsTools.parseAppLanguage("tiếng tây ban nha")).isEqualTo(com.speakdrive.ai.model.AppLanguage.SPANISH)

        assertThat(VoiceSettingsTools.parseAppLanguage("ko")).isEqualTo(com.speakdrive.ai.model.AppLanguage.KOREAN)
        assertThat(VoiceSettingsTools.parseAppLanguage("zh")).isEqualTo(com.speakdrive.ai.model.AppLanguage.CHINESE)
        assertThat(VoiceSettingsTools.parseAppLanguage("fr")).isEqualTo(com.speakdrive.ai.model.AppLanguage.FRENCH)
        assertThat(VoiceSettingsTools.parseAppLanguage("de")).isEqualTo(com.speakdrive.ai.model.AppLanguage.GERMAN)
        assertThat(VoiceSettingsTools.parseAppLanguage("system")).isEqualTo(com.speakdrive.ai.model.AppLanguage.SYSTEM)
        assertThat(VoiceSettingsTools.parseAppLanguage("mặc định")).isEqualTo(com.speakdrive.ai.model.AppLanguage.SYSTEM)

        assertThat(VoiceSettingsTools.parseAppLanguage(null)).isNull()
        assertThat(VoiceSettingsTools.parseAppLanguage("xyz")).isNull()
    }

    @Test
    fun `skipDrillSentenceTool is registered with correct name and description`() {
        val tool = VoiceSettingsTools.skipDrillSentenceTool
        assertThat(tool.name).isEqualTo(VoiceSettingsTools.SKIP_DRILL_SENTENCE_FUNCTION)
        assertThat(tool.description).contains("skip")
    }

    @Test
    fun `repeatDrillSentenceTool is registered with correct name and description`() {
        val tool = VoiceSettingsTools.repeatDrillSentenceTool
        assertThat(tool.name).isEqualTo(VoiceSettingsTools.REPEAT_DRILL_SENTENCE_FUNCTION)
        assertThat(tool.description).contains("repeat")
    }

    @Test
    fun `parseApplyLevelRecommendation resolves accept and keep choices`() {
        // Vietnamese accept
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("đồng ý tăng cấp độ")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("chấp nhận lên cấp")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("lên cấp")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("giảm cấp độ")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("đồng ý")).isTrue()

        // English accept
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("level up")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("accept recommendation")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("apply new level")).isTrue()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("yes")).isTrue()

        // Vietnamese keep / dismiss
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("giữ nguyên cấp độ")).isFalse()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("không đổi")).isFalse()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("để sau")).isFalse()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("giữ nguyên")).isFalse()

        // English keep / dismiss
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("keep current level")).isFalse()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("stay here")).isFalse()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("no")).isFalse()

        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation(null)).isNull()
        assertThat(VoiceSettingsTools.parseApplyLevelRecommendation("chào bạn")).isNull()
    }

    @Test
    fun `parseAdaptiveLevel resolves enable and disable phrases`() {
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("bật tự động gợi ý")).isTrue()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("bật gợi ý cấp độ")).isTrue()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("enable adaptive level")).isTrue()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("true")).isTrue()

        assertThat(VoiceSettingsTools.parseAdaptiveLevel("tắt tự động gợi ý cấp độ")).isFalse()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("không gợi ý")).isFalse()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("disable level recommendation")).isFalse()
        assertThat(VoiceSettingsTools.parseAdaptiveLevel("false")).isFalse()

        assertThat(VoiceSettingsTools.parseAdaptiveLevel(null)).isNull()
    }

    @Test
    fun `parseDrillSentenceLength resolves English and Vietnamese phrases`() {
        // Auto on car / Driving mode
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("auto")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("tự động khi lái xe")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("trên ô tô")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("android auto")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("driving")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("khi lái xe")).isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        // Always short
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("short")).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("ngắn")).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("rút ngắn")).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("câu ngắn thôi")).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("ngắn gọn")).isEqualTo(DrillSentenceLength.ALWAYS_SHORT)

        // Standard
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("standard")).isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("tiêu chuẩn")).isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("bình thường")).isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("câu dài hơn")).isEqualTo(DrillSentenceLength.STANDARD)

        assertThat(VoiceSettingsTools.parseDrillSentenceLength(null)).isNull()
        assertThat(VoiceSettingsTools.parseDrillSentenceLength("xin chào")).isNull()
    }

    @Test
    fun `parseDrillCategory resolves English and Vietnamese phrases`() {
        // Vietnamese pitfalls
        assertThat(VoiceSettingsTools.parseDrillCategory("vietnamese_pitfalls")).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceSettingsTools.parseDrillCategory("lỗi âm người Việt")).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceSettingsTools.parseDrillCategory("luyện âm đuôi")).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceSettingsTools.parseDrillCategory("ending sounds")).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceSettingsTools.parseDrillCategory("pitfalls")).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)

        // Driving phrases
        assertThat(VoiceSettingsTools.parseDrillCategory("driving_phrases")).isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceSettingsTools.parseDrillCategory("tiếng Anh lái xe")).isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceSettingsTools.parseDrillCategory("khi lái ô tô")).isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceSettingsTools.parseDrillCategory("driving")).isEqualTo(DrillCategory.DRIVING_PHRASES)

        // Conversational reflex
        assertThat(VoiceSettingsTools.parseDrillCategory("conversational_reflex")).isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceSettingsTools.parseDrillCategory("phản xạ giao tiếp")).isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceSettingsTools.parseDrillCategory("giao tiếp hàng ngày")).isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceSettingsTools.parseDrillCategory("reflex")).isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)

        // Business & work
        assertThat(VoiceSettingsTools.parseDrillCategory("business_work")).isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceSettingsTools.parseDrillCategory("công sở")).isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceSettingsTools.parseDrillCategory("tiếng Anh văn phòng")).isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceSettingsTools.parseDrillCategory("business")).isEqualTo(DrillCategory.BUSINESS_WORK)

        // Travel & daily
        assertThat(VoiceSettingsTools.parseDrillCategory("travel_daily")).isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceSettingsTools.parseDrillCategory("tiếng Anh du lịch")).isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceSettingsTools.parseDrillCategory("khách sạn sân bay")).isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceSettingsTools.parseDrillCategory("travel")).isEqualTo(DrillCategory.TRAVEL_DAILY)

        // All categories
        assertThat(VoiceSettingsTools.parseDrillCategory("all")).isEqualTo(DrillCategory.ALL)
        assertThat(VoiceSettingsTools.parseDrillCategory("tất cả chủ đề")).isEqualTo(DrillCategory.ALL)
        assertThat(VoiceSettingsTools.parseDrillCategory("tổng hợp")).isEqualTo(DrillCategory.ALL)

        assertThat(VoiceSettingsTools.parseDrillCategory(null)).isNull()
        assertThat(VoiceSettingsTools.parseDrillCategory("không liên quan")).isNull()
    }

    @Test
    fun `parseTranslationSubtitles resolves boolean and string inputs`() {
        // Boolean inputs
        assertThat(VoiceSettingsTools.parseTranslationSubtitles(true)).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles(false)).isFalse()

        // String inputs - True (Vietnamese & English)
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("true")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("enable")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("bật")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("on")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("hiện")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("show")).isTrue()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("bật phụ đề")).isTrue()

        // String inputs - False (Vietnamese & English)
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("false")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("disable")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("tắt")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("off")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("ẩn")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("hide")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("tắt phụ đề")).isFalse()

        // Null / Unknown
        assertThat(VoiceSettingsTools.parseTranslationSubtitles(null)).isNull()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("xyz")).isNull()
    }

    @Test
    fun `parseSessionMode resolves mode inputs correctly`() {
        // Direct enum names
        assertThat(VoiceSettingsTools.parseSessionMode("REPEAT_AFTER_ME")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("FREE_TALK")).isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceSettingsTools.parseSessionMode("STORY_LISTENING")).isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(VoiceSettingsTools.parseSessionMode("ROLEPLAY")).isEqualTo(SessionMode.ROLEPLAY)
        assertThat(VoiceSettingsTools.parseSessionMode("VOCAB_REVIEW")).isEqualTo(SessionMode.VOCAB_REVIEW)

        // Shadowing / Pronunciation phrases
        assertThat(VoiceSettingsTools.parseSessionMode("shadowing")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("luyện phát âm")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("tập phát âm")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("nhắc lại theo bạn")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("repeat after me")).isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceSettingsTools.parseSessionMode("pronunciation drill")).isEqualTo(SessionMode.REPEAT_AFTER_ME)

        // Free talk / Conversation
        assertThat(VoiceSettingsTools.parseSessionMode("hội thoại tự do")).isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceSettingsTools.parseSessionMode("trò chuyện")).isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceSettingsTools.parseSessionMode("free talk")).isEqualTo(SessionMode.FREE_TALK)

        // Story listening
        assertThat(VoiceSettingsTools.parseSessionMode("kể chuyện")).isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(VoiceSettingsTools.parseSessionMode("story listening")).isEqualTo(SessionMode.STORY_LISTENING)

        // Roleplay
        assertThat(VoiceSettingsTools.parseSessionMode("nhập vai")).isEqualTo(SessionMode.ROLEPLAY)
        assertThat(VoiceSettingsTools.parseSessionMode("roleplay")).isEqualTo(SessionMode.ROLEPLAY)

        // Vocab review
        assertThat(VoiceSettingsTools.parseSessionMode("ôn từ vựng")).isEqualTo(SessionMode.VOCAB_REVIEW)
        assertThat(VoiceSettingsTools.parseSessionMode("vocabulary review")).isEqualTo(SessionMode.VOCAB_REVIEW)

        // Unknown / null
        assertThat(VoiceSettingsTools.parseSessionMode(null)).isNull()
        assertThat(VoiceSettingsTools.parseSessionMode("bla bla bla")).isNull()
    }

    @Test
    fun `tool arguments are matched on whole words`() {
        // "harder" contains "de" and used to resolve to BEGINNER.
        assertThat(VoiceSettingsTools.parseLevel("harder")).isNotEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceSettingsTools.parseLevel("harder", DifficultyLevel.INTERMEDIATE)).isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("dễ hơn", DifficultyLevel.INTERMEDIATE)).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceSettingsTools.parseLevel("không")).isNull()
        // "don't" contains "on", "an" is inside many words.
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("don't show translation")).isFalse()
        assertThat(VoiceSettingsTools.parseTranslationSubtitles("show translation")).isTrue()
        assertThat(VoiceSettingsTools.parseAutoPauseWhenUnfocused("dont pause")).isFalse()
        assertThat(VoiceSettingsTools.parseDrillCategory("không liên quan gì")).isNull()
    }
}
