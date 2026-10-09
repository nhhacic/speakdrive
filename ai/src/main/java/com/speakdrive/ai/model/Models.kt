package com.speakdrive.ai.model

import com.speakdrive.ai.pronunciation.PronunciationAttempt

/** Learner level. [cefr] is fed to the model; [labelVi] is shown in Vietnamese UI. */
enum class DifficultyLevel(
    val displayName: String,
    val labelVi: String,
    val cefr: String,
    val descriptionVi: String = "",
    val descriptionEn: String = ""
) {
    BEGINNER(
        "Beginner",
        "Người mới bắt đầu",
        "A1",
        "Làm quen câu chào hỏi, từ vựng rất cơ bản, nói chậm rãi từng câu ngắn.",
        "Basic greetings, essential vocabulary, slow and short sentences."
    ),
    ELEMENTARY(
        "Elementary",
        "Sơ cấp",
        "A2",
        "Giao tiếp câu đơn giản hàng ngày: gia đình, thói quen, mua sắm, chỉ đường.",
        "Everyday simple communication: family, routines, shopping, directions."
    ),
    PRE_INTERMEDIATE(
        "Pre-Intermediate",
        "Tiền trung cấp",
        "A2–B1",
        "Cầu nối quan trọng: tập ghép câu, diễn đạt trải nghiệm, lý do và dự định với tốc độ vừa phải.",
        "Sentence building, expressing experiences, reasons and plans at a moderate pace."
    ),
    INTERMEDIATE(
        "Intermediate",
        "Trung cấp",
        "B1",
        "Giao tiếp độc lập trong hầu hết tình huống thực tế, phản xạ đàm thoại linh hoạt.",
        "Independent communication in most real-life situations, flexible reflex."
    ),
    UPPER_INTERMEDIATE(
        "Upper-Intermediate",
        "Trung cấp trên",
        "B2",
        "Thảo luận chuyên sâu, tự tin chia sẻ quan điểm với vốn từ phong phú và cấu trúc tự nhiên.",
        "In-depth discussions, confident opinions with rich vocabulary and natural phrasing."
    ),
    ADVANCED(
        "Advanced",
        "Nâng cao",
        "C1–C2",
        "Sử dụng tiếng Anh lưu loát, tinh tế như người bản xứ trong mọi chủ đề.",
        "Fluent and nuanced English like a native speaker in any topic."
    );

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    fun nextLevel(): DifficultyLevel? = when (this) {
        BEGINNER -> ELEMENTARY
        ELEMENTARY -> PRE_INTERMEDIATE
        PRE_INTERMEDIATE -> INTERMEDIATE
        INTERMEDIATE -> UPPER_INTERMEDIATE
        UPPER_INTERMEDIATE -> ADVANCED
        ADVANCED -> null
    }

    fun previousLevel(): DifficultyLevel? = when (this) {
        ADVANCED -> UPPER_INTERMEDIATE
        UPPER_INTERMEDIATE -> INTERMEDIATE
        INTERMEDIATE -> PRE_INTERMEDIATE
        PRE_INTERMEDIATE -> ELEMENTARY
        ELEMENTARY -> BEGINNER
        BEGINNER -> null
    }

    companion object {
        /** Parses a stored value, tolerating enum names and display names. */
        fun fromStored(value: String?): DifficultyLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: INTERMEDIATE
    }
}

