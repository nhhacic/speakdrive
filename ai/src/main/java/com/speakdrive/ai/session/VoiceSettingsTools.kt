package com.speakdrive.ai.session

import com.speakdrive.ai.live.LiveTool
import com.speakdrive.ai.live.LiveToolParam
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.TopicManager

/**
 * Declares the Gemini Live tools that let learners adjust settings hands-free during a lesson.
 */
object VoiceSettingsTools {
    const val SWITCH_SESSION_MODE_FUNCTION = "switch_session_mode"
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
    const val REPEAT_DRILL_SENTENCE_FUNCTION = "repeat_drill_sentence"
    const val APPLY_LEVEL_RECOMMENDATION_FUNCTION = "apply_level_recommendation"
    const val SET_ADAPTIVE_LEVEL_FUNCTION = "set_adaptive_level"
    const val SET_DRILL_SENTENCE_LENGTH_FUNCTION = "set_drill_sentence_length"
    const val SET_DRILL_CATEGORY_FUNCTION = "set_drill_category"
    const val SET_AI_VOLUME_FUNCTION = "set_ai_volume"
    const val SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION = "set_auto_pause_when_unfocused"
    const val SET_TRANSLATION_SUBTITLES_FUNCTION = "set_translation_subtitles"
    const val SET_LEARNER_MEMORY_FUNCTION = "set_learner_memory"
    const val SET_PRACTICE_REMINDER_FUNCTION = "set_practice_reminder"
    const val SET_STREAK_FREEZE_FUNCTION = "set_streak_freeze"
    const val SET_OFFLINE_PRACTICE_FUNCTION = "set_offline_practice"
    const val SET_AUTO_START_IN_CAR_FUNCTION = "set_auto_start_in_car"
    const val SET_DAILY_GOAL_FUNCTION = "set_daily_goal"
    const val SET_AZURE_SCORING_FUNCTION = "set_azure_pronunciation_scoring"
    const val SET_SCREEN_AWAKE_FUNCTION = "set_screen_awake"
    const val SET_BETTER_PHRASING_FUNCTION = "set_better_phrasing"
    const val GET_WEEKLY_DIGEST_FUNCTION = "get_weekly_digest"
    const val SET_WEEKLY_DIGEST_FUNCTION = "set_weekly_digest"
    const val CREATE_CUSTOM_SCENARIO_FUNCTION = "create_custom_scenario"

    val switchSessionModeTool = LiveTool(
        name = SWITCH_SESSION_MODE_FUNCTION,
        description = "Switches the current learning mode hands-free when requested by the learner in Vietnamese or English " +
            "(e.g. \"luyện phát âm\", \"chuyển sang luyện phát âm\", \"tập phát âm\", \"luyện shadowing\", \"chuyển sang shadowing\", \"nhắc lại theo bạn\", \"luyện nói theo\", \"nói theo\", \"nhắc lại từng câu\", \"chuyển sang hội thoại\", \"nói chuyện tự do\", \"kể chuyện đi\", \"chuyển sang nghe kể chuyện\", \"nhập vai\", \"ôn từ vựng\", \"ôn lỗi sai\", \"ôn lại lỗi cũ\", " +
            "\"switch to shadowing\", \"practice pronunciation\", \"repeat after me\", \"shadowing mode\", \"switch to conversation\", \"free talk\", \"tell me a story\", \"story listening\", \"roleplay mode\", \"review vocabulary\", \"review my mistakes\").",
        parameters = listOf(
            LiveToolParam(
                name = "mode",
                type = LiveToolParam.Type.STRING,
                description = "Target learning mode: REPEAT_AFTER_ME (Shadowing / Pronunciation drill), FREE_TALK (Open conversational English), STORY_LISTENING (Audio story listening), ROLEPLAY (Scenario roleplay), VOCAB_REVIEW (Vocabulary flashcard drill), or MISTAKE_REVIEW (Practise the learner's own past mistakes again)."
            )
        )
    )

