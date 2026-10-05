package com.speakdrive.ai.session

import com.speakdrive.ai.live.LiveTool
import com.speakdrive.ai.live.LiveToolParam
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.TopicManager

/**
 * Declares the Gemini Live tools that let learners adjust settings hands-free during a lesson.
 */
object VoiceSettingsTools {
    const val SET_DIFFICULTY_LEVEL_FUNCTION = "set_difficulty_level"
    const val SET_VIETNAMESE_HELP_FUNCTION = "set_vietnamese_help"
    const val SET_APP_LANGUAGE_FUNCTION = "set_app_language"
    const val SET_STORYTELLING_STYLE_FUNCTION = "set_storytelling_style"
    const val SET_STORY_DURATION_FUNCTION = "set_story_duration"
    const val SET_MULTI_VOICE_FUNCTION = "set_multi_voice_storytelling"
    const val SET_PRONUNCIATION_STRICTNESS_FUNCTION = "set_pronunciation_strictness"
    const val SET_BARGE_IN_FUNCTION = "set_barge_in"
    const val SET_VOICE_FUNCTION = "set_ai_voice"
    const val SET_RANDOM_VOICE_FUNCTION = "set_random_voice"
    const val NEXT_STORY_FUNCTION = "next_story"
    const val REPLAY_STORY_FUNCTION = "replay_story"
    const val RESUME_STORY_FUNCTION = "resume_story"
    const val SKIP_DRILL_SENTENCE_FUNCTION = "skip_drill_sentence"
    const val APPLY_LEVEL_RECOMMENDATION_FUNCTION = "apply_level_recommendation"
    const val SET_ADAPTIVE_LEVEL_FUNCTION = "set_adaptive_level"
    const val SET_DRILL_SENTENCE_LENGTH_FUNCTION = "set_drill_sentence_length"
    const val SET_DRILL_CATEGORY_FUNCTION = "set_drill_category"
    const val SET_AI_VOLUME_FUNCTION = "set_ai_volume"

    val setAiVolumeTool = LiveTool(
        name = SET_AI_VOLUME_FUNCTION,
        description = "Adjusts AI voice playback volume when requested by the learner in Vietnamese or English " +
            "(e.g. \"nói nhỏ lại\", \"giảm âm lượng\", \"cho nhỏ tiếng lại\", \"bé tiếng thôi\", \"nói to lên\", \"tăng âm lượng\", \"âm lượng 50%\", " +
            "\"volume down\", \"speak softer\", \"lower volume\", \"quieter\", \"turn down the volume\", \"speak louder\", \"volume up\", \"set volume to 70%\").",
        parameters = listOf(
            LiveToolParam(
                name = "volume",
                type = LiveToolParam.Type.STRING,
                description = "Target volume percentage (e.g. '50', '70%'), or relative direction ('softer', 'louder', 'quieter', 'down', 'up', 'max', 'min')."
            )
        )
    )

    val setDrillCategoryTool = LiveTool(
        name = SET_DRILL_CATEGORY_FUNCTION,
        description = "Changes the pronunciation/repeat-after-me drill topic category when requested by the learner in Vietnamese or English " +
            "(e.g. \"luyện các lỗi phát âm người Việt\", \"chuyển sang phản xạ hội thoại\", \"luyện tiếng Anh khi lái xe\", \"chủ đề công sở\", \"luyện câu du lịch\", \"tất cả các chủ đề\", " +
            "\"practice Vietnamese pitfalls\", \"switch to daily reflex\", \"driving phrases\", \"business English\", \"travel phrases\", \"all drill topics\").",
        parameters = listOf(
            LiveToolParam(
                name = "category",
                type = LiveToolParam.Type.STRING,
                description = "Target drill category: ALL (Tất cả chủ đề), VIETNAMESE_PITFALLS (Lỗi âm người Việt hay gặp), CONVERSATIONAL_REFLEX (Phản xạ giao tiếp thực tế), DRIVING_PHRASES (Tiếng Anh khi lái xe & An toàn), BUSINESS_WORK (Công sở & Làm việc), or TRAVEL_DAILY (Du lịch & Đời sống)."
            )
        )
    )