/** Strictness level when evaluating learner's pronunciation attempts. */
enum class PronunciationStrictness(
    val displayName: String,
    val labelVi: String,
    val descriptionVi: String,
    val descriptionEn: String = ""
) {
    AUTO(
        displayName = "Auto (By Level)",
        labelVi = "Theo cấp độ",
        descriptionVi = "Tự động áp dụng tiêu chí chấm phù hợp với trình độ học viên hiện tại của bạn",
        descriptionEn = "Automatically adapts grading criteria to your current learner level"
    ),
    BEGINNER(
        displayName = "Beginner (A1)",
        labelVi = "A1 - Rất nhẹ",
        descriptionVi = "Dung sai lớn, bỏ qua lỗi âm đuôi và phát âm vụng, tập trung phản xạ và độ tự tin",
        descriptionEn = "High tolerance, lenient on endings, focus on reflex and confidence"
    ),
    ELEMENTARY(
        displayName = "Elementary (A2)",
        labelVi = "A2 - Dễ chịu",
        descriptionVi = "Châm chước lỗi ngữ âm nhẹ, nhận diện rõ từ vựng hàng ngày cơ bản",
        descriptionEn = "Lenient on minor phonetics, requires clear everyday words"
    ),
    PRE_INTERMEDIATE(
        displayName = "Pre-Intermediate (A2–B1)",
        labelVi = "A2–B1 - Tiền trung cấp",
        descriptionVi = "Yêu cầu phát âm rõ từ vựng quen thuộc, bắt đầu chú ý các phụ âm chính",
        descriptionEn = "Clear everyday vocabulary, attention to main consonants"
    ),
    INTERMEDIATE(
        displayName = "Intermediate (B1)",
        labelVi = "B1 - Tiêu chuẩn",
        descriptionVi = "Cân bằng, yêu cầu phát âm rõ ràng từ và âm đuôi chính",
        descriptionEn = "Balanced, requires clear pronunciation of words and key ending sounds"
    ),
    UPPER_INTERMEDIATE(
        displayName = "Upper-Intermediate (B2)",
        labelVi = "B2 - Khắt khe",
        descriptionVi = "Khắt khe với âm cuối (-s, -ed), trọng âm từ rõ ràng và ngữ điệu tự nhiên",
        descriptionEn = "Strict on endings (-s, -ed), word stress and natural intonation"
    ),
    ADVANCED(
        displayName = "Advanced (C1–C2)",
        labelVi = "C1–C2 - Chuẩn bản xứ",
        descriptionVi = "Chấm chuẩn từng âm vị, âm đuôi và nối âm (chuẩn bản xứ)",
        descriptionEn = "Strict scoring on every phoneme, ending sound, and linking (native standard)"
    ),

    // Deprecated 3-level aliases for backward compatibility
    @Deprecated("Use ELEMENTARY instead", ReplaceWith("ELEMENTARY"))
    RELAXED(
        displayName = "Relaxed",
        labelVi = "Dễ chịu",
        descriptionVi = "Bỏ qua lỗi nhỏ, tập trung phản xạ và độ tự tin khi nói",
        descriptionEn = "Lenient on minor errors, focus on confidence and speaking reflex"
    ),
    @Deprecated("Use INTERMEDIATE instead", ReplaceWith("INTERMEDIATE"))
    STANDARD(
        displayName = "Standard",
        labelVi = "Tiêu chuẩn",
        descriptionVi = "Cân bằng, yêu cầu phát âm rõ ràng từ và âm đuôi chính",
        descriptionEn = "Balanced, requires clear pronunciation of words and key ending sounds"
    ),
    @Deprecated("Use ADVANCED instead", ReplaceWith("ADVANCED"))
    STRICT(
        displayName = "Strict",
        labelVi = "Khắt khe",
        descriptionVi = "Chấm chuẩn từng âm vị, âm đuôi và nối âm (chuẩn bản xứ)",
        descriptionEn = "Strict scoring on every phoneme, ending sound, and linking (native standard)"
    );

    fun resolveForLevel(level: DifficultyLevel): PronunciationStrictness = when (this) {
        AUTO -> when (level) {
            DifficultyLevel.BEGINNER -> BEGINNER
            DifficultyLevel.ELEMENTARY -> ELEMENTARY
            DifficultyLevel.PRE_INTERMEDIATE -> PRE_INTERMEDIATE
            DifficultyLevel.INTERMEDIATE -> INTERMEDIATE
            DifficultyLevel.UPPER_INTERMEDIATE -> UPPER_INTERMEDIATE
            DifficultyLevel.ADVANCED -> ADVANCED
        }
        RELAXED -> ELEMENTARY
        STANDARD -> INTERMEDIATE
        STRICT -> ADVANCED
        else -> this
    }

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        /** The entries shown to users in the UI (excluding deprecated aliases). */
        val userFacingEntries: List<PronunciationStrictness> = listOf(
            AUTO,
            BEGINNER,
            ELEMENTARY,
            PRE_INTERMEDIATE,
            INTERMEDIATE,
            UPPER_INTERMEDIATE,
            ADVANCED
        )

        fun fromStored(value: String?): PronunciationStrictness = when (value?.trim()?.uppercase()) {
            "AUTO", "BY_LEVEL", "LEVEL" -> AUTO
            "BEGINNER", "A1" -> BEGINNER
            "ELEMENTARY", "A2", "RELAXED" -> ELEMENTARY
            "PRE_INTERMEDIATE", "A2_B1", "A2-B1" -> PRE_INTERMEDIATE
            "INTERMEDIATE", "B1", "STANDARD" -> INTERMEDIATE
            "UPPER_INTERMEDIATE", "B2" -> UPPER_INTERMEDIATE
            "ADVANCED", "C1", "C2", "STRICT" -> ADVANCED
            else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: AUTO
        }
    }
}

