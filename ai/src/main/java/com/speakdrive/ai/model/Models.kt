package com.speakdrive.ai.model

/** Learner level. [cefr] is fed to the model; [labelVi] is shown in the UI. */
enum class DifficultyLevel(val displayName: String, val labelVi: String, val cefr: String) {
    BEGINNER("Beginner", "Người mới bắt đầu", "A1–A2"),
    INTERMEDIATE("Intermediate", "Trung cấp", "B1–B2"),
    ADVANCED("Advanced", "Nâng cao", "C1–C2");

    companion object {
        /** Parses a stored value, tolerating enum names and display names. */
        fun fromStored(value: String?): DifficultyLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: INTERMEDIATE
    }
}

enum class SessionMode {
    /** Open conversation about a topic. */
    FREE_TALK,

    /** The AI plays a character in a concrete scenario (F8). */
    ROLEPLAY,

    /** The AI quizzes the learner on words that are due for spaced-repetition review (F9). */
    VOCAB_REVIEW
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
    val learnerRole: String
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
    val scenarioId: String? = null
)

/** A lesson after its topic, level and mode have been resolved. */
data class ActiveLesson(
    val sessionId: String,
    val topic: Topic,
    val scenario: Scenario?,
    val level: DifficultyLevel,
    val mode: SessionMode,
    val startedAt: Long,
    val reviewWords: List<ReviewWord>
) {
    val titleVi: String
        get() = when (mode) {
            SessionMode.VOCAB_REVIEW -> "Ôn tập từ vựng"
            SessionMode.ROLEPLAY -> "Nhập vai: ${scenario?.titleVi ?: topic.titleVi}"
            SessionMode.FREE_TALK -> topic.titleVi
        }
}

data class ReviewWord(val word: String, val meaning: String)

data class NewWord(val word: String, val meaning: String, val example: String)

data class Correction(val original: String, val corrected: String, val explanation: String)

data class SessionSummary(
    /** 1–10, or null when the lesson was too short to judge. */
    val fluencyScore: Int?,
    val grammarScore: Int?,
    val vocabularyScore: Int?,
    val newWords: List<NewWord>,
    val corrections: List<Correction>,
    val encouragement: String,
    val nextSuggestion: String
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
    val reviewedWords: List<String>,
    /** False for drafts saved during the lesson, so a killed app does not lose the transcript. */
    val isCompleted: Boolean = true
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
    val lastTopicId: String? = null
)

/** Prebuilt Gemini voices offered in settings. */
enum class AiVoice(val id: String, val labelVi: String) {
    KORE("Kore", "Nữ – rõ ràng"),
    AOEDE("Aoede", "Nữ – nhẹ nhàng"),
    PUCK("Puck", "Nam – vui vẻ"),
    CHARON("Charon", "Nam – trầm ấm");

    companion object {
        val DEFAULT = KORE
        fun fromId(id: String?): AiVoice = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

/** Errors surfaced to the UI and to Android Auto. Messages are Vietnamese because they are shown to the learner. */
sealed class EngineError(val messageVi: String) {
    data object MissingMicPermission : EngineError("Hãy mở SpeakDrive trên điện thoại và cấp quyền micro.")
    data object NoNetwork : EngineError("Không có kết nối mạng. Bài học sẽ tiếp tục khi có mạng.")
    data object AudioFocusDenied : EngineError("Không thể phát âm thanh lúc này (đang có cuộc gọi?).")
    data object AiNotConfigured : EngineError("Chưa cấu hình Firebase/Gemini. Xem docs/FIREBASE_SETUP.md.")
    data class ConnectionFailed(val detail: String?) : EngineError("Không kết nối được với AI. Vui lòng thử lại.")
}