    val setDrillSentenceLengthTool = LiveTool(
        name = SET_DRILL_SENTENCE_LENGTH_FUNCTION,
        description = "Adjusts sentence length for pronunciation/repeat-after-me practice when requested by the learner in Vietnamese or English " +
            "(e.g. \"câu ngắn khi lái xe\", \"rút ngắn câu lặp lại\", \"câu ngắn thôi\", \"câu nhắc lại ngắn gọn\", \"câu dài hơn\", \"độ dài tiêu chuẩn\", \"tự động ngắn khi lái xe\", " +
            "\"short drill sentences\", \"shorter sentences\", \"keep sentences short on Android Auto\", \"standard sentence length\").",
        parameters = listOf(
            LiveToolParam(
                name = "length",
                type = LiveToolParam.Type.STRING,
                description = "Target sentence length mode: AUTO_ON_CAR (automatically short 5-8 words when driving on Android Auto, standard elsewhere), ALWAYS_SHORT (always keep short 3-7 words), or STANDARD (standard CEFR length up to 12-18 words)."
            )
        )
    )

    val applyLevelRecommendationTool = LiveTool(
        name = APPLY_LEVEL_RECOMMENDATION_FUNCTION,
        description = "Applies or dismisses an AI-recommended difficulty level change when requested by the learner in Vietnamese or English " +
            "(e.g. \"đồng ý tăng cấp độ\", \"chấp nhận lên cấp\", \"đồng ý đổi cấp độ\", \"giữ nguyên cấp độ\", \"không đổi\", " +
            "\"level up\", \"accept recommendation\", \"apply level suggestion\", \"keep current level\", \"stay at this level\").",
        parameters = listOf(
            LiveToolParam(
                name = "accept",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to accept the level change (level up or down), false to keep the current level."
            ),
            LiveToolParam(
                name = "target_level",
                type = LiveToolParam.Type.STRING,
                description = "Optional explicit target level if mentioned by the user (e.g. PRE_INTERMEDIATE, INTERMEDIATE).",
                optional = true
            )
        )
    )