/** Storytelling style when listening to AI short stories. */
enum class StorytellingStyle(
    val displayName: String,
    val labelVi: String,
    val descriptionVi: String,
    val descriptionEn: String = ""
) {
    INTERACTIVE(
        displayName = "Interactive",
        labelVi = "Tương tác từng đoạn",
        descriptionVi = "AI kể từng đoạn ngắn (~45–60s) và dừng lại hỏi gợi mở / kiểm tra độ hiểu",
        descriptionEn = "AI pauses after each section to ask engaging questions and check comprehension"
    ),
    CONTINUOUS(
        displayName = "Podcast (Continuous)",
        labelVi = "Podcast liền mạch",
        descriptionVi = "AI kể trọn vẹn toàn bộ câu chuyện từ đầu tới cuối không ngắt quãng",
        descriptionEn = "AI narrates the entire story seamlessly without interruptions (ideal for driving focus)"
    );

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(value: String?): StorytellingStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: INTERACTIVE
    }
}

/** Story duration preference: ~5-minute short stories or 10-30 minute extended stories. */
enum class StoryDuration(
    val displayName: String,
    val labelVi: String,
    val descriptionVi: String,
    val descriptionEn: String = ""
) {
    SHORT_5_MIN(
        displayName = "Short (~5 min)",
        labelVi = "Ngắn (~5 phút)",
        descriptionVi = "Câu chuyện ngắn gọn, súc tích khoảng 5 phút (~600–800 từ), lý tưởng cho chuyến đi ngắn.",
        descriptionEn = "Concise story (~5 min, 600–800 words), perfect for short commutes."
    ),
    FULL_10_TO_30_MIN(
        displayName = "Extended (10–30 min)",
        labelVi = "Dài (10–30 phút)",
        descriptionVi = "Tác phẩm đầy đủ, giàu kịch tính từ 10 đến 30 phút (~1,500–4,500 từ), hỗ trợ lưu và tiếp nối khi nghe dở.",
        descriptionEn = "Full dramatic stories from 10 to 30 min (1,500–4,500 words), supports bookmarking and resuming."
    );

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(value: String?): StoryDuration =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: FULL_10_TO_30_MIN
    }
}

/** Sentence length preference for repeat-after-me / pronunciation drills. */
enum class DrillSentenceLength(
    val displayName: String,
    val labelVi: String,
    val descriptionVi: String,
    val descriptionEn: String = ""
) {
    AUTO_ON_CAR(
        displayName = "Auto (Short on Android Auto)",
        labelVi = "Tự động khi lái xe",
        descriptionVi = "Tự động rút ngắn câu (5–8 từ) khi kết nối Android Auto để người lái xe dễ nhớ và an toàn",
        descriptionEn = "Automatically keeps repeat sentences short (5–8 words) on Android Auto for driving safety and easy recall"
    ),
    ALWAYS_SHORT(
        displayName = "Always Short (3–7 words)",
        labelVi = "Luôn ngắn gọn",
        descriptionVi = "Luôn giới hạn câu ngắn gọn (3–7 từ) trong mọi buổi luyện để giảm tải ghi nhớ",
        descriptionEn = "Always keeps sentences short (3–7 words) across all sessions to reduce memory load"
    ),
    STANDARD(
        displayName = "Standard (by Level)",
        labelVi = "Tiêu chuẩn theo cấp độ",
        descriptionVi = "Độ dài câu tăng dần theo cấp độ khó CEFR (lên đến 12–18 từ ở trình độ nâng cao)",
        descriptionEn = "Sentence length scales naturally with CEFR difficulty levels (up to 12–18 words at advanced levels)"
    );

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(value: String?): DrillSentenceLength =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: AUTO_ON_CAR
    }
}