    val setLearnerMemoryTool = LiveTool(
        name = SET_LEARNER_MEMORY_FUNCTION,
        description = "Turns the AI's memory of the learner on or off (their past mistakes and the personal details they shared, used to personalise later lessons) " +
            "when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật ghi nhớ\", \"nhớ về tôi nhé\", \"bật trí nhớ AI\", \"tắt ghi nhớ\", \"đừng ghi nhớ gì về tôi\", \"quên tôi đi\", \"tắt trí nhớ AI\", " +
            "\"remember me\", \"turn on memory\", \"turn off memory\", \"don't remember anything about me\", \"forget about me\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to let the AI remember the learner across lessons, false to stop remembering."
            )
        )
    )

    val setPracticeReminderTool = LiveTool(
        name = SET_PRACTICE_REMINDER_FUNCTION,
        description = "Turns the daily practice reminder notification on or off, or sets its time, when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật nhắc học\", \"tắt nhắc học\", \"đừng nhắc tôi nữa\", \"nhắc tôi lúc 7 giờ sáng\", \"nhắc học lúc 8 giờ tối\", \"nhắc học tự động\", " +
            "\"turn on reminders\", \"turn off reminders\", \"remind me at 6:30 pm\", \"remind me automatically\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to send the daily reminder, false to stop it."
            ),
            LiveToolParam(
                name = "time",
                type = LiveToolParam.Type.STRING,
                description = "Optional reminder time in 24-hour HH:MM (e.g. \"07:00\", \"20:30\"), or \"auto\" to remind shortly before the learner's usual practice time. Leave out to keep the current time.",
                optional = true
            )
        )
    )

    val setStreakFreezeTool = LiveTool(
        name = SET_STREAK_FREEZE_FUNCTION,
        description = "Turns streak freezes on or off (a missed day uses a freeze earned by 7 practice days in a row instead of breaking the streak) when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật bảo toàn chuỗi\", \"tắt bảo toàn chuỗi\", \"giữ chuỗi khi tôi bận\", \"turn on streak freeze\", \"turn off streak freeze\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to use streak freezes, false to turn them off."
            )
        )
    )

    val setOfflinePracticeTool = LiveTool(
        name = SET_OFFLINE_PRACTICE_FUNCTION,
        description = "Turns offline practice on or off: when the network is lost during a lesson, the phone keeps a repeat-after-me practice going on its own until the connection is back. " +
            "Call it when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật luyện offline\", \"tắt luyện offline\", \"mất sóng thì đừng luyện\", \"turn on offline practice\", \"turn off offline practice\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to practise offline when the network is lost, false to just wait quietly."
            )
        )
    )

    val setAutoStartInCarTool = LiveTool(
        name = SET_AUTO_START_IN_CAR_FUNCTION,
        description = "Turns on or off starting a lesson by itself as soon as the phone connects to Android Auto, when requested by the learner in Vietnamese or English " +
            "(e.g. \"tắt tự động học khi lên xe\", \"đừng tự bắt đầu khi kết nối ô tô\", \"bật tự động bắt đầu khi lên xe\", " +
            "\"don't start lessons automatically in the car\", \"turn on auto start in the car\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to start a lesson automatically when Android Auto connects, false to wait for the learner."
            )
        )
    )

    val setDailyGoalTool = LiveTool(
        name = SET_DAILY_GOAL_FUNCTION,
        description = "Sets the learner's daily practice goal in minutes (5 to 60) when requested in Vietnamese or English " +
            "(e.g. \"đặt mục tiêu 20 phút mỗi ngày\", \"mục tiêu hàng ngày 30 phút\", \"tăng mục tiêu lên\", \"giảm mục tiêu\", " +
            "\"set my daily goal to 25 minutes\", \"increase my daily goal\").",
        parameters = listOf(
            LiveToolParam(
                name = "minutes",
                type = LiveToolParam.Type.STRING,
                description = "Minutes per day as a number (e.g. \"20\"), or \"more\" / \"less\" to change the current goal by 5 minutes."
            )
        )
    )

    val setAzureScoringTool = LiveTool(
        name = SET_AZURE_SCORING_FUNCTION,
        description = "Turns Microsoft Azure pronunciation scoring on or off for repeat-after-me drills, when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật chấm điểm Azure\", \"dùng Azure để chấm\", \"tắt Azure\", \"turn on Azure scoring\", \"stop using Azure\"). " +
            "Azure needs the learner's own key, entered in Settings.",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to also grade attempts with Azure, false to stop using it."
            )
        )
    )

    val setScreenAwakeTool = LiveTool(
        name = SET_SCREEN_AWAKE_FUNCTION,
        description = "Sets how long the phone screen stays on during a lesson (outside Android Auto), when requested by the learner in Vietnamese or English " +
            "(e.g. \"luôn sáng màn hình\", \"giữ màn hình sáng\", \"cho màn hình tự tắt theo máy\", \"tắt màn hình sau 1 phút\", " +
            "\"keep the screen on\", \"let the screen turn off\", \"turn the screen off after 30 seconds\").",
        parameters = listOf(
            LiveToolParam(
                name = "mode",
                type = LiveToolParam.Type.STRING,
                description = "ALWAYS_ON, FOLLOW_SYSTEM (the phone's own timeout), AFTER_30_SECONDS, AFTER_1_MINUTE, AFTER_2_MINUTES or AFTER_5_MINUTES without touching the screen."
            )
        )
    )

    val setTranslationSubtitlesTool = LiveTool(
        name = SET_TRANSLATION_SUBTITLES_FUNCTION,
        description = "Enables or disables translation subtitles under the repeat drill sentence on the car and phone screen when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật phụ đề dịch\", \"hiện phụ đề\", \"hiện bản dịch\", \"dịch câu nói\", \"bật dịch nghĩa\", \"hiển thị phụ đề\", \"tắt phụ đề dịch\", \"ẩn phụ đề\", \"tắt bản dịch\", \"ẩn bản dịch\", \"không cần dịch\", " +
            "\"turn on translation subtitles\", \"show subtitles\", \"show translation\", \"enable subtitles\", \"turn off subtitles\", \"hide subtitles\", \"hide translation\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to show translation subtitles below the drill sentence; false to hide them."
            )
        )
    )

    val setAutoPauseWhenUnfocusedTool = LiveTool(
        name = SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION,
        description = "Enables or disables auto-pausing the lesson when the app loses focus or screen turns off (outside Android Auto) when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật tự động tạm dừng khi tắt màn hình\", \"tự động tạm dừng khi rời app\", \"tắt tạm dừng khi tắt màn hình\", \"đừng tạm dừng khi thoát app\", \"bật tự động dừng\", \"tắt tự động pause\", " +
            "\"enable auto pause\", \"turn on auto pause\", \"pause when leaving app\", \"pause on screen off\", \"disable auto pause\", \"turn off auto pause\", \"don't pause when screen off\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to auto-pause when leaving the app or turning off screen outside Android Auto; false to keep playing in the background."
            )
        )
    )

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

    val repeatDrillSentenceTool = LiveTool(
        name = REPEAT_DRILL_SENTENCE_FUNCTION,
        description = "Repeats or replays the current pronunciation/shadowing sentence clearly when requested by the learner in Vietnamese or English " +
            "(e.g. \"repeat\", \"đọc lại\", \"nói lại\", \"nghe lại\", \"lặp lại câu này\", \"say again\", \"can you repeat\", \"one more time\", \"repeat sentence\").",
        parameters = emptyList()
    )

    val setBetterPhrasingTool = LiveTool(
        name = SET_BETTER_PHRASING_FUNCTION,
        description = "Turns natural phrasing suggestions on or off: when enabled, the AI occasionally offers natural, native-like phrasing upgrades when the learner says grammatically correct but simple sentences (e.g. \"very tired\" -> \"exhausted\"). " +
            "Call it when requested by the learner in Vietnamese or English " +
            "(e.g. \"bật gợi ý nói hay hơn\", \"gợi ý diễn đạt tự nhiên hơn\", \"tắt gợi ý diễn đạt\", \"đừng sửa cách nói\", \"turn on better phrasing\", \"suggest better phrasing\", \"turn off phrasing upgrades\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to occasionally suggest natural phrasing upgrades, false to disable phrasing suggestions."
            )
        )
    )

    val getWeeklyDigestTool = LiveTool(
        name = GET_WEEKLY_DIGEST_FUNCTION,
        description = "Reports the learner's weekly progress summary (minutes spoken, session count, average WPM speaking speed, and new words learned over the past 7 days) " +
            "when requested by the learner in Vietnamese or English " +
            "(e.g. \"báo cáo tuần\", \"tổng kết tuần qua\", \"tóm tắt tuần\", \"xem tiến độ tuần\", \"tuần qua tôi học thế nào\", " +
            "\"weekly digest\", \"weekly report\", \"how did i do last week\", \"summarize last week\").",
        parameters = emptyList()
    )

    val setWeeklyDigestTool = LiveTool(
        name = SET_WEEKLY_DIGEST_FUNCTION,
        description = "Turns the automatic weekly spoken progress digest on or off at the beginning of each week when requested in Vietnamese or English " +
            "(e.g. \"bật báo cáo tuần\", \"tắt báo cáo tuần\", \"đừng báo cáo tuần nữa\", \"tự động tóm tắt tuần\", " +
            "\"turn on weekly digest\", \"turn off weekly digest\", \"disable weekly summary\").",
        parameters = listOf(
            LiveToolParam(
                name = "enabled",
                type = LiveToolParam.Type.BOOLEAN,
                description = "True to hear a weekly progress digest at the start of each week, false to turn it off."
            )
        )
    )

    val createCustomScenarioTool = LiveTool(
        name = CREATE_CUSTOM_SCENARIO_FUNCTION,
        description = "Creates and saves a personalized custom roleplay scenario when the learner shares a real-world upcoming life event, job interview, travel, or negotiation " +
            "(e.g. \"tuần sau tôi đi phỏng vấn xin việc\", \"tôi sắp đi du lịch Nhật Bản\", \"tạo tình huống phỏng vấn cho tôi\", \"lưu tình huống này lại\", " +
            "\"i have a job interview next week\", \"create a custom scenario for me\", \"save this scenario\").",
        parameters = listOf(
            LiveToolParam(
                name = "title_vi",
                type = LiveToolParam.Type.STRING,
                description = "Short Vietnamese title for the scenario (e.g. \"Phỏng vấn xin việc logistics\")."
            ),
            LiveToolParam(
                name = "title_en",
                type = LiveToolParam.Type.STRING,
                description = "Short English title for the scenario (e.g. \"Logistics Job Interview\")."
            ),
            LiveToolParam(
                name = "ai_role",
                type = LiveToolParam.Type.STRING,
                description = "The character or professional role played by the AI (e.g. \"Senior Logistics Hiring Manager\")."
            ),
            LiveToolParam(
                name = "learner_role",
                type = LiveToolParam.Type.STRING,
                description = "The role played by the learner (e.g. \"Job candidate applying for warehouse supervisor\")."
            ),
            LiveToolParam(
                name = "custom_context",
                type = LiveToolParam.Type.STRING,
                description = "Detailed situational background and conversational context."
            ),
            LiveToolParam(
                name = "mission_objective",
                type = LiveToolParam.Type.STRING,
                description = "Optional key target or mission objective for the learner to accomplish.",
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
        repeatDrillSentenceTool,
        applyLevelRecommendationTool,
        setAdaptiveLevelTool,
        setDrillSentenceLengthTool,
        setDrillCategoryTool,
        setAiVolumeTool,
        setAutoPauseWhenUnfocusedTool,
        setTranslationSubtitlesTool,
        setLearnerMemoryTool,
        setPracticeReminderTool,
        setStreakFreezeTool,
        setOfflinePracticeTool,
        setAutoStartInCarTool,
        setDailyGoalTool,
        setAzureScoringTool,
        setScreenAwakeTool,
        setBetterPhrasingTool,
        getWeeklyDigestTool,
        setWeeklyDigestTool,
        createCustomScenarioTool,
        switchSessionModeTool
    )

    /** Changed in every kind of lesson. */
    private val coreTools = listOf(
        switchSessionModeTool,
        setDifficultyLevelTool,
        setVietnameseHelpTool,
        setAiVolumeTool,
        setVoiceTool,
        setBargeInTool,
        applyLevelRecommendationTool
    )

    private val drillTools = listOf(
        skipDrillSentenceTool,
        repeatDrillSentenceTool,
        setDrillSentenceLengthTool,
        setDrillCategoryTool,
        setPronunciationStrictnessTool,
        setAzureScoringTool,
        setTranslationSubtitlesTool
    )

    private val storyTools = listOf(
        setStorytellingStyleTool,
        setStoryDurationTool,
        setMultiVoiceTool,
        nextStoryTool,
        replayStoryTool,
        resumeStoryTool
    )

    private val conversationTools = listOf(setBetterPhrasingTool, setAdaptiveLevelTool)

    /**
     * The tools declared to the Live session of a [mode] lesson: what a learner changes during that
     * kind of lesson. Fewer declarations keep the session light (setup size, time to answer). Every
     * other setting in [allTools] (reminders, daily goal, app language, screen, memory...) is still
     * changed by voice: [VoiceCommandRecognizer] recognises it in the transcript and the engine applies
     * it straight away, because the model has no tool for it.
     */
    fun toolsFor(mode: SessionMode): List<LiveTool> = coreTools + when (mode) {
        SessionMode.REPEAT_AFTER_ME -> drillTools
        SessionMode.STORY_LISTENING -> storyTools
        SessionMode.FREE_TALK, SessionMode.ROLEPLAY -> conversationTools + createCustomScenarioTool
        SessionMode.IELTS_SPEAKING, SessionMode.VOCAB_REVIEW, SessionMode.MISTAKE_REVIEW -> conversationTools
    }

    /**
     * Resolves a daily goal argument: a number of minutes, or "more"/"less" (5 minutes) from [current].
     * The result is kept within 5–60 minutes.
     */
    fun parseDailyGoal(value: Any?, current: Int): Int? {
        val minutes = when (value) {
            is Number -> value.toInt()
            is String -> {
                val normalized = TopicManager.normalize(value.trim())
                Regex("(\\d{1,3})").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
                    ?: when {
                        hasWord(normalized, "more") || normalized.contains("increase") || normalized.contains("tang") ||
                            normalized.contains("nhieu hon") || normalized.contains("higher") -> current + GOAL_STEP_MINUTES
                        hasWord(normalized, "less") || normalized.contains("decrease") || normalized.contains("giam") ||
                            normalized.contains("it hon") || normalized.contains("lower") -> current - GOAL_STEP_MINUTES
                        else -> null
                    }
            }
            else -> null
        } ?: return null
        return minutes.coerceIn(LearnerSettings.MIN_DAILY_GOAL_MINUTES, LearnerSettings.MAX_DAILY_GOAL_MINUTES)
    }

    /** Resolves a screen-awake mode from its enum name or a spoken description. */
    fun parseScreenAwakeMode(value: String?): ScreenAwakeMode? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        ScreenAwakeMode.entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val normalized = TopicManager.normalize(trimmed)
        return when {
            Regex("\\b30\\b").containsMatchIn(normalized) -> ScreenAwakeMode.AFTER_30_SECONDS
            Regex("\\b5\\b").containsMatchIn(normalized) || normalized.contains("nam phut") || normalized.contains("five minute") -> ScreenAwakeMode.AFTER_5_MINUTES
            Regex("\\b2\\b").containsMatchIn(normalized) || normalized.contains("hai phut") || normalized.contains("two minute") -> ScreenAwakeMode.AFTER_2_MINUTES
            Regex("\\b1\\b").containsMatchIn(normalized) || normalized.contains("mot phut") || normalized.contains("one minute") -> ScreenAwakeMode.AFTER_1_MINUTE
            normalized.contains("system") || normalized.contains("theo may") || normalized.contains("tu tat") ||
                normalized.contains("follow") || normalized.contains("mac dinh") -> ScreenAwakeMode.FOLLOW_SYSTEM
            normalized.contains("always") || normalized.contains("luon") || normalized.contains("keep") ||
                normalized.contains("giu") -> ScreenAwakeMode.ALWAYS_ON
            else -> null
        }
    }

    private const val GOAL_STEP_MINUTES = 5

    /** True when [word] (one or more words) appears as whole words in [normalized] text. */
    private fun hasWord(normalized: String, word: String): Boolean = " $normalized ".contains(" $word ")

    /**
     * Resolves a translation subtitles toggle argument from Gemini Live tool calls or transcripts.
     */
    fun parseTranslationSubtitles(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> {
            val normalized = TopicManager.normalize(value.trim())
            when {
                normalized.contains("false") || hasWord(normalized, "tat") || normalized.contains("disable") ||
                    hasWord(normalized, "off") || hasWord(normalized, "an") || normalized.contains("hide") ||
                    hasWord(normalized, "khong") || hasWord(normalized, "dung") || hasWord(normalized, "dont") ||
                    hasWord(normalized, "don t") -> false
                normalized.contains("true") || hasWord(normalized, "bat") || normalized.contains("enable") ||
                    hasWord(normalized, "on") || hasWord(normalized, "hien") || normalized.contains("show") ||
                    hasWord(normalized, "co") -> true
                else -> null
            }
        }
        else -> null
    }

    /** Resolves the reminder time argument: "auto", "07:30", "7 pm", "19h". Null when it cannot be read. */
    fun parseReminderTime(value: Any?): Int? {
        val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = TopicManager.normalize(text)
        if (normalized.contains("auto") || normalized.contains("tu dong")) return LearnerSettings.REMINDER_AUTO
        return VoiceCommandParser.parseTimeOfDay(text)
    }

    /** Resolves the learner memory toggle from Gemini Live tool calls ("true"/"false", "bật"/"tắt", "on"/"off"). */
    fun parseLearnerMemory(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> {
            val normalized = TopicManager.normalize(value.trim())
            when {
                normalized.contains("false") || hasWord(normalized, "tat") || normalized.contains("disable") ||
                    hasWord(normalized, "off") || hasWord(normalized, "khong") || hasWord(normalized, "dung") ||
                    hasWord(normalized, "quen") || normalized.contains("forget") -> false
                normalized.contains("true") || hasWord(normalized, "bat") || normalized.contains("enable") ||
                    hasWord(normalized, "on") || hasWord(normalized, "nho") || normalized.contains("remember") -> true
                else -> null
            }
        }
        else -> null
    }

    /** Resolves the better phrasing toggle from Gemini Live tool calls. */
    fun parseBetterPhrasing(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> {
            val normalized = TopicManager.normalize(value.trim())
            when {
                normalized.contains("false") || hasWord(normalized, "tat") || normalized.contains("disable") ||
                    hasWord(normalized, "off") || hasWord(normalized, "khong") || hasWord(normalized, "dung") ||
                    normalized.contains("stop") -> false
                normalized.contains("true") || hasWord(normalized, "bat") || normalized.contains("enable") ||
                    hasWord(normalized, "on") || hasWord(normalized, "co") || normalized.contains("goi y") ||
                    normalized.contains("hay hon") || normalized.contains("tu nhien") ||
                    normalized.contains("better") || normalized.contains("natural") ||
                    normalized.contains("phras") -> true
                else -> null
            }
        }
        else -> null
    }

    /** Resolves the weekly digest toggle from Gemini Live tool calls. */
    fun parseWeeklyDigest(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> {
            val normalized = TopicManager.normalize(value.trim())
            when {
                normalized.contains("false") || hasWord(normalized, "tat") || normalized.contains("disable") ||
                    hasWord(normalized, "off") || hasWord(normalized, "khong") || hasWord(normalized, "dung") ||
                    normalized.contains("stop") -> false
                normalized.contains("true") || hasWord(normalized, "bat") || normalized.contains("enable") ||
                    hasWord(normalized, "on") || hasWord(normalized, "co") -> true
                else -> null
            }
        }
        else -> null
    }

    /**
     * Resolves an auto-pause when unfocused/screen off toggle argument from Gemini Live tool calls or transcripts.
     */
    fun parseAutoPauseWhenUnfocused(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> {
            val normalized = TopicManager.normalize(value.trim())
            when {
                normalized.contains("false") || hasWord(normalized, "tat") || normalized.contains("disable") ||
                    hasWord(normalized, "off") || hasWord(normalized, "khong") || hasWord(normalized, "dung") ||
                    hasWord(normalized, "dont") || hasWord(normalized, "don t") -> false
                normalized.contains("true") || hasWord(normalized, "bat") || normalized.contains("enable") ||
                    hasWord(normalized, "on") || hasWord(normalized, "co") -> true
                else -> null
            }
        }
        else -> null
    }

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
        if (hasWord(normalized, "nho") || normalized.contains("giam") || hasWord(normalized, "be") ||
            normalized.contains("soft") || normalized.contains("quiet") || normalized.contains("down") ||
            normalized.contains("lower") || hasWord(normalized, "ha")
        ) {
            return (currentVolume - 20).coerceAtLeast(10)
        }

        // Relative up / louder
        if (hasWord(normalized, "to") || normalized.contains("tang") || hasWord(normalized, "lon") ||
            normalized.contains("loud") || hasWord(normalized, "up") || normalized.contains("higher") ||
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
    fun parseLevel(value: String?, currentLevel: DifficultyLevel? = null): DifficultyLevel? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        DifficultyLevel.entries.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true)
        }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)
        // Relative requests ("harder", "dễ hơn") step from the current level when it is known.
        if (currentLevel != null) {
            if (normalized.contains("harder") || normalized.contains("kho hon") || normalized.contains("more difficult") ||
                normalized.contains("higher") || normalized.contains("nang len") || normalized.contains("tang cap")
            ) return currentLevel.nextLevel() ?: currentLevel
            if (normalized.contains("easier") || normalized.contains("de hon") || normalized.contains("simpler") ||
                normalized.contains("lower") || normalized.contains("ha cap") || normalized.contains("giam cap")
            ) return currentLevel.previousLevel() ?: currentLevel
        }
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
                normalized.contains("easy") || hasWord(normalized, "de") ||
                normalized.contains("a1") -> DifficultyLevel.BEGINNER

            // Intermediate (B1, trung cấp, B1-B2)
            normalized.contains("intermediate") || normalized.contains("trung cap") ||
                normalized.contains("trung binh") || normalized.contains("medium") ||
                normalized.contains("b1") -> DifficultyLevel.INTERMEDIATE

            // Advanced (C1, C2, nâng cao, khó)
            normalized.contains("advanced") || normalized.contains("nang cao") ||
                normalized.contains("hard") || normalized.contains("difficult") || hasWord(normalized, "kho") ||
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
                normalized.contains("tram am") || normalized.contains("trầm ấm") -> AiVoice.CHARON

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

            hasWord(normalized, "lai") || normalized.contains("driving") || hasWord(normalized, "xe") ||
                normalized.contains("oto") || normalized.contains("o to") ||
                normalized.contains("giao thong") || normalized.contains("traffic") || hasWord(normalized, "car") ||
                normalized.contains("duong pho") -> DrillCategory.DRIVING_PHRASES

            normalized.contains("cong so") || normalized.contains("business") || normalized.contains("van phong") ||
                normalized.contains("lam viec") || normalized.contains("work") || normalized.contains("dong nghiep") ||
                normalized.contains("cuoc hop") || normalized.contains("meeting") -> DrillCategory.BUSINESS_WORK

            normalized.contains("du lich") || normalized.contains("travel") || normalized.contains("san bay") ||
                normalized.contains("airport") || normalized.contains("khach san") || normalized.contains("hotel") ||
                normalized.contains("doi song") || normalized.contains("nha hang") -> DrillCategory.TRAVEL_DAILY

            normalized.contains("tat ca") || hasWord(normalized, "all") || normalized.contains("tong hop") ||
                normalized.contains("ngau nhien") || normalized.contains("da dang") ||
                normalized.contains("phong phu") || normalized.contains("variety") -> DrillCategory.ALL

            else -> null
        }
    }

    /**
     * Resolves session mode preference from tool args or transcript phrases.
     */
    fun parseSessionMode(value: String?): SessionMode? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        SessionMode.entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

        val normalized = TopicManager.normalize(trimmed)
        return when {
            normalized.contains("shadowing") || normalized.contains("phat am") ||
                normalized.contains("nhac lai") || normalized.contains("doc theo") ||
                normalized.contains("noi theo") || normalized.contains("cau ngan") ||
                normalized.contains("drill") || normalized.contains("pronunciation") ||
                normalized.contains("repeat") -> SessionMode.REPEAT_AFTER_ME

            normalized.contains("hoi thoai") || normalized.contains("tro chuyen") ||
                normalized.contains("noi chuyen") || normalized.contains("free talk") ||
                normalized.contains("conversation") || normalized.contains("talk") -> SessionMode.FREE_TALK

            normalized.contains("ke chuyen") || normalized.contains("nghe chuyen") ||
                normalized.contains("truyen") || normalized.contains("story") ||
                normalized.contains("podcast") -> SessionMode.STORY_LISTENING

            normalized.contains("nhap vai") || normalized.contains("dong vai") ||
                normalized.contains("roleplay") || normalized.contains("role play") ||
                normalized.contains("tinh huong") || normalized.contains("scenario") -> SessionMode.ROLEPLAY

            normalized.contains("loi sai") || normalized.contains("loi cu") ||
                normalized.contains("mistake") || normalized.contains("on loi") -> SessionMode.MISTAKE_REVIEW

            normalized.contains("on tu") || normalized.contains("tu vung") ||
                normalized.contains("vocab") || normalized.contains("word") ||
                normalized.contains("tu moi") -> SessionMode.VOCAB_REVIEW

            normalized.contains("ielts") || normalized.contains("luyen thi") ||
                normalized.contains("speaking test") -> SessionMode.IELTS_SPEAKING

            else -> null
        }
    }
}