    val setAdaptiveLevelTool = LiveTool(
        name = SET_ADAPTIVE_LEVEL_FUNCTION,
        description = "Enables or disables automatic adaptive level suggestions when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật tự động gợi ý cấp độ\", \"tắt tự động gợi ý cấp độ\", \"bật gợi ý cấp độ\", \"tắt gợi ý cấp độ\", " +
            "\"enable adaptive level\", \"turn off level recommendation\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to enable adaptive level suggestions, false to disable."
            )
        )
    )

    val setDifficultyLevelTool = LiveTool(
        name = SET_DIFFICULTY_LEVEL_FUNCTION,
        description = "Changes the difficulty level of the lesson or story narration when requested by the learner in Vietnamese or English " +
            "(e.g. \"chuyển sang cấp độ sơ cấp A2\", \"đổi sang tiền trung cấp A2-B1\", \"mức trung cấp B1\", \"trung cấp trên B2\", \"mức cơ bản A1\", \"cấp độ nâng cao C1-C2\", " +
            "\"kể dễ hơn\", \"chuyện khó quá kể đơn giản thôi\", \"kể dễ hiểu hơn\", \"kể nâng cao hơn\", \"kể khó hơn\", \"tăng độ khó\", " +
            "\"switch to elementary\", \"switch to pre-intermediate\", \"switch to intermediate\", \"change difficulty to upper-intermediate\", " +
            "\"make it easier\", \"make story simpler\", \"tell a simpler story\", \"this story is too hard\", \"make story more advanced\", \"make it harder\").",
        parameters = listOf(
            LiveToolParam(
                name = "level",
                type = LiveToolParam.Type.STRING,
                description = "The target difficulty level: BEGINNER (A1), ELEMENTARY (A2), PRE_INTERMEDIATE (A2–B1), INTERMEDIATE (B1), UPPER_INTERMEDIATE (B2), or ADVANCED (C1–C2)."
            )
        )
    )

    val setVietnameseHelpTool = LiveTool(
        name = SET_VIETNAMESE_HELP_FUNCTION,
        description = "Enables or disables Vietnamese explanations when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật tiếng Việt\", \"giải thích bằng tiếng Việt\", \"tắt tiếng Việt\", \"chỉ nói tiếng Anh thôi\", " +
            "\"turn on Vietnamese help\", \"English only\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to allow occasional Vietnamese explanations, false for English only."
            )
        )
    )

    val setAppLanguageTool = LiveTool(
        name = SET_APP_LANGUAGE_FUNCTION,
        description = "Changes the app interface and explanation language when requested by the learner in Vietnamese, English or other languages " +
            "(e.g. \"đổi ngôn ngữ sang tiếng Anh\", \"chuyển sang tiếng Việt\", \"đổi sang tiếng Nhật\", \"tiếng Hàn\", \"tiếng Tây Ban Nha\", \"tiếng Pháp\", \"tiếng Đức\", \"tiếng Trung\", " +
            "\"change language to English\", \"switch to Spanish\", \"change language to Japanese\", \"switch to French\", \"set language to Korean\", \"cambiar idioma a español\", \"日本語に変更\").",
        parameters = listOf(
            LiveToolParam(
                name = "language",
                type = LiveToolParam.Type.STRING,
                description = "The target language code or name: vi (Tiếng Việt), en (English), es (Español), ja (Japanese), ko (Korean), zh (Chinese), fr (French), de (German), or system (system default)."
            )
        )
    )

    val setStorytellingStyleTool = LiveTool(
        name = SET_STORYTELLING_STYLE_FUNCTION,
        description = "Changes the storytelling style when requested by the learner in Vietnamese or English " +
            "(e.g. \"chuyển sang kể liền mạch\", \"chỉ kể chuyện thôi\", \"chế độ podcast\", \"chuyển sang podcast\", \"kể liên tục\", " +
            "\"đừng hỏi nữa, cứ kể tiếp đi\", \"kể tương tác\", \"hỏi sau mỗi đoạn\", \"switch to continuous storytelling\", " +
            "\"podcast mode\", \"stop asking me questions and keep telling the story\", \"interactive mode\").",
        parameters = listOf(
            LiveToolParam(
                name = "style",
                type = LiveToolParam.Type.STRING,
                description = "Target storytelling style: INTERACTIVE (interactive with pause & questions) or CONTINUOUS (podcast smooth monologue)."
            )
        )
    )

    val setStoryDurationTool = LiveTool(
        name = SET_STORY_DURATION_FUNCTION,
        description = "Sets or switches the story listening duration when requested by the learner in Vietnamese or English " +
            "(e.g. \"chuyện ngắn 5 phút\", \"nghe chuyện ngắn thôi\", \"chuyện dài 15 phút\", \"chuyện 30 phút\", \"chuyện dài đầy đủ\", " +
            "\"short story 5 minutes\", \"full story 10 to 30 minutes\", \"longer story\").",
        parameters = listOf(
            LiveToolParam(
                name = "duration",
                type = LiveToolParam.Type.STRING,
                description = "Target story duration: SHORT_5_MIN (truyện ngắn ~5 phút) or FULL_10_TO_30_MIN (truyện dài 10-30 phút)."
            )
        )
    )

    val setMultiVoiceTool = LiveTool(
        name = SET_MULTI_VOICE_FUNCTION,
        description = "Enables or disables multi-voice audio drama mode for storytelling when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật lồng tiếng đa giọng\", \"kể nhiều giọng\", \"kịch truyền thanh đa vai\", \"tắt đa giọng\", \"kể một giọng thôi\", " +
            "\"enable multi-voice drama\", \"single voice only\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True for multi-voice dramatic acting (differentiated voice tones/pitch/styles per character), false for single narrator voice."
            )
        )
    )

    val setPronunciationStrictnessTool = LiveTool(
        name = SET_PRONUNCIATION_STRICTNESS_FUNCTION,
        description = "Changes pronunciation grading strictness during repeat-after-me practice when requested in Vietnamese or English " +
            "(e.g. \"chấm theo cấp độ\", \"chấm tự động\", \"chấm như A1/A2\", \"chấm chuẩn B1/B2\", \"chấm chuẩn bản xứ\", \"chấm dễ hơn\", \"chấm khắt khe\", " +
            "\"auto strictness\", \"grade by level\", \"beginner strictness\", \"strict mode\", \"native standard\").",
        parameters = listOf(
            LiveToolParam(
                name = "strictness",
                type = LiveToolParam.Type.STRING,
                description = "Target strictness: AUTO (tự động theo cấp độ học viên), BEGINNER (A1 - rất nhẹ), ELEMENTARY (A2 - dễ chịu), " +
                    "PRE_INTERMEDIATE (A2–B1 - tiền trung cấp), INTERMEDIATE (B1 - tiêu chuẩn), UPPER_INTERMEDIATE (B2 - khắt khe), or ADVANCED (C1–C2 - chuẩn bản xứ)."
            )
        )
    )

    val setBargeInTool = LiveTool(
        name = SET_BARGE_IN_FUNCTION,
        description = "Enables or disables interruption / barge-in mode so the learner can speak over the AI " +
            "(e.g. \"bật ngắt lời\", \"cho phép ngắt lời\", \"tắt ngắt lời\", \"đừng ngắt lời\", \"enable barge-in\", \"disable interruption\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to allow learner to interrupt AI speech, false to require waiting until AI finishes speaking."
            )
        )
    )

    val setVoiceTool = LiveTool(
        name = SET_VOICE_FUNCTION,
        description = "Changes preferred AI voice for the conversation or enables random voice when requested in Vietnamese or English " +
            "(e.g. \"đổi giọng nam\", \"đổi giọng nữ\", \"đổi sang giọng Puck\", \"giọng Charon\", \"giọng Kore\", \"chọn giọng ngẫu nhiên\", \"change voice to Aoede\", \"random voice\").",
        parameters = listOf(
            LiveToolParam(
                name = "voice",
                type = LiveToolParam.Type.STRING,
                description = "Target AI voice name: KORE (nữ rõ ràng), AOEDE (nữ nhẹ nhàng), PUCK (nam vui vẻ), CHARON (nam trầm ấm), or RANDOM."
            )
        )
    )

    val setRandomVoiceTool = LiveTool(
        name = SET_RANDOM_VOICE_FUNCTION,
        description = "Enables or disables random AI voice selection for each lesson when requested by the learner in Vietnamese or English " +
            "(e.g. \"chọn giọng ngẫu nhiên\", \"bật giọng ngẫu nhiên\", \"tắt giọng ngẫu nhiên\", \"đổi giọng ngẫu nhiên\", \"mỗi bài một giọng\", " +
            "\"switch to random voice\", \"turn on random voice\", \"randomize voices\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to automatically pick a random AI voice for each lesson, false to keep a fixed voice."
            )
        )
    )

    val nextStoryTool = LiveTool(
        name = NEXT_STORY_FUNCTION,
        description = "Skips the current story and switches to a different story when the learner asks (e.g. \"next\", \"đổi chuyện khác\", \"bỏ qua\", \"chuyện khác đi\", \"chán quá đổi chuyện\", \"skip story\", \"next story\").",
        parameters = listOf(
            LiveToolParam(
                name = "reason",
                type = LiveToolParam.Type.STRING,
                description = "Optional reason for skipping (e.g. \"disliked\", \"boring\", \"want_different_topic\").",
                optional = true
            )
        )
    )

    val replayStoryTool = LiveTool(
        name = REPLAY_STORY_FUNCTION,
        description = "Restarts or replays the current story from the beginning when the learner requests (e.g. \"kể lại từ đầu\", \"kể lại chuyện vừa rồi\", \"nghe lại chuyện này\", \"replay story\", \"restart story\").",
        parameters = emptyList()
    )

    val resumeStoryTool = LiveTool(
        name = RESUME_STORY_FUNCTION,
        description = "Resumes an unfinished story from the previous listening session when requested by the learner in Vietnamese or English " +
            "(e.g. \"tiếp tục câu chuyện\", \"kể tiếp chuyện hôm trước\", \"bật tiếp chuyện nghe dở\", \"kể tiếp\", " +
            "\"resume story\", \"continue last story\", \"pick up where we left off\").",
        parameters = emptyList()
    )

    val skipDrillSentenceTool = LiveTool(
        name = SKIP_DRILL_SENTENCE_FUNCTION,
        description = "Skips the current pronunciation/shadowing sentence and moves to the next sentence when requested by the learner in Vietnamese or English " +
            "(e.g. \"skip\", \"next sentence\", \"bỏ qua\", \"câu tiếp theo\", \"câu khác đi\").",
        parameters = listOf(
            LiveToolParam(
                name = "reason",
                type = LiveToolParam.Type.STRING,
                description = "Why the sentence was skipped (e.g. \"too_hard\", \"learner_requested\").",
                optional = true
            )
        )
    )

    val allTools: List<LiveTool> = listOf(
        setDifficultyLevelTool,
        setVietnameseHelpTool,
        setAppLanguageTool,
        setStorytellingStyleTool,
        setStoryDurationTool,
        setMultiVoiceTool,
        setPronunciationStrictnessTool,
        setBargeInTool,
        setVoiceTool,
        setRandomVoiceTool,
        nextStoryTool,
        replayStoryTool,
        resumeStoryTool,
        skipDrillSentenceTool,
        applyLevelRecommendationTool,
        setAdaptiveLevelTool,
        setDrillSentenceLengthTool,
        setDrillCategoryTool,
        setAiVolumeTool
    )

    /**
     * Resolves a volume argument from Gemini or text commands.
     * Supports exact percentages ("50%", "70"), numbers, and relative keywords ("softer", "louder", "nhỏ lại", "to lên").
     */
    fun parseVolume(value: String?, currentVolume: Int = 80): Int? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val normalized = TopicManager.normalize(trimmed)

        // Max / Min checks first
        if (normalized.contains("max") || normalized.contains("toi da") || normalized.contains("het co")) {
            return 100
        }
        if (normalized.contains("min") || normalized.contains("nho nhat") || normalized.contains("be nhat")) {
            return 10
        }

        // Try extracting explicit percentage or number, e.g. "50%", "50", "volume 70"
        val numberMatch = Regex("(\\d{1,3})").find(trimmed)
        if (numberMatch != null) {
            val num = numberMatch.groupValues[1].toIntOrNull()
            if (num != null) return num.coerceIn(10, 100)
        }

        // Relative down / softer / quieter
        if (normalized.contains("nho") || normalized.contains("giam") || normalized.contains("be") ||
            normalized.contains("soft") || normalized.contains("quiet") || normalized.contains("down") ||
            normalized.contains("lower") || normalized.contains("ha")
        ) {
            return (currentVolume - 20).coerceAtLeast(10)
        }

        // Relative up / louder
        if (normalized.contains("to") || normalized.contains("tang") || normalized.contains("lon") ||
            normalized.contains("loud") || normalized.contains("up") || normalized.contains("higher") ||
            normalized.contains("increase")
        ) {
            return (currentVolume + 20).coerceAtMost(100)
        }

        return null
    }

    /**
     * Resolves a level argument from Gemini or text commands, tolerating CEFR codes (A1, A2, A2-B1, B1, B2, C1-C2),
     * English names, Vietnamese names and common variations.
     */
    fun parseLevel(value: String?): DifficultyLevel? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        DifficultyLevel.entries.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true)
        }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)
        return when {
            // Pre-intermediate first to avoid false-matching intermediate
            normalized.contains("pre intermediate") || normalized.contains("preintermediate") ||
                normalized.contains("tien trung cap") ||
                normalized.contains("so trung cap") ||
                normalized.contains("a2 b1") -> DifficultyLevel.PRE_INTERMEDIATE

            // Upper-intermediate first to avoid false-matching intermediate
            normalized.contains("upper intermediate") || normalized.contains("upperintermediate") ||
                normalized.contains("trung cap tren") ||
                normalized.contains("trung cap cao") ||
                normalized.contains("trung cap nang cao") ||
                (normalized.contains("b2") && !normalized.contains("b1 b2")) -> DifficultyLevel.UPPER_INTERMEDIATE

            // Elementary (A2, sơ cấp)
            normalized.contains("elementary") || normalized.contains("so cap") ||
                (normalized.contains("a2") && !normalized.contains("a1 a2") && !normalized.contains("a2 b1")) -> DifficultyLevel.ELEMENTARY

            // Beginner (A1, cơ bản, người mới bắt đầu, A1-A2)
            normalized.contains("beginner") || normalized.contains("co ban") ||
                normalized.contains("moi bat dau") ||
                normalized.contains("easy") || normalized.contains("de") ||
                normalized.contains("a1") -> DifficultyLevel.BEGINNER

            // Intermediate (B1, trung cấp, B1-B2)
            normalized.contains("intermediate") || normalized.contains("trung cap") ||
                normalized.contains("trung binh") || normalized.contains("medium") ||
                normalized.contains("b1") -> DifficultyLevel.INTERMEDIATE

            // Advanced (C1, C2, nâng cao, khó)
            normalized.contains("advanced") || normalized.contains("nang cao") ||
                normalized.contains("hard") || normalized.contains("difficult") || normalized.contains("kho") ||
                normalized.contains("c1") || normalized.contains("c2") -> DifficultyLevel.ADVANCED

            else -> null
        }
    }

    /**
     * Resolves storytelling style from tool args or transcript phrases.
     */
    fun parseStorytellingStyle(value: String?): StorytellingStyle? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return when {
            normalized.contains("continuous") || normalized.contains("lien mach") || normalized.contains("liền mạch") ||
                normalized.contains("podcast") || normalized.contains("mot mach") || normalized.contains("một mạch") ||
                normalized.contains("khong ngat") || normalized.contains("không ngắt") -> StorytellingStyle.CONTINUOUS

            normalized.contains("interactive") || normalized.contains("tuong tac") || normalized.contains("tương tác") ||
                normalized.contains("tung doan") || normalized.contains("từng đoạn") ||
                normalized.contains("hoi") || normalized.contains("hỏi") -> StorytellingStyle.INTERACTIVE

            else -> StorytellingStyle.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        }
    }

    /**
     * Resolves story duration from tool args or transcript phrases.
     */
    fun parseStoryDuration(value: String?): StoryDuration? {
        if (value.isNullOrBlank()) return null
        val normalized = TopicManager.normalize(value.trim())

        val hasExplicitLong = Regex("""\b(10|15|20|25|30)\b|\b(10|15|20|25|30)\s*(min|minute|phut|p\b)""").containsMatchIn(normalized) ||
            normalized.contains("full") || normalized.contains("dai") || normalized.contains("long") || normalized.contains("extended")

        val hasExplicit5 = Regex("""\b5\b|\b5\s*(min|minute|phut|p\b)|nam phut""").containsMatchIn(normalized)
        val hasExplicitShort = normalized.contains("short") || normalized.contains("ngan")

        return when {
            hasExplicitLong -> StoryDuration.FULL_10_TO_30_MIN
            hasExplicit5 || hasExplicitShort -> StoryDuration.SHORT_5_MIN
            else -> StoryDuration.fromStored(value)
        }
    }

    /**
     * Resolves multi-voice acting mode from tool args or transcript phrases.
     */
    fun parseMultiVoice(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        val normalized = TopicManager.normalize(value.trim())
        return when {
            // Check disable / single-voice phrases first to handle "tat da giong", "disable multi voice", etc.
            normalized.contains("tat") || normalized.contains("disable") || normalized.contains("off") ||
                normalized.contains("khong da giong") || normalized.contains("khong can da giong") ||
                normalized.contains("mot giong") || normalized.contains("don giong") ||
                normalized.contains("single voice") || normalized.contains("false") -> false

            normalized.contains("bat") || normalized.contains("enable") || normalized.contains("on") ||
                normalized.contains("da giong") || normalized.contains("nhieu giong") ||
                normalized.contains("kich truyen thanh") || normalized.contains("audio drama") ||
                normalized.contains("multi voice") || normalized.contains("nhap vai") ||
                normalized.contains("true") -> true

            else -> null
        }
    }

    /**
     * Resolves pronunciation strictness from tool args or transcript phrases.
     */
    fun parseStrictness(value: String?): PronunciationStrictness? {
        if (value.isNullOrBlank()) return null
        val normalized = TopicManager.normalize(value.trim())
        return when {
            // Auto / By level
            normalized.contains("auto") || normalized.contains("tu dong") ||
                normalized.contains("theo cap do") || normalized.contains("theo trinh do") ||
                normalized.contains("by level") || normalized.contains("adaptive") ||
                normalized.contains("theo level") || normalized.contains("mac dinh") -> PronunciationStrictness.AUTO

            // Pre-Intermediate (A2–B1, tiền trung cấp) - check before intermediate
            normalized.contains("pre intermediate") || normalized.contains("pre-intermediate") ||
                normalized.contains("tien trung cap") || normalized.contains("so trung cap") ||
                normalized.contains("a2 b1") || normalized.contains("a2-b1") -> PronunciationStrictness.PRE_INTERMEDIATE

            // Upper-Intermediate (B2, trung cấp trên) - check before intermediate
            normalized.contains("upper intermediate") || normalized.contains("upper-intermediate") ||
                normalized.contains("trung cap tren") || normalized.contains("trung cap cao") ||
                normalized.contains("trung cap nang cao") ||
                (normalized.contains("b2") && !normalized.contains("b1 b2")) -> PronunciationStrictness.UPPER_INTERMEDIATE

            // Advanced (C1–C2, chuẩn bản xứ, rất khắt khe)
            normalized.contains("advanced") || normalized.contains("c1") || normalized.contains("c2") ||
                normalized.contains("chuan ban xu") || normalized.contains("ban xu") ||
                normalized.contains("native") || normalized.contains("cuc ky khat khe") ||
                normalized.contains("gat") -> PronunciationStrictness.ADVANCED

            // Beginner (A1, rất nhẹ, người mới bắt đầu)
            normalized.contains("beginner") || normalized.contains("a1") ||
                normalized.contains("co ban") || normalized.contains("moi bat dau") ||
                normalized.contains("rat de") || normalized.contains("rat nhe") -> PronunciationStrictness.BEGINNER

            // Elementary (A2, dễ chịu, relaxed)
            normalized.contains("elementary") || normalized.contains("a2") ||
                normalized.contains("so cap") || normalized.contains("de tinh") ||
                normalized.contains("de chiu") || normalized.contains("relaxed") ||
                normalized.contains("lenient") || normalized.contains("nhe tay") ||
                normalized.contains("de hon") || normalized.contains("easier") -> PronunciationStrictness.ELEMENTARY

            // Strict / Khắt khe chung -> UPPER_INTERMEDIATE hoặc ADVANCED
            normalized.contains("strict") || normalized.contains("khat khe") ||
                normalized.contains("nghiem khac") || normalized.contains("harder") -> PronunciationStrictness.UPPER_INTERMEDIATE

            // Intermediate (B1, tiêu chuẩn, vừa phải)
            normalized.contains("intermediate") || normalized.contains("b1") ||
                normalized.contains("trung cap") || normalized.contains("tieu chuan") ||
                normalized.contains("standard") || normalized.contains("medium") ||
                normalized.contains("binh thuong") || normalized.contains("vua phai") ||
                normalized.contains("chuan") -> PronunciationStrictness.INTERMEDIATE

            else -> PronunciationStrictness.fromStored(value)
        }
    }

    /**
     * Resolves AI voice from tool args or transcript phrases.
     */
    fun parseVoice(value: String?): AiVoice? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return when {
            normalized.contains("charon") || normalized.contains("nam tram am") || normalized.contains("nam trầm ấm") ||
                normalized.contains("tram am") || normalized.contains("trầm ấm") ||
                normalized.contains("tram") || normalized.contains("trầm") -> AiVoice.CHARON

            normalized.contains("puck") || normalized.contains("nam vui ve") || normalized.contains("nam vui vẻ") ||
                normalized.contains("vui ve") || normalized.contains("vui vẻ") -> AiVoice.PUCK

            normalized.contains("kore") || normalized.contains("nu ro rang") || normalized.contains("nữ rõ ràng") ||
                normalized.contains("ro rang") || normalized.contains("rõ ràng") -> AiVoice.KORE

            normalized.contains("aoede") || normalized.contains("nu nhe nhang") || normalized.contains("nữ nhẹ nhàng") ||
                normalized.contains("nhe nhang") || normalized.contains("nhẹ nhàng") -> AiVoice.AOEDE

            normalized.contains("female") || normalized.contains("giong nu") || normalized.contains("giọng nữ") -> AiVoice.AOEDE
            normalized.contains("male") || normalized.contains("giong nam") || normalized.contains("giọng nam") -> AiVoice.PUCK

            else -> AiVoice.entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true) }
        }
    }

    /**
     * Checks if the value requests random voice selection.
     */
    fun isRandomVoiceRequested(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.trim().lowercase()
        return normalized.contains("random") ||
            normalized.contains("ngau nhien") || normalized.contains("ngẫu nhiên") ||
            normalized.contains("bat ky") || normalized.contains("bất kỳ") ||
            normalized.contains("tu do") || normalized.contains("tự do")
    }

    /**
     * Resolves AppLanguage from tool args or transcript phrases.
     */
    fun parseAppLanguage(value: String?): AppLanguage? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        AppLanguage.entries.firstOrNull {
            it.code.equals(trimmed, ignoreCase = true) ||
                it.name.equals(trimmed, ignoreCase = true) ||
                it.englishName.equals(trimmed, ignoreCase = true) ||
                it.nativeName.equals(trimmed, ignoreCase = true)
        }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)
        return when {
            normalized.contains("tieng anh") || normalized.contains("english") -> AppLanguage.ENGLISH
            normalized.contains("tieng viet") || normalized.contains("vietnamese") -> AppLanguage.VIETNAMESE
            normalized.contains("tieng nhat") || normalized.contains("japanese") || normalized.contains("nihongo") -> AppLanguage.JAPANESE
            normalized.contains("tieng han") || normalized.contains("korean") || normalized.contains("hangul") -> AppLanguage.KOREAN
            normalized.contains("tieng trung") || normalized.contains("chinese") || normalized.contains("mandarin") -> AppLanguage.CHINESE
            normalized.contains("tieng tay ban nha") || normalized.contains("spanish") || normalized.contains("espanol") -> AppLanguage.SPANISH
            normalized.contains("tieng phap") || normalized.contains("french") || normalized.contains("francais") -> AppLanguage.FRENCH
            normalized.contains("tieng duc") || normalized.contains("german") || normalized.contains("deutsch") -> AppLanguage.GERMAN
            normalized.contains("mac dinh") || normalized.contains("he thong") || normalized.contains("system") -> AppLanguage.SYSTEM
            else -> null
        }
    }

    /**
     * Resolves an accept/reject choice for level recommendation from tool args or transcript phrases.
     */
    fun parseApplyLevelRecommendation(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        val normalized = TopicManager.normalize(value.trim())

        return when {
            // Dismiss / Keep current level
            normalized.contains("khong") || normalized.contains("giu nguyen") || normalized.contains("giu") ||
                normalized.contains("keep") || normalized.contains("stay") || normalized.contains("reject") ||
                normalized.contains("de sau") || normalized.contains("cancel") || normalized.contains("false") ||
                normalized == "no" || normalized.split(" ").contains("no") || normalized.contains("nope") ||
                normalized.contains("not now") -> false

            // Accept / Change level / Level up / Level down
            normalized.contains("dong y") || normalized.contains("chap nhan") || normalized.contains("len cap") ||
                normalized.contains("giam cap") || normalized.contains("tang cap") || normalized.contains("doi cap") ||
                normalized.contains("accept") || normalized.contains("apply") || normalized.contains("level up") ||
                normalized.contains("level down") || normalized.contains("yes") || normalized.contains("true") -> true

            else -> null
        }
    }

    /**
     * Resolves enable/disable for adaptive level suggestions.
     */
    fun parseAdaptiveLevel(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        val normalized = TopicManager.normalize(value.trim())

        return when {
            normalized.contains("tat") || normalized.contains("disable") || normalized.contains("off") ||
                normalized.contains("khong goi y") || normalized.contains("false") -> false

            normalized.contains("bat") || normalized.contains("enable") || normalized.contains("on") ||
                normalized.contains("tu dong") || normalized.contains("goi y") || normalized.contains("true") -> true

            else -> null
        }
    }

    /**
     * Resolves drill sentence length preference from tool args or transcript phrases.
     */
    fun parseDrillSentenceLength(value: String?): DrillSentenceLength? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        DrillSentenceLength.entries.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true)
        }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)

        return when {
            // Auto on car / Driving mode
            normalized.contains("auto") || normalized.contains("tu dong") ||
                normalized.contains("khi lai xe") || normalized.contains("tren xe") ||
                normalized.contains("android auto") || normalized.contains("oto") ||
                normalized.contains("o to") || normalized.contains("car") ||
                normalized.contains("driving") -> DrillSentenceLength.AUTO_ON_CAR

            // Standard / longer
            normalized.contains("standard") || normalized.contains("tieu chuan") ||
                normalized.contains("binh thuong") || normalized.contains("dai hon") ||
                normalized.contains("longer") || normalized.contains("full") -> DrillSentenceLength.STANDARD

            // Short / concise
            normalized.contains("short") || normalized.contains("ngan") ||
                normalized.contains("gon") || normalized.contains("brief") ||
                normalized.contains("de nho") || normalized.contains("rut ngan") -> DrillSentenceLength.ALWAYS_SHORT

            else -> null
        }
    }

    /**
     * Resolves drill category preference from tool args or transcript phrases.
     */
    fun parseDrillCategory(value: String?): DrillCategory? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        DrillCategory.entries.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true) || it.id.equals(trimmed, ignoreCase = true)
        }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)
        return when {
            normalized.contains("loi") || normalized.contains("am duoi") || normalized.contains("ending sound") ||
                normalized.contains("pitfall") || normalized.contains("nguoi viet") || normalized.contains("vietnamese") ||
                normalized.contains("phat am chuan") || normalized.contains("phat am sai") -> DrillCategory.VIETNAMESE_PITFALLS

            normalized.contains("phan xa") || normalized.contains("giao tiep") || normalized.contains("reflex") ||
                normalized.contains("hang ngay") || normalized.contains("conversation") || normalized.contains("tro chuyen") -> DrillCategory.CONVERSATIONAL_REFLEX

            normalized.contains("lai") || normalized.contains("driving") || normalized.contains("xe") ||
                normalized.contains("oto") || normalized.contains("o to") ||
                normalized.contains("giao thong") || normalized.contains("traffic") || normalized.contains("car") ||
                normalized.contains("duong pho") -> DrillCategory.DRIVING_PHRASES

            normalized.contains("cong so") || normalized.contains("business") || normalized.contains("van phong") ||
                normalized.contains("lam viec") || normalized.contains("work") || normalized.contains("dong nghiep") ||
                normalized.contains("cuoc hop") || normalized.contains("meeting") -> DrillCategory.BUSINESS_WORK

            normalized.contains("du lich") || normalized.contains("travel") || normalized.contains("san bay") ||
                normalized.contains("airport") || normalized.contains("khach san") || normalized.contains("hotel") ||
                normalized.contains("doi song") || normalized.contains("nha hang") -> DrillCategory.TRAVEL_DAILY

            normalized.contains("tat ca") || normalized.contains("all") || normalized.contains("tong hop") ||
                normalized.contains("ngau nhien") || normalized.contains("da dang") -> DrillCategory.ALL

            else -> null
        }
    }
}