/** Category filter for repeat-after-me pronunciation drills. */
enum class DrillCategory(
    val id: String,
    val displayName: String,
    val labelVi: String,
    val descriptionVi: String,
    val descriptionEn: String = ""
) {
    ALL(
        id = "all",
        displayName = "All Categories",
        labelVi = "Tất cả danh mục",
        descriptionVi = "Luyện tổng hợp mọi nhóm câu từ phản xạ đến lỗi ngữ âm",
        descriptionEn = "Comprehensive mix of all categories"
    ),
    VIETNAMESE_PITFALLS(
        id = "vietnamese_pitfalls",
        displayName = "Vietnamese Pitfalls",
        labelVi = "Đặc trị lỗi âm người Việt",
        descriptionVi = "Tập trung âm đuôi (-s, -ed, -t, -k), cặp âm dễ nhầm (/θ/, /ʃ/, /v/) và nối âm",
        descriptionEn = "Focus on final consonants, challenging consonant pairs and connected speech"
    ),
    CONVERSATIONAL_REFLEX(
        id = "conversational_reflex",
        displayName = "Conversational Reflex",
        labelVi = "Phản xạ giao tiếp tự nhiên",
        descriptionVi = "Các cụm từ cửa miệng, mẫu câu thông dụng người bản xứ dùng mỗi ngày",
        descriptionEn = "Common chunks, collocations and everyday native speaking expressions"
    ),
    DRIVING_PHRASES(
        id = "driving_phrases",
        displayName = "Driving & Commute",
        labelVi = "Tiếng Anh khi lái xe",
        descriptionVi = "Câu ngắn gọn (5–8 từ), nhịp điệu dứt khoát, an toàn và dễ nhớ khi lái xe",
        descriptionEn = "Short, punchy sentences (5–8 words) optimized for safe hands-free driving"
    ),
    BUSINESS_WORK(
        id = "business_work",
        displayName = "Business & Work",
        labelVi = "Công sở & Kinh doanh",
        descriptionVi = "Mẫu câu đàm phán, cuộc họp, tranh luận chuyên nghiệp tại nơi làm việc",
        descriptionEn = "High-impact professional phrases for meetings, negotiations and presentations"
    ),
    TRAVEL_DAILY(
        id = "travel_daily",
        displayName = "Travel & Daily Life",
        labelVi = "Du lịch & Đời sống",
        descriptionVi = "Mẫu câu du lịch, gọi món, hỏi đường và giải quyết tình huống phát sinh",
        descriptionEn = "Essential phrases for travel, dining, navigating and solving daily problems"
    );

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else displayName
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(value: String?): DrillCategory = when (value?.trim()?.lowercase()) {
            "vietnamese_pitfalls", "pitfalls", "loi_am", "phat_am", "am_duoi" -> VIETNAMESE_PITFALLS
            "conversational_reflex", "reflex", "phan_xa", "giao_tiep", "tu_nhien" -> CONVERSATIONAL_REFLEX
            "driving_phrases", "driving", "lai_xe", "giao_thong" -> DRIVING_PHRASES
            "business_work", "business", "work", "cong_viec", "kinh_doanh" -> BUSINESS_WORK
            "travel_daily", "travel", "du_lich", "hang_ngay" -> TRAVEL_DAILY
            else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.id.equals(value, ignoreCase = true) }
                ?: ALL
        }
    }
}

enum class SessionMode {
    /** Open conversation about a topic. */
    FREE_TALK,

    /** The AI plays a character in a concrete scenario (F8). */
    ROLEPLAY,

    /** The AI quizzes the learner on words that are due for spaced-repetition review (F9). */
    VOCAB_REVIEW,

    /** The AI says a sentence, the learner repeats it, and every attempt is graded strictly. */
    REPEAT_AFTER_ME,

    /** The AI narrates engaging short stories for listening comprehension. */
    STORY_LISTENING,

    /** The AI brings back sentences the learner got wrong in earlier lessons and has them say them correctly. */
    MISTAKE_REVIEW,

    /** IELTS Speaking preparation with Part 1, Part 2, and Part 3 format and band estimation. */
    IELTS_SPEAKING
}

enum class ConversationState {
    IDLE,
    CONNECTING,
    ACTIVE,
    PAUSED,
    RECONNECTING,
    WAITING_FOR_NETWORK,
    ENDING,
    ENDED,
    ERROR;

    /** True while a lesson exists and has not been ended yet. */
    val isInLesson: Boolean
        get() = this == CONNECTING || this == ACTIVE || this == PAUSED || this == RECONNECTING || this == WAITING_FOR_NETWORK
}

