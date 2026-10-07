package com.speakdrive.ai.session

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.StorytellingStyle

/**
 * Recognises in-lesson voice commands (difficulty change, Vietnamese help, app language, storytelling control) from speech transcripts.
 * Serves as a reliable client-side safety net when Gemini does not invoke the function tool directly.
 */
object VoiceCommandParser {

    enum class VocabStudyIntent {
        REVIEW_ALL,
        PRONUNCIATION,
        SENTENCE_MAKING
    }

    /**
     * Parses an utterance for a request to practice vocabulary, pronunciation of words, or sentence making.
     * Returns the target [VocabStudyIntent] if detected, or null.
     */
    fun parseVocabStudyCommand(utterance: String): VocabStudyIntent? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, listOf(
            "luyen phat am tu vung", "phat am tu vung", "luyen phat am tu moi", "phat am tu moi",
            "vocab pronunciation", "pronounce vocabulary", "word pronunciation", "pronounce words"
        ))) {
            return VocabStudyIntent.PRONUNCIATION
        }

        if (containsAny(q, listOf(
            "luyen dat cau", "tap dat cau", "thu thach dat cau", "dat cau tu vung", "dat cau voi tu",
            "sentence making", "sentence practice", "practice making sentences", "sentence challenge", "make sentences"
        ))) {
            return VocabStudyIntent.SENTENCE_MAKING
        }

        if (containsAny(q, listOf(
            "so tu vung", "hoc tu vung", "luyen tu vung", "on tu vung", "on tap tu vung", "hoc tu moi", "on tu moi",
            "review vocabulary", "practice vocabulary", "study vocabulary", "vocabulary notebook", "review words"
        ))) {
            return VocabStudyIntent.REVIEW_ALL
        }

        return null
    }

    /**
     * Parses an utterance for a request to adjust AI speech volume.
     * Supports relative adjustments (nói nhỏ lại, giảm âm lượng, nói to lên, louder, softer)
     * and explicit percentage adjustments (âm lượng 50%, volume 70%).
     */
    fun parseVolumeCommand(utterance: String, currentVolume: Int = 80): Int? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        val hasVolumeWord = containsAny(q, listOf(
            "am luong", "volume", "tieng", "tieng noi", "giong noi", "sound"
        ))

        // Check explicit number first (e.g. "am luong 50%", "volume 70", "cho am luong 60")
        if (hasVolumeWord) {
            val numberMatch = Regex("(\\d{1,3})").find(utterance)
            if (numberMatch != null) {
                val num = numberMatch.groupValues[1].toIntOrNull()
                if (num != null) return num.coerceIn(10, 100)
            }
        }

        // Max volume
        if (containsAny(q, listOf(
            "am luong toi da", "am luong max", "to het co", "max volume", "maximum volume"
        ))) {
            return 100
        }

        // Min volume
        if (containsAny(q, listOf(
            "am luong nho nhat", "am luong min", "nho het co", "minimum volume", "min volume"
        ))) {
            return 10
        }

        // Louder / volume up / nói to lên
        if (containsAny(q, listOf(
            "noi to len", "to len", "noi to hon", "to hon", "tang am luong", "cho to tieng", "cho tieng to len",
            "tieng to hon", "giong to hon", "lon hon", "noi lon hon", "tang volume",
            "volume up", "speak louder", "louder", "turn up volume", "turn up the volume", "increase volume",
            "make it louder", "sound louder"
        ))) {
            return (currentVolume + 20).coerceAtMost(100)
        }

        // Softer / quieter / volume down / nói nhỏ lại
        if (containsAny(q, listOf(
            "noi nho lai", "nho lai", "noi nho thoi", "nho thoi", "nho tieng lai", "cho nho tieng", "cho nho lai",
            "giam am luong", "be lai", "noi be lai", "be thoi", "nho hon", "ha am luong", "giam volume",
            "volume down", "speak softer", "softer", "quieter", "turn down volume", "turn down the volume",
            "lower volume", "make it softer", "quieter please", "speak quiet"
        ))) {
            return (currentVolume - 20).coerceAtLeast(10)
        }

        return null
    }

    /**
     * Parses an utterance for a request to change the app interface language.
     * Supports Vietnamese and English commands.
     */
    fun parseAppLanguageCommand(utterance: String): AppLanguage? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        val hasLangContext = containsAny(q, listOf(
            "ngon ngu", "giao dien", "tieng", "language", "app language", "idioma", "langue", "sprache"
        ))
        val hasSwitchAction = containsAny(q, listOf(
            "doi", "chuyen", "cai", "chinh", "chon", "switch", "change", "set", "use", "make"
        ))

        if (hasLangContext || hasSwitchAction) {
            if (containsAny(q, listOf("tieng anh", "english", "to english", "sang tieng anh"))) return AppLanguage.ENGLISH
            if (containsAny(q, listOf("tieng viet", "vietnamese", "to vietnamese", "sang tieng viet"))) return AppLanguage.VIETNAMESE
            if (containsAny(q, listOf("tieng nhat", "japanese", "to japanese", "sang tieng nhat", "nihongo"))) return AppLanguage.JAPANESE
            if (containsAny(q, listOf("tieng han", "korean", "to korean", "sang tieng han", "hangul"))) return AppLanguage.KOREAN
            if (containsAny(q, listOf("tieng trung", "chinese", "to chinese", "sang tieng trung", "mandarin"))) return AppLanguage.CHINESE
            if (containsAny(q, listOf("tieng tay ban nha", "spanish", "to spanish", "sang tieng tay ban nha", "espanol"))) return AppLanguage.SPANISH
            if (containsAny(q, listOf("tieng phap", "french", "to french", "sang tieng phap", "francais"))) return AppLanguage.FRENCH
            if (containsAny(q, listOf("tieng duc", "german", "to german", "sang tieng duc", "deutsch"))) return AppLanguage.GERMAN
            if (containsAny(q, listOf("he thong", "mac dinh", "system default", "system language", "default language"))) return AppLanguage.SYSTEM
        }

        return null
    }

    /**
     * Parses an utterance for an explicit request to change difficulty level.
     * Supports relative adjustments ("kể dễ hơn", "chuyện khó quá", "make story easier")
     * and explicit level selections (A1, A2, B1, B2, C1).
     * Returns the target [DifficultyLevel] if a clear command is detected, null otherwise.
     */
    fun parseDifficultyCommand(utterance: String, currentLevel: DifficultyLevel? = null): DifficultyLevel? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null
        if (isPronunciationContext(q)) return null

        // Check relative adjustments: "khó hơn" (harder), "dễ hơn" (easier), "chuyện khó quá" (story too hard)...
        if (hasRelativeHarder(q)) {
            return if (currentLevel != null) currentLevel.nextLevel() else DifficultyLevel.ADVANCED
        }
        if (hasRelativeEasier(q)) {
            return if (currentLevel != null) currentLevel.previousLevel() else DifficultyLevel.BEGINNER
        }

        val hasAction = hasIntentAction(q)
        val hasLevelWord = hasLevelContext(q)

        // If the utterance has an action or level context, resolve the target level
        if (hasAction || hasLevelWord) {
            if (matchesPreIntermediate(q)) return DifficultyLevel.PRE_INTERMEDIATE
            if (matchesUpperIntermediate(q)) return DifficultyLevel.UPPER_INTERMEDIATE
            if (matchesElementary(q)) return DifficultyLevel.ELEMENTARY
            if (matchesIntermediate(q)) return DifficultyLevel.INTERMEDIATE
            if (matchesBeginner(q)) return DifficultyLevel.BEGINNER
            if (matchesAdvanced(q)) return DifficultyLevel.ADVANCED
        }

        return null
    }

    /**
     * Parses an utterance for a request to toggle Vietnamese explanations.
     * Returns true to enable, false to disable, null if not a command.
     */
    fun parseVietnameseHelpCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        // Commands to turn OFF Vietnamese / speak English only
        if (containsAny(q, OFF_VI_PHRASES) || isEnglishOnly(q)) return false

        // Commands to turn ON Vietnamese
        if (containsAny(q, ON_VI_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for a request to change the storytelling style.
     * @param inStorySession when true, "stop asking questions"-style requests also mean Continuous Podcast mode.
     */
    fun parseStorytellingStyleCommand(utterance: String, inStorySession: Boolean = true): StorytellingStyle? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, CONTINUOUS_STORY_PHRASES)) return StorytellingStyle.CONTINUOUS
        if (inStorySession && containsAny(q, STORY_NO_QUESTION_PHRASES)) return StorytellingStyle.CONTINUOUS
        if (containsAny(q, INTERACTIVE_STORY_PHRASES)) return StorytellingStyle.INTERACTIVE

        return null
    }

    /**
     * Parses an utterance for a request to set story duration (5 min vs 10-30 min).
     */
    fun parseStoryDurationCommand(utterance: String): com.speakdrive.ai.model.StoryDuration? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, SHORT_STORY_PHRASES)) return com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN
        if (containsAny(q, FULL_STORY_PHRASES)) return com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN

        return null
    }

    /**
     * Parses an utterance for enabling or disabling multi-voice audio drama mode.
     */
    fun parseMultiVoiceCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, OFF_MULTI_VOICE_PHRASES)) return false
        if (containsAny(q, ON_MULTI_VOICE_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for a request to resume an unfinished story from previous session.
     */
    fun parseResumeStoryCommand(utterance: String): Boolean {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return false
        return containsAny(q, RESUME_STORY_PHRASES)
    }

    /**
     * Parses an utterance for a request to skip to the next story.
     */
    fun parseNextStoryCommand(utterance: String): Boolean {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return false
        if (q == "next" || q == "next please" || q == "tiep theo" || q == "tiep di" || q == "bo qua") return true
        return containsAny(q, NEXT_STORY_PHRASES)
    }

    /**
     * Parses an utterance for a request to skip to the next pronunciation/shadowing sentence.
     * Supports Vietnamese and English voice commands.
     */
    fun parseSkipDrillSentenceCommand(utterance: String): Boolean {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return false
        if (q == "skip" || q == "next" || q == "bo qua" || q == "tiep theo" || q == "cau khac") return true
        return containsAny(q, SKIP_DRILL_SENTENCE_PHRASES)
    }

    /**
     * Parses an utterance for a request to repeat/replay the current pronunciation/shadowing sentence.
     * Supports Vietnamese and English voice commands.
     */
    fun parseRepeatDrillSentenceCommand(utterance: String): Boolean {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return false
        if (q == "repeat" || q == "again" || q == "say again" || q == "repeat please" || q == "doc lai" || q == "noi lai" || q == "nghe lai" || q == "lap lai" || q == "nhac lai") return true
        return containsAny(q, REPEAT_DRILL_SENTENCE_PHRASES)
    }

    /**
     * Parses an utterance for a request to replay/restart the current story.
     */
    fun parseReplayStoryCommand(utterance: String): Boolean {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return false
        return containsAny(q, REPLAY_STORY_PHRASES)
    }

    /**
     * Parses an utterance for a request to adjust pronunciation grading strictness.
     */
    fun parsePronunciationStrictnessCommand(utterance: String): PronunciationStrictness? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, AUTO_PRONUNCIATION_PHRASES)) return PronunciationStrictness.AUTO
        if (containsAny(q, ADVANCED_PRONUNCIATION_PHRASES)) return PronunciationStrictness.ADVANCED
        if (containsAny(q, PRE_INTERMEDIATE_PRONUNCIATION_PHRASES)) return PronunciationStrictness.PRE_INTERMEDIATE
        if (containsAny(q, UPPER_INTERMEDIATE_PRONUNCIATION_PHRASES)) return PronunciationStrictness.UPPER_INTERMEDIATE
        if (containsAny(q, BEGINNER_PRONUNCIATION_PHRASES)) return PronunciationStrictness.BEGINNER
        if (containsAny(q, ELEMENTARY_PRONUNCIATION_PHRASES)) return PronunciationStrictness.ELEMENTARY
        if (containsAny(q, INTERMEDIATE_PRONUNCIATION_PHRASES)) return PronunciationStrictness.INTERMEDIATE

        return null
    }

    /**
     * Parses an utterance for enabling or disabling interruption / barge-in.
     */
    fun parseBargeInCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, OFF_BARGE_IN_PHRASES)) return false
        if (containsAny(q, ON_BARGE_IN_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for changing the AI voice.
     */
    fun parseVoiceCommand(utterance: String): AiVoice? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (q.contains("charon") || containsAny(q, listOf("giong charon", "voice charon", "nam tram am", "tram am", "nam tram"))) return AiVoice.CHARON
        if (q.contains("puck") || containsAny(q, listOf("giong puck", "voice puck", "nam vui ve", "vui ve", "nam vui"))) return AiVoice.PUCK
        if (q.contains("aoede") || containsAny(q, listOf("giong aoede", "voice aoede", "nu nhe nhang", "nhe nhang", "nu nhe"))) return AiVoice.AOEDE
        if (q.contains("kore") || containsAny(q, listOf("giong kore", "voice kore", "nu ro rang", "ro rang", "nu ro"))) return AiVoice.KORE

        if (containsAny(q, listOf("doi giong nam", "chuyen sang giong nam", "giong con trai", "giong nam", "male voice", "switch to male voice"))) return AiVoice.PUCK
        if (containsAny(q, listOf("doi giong nu", "chuyen sang giong nu", "giong con gai", "giong nu", "female voice", "switch to female voice"))) return AiVoice.AOEDE

        return null
    }

    /**
     * Parses an utterance for a request to enable or disable random AI voice selection for lessons.
     * Returns true to enable, false to disable, null if not a command.
     */
    fun parseRandomVoiceCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, OFF_RANDOM_VOICE_PHRASES)) return false
        if (containsAny(q, ON_RANDOM_VOICE_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for a request to accept (true) or keep/dismiss (false) an AI level recommendation.
     */
    fun parseApplyRecommendationCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, KEEP_LEVEL_PHRASES)) return false
        if (containsAny(q, ACCEPT_LEVEL_RECOMMENDATION_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for a request to enable or disable automatic adaptive level suggestions.
     */
    fun parseAdaptiveLevelCommand(utterance: String): Boolean? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, OFF_ADAPTIVE_LEVEL_PHRASES)) return false
        if (containsAny(q, ON_ADAPTIVE_LEVEL_PHRASES)) return true

        return null
    }

    /**
     * Parses an utterance for a request to change drill sentence length (e.g. short sentences while driving).
     */
    fun parseDrillSentenceLengthCommand(utterance: String): DrillSentenceLength? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        // Check Auto on car / Driving mode first
        if (containsAny(q, AUTO_CAR_DRILL_LENGTH_PHRASES)) return DrillSentenceLength.AUTO_ON_CAR

        // Check Standard / longer phrases
        if (containsAny(q, STANDARD_DRILL_LENGTH_PHRASES)) return DrillSentenceLength.STANDARD

        // Check Short drill phrases
        if (containsAny(q, SHORT_DRILL_LENGTH_PHRASES)) return DrillSentenceLength.ALWAYS_SHORT

        return null
    }

    /**
     * Parses an utterance for a request to change drill category (Vietnamese pitfalls, reflex, driving, business, travel, all).
     */
    fun parseDrillCategoryCommand(utterance: String): DrillCategory? {
        val q = TopicManager.normalize(utterance)
        if (q.isBlank()) return null

        if (containsAny(q, VIETNAMESE_PITFALLS_DRILL_PHRASES)) return DrillCategory.VIETNAMESE_PITFALLS
        if (containsAny(q, DRIVING_PHRASES_DRILL_PHRASES)) return DrillCategory.DRIVING_PHRASES
        if (containsAny(q, BUSINESS_WORK_DRILL_PHRASES)) return DrillCategory.BUSINESS_WORK
        if (containsAny(q, TRAVEL_DAILY_DRILL_PHRASES)) return DrillCategory.TRAVEL_DAILY
        if (containsAny(q, CONVERSATIONAL_REFLEX_DRILL_PHRASES)) return DrillCategory.CONVERSATIONAL_REFLEX
        if (containsAny(q, ALL_DRILL_PHRASES)) return DrillCategory.ALL

        return null
    }

    private fun hasIntentAction(q: String): Boolean =
        containsAny(q, listOf("chuyen", "doi", "chinh", "chon", "dat", "switch", "change", "set", "make"))

    private fun hasLevelContext(q: String): Boolean =
        containsAny(q, listOf("cap do", "do kho", "muc do", "muc", "level", "difficulty"))

    private fun hasRelativeHarder(q: String): Boolean =
        containsAny(q, listOf(
            "kho hon", "nang cao hon", "phuc tap hon", "harder", "more difficult", "make it harder", "more advanced",
            "ke kho hon", "ke nang cao hon", "ke chuyen nang cao", "ke chuyen kho hon", "ke chuyen phuc tap hon",
            "chuyen de qua", "truyen de qua", "chuyen don gian qua", "de qua", "qua de",
            "tang do kho", "tang cap do", "nang do kho",
            "make story harder", "make story more advanced", "tell a more advanced story", "harder story", "advanced story",
            "this story is too easy", "this story is too simple", "too easy", "increase difficulty"
        ))

    private fun hasRelativeEasier(q: String): Boolean =
        containsAny(q, listOf(
            "de hon", "don gian hon", "de hieu hon", "easier", "simpler", "make it easier", "make it simpler",
            "ke de hon", "ke don gian hon", "ke de hieu hon", "ke chuyen de hon", "ke chuyen don gian thoi",
            "chuyen kho qua", "truyen kho qua", "chuyen kho nghe qua", "kho qua", "qua kho",
            "ha do kho", "ha cap do", "giam do kho", "giam cap do",
            "make story easier", "make story simpler", "tell a simpler story", "simpler story", "easier story",
            "this story is too hard", "this story is too difficult", "too hard", "too difficult", "lower the difficulty"
        ))

    private fun matchesPreIntermediate(q: String): Boolean =
        containsAny(q, listOf(
            "pre intermediate", "pre-intermediate", "preintermediate",
            "tien trung cap", "so trung cap", "a2 b1", "a2-b1"
        ))

    private fun matchesUpperIntermediate(q: String): Boolean =
        containsAny(q, listOf(
            "upper intermediate", "upper-intermediate", "upperintermediate",
            "trung cap tren", "trung cap cao", "trung cap nang cao"
        )) || (containsAny(q, listOf("b2")) && !containsAny(q, listOf("b1 b2", "b1-b2")))

    private fun matchesElementary(q: String): Boolean =
        containsAny(q, listOf("elementary", "so cap")) ||
            (containsAny(q, listOf("a2")) && !containsAny(q, listOf("a1 a2", "a1-a2", "a2 b1", "a2-b1")))

    private fun matchesIntermediate(q: String): Boolean =
        containsAny(q, listOf("b1", "b1 b2", "b1-b2", "intermediate", "trung cap", "trung binh", "medium"))

    private fun matchesBeginner(q: String): Boolean =
        containsAny(q, listOf("a1", "a1 a2", "a1-a2", "beginner", "co ban", "moi bat dau", "easy", "basic"))

    private fun matchesAdvanced(q: String): Boolean =
        containsAny(q, listOf("c1", "c2", "c1 c2", "c1-c2", "advanced", "nang cao", "kho", "hard", "difficult"))

    private val ON_VI_PHRASES = listOf(
        "bat tieng viet", "mo tieng viet", "noi tieng viet", "giai thich tieng viet",
        "giai thich bang tieng viet", "ho tro tieng viet", "cho phep tieng viet",
        "turn on vietnamese", "enable vietnamese", "allow vietnamese", "speak vietnamese",
        "explain in vietnamese"
    )

    private val OFF_VI_PHRASES = listOf(
        "tat tieng viet", "dung noi tieng viet", "khong noi tieng viet", "ngung tieng viet",
        "turn off vietnamese", "disable vietnamese", "stop vietnamese", "no vietnamese"
    )

    private val CONTINUOUS_STORY_PHRASES = listOf(
        "ke lien mach", "chuyen sang ke lien mach", "che do lien mach", "chi ke chuyen thoi",
        "ke mot mach", "podcast mode", "che do podcast", "sang podcast", "kieu podcast", "dang podcast",
        "nhu podcast", "ke podcast", "che do pot cat", "sang pot cat", "continuous mode", "continuous storytelling",
        "just tell the story", "tell the story without stopping", "khong ngat quang",
        "ke lien tuc", "ke mot len", "tell it continuously", "switch to podcast"
    )

    /** Only treated as "switch to podcast" while a story is playing. */
    private val STORY_NO_QUESTION_PHRASES = listOf(
        "dung hoi nua", "dung hoi toi nua", "khong can hoi", "dung dung lai hoi", "nghe lien tuc",
        "stop asking questions", "stop asking me questions", "no more questions",
        "don t ask me questions", "do not ask me questions", "keep telling the story"
    )

    private val INTERACTIVE_STORY_PHRASES = listOf(
        "ke tuong tac", "che do tuong tac", "chuyen sang ke tuong tac", "hoi sau moi doan",
        "interactive mode", "interactive storytelling", "hoi tung doan", "hoi dap tung doan",
        "che do hoi dap tung doan", "tung doan"
    )

    private val NEXT_STORY_PHRASES = listOf(
        "next story", "doi chuyen khac", "chuyen khac", "chuyen khac di", "bo qua chuyen nay",
        "skip story", "chuyen khong hay", "chan qua doi chuyen", "ke chuyen khac",
        "different story", "another story", "next please", "tiep theo", "doi truyen khac", "truyen khac",
        "tell another story"
    )

    private val REPLAY_STORY_PHRASES = listOf(
        "ke lai tu dau", "ke lai chuyen nay", "ke lai chuyen vua roi", "nghe lai chuyen vua roi",
        "nghe lai tu dau", "replay story", "restart story", "play again", "tell it again from the beginning",
        "start over", "ke lai", "nghe lai cau chuyen nay", "nghe lai truyen nay", "bat lai chuyen vua nay",
        "start the story from beginning"
    )

    private val SHORT_STORY_PHRASES = listOf(
        "chuyen ngan 5 phut", "truyen ngan 5 phut", "chuyen ngan", "truyen ngan",
        "ke chuyen ngan", "nghe chuyen ngan", "chuyen 5 phut", "truyen 5 phut",
        "5 phut thoi", "nam phut thoi", "nam phut", "5 phut", "short story",
        "short story 5 minutes", "5 minute story", "5 minutes story", "brief story", "quick story"
    )

    private val FULL_STORY_PHRASES = listOf(
        "chuyen dai", "truyen dai", "ke chuyen dai", "nghe chuyen dai",
        "chuyen dai 15 phut", "chuyen 10 phut", "chuyen 15 phut", "chuyen 20 phut", "chuyen 30 phut",
        "truyen 10 phut", "truyen 15 phut", "truyen 20 phut", "truyen 30 phut",
        "chuyen day du", "truyen day du", "10 phut", "15 phut", "20 phut", "30 phut",
        "full story", "long story", "longer story", "extended story", "10 to 30 minutes",
        "full length story", "full length"
    )

    private val ON_MULTI_VOICE_PHRASES = listOf(
        "bat da giong", "mo da giong", "bat long tieng da giong", "ke da giong", "long tieng da giong",
        "kich truyen thanh", "nhieu giong doc", "nhieu giong", "nhap vai tung nhan vat", "doi giong theo nhan vat",
        "kich truyen thanh da vai", "audio drama", "multi voice", "enable multi voice", "turn on multi voice",
        "multi voice drama", "character voices"
    )

    private val OFF_MULTI_VOICE_PHRASES = listOf(
        "tat da giong", "tat long tieng da giong", "mot giong thoi", "don giong", "doc mot giong",
        "ke mot giong", "single voice", "disable multi voice", "turn off multi voice",
        "one voice", "narrator only", "just one voice"
    )

    private val RESUME_STORY_PHRASES = listOf(
        "tiep tuc cau chuyen", "tiep tuc truyen", "ke tiep chuyen hom truoc", "ke tiep truyen hom truoc",
        "bat tiep chuyen nghe do", "bat tiep truyen nghe do", "nghe tiep truyen do", "nghe tiep chuyen do",
        "ke tiep doan truoc", "ke tiep", "doc tiep", "nghe tiep", "bat tiep chuyen do", "tiep tuc chuyen do",
        "resume story", "continue story", "continue last story", "pick up where we left off",
        "resume unfinished story", "keep listening"
    )

    private val SKIP_DRILL_SENTENCE_PHRASES = listOf(
        "skip sentence", "skip this sentence", "skip this", "next sentence", "next one",
        "another sentence", "change sentence", "skip question", "next question",
        "bo qua", "bo qua cau nay", "bo qua di", "chuyen cau", "chuyen sang cau khac",
        "cau tiep theo", "cau ke tiep", "cau khac", "cau khac di", "doi cau", "doi cau khac",
        "qua cau khac", "sang cau khac", "chuyen cau khac", "doc cau khac", "luyen cau khac",
        "cau khac phong phu hon", "luyen cau phong phu hon", "cau phong phu hon", "cau da dang hon",
        "luyen cau da dang hon", "doi cau da dang hon", "doi cau moi", "cho cau moi", "tao cau moi",
        "cau ngau nhien", "cung cap cau khac", "cau moi", "doc cau moi",
        "more diverse sentences", "diverse sentences", "give me a different sentence",
        "give me another sentence", "fresh sentence", "new sentence", "variety of sentences"
    )

    private val REPEAT_DRILL_SENTENCE_PHRASES = listOf(
        "repeat sentence", "repeat this sentence", "repeat that", "repeat it", "repeat please",
        "say again", "say that again", "say it again", "can you repeat", "could you repeat",
        "read again", "read this sentence again", "one more time", "once more", "play again",
        "doc lai", "doc lai cau nay", "doc lai cau", "doc lai giup toi", "doc lai di",
        "noi lai", "noi lai cau nay", "noi lai cau", "noi lai giup toi", "noi lai di",
        "nghe lai", "nghe lai cau nay", "nghe lai cau", "cho nghe lai", "cho nghe lai cau nay",
        "lap lai cau nay", "lap lai cau", "nhac lai cau nay", "nhac lai cau", "lap lai giup toi", "nhac lai giup toi",
        "doc cham lai", "noi cham lai", "phat am lai", "phat am lai cau nay"
    )

    private val AUTO_PRONUNCIATION_PHRASES = listOf(
        "cham theo cap do", "cham theo trinh do", "cham tu dong", "do khat khe tu dong", "tu dong theo cap do",
        "tu dong theo trinh do", "cham theo level", "cham theo trinh do hoc vien", "cham theo cap do hoc vien",
        "auto strictness", "grade by level", "strictness by level", "automatic strictness", "adaptive strictness"
    )

    private val BEGINNER_PRONUNCIATION_PHRASES = listOf(
        "cham theo a1", "cham a1", "cham beginner", "cham muc a1", "cham khoi dau", "cham rat de", "cham rat nhe", "cham co ban",
        "a1 strictness", "beginner strictness", "grade as beginner", "grade as a1"
    )

    private val ELEMENTARY_PRONUNCIATION_PHRASES = listOf(
        "cham theo a2", "cham a2", "cham elementary", "cham muc a2", "cham so cap", "cham de hon", "cham de tinh", "cham de", "cham nhe tay", "de tinh hon", "de chiu hon",
        "cham phat am de hon", "cham phat am de tinh", "giam do kho cham phat am",
        "lenient pronunciation", "relaxed pronunciation", "make pronunciation easier", "easier pronunciation", "grade easier", "relaxed mode", "elementary strictness", "grade as a2", "grade as elementary"
    )

    private val PRE_INTERMEDIATE_PRONUNCIATION_PHRASES = listOf(
        "cham theo a2 b1", "cham a2 b1", "cham tien trung cap", "cham so trung cap", "cham pre intermediate", "cham muc tien trung cap",
        "pre-intermediate strictness", "pre intermediate strictness", "grade as pre-intermediate"
    )

    private val INTERMEDIATE_PRONUNCIATION_PHRASES = listOf(
        "cham theo b1", "cham b1", "cham intermediate", "cham trung cap", "cham tieu chuan", "cham binh thuong", "cham vua phai", "cham chuan",
        "cham phat am tieu chuan", "cham phat am binh thuong",
        "standard pronunciation", "normal pronunciation", "standard strictness", "standard mode", "intermediate strictness", "grade as intermediate", "grade as b1"
    )

    private val UPPER_INTERMEDIATE_PRONUNCIATION_PHRASES = listOf(
        "cham theo b2", "cham b2", "cham upper intermediate", "cham trung cap tren", "cham trung cap cao", "cham kho hon", "cham gat hon", "cham nghiem khac", "cham khat khe",
        "cham phat am kho hon", "cham phat am gat hon", "cham phat am nghiem khac", "khat khe hon",
        "upper intermediate strictness", "upper-intermediate strictness", "strict pronunciation", "grade strictly", "stricter pronunciation", "make pronunciation harder", "strict mode", "grade as upper-intermediate", "grade as b2"
    )

    private val ADVANCED_PRONUNCIATION_PHRASES = listOf(
        "cham theo c1", "cham theo c2", "cham c1", "cham c2", "cham advanced", "cham chuan ban xu", "cham ban xu", "cham cuc ky khat khe",
        "native pronunciation", "native standard", "advanced strictness", "grade as advanced", "grade as native", "c1 strictness", "c2 strictness"
    )

    private val ON_BARGE_IN_PHRASES = listOf(
        "bat ngat loi", "cho phep ngat loi", "mo ngat loi", "bat barge in", "cho phep cat ngang",
        "bat cat ngang", "bat che do ngat loi", "ngat loi ai", "bat ngat loi ai", "cho ngat loi",
        "cho phep ngat loi ai", "cho toi ngat loi", "mo barge in", "bat tinh nang ngat loi",
        "enable barge in", "turn on barge in", "allow interruption", "enable interruption",
        "turn on interruption", "allow barge in", "enable interrupt", "allow interrupt", "turn on interrupt"
    )

    private val OFF_BARGE_IN_PHRASES = listOf(
        "tat ngat loi", "khong cho ngat loi", "tat barge in", "tat cat ngang", "dung cat ngang",
        "khong ngat loi", "tat che do ngat loi", "tat ngat loi ai", "khong ngat loi ai", "dung ngat loi ai",
        "dung ngat loi", "tat tinh nang ngat loi", "khong cho cat ngang", "dung cat ngang nua",
        "disable barge in", "turn off barge in", "no interruption", "disable interruption",
        "turn off interruption", "disable interrupt", "turn off interrupt", "stop interruption"
    )

    private val ON_RANDOM_VOICE_PHRASES = listOf(
        "giong ngau nhien", "chon giong ngau nhien", "doi giong ngau nhien", "bat giong ngau nhien",
        "bat che do giong ngau nhien", "giong ngau nhien moi bai", "giong ngau nhien moi phien",
        "moi bai mot giong", "moi phien mot giong", "ngau nhien giong", "giong bat ky", "chon giong bat ky",
        "random voice", "pick a random voice", "switch to random voice", "use random voice",
        "enable random voice", "turn on random voice", "randomize voice", "randomize voices",
        "random voice mode", "different voice each lesson"
    )

    private val OFF_RANDOM_VOICE_PHRASES = listOf(
        "tat giong ngau nhien", "tat che do ngau nhien", "dung giong ngau nhien", "khong ngau nhien",
        "giu co dinh giong", "co dinh giong", "tat random voice",
        "turn off random voice", "disable random voice", "stop random voice", "fixed voice", "keep current voice"
    )

    private val ACCEPT_LEVEL_RECOMMENDATION_PHRASES = listOf(
        "dong y tang cap do", "dong y tang cap", "dong y len cap", "dong y giam cap",
        "dong y giam cap do", "chap nhan goi y", "dong y doi cap do", "dong y doi cap",
        "len cap di", "tang cap do di", "giam cap do di", "doi cap do moi", "ap dung goi y",
        "ap dung cap do moi", "chuyen cap do theo goi y", "dong y", "chap nhan",
        "accept recommendation", "apply recommendation", "level up", "level down",
        "accept level change", "yes change level", "yes level up", "agree to change level",
        "apply new level", "change to recommended level"
    )

    private val KEEP_LEVEL_PHRASES = listOf(
        "giu nguyen cap do", "giu nguyen cap", "giu cap do nay", "khong doi cap do",
        "khong can doi", "khong muon doi", "o lai cap do nay", "giu nguyen trinh do",
        "de sau", "khong can dau", "de sau nhe",
        "keep current level", "stay at current level", "do not change level", "keep this level",
        "no keep it", "keep it as is", "dismiss recommendation", "no thanks"
    )

    private val ON_ADAPTIVE_LEVEL_PHRASES = listOf(
        "bat tu dong goi y cap do", "bat goi y cap do", "bat cap do thich ung", "mo goi y cap do",
        "tu dong goi y cap do", "cho phep goi y cap do", "bat danh gia cap do",
        "enable adaptive level", "turn on adaptive level", "enable level recommendation",
        "turn on level recommendation", "adaptive level on"
    )

    private val OFF_ADAPTIVE_LEVEL_PHRASES = listOf(
        "tat tu dong goi y cap do", "tat goi y cap do", "tat cap do thich ung", "dung goi y cap do",
        "khong goi y cap do", "tat danh gia cap do",
        "disable adaptive level", "turn off adaptive level", "disable level recommendation",
        "turn off level recommendation", "adaptive level off"
    )

    private val AUTO_CAR_DRILL_LENGTH_PHRASES = listOf(
        "cau ngan khi lai xe", "rut ngan cau khi lai xe", "cau ngan tren xe", "rut ngan cau tren xe",
        "cau ngan tren oto", "cau ngan tren o to", "rut ngan cau tren oto", "rut ngan cau tren o to",
        "rut ngan cau khi ket noi oto", "rut ngan cau khi ket noi o to", "cau ngan android auto",
        "rut ngan cau android auto", "tu dong ngan khi lai xe", "tu dong cau ngan",
        "tu dong khi lai xe", "tu dong tren xe", "cau ngan tren xe hoi", "tu dong rut ngan khi lai xe",
        "auto short on car", "short sentences for driving", "short sentences while driving",
        "short repetition on car", "short drill on car", "auto short sentences",
        "short sentences android auto", "auto drill length", "auto sentence length"
    )

    private val SHORT_DRILL_LENGTH_PHRASES = listOf(
        "cau ngan thoi", "cau ngan hon", "cau ngan", "rut ngan cau", "rut ngan cau lai",
        "rut ngan cau lap lai", "rut ngan cau nhac lai", "cau nhac lai ngan", "cau nhac lai ngan hon",
        "cau nhac lai ngan thoi", "cau nhac lai ngan gon", "cau lap lai ngan", "cau lap lai ngan hon",
        "cau lap lai ngan thoi", "cau lap lai ngan gon", "noi cau ngan", "noi cau ngan thoi",
        "noi cau ngan hon", "doc cau ngan", "doc cau ngan thoi", "doc cau ngan hon",
        "luyen cau ngan", "che do cau ngan", "cau ngan gon", "cau de nho",
        "short sentences", "shorter sentences", "short sentence", "short sentences please",
        "make sentences shorter", "keep sentences short", "shorter drill sentences",
        "short drill sentences", "short repetition", "shorter repetition", "short phrases",
        "shorter phrases", "concise sentences", "easy to remember sentences"
    )

    private val STANDARD_DRILL_LENGTH_PHRASES = listOf(
        "cau dai hon", "cau dai", "cau tieu chuan", "do dai tieu chuan", "do dai cau tieu chuan",
        "cau binh thuong", "do dai binh thuong", "quay lai cau binh thuong", "tro lai cau tieu chuan",
        "tro ve cau tieu chuan", "tat cau ngan", "khong can rut ngan cau",
        "standard sentence length", "standard sentences", "normal sentence length", "normal sentences",
        "longer sentences", "longer drill sentences", "default sentence length", "standard drill length",
        "disable short sentences", "turn off short sentences"
    )

    private val VIETNAMESE_PITFALLS_DRILL_PHRASES = listOf(
        "loi phat am nguoi viet", "loi nguoi viet", "dac tri loi am nguoi viet", "loi am nguoi viet",
        "loi am", "am duoi", "luyen am duoi", "luyen loi nguoi viet", "ending sound", "ending sounds",
        "vietnamese pitfalls", "vietnamese mistakes", "vietnamese pronunciation", "practice vietnamese pitfalls",
        "pitfalls", "loi phat am"
    )

    private val DRIVING_PHRASES_DRILL_PHRASES = listOf(
        "tieng anh lai xe", "tieng anh khi lai xe", "cau lai xe", "chu de lai xe", "luyen tieng anh lai xe",
        "luyen cau lai xe", "lai xe an toan", "lai xe", "driving phrases", "driving english",
        "practice driving phrases", "english while driving", "car phrases", "driving commute"
    )

    private val BUSINESS_WORK_DRILL_PHRASES = listOf(
        "tieng anh cong so", "chu de cong so", "chu de van phong", "tieng anh van phong", "luyen cau cong so",
        "luyen tieng anh cong so", "tieng anh di lam", "cau di lam", "cong so", "kinh doanh", "lam viec",
        "business english", "work phrases", "office english", "business phrases", "practice business english", "business work"
    )

    private val TRAVEL_DAILY_DRILL_PHRASES = listOf(
        "tieng anh du lich", "chu de du lich", "cau du lich", "luyen cau du lich", "luyen tieng anh du lich",
        "du lich khach san", "du lich san bay", "du lich doi song", "du lich va doi song", "du lich",
        "travel english", "travel phrases", "tourism phrases", "practice travel phrases", "travel and daily", "travel daily"
    )

    private val CONVERSATIONAL_REFLEX_DRILL_PHRASES = listOf(
        "phan xa giao tiep", "phan xa hoi thoai", "luyen phan xa", "cau phan xa", "giao tiep hang ngay",
        "phan xa hang ngay", "chuyen sang phan xa", "phan xa", "giao tiep tu nhien",
        "conversational reflex", "daily reflex", "reflex practice", "conversation reflex", "practice reflex"
    )

    private val ALL_DRILL_PHRASES = listOf(
        "tat ca chu de", "chu de tong hop", "luyen tat ca", "tat ca cac chu de", "luyen ngau nhien",
        "ngau nhien chu de", "tat ca danh muc", "all categories", "all drill topics", "all topics",
        "mixed topics", "all drill categories",
        "tat ca the loai", "tat ca danh muc luyen", "luyen phong phu", "luyen da dang",
        "phong phu hon", "da dang hon", "luyen phong phu hon", "luyen tap phong phu hon",
        "muon phong phu hon", "tap phong phu hon", "luyen da dang hon", "tap da dang hon",
        "tong hop", "luyen tong hop", "chu de da dang", "more variety", "diverse practice", "more diverse"
    )

    private fun isPronunciationContext(q: String): Boolean =
        containsAny(q, listOf("cham", "phat am", "pronunciation", "grade", "cham phat am"))

    private fun isEnglishOnly(q: String): Boolean =
        containsAny(q, listOf("chi noi tieng anh", "tieng anh thoi", "chi dung tieng anh", "english only", "only english"))

    private fun containsAny(haystack: String, needles: List<String>): Boolean {
        val padded = " $haystack "
        return needles.any { needle ->
            padded.contains(" $needle ")
        }
    }

    /**
     * Parses commands requesting to enable or disable auto-pausing when the app loses focus or screen turns off.
     * Returns true to enable, false to disable, or null if no command was recognized.
     */
    fun parseAutoPauseWhenUnfocusedCommand(text: String): Boolean? {
        val q = TopicManager.normalize(text)

        // Disable phrases first
        val disablePhrases = listOf(
            "tat tu dong tam dung", "tat tam dung khi tat man hinh", "khong tam dung khi tat man hinh",
            "dung tam dung khi tat man hinh", "tat tam dung khi roi app", "dung tam dung khi roi app",
            "khong tam dung khi roi app", "tat tu dong dung", "dung tu dong dung",
            "tat tu dong pause", "dung tu dong pause", "tat pause khi tat man hinh",
            "disable auto pause", "turn off auto pause", "stop auto pause",
            "don't auto pause", "dont auto pause", "do not auto pause",
            "don't pause when screen off", "dont pause when screen off", "do not pause when screen off",
            "don't pause when screen locked", "dont pause when screen locked", "do not pause when screen locked",
            "don't pause when leaving app", "dont pause when leaving app", "do not pause when leaving app",
            "keep playing in background", "continue in background"
        ).map { TopicManager.normalize(it) }
        if (disablePhrases.any { q.contains(it) }) return false

        // Enable phrases
        val enablePhrases = listOf(
            "bat tu dong tam dung", "tu dong tam dung khi tat man hinh", "tam dung khi tat man hinh",
            "tam dung khi khoa man hinh", "tu dong tam dung khi khoa man hinh", "tu dong dung khi tat man hinh",
            "tu dong tam dung khi roi app", "tam dung khi roi app", "tam dung khi thoat app",
            "tam dung khi chuyen app", "tu dong dung khi thoat app", "tu dong pause khi tat man hinh",
            "tu dong pause khi roi app", "bat tu dong dung", "bat tu dong pause", "tu dong tam dung",
            "enable auto pause", "turn on auto pause", "auto pause on screen off",
            "pause when screen off", "pause when screen is off", "pause on screen off",
            "pause when screen locked", "pause when leaving app", "auto pause when leaving app",
            "pause when app unfocused", "auto pause when unfocused", "auto pause"
        ).map { TopicManager.normalize(it) }
        if (enablePhrases.any { q.contains(it) }) return true

        return null
    }

    /**
     * Parses commands requesting to enable or disable translation subtitles under the repeat drill sentence.
     * Returns true to enable, false to disable, or null if no command was recognized.
     */
    fun parseTranslationSubtitlesCommand(text: String): Boolean? {
        val q = TopicManager.normalize(text)

        // Disable / Hide phrases first
        val disablePhrases = listOf(
            "tat phu de dich", "tat phu de", "tat ban dich", "an ban dich", "an phu de",
            "khong can phu de", "khong can dich", "dung dich", "tat dich nghia", "an dich nghia",
            "tat phu de tieng viet", "khong hien phu de", "khong can hien phu de", "dung hien phu de",
            "dung hien ban dich", "an dong dich", "tat dong dich", "tat phu de dong",
            "turn off subtitles", "turn off subtitle", "disable subtitles", "hide subtitles",
            "hide subtitle", "turn off translation", "hide translation", "disable translation",
            "no subtitles", "without subtitles", "no translation", "don't show translation", "dont show translation"
        ).map { TopicManager.normalize(it) }
        if (disablePhrases.any { q.contains(it) }) return false

        // Enable / Show phrases
        val enablePhrases = listOf(
            "bat phu de dich", "bat phu de", "hien phu de", "hien ban dich", "bat ban dich",
            "dich cau noi", "dich nghia", "bat dich nghia", "hien dich nghia", "bat phu de tieng viet",
            "hien phu de tieng viet", "cho xem phu de", "cho xem ban dich", "hien phu de dich",
            "bat phu de dich nghia", "hien dong dich", "bat dong dich", "hien thi phu de",
            "hien thi ban dich", "dich sang tieng viet", "phu de tieng viet",
            "turn on subtitles", "turn on subtitle", "enable subtitles", "show subtitles",
            "show subtitle", "turn on translation", "show translation", "enable translation",
            "with subtitles", "display subtitles", "display translation", "show subtitle translation"
        ).map { TopicManager.normalize(it) }
        if (enablePhrases.any { q.contains(it) }) return true

        return null
    }
}