enum class Speaker { USER, AI }

data class TranscriptTurn(
    val id: Long,
    val speaker: Speaker,
    val text: String,
    val timestamp: Long
)

data class Scenario(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    /** Who the AI plays in roleplay mode, e.g. "a hotel receptionist". */
    val aiRole: String,
    /** Who the learner plays, e.g. "a guest checking in". */
    val learnerRole: String,
    /** Optional detailed context or prompt hint for dynamic open-ended expansion. */
    val customContext: String? = null,
    /** Optional specific mission objective / challenge goal for the learner to accomplish. */
    val missionObjective: String? = null
)

data class Topic(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val description: String,
    val emoji: String,
    /** Extra words (English and Vietnamese, without accents) that voice search should match. */
    val keywords: List<String>,
    val scenarios: List<Scenario>
)

/** Everything needed to start a lesson. Null topic means "pick one for me". */
data class LessonRequest(
    val topicId: String? = null,
    val level: DifficultyLevel? = null,
    val mode: SessionMode = SessionMode.FREE_TALK,
    val scenarioId: String? = null,
    val resumeStoryContext: String? = null,
    val resumeStoryTitle: String? = null,
    val targetWords: List<ReviewWord> = emptyList()
)

/** A lesson after its topic, level and mode have been resolved. */
data class ActiveLesson(
    val sessionId: String,
    val topic: Topic,
    val scenario: Scenario?,
    val level: DifficultyLevel,
    val mode: SessionMode,
    val startedAt: Long,
    val reviewWords: List<ReviewWord>,
    val voiceId: String = AiVoice.DEFAULT.id,
    val resumeStoryContext: String? = null,
    val resumeStoryTitle: String? = null,
    /** Earlier mistakes to practise in a [SessionMode.MISTAKE_REVIEW] lesson. */
    val reviewMistakes: List<ReviewMistake> = emptyList(),
    /** What the AI remembers about the learner; null when the learner turned memory off. */
    val learnerMemory: LearnerMemory? = null,
    /** Whether the AI gently offers native speaker phrasing upgrades during conversation. */
    val betterPhrasingEnabled: Boolean = true
) {
    val titleVi: String
        get() = when (mode) {
            SessionMode.VOCAB_REVIEW -> "Ôn tập từ vựng"
            SessionMode.MISTAKE_REVIEW -> "Ôn lỗi sai"
            SessionMode.REPEAT_AFTER_ME -> "Luyện phát âm: ${topic.titleVi}"
            SessionMode.ROLEPLAY -> "Nhập vai: ${scenario?.titleVi ?: topic.titleVi}"
            SessionMode.STORY_LISTENING -> "Luyện nghe kể chuyện: ${scenario?.titleVi ?: topic.titleVi}"
            SessionMode.IELTS_SPEAKING -> "Luyện thi IELTS: ${scenario?.titleVi ?: topic.titleVi}"
            SessionMode.FREE_TALK -> topic.titleVi
        }

    val titleEn: String
        get() = when (mode) {
            SessionMode.VOCAB_REVIEW -> "Vocabulary Review"
            SessionMode.MISTAKE_REVIEW -> "Mistake Review"
            SessionMode.REPEAT_AFTER_ME -> "Pronunciation Drill: ${topic.titleEn}"
            SessionMode.ROLEPLAY -> "Roleplay: ${scenario?.titleEn ?: topic.titleEn}"
            SessionMode.STORY_LISTENING -> "Story Listening: ${scenario?.titleEn ?: topic.titleEn}"
            SessionMode.IELTS_SPEAKING -> "IELTS Speaking: ${scenario?.titleEn ?: topic.titleEn}"
            SessionMode.FREE_TALK -> topic.titleEn
        }

    fun getTitle(isVi: Boolean): String = if (isVi) titleVi else titleEn
}

data class ReviewWord(val word: String, val meaning: String)

data class NewWord(
    val word: String,
    val meaning: String,
    val example: String,
    /** Whether this entry is a natural multi-word collocation rather than a single vocabulary word. */
    val isCollocation: Boolean = false
)

data class Correction(val original: String, val corrected: String, val explanation: String)

/** A stored mistake brought back for spaced-repetition review. [id] is its row in the mistakes table. */
data class ReviewMistake(val id: Long, val original: String, val corrected: String, val explanation: String)

/** Whether the learner said a reviewed mistake correctly this time. */
data class MistakeReviewResult(val mistakeId: Long, val fixed: Boolean)

/** Objective fluency metrics computed deterministically from the user's transcript. */
data class ObjectiveFluencyMetrics(
    val wordsPerMinute: Int = 0,
    val fillerWordsCount: Int = 0,
    val fillerWordsRatio: Float = 0f,
    val meanLengthOfUtterance: Float = 0f,
    val vietnameseWordsRatio: Float = 0f
)

/** IELTS Speaking criteria scores and estimated band score. */
data class IeltsEvaluation(
    val fluencyAndCoherence: Float = 0f,
    val lexicalResource: Float = 0f,
    val grammaticalRangeAndAccuracy: Float = 0f,
    val pronunciation: Float = 0f,
    val overallBand: Float = 0f,
    val feedbackVi: String = "",
    val feedbackEn: String = ""
)

/** User-tailored roleplay or practice scenario created dynamically from life/work events. */
data class CustomScenario(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val aiRole: String,
    val learnerRole: String,
    val customContext: String,
    val missionObjective: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toScenario(): Scenario = Scenario(
        id = id,
        titleVi = titleVi,
        titleEn = titleEn,
        aiRole = aiRole,
        learnerRole = learnerRole,
        customContext = customContext,
        missionObjective = missionObjective
    )
}

/**
 * What the AI remembers about the learner across lessons, built from earlier summaries and drills.
 * Everything stays on the device; it is only sent to Gemini as part of the lesson instructions.
 */
data class LearnerMemory(
    /** Mistakes the learner made most often or most recently. */
    val recurringMistakes: List<Correction> = emptyList(),
    /** Words the learner keeps mispronouncing in drills. */
    val weakWords: List<String> = emptyList(),
    /** Short facts the learner shared about themselves ("Works as a nurse in Da Nang"). */
    val facts: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = recurringMistakes.isEmpty() && weakWords.isEmpty() && facts.isEmpty()

    companion object {
        val EMPTY = LearnerMemory()
    }
}

enum class LevelAdjustmentDirection {
    KEEP,
    LEVEL_UP,
    LEVEL_DOWN
}

data class LevelRecommendation(
    val direction: LevelAdjustmentDirection,
    val targetLevel: DifficultyLevel,
    val reasonVi: String,
    val reasonEn: String = ""
)

data class SessionSummary(
    /** 1–10, or null when the lesson was too short to judge. */
    val fluencyScore: Int?,
    val grammarScore: Int?,
    val vocabularyScore: Int?,
    val newWords: List<NewWord>,
    val corrections: List<Correction>,
    val encouragement: String,
    val nextSuggestion: String,
    /** Share of drill sentences passed, 0–100. Only for repeat-after-me lessons. */
    val pronunciationScore: Int? = null,
    /** Recommendation for leveling up, down, or maintaining current difficulty. */
    val levelRecommendation: LevelRecommendation? = null,
    /** New personal facts the learner shared, for the AI to remember next time. */
    val learnerFacts: List<String> = emptyList(),
    /** Outcome for each mistake practised in a mistake review lesson. */
    val mistakeResults: List<MistakeReviewResult> = emptyList(),
    /** Objective fluency metrics calculated directly on-device from speech transcripts. */
    val fluencyMetrics: ObjectiveFluencyMetrics? = null,
    /** Score (0-100) for story comprehension listening quiz, if applicable. */
    val comprehensionScore: Int? = null,
    /** IELTS speaking evaluation and estimated band score (for IELTS mode). */
    val ieltsEvaluation: IeltsEvaluation? = null
)

data class CompletedSession(
    val id: String,
    val topicId: String,
    val scenarioId: String?,
    val level: DifficultyLevel,
    val mode: SessionMode,
    val startedAt: Long,
    val endedAt: Long,
    /** Time actually spent talking — paused time is excluded. */
    val activeDurationMs: Long,
    val transcript: List<TranscriptTurn>,
    val summary: SessionSummary?,
    val reviewedWords: List<String> = emptyList(),
    /** False for drafts saved during the lesson, so a killed app does not lose the transcript. */
    val isCompleted: Boolean = true,
    val pronunciationAttempts: List<PronunciationAttempt> = emptyList()
)

/** What the learner chose in settings. */
data class LearnerSettings(
    val level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    val voiceId: String = AiVoice.DEFAULT.id,
    val allowVietnameseHelp: Boolean = true,
    /**
     * Lets the learner talk over the AI. Off by default: through a phone or car speaker the
     * microphone hears the AI and it keeps interrupting itself. Safe with headphones.
     */
    val allowBargeIn: Boolean = false,
    val lastTopicId: String? = null,
    val lastSessionMode: SessionMode = SessionMode.FREE_TALK,
    val lastScenarioId: String? = null,
    /** Grade "repeat after me" attempts with Azure Pronunciation Assessment as well. */
    val azureEnabled: Boolean = false,
    val azureRegion: String = "",
    val azureKey: String = "",
    val pronunciationStrictness: PronunciationStrictness = PronunciationStrictness.AUTO,
    val storytellingStyle: StorytellingStyle = StorytellingStyle.INTERACTIVE,
    val randomVoice: Boolean = false,
    val storyDuration: StoryDuration = StoryDuration.FULL_10_TO_30_MIN,
    val multiVoiceStorytelling: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val adaptiveLevelRecommendation: Boolean = true,
    val drillSentenceLength: DrillSentenceLength = DrillSentenceLength.AUTO_ON_CAR,
    val drillCategory: DrillCategory = DrillCategory.ALL,
    /** Output volume percentage for AI speech, from 10 to 100. Default is comfortable 80. */
    val aiVolume: Int = 80,
    /** Automatically pause practice when app loses focus or screen turns off (outside Android Auto). Default is true. */
    val autoPauseWhenUnfocused: Boolean = true,
    /** Show translation subtitles under drill sentences on car and phone screen. Default is true. */
    val showTranslationSubtitle: Boolean = true,
    /** Let the AI remember the learner's mistakes and personal details across lessons. Default is true. */
    val rememberLearner: Boolean = true,
    /** Gently suggest native and natural phrasing upgrades when simple correct English is spoken. Default is true. */
    val betterPhrasingEnabled: Boolean = true,
    /** Daily notification when the learner has not practised yet. */
    val practiceReminderEnabled: Boolean = true,
    /** Minutes after midnight for the reminder, or [REMINDER_AUTO] to follow the learner's usual practice time. */
    val practiceReminderMinute: Int = REMINDER_AUTO,
    /** A missed day uses an earned streak freeze instead of breaking the streak. */
    val streakFreezeEnabled: Boolean = true,
    /** Keep practising "repeat after me" on the phone alone while the network is gone. */
    val offlinePracticeEnabled: Boolean = true,
    /** Start a lesson by itself when the phone connects to Android Auto. */
    val autoStartOnCarConnect: Boolean = true,
    /** Minutes of practice the learner aims for each day (5–60). */
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
    /** How long the phone screen stays on during a lesson outside Android Auto. */
    val screenAwakeMode: ScreenAwakeMode = ScreenAwakeMode.ALWAYS_ON,
    /** Spoken weekly progress digest at the start of each week. Default is true. */
    val weeklyDigestEnabled: Boolean = true
) {
    companion object {
        const val REMINDER_AUTO = -1
        const val DEFAULT_DAILY_GOAL_MINUTES = 15
        const val MIN_DAILY_GOAL_MINUTES = 5
        const val MAX_DAILY_GOAL_MINUTES = 60
    }
}

/** How long the phone screen stays on during a lesson (outside Android Auto). */
enum class ScreenAwakeMode(
    val shortLabelVi: String,
    val descriptionVi: String,
    val timeoutSeconds: Int,
    val shortLabelEn: String = "",
    val descriptionEn: String = ""
) {
    ALWAYS_ON(
        shortLabelVi = "Luôn bật",
        descriptionVi = "Màn hình luôn sáng trong suốt buổi luyện nói để tiện nhìn văn bản.",
        timeoutSeconds = -1,
        shortLabelEn = "Always on",
        descriptionEn = "Screen stays continuously on while practicing to easily read transcripts."
    ),
    FOLLOW_SYSTEM(
        shortLabelVi = "Theo máy",
        descriptionVi = "Màn hình tự tắt và khoá theo cài đặt thời gian chờ của điện thoại khi bạn không chạm vào máy. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 0,
        shortLabelEn = "System default",
        descriptionEn = "Screen turns off according to your phone display sleep timeout. Microphone continues listening."
    ),
    AFTER_30_SECONDS(
        shortLabelVi = "Sau 30s",
        descriptionVi = "Màn hình tự tắt sau 30 giây nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 30,
        shortLabelEn = "After 30s",
        descriptionEn = "Screen turns off after 30 seconds of inactivity. Microphone continues listening."
    ),
    AFTER_1_MINUTE(
        shortLabelVi = "Sau 1 phút",
        descriptionVi = "Màn hình tự tắt sau 1 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 60,
        shortLabelEn = "After 1m",
        descriptionEn = "Screen turns off after 1 minute of inactivity. Microphone continues listening."
    ),
    AFTER_2_MINUTES(
        shortLabelVi = "Sau 2 phút",
        descriptionVi = "Màn hình tự tắt sau 2 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 120,
        shortLabelEn = "After 2m",
        descriptionEn = "Screen turns off after 2 minutes of inactivity. Microphone continues listening."
    ),
    AFTER_5_MINUTES(
        shortLabelVi = "Sau 5 phút",
        descriptionVi = "Màn hình tự tắt sau 5 phút nếu không có thao tác chạm. Micro vẫn tiếp tục hoạt động.",
        timeoutSeconds = 300,
        shortLabelEn = "After 5m",
        descriptionEn = "Screen turns off after 5 minutes of inactivity. Microphone continues listening."
    );

    fun getLabel(isVi: Boolean): String = if (isVi) shortLabelVi else shortLabelEn
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn

    companion object {
        fun fromStored(name: String?): ScreenAwakeMode =
            entries.firstOrNull { it.name == name } ?: ALWAYS_ON
    }
}


/** App interface language option for multilingual support. */
enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flagEmoji: String
) {
    SYSTEM("", "Mặc định hệ thống", "System Default", "🌐"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
    ENGLISH("en", "English", "English", "🇺🇸"),
    SPANISH("es", "Español", "Spanish", "🇪🇸"),
    JAPANESE("ja", "日本語", "Japanese", "🇯🇵"),
    KOREAN("ko", "한국어", "Korean", "🇰🇷"),
    CHINESE("zh", "简体中文", "Chinese (Simplified)", "🇨🇳"),
    FRENCH("fr", "Français", "French", "🇫🇷"),
    GERMAN("de", "Deutsch", "German", "🇩🇪");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

/** Prebuilt Gemini voices offered in settings. */
enum class AiVoice(val id: String, val labelVi: String, val labelEn: String) {
    KORE("Kore", "Nữ – rõ ràng", "Female – Clear"),
    AOEDE("Aoede", "Nữ – nhẹ nhàng", "Female – Gentle"),
    PUCK("Puck", "Nam – vui vẻ", "Male – Cheerful"),
    CHARON("Charon", "Nam – trầm ấm", "Male – Warm");

    fun getLabel(isVi: Boolean): String = if (isVi) labelVi else labelEn

    companion object {
        val DEFAULT = KORE
        fun fromId(id: String?): AiVoice = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

/** Errors surfaced to the UI and to Android Auto. */
sealed class EngineError(val messageVi: String, val messageEn: String) {
    fun getMessage(isVi: Boolean): String = if (isVi) messageVi else messageEn

    data object MissingMicPermission : EngineError(
        "Hãy mở SpeakDrive trên điện thoại và cấp quyền micro.",
        "Please open SpeakDrive on your phone and grant microphone permission."
    )
    data object NoNetwork : EngineError(
        "Không có kết nối mạng. Bài học sẽ tiếp tục khi có mạng.",
        "No network connection. The lesson will resume once connected."
    )
    data object AudioFocusDenied : EngineError(
        "Không thể phát âm thanh lúc này (đang có cuộc gọi?).",
        "Cannot play audio right now (in an active call?)."
    )
    data class AiNotConfigured(val detail: String? = null) : EngineError(
        if (detail.isNullOrBlank()) "Chưa cấu hình Firebase/Gemini. Xem docs/FIREBASE_SETUP.md."
        else "Chưa cấu hình Firebase/Gemini: $detail.\nXem docs/FIREBASE_SETUP.md.",
        if (detail.isNullOrBlank()) "Firebase/Gemini is not configured. See docs/FIREBASE_SETUP.md."
        else "Firebase/Gemini not configured: $detail.\nSee docs/FIREBASE_SETUP.md."
    )
    data class ConnectionFailed(val detail: String?) : EngineError(
        "Không kết nối được với AI. Vui lòng thử lại.",
        "Failed to connect to AI. Please try again."
    )
}
