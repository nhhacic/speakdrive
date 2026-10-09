package com.speakdrive.ai.summary

import com.speakdrive.ai.evaluation.LevelEvaluator
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.Correction
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.MistakeReviewResult
import com.speakdrive.ai.model.NewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationDrill
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses the JSON summary returned by Gemini, tolerating code fences and missing fields. */
object SummaryParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(
        raw: String,
        currentLevel: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        pronunciationScore: Int? = null,
        learnerTurns: Int = 3
    ): SessionSummary {
        val body = extractJsonObject(raw) ?: throw IllegalArgumentException("No JSON object in summary response")
        val dto = json.decodeFromString(SummaryDto.serializer(), body)
        val fluency = dto.fluencyScore?.coerceIn(1, 10)
        val grammar = dto.grammarScore?.coerceIn(1, 10)
        val vocab = dto.vocabularyScore?.coerceIn(1, 10)
        val newWords = dto.newWords
            .filter { it.word.isNotBlank() }
            .distinctBy { it.word.trim().lowercase() }
            .map { NewWord(it.word.trim(), it.meaningVi.trim(), it.example.trim(), it.isCollocation) }
        val corrections = dto.corrections
            .filter { it.original.isNotBlank() && it.corrected.isNotBlank() }
            .map { Correction(it.original.trim(), it.corrected.trim(), it.explanationVi.trim()) }

        val direction = when (dto.levelRecommendationDirection?.trim()?.uppercase()) {
            "UP", "LEVEL_UP" -> LevelAdjustmentDirection.LEVEL_UP
            "DOWN", "LEVEL_DOWN" -> LevelAdjustmentDirection.LEVEL_DOWN
            "KEEP" -> LevelAdjustmentDirection.KEEP
            else -> null
        }
        val targetLevel = dto.recommendedLevel?.let { DifficultyLevel.fromStored(it) }

        val recommendation = if (direction != null && targetLevel != null && !dto.levelRecommendationReasonVi.isNullOrBlank()) {
            LevelRecommendation(
                direction = direction,
                targetLevel = targetLevel,
                reasonVi = dto.levelRecommendationReasonVi.trim(),
                reasonEn = dto.levelRecommendationReasonEn?.trim().orEmpty()
            )
        } else {
            LevelEvaluator.evaluateSession(
                currentLevel = currentLevel,
                fluencyScore = fluency,
                grammarScore = grammar,
                vocabularyScore = vocab,
                pronunciationScore = pronunciationScore,
                correctionsCount = corrections.size,
                learnerTurns = learnerTurns
            )
        }

        val ielts = dto.ieltsEvaluation?.let {
            com.speakdrive.ai.model.IeltsEvaluation(
                fluencyAndCoherence = it.fc ?: 0f,
                lexicalResource = it.lr ?: 0f,
                grammaticalRangeAndAccuracy = it.gra ?: 0f,
                pronunciation = it.p ?: 0f,
                overallBand = it.overallBand ?: 0f,
                feedbackVi = it.feedbackVi.trim(),
                feedbackEn = it.feedbackEn.trim()
            )
        }

        return SessionSummary(
            fluencyScore = fluency,
            grammarScore = grammar,
            vocabularyScore = vocab,
            newWords = newWords,
            corrections = corrections,
            encouragement = dto.encouragementVi.trim(),
            nextSuggestion = dto.nextSuggestionVi.trim(),
            pronunciationScore = pronunciationScore,
            levelRecommendation = recommendation,
            learnerFacts = dto.learnerFacts
                .map { it.trim().trimEnd('.') }
                .filter { it.length in 3..MAX_FACT_LENGTH }
                .distinctBy { it.lowercase() }
                .take(MAX_NEW_FACTS),
            mistakeResults = dto.mistakeResults.mapNotNull { r ->
                val id = r.id ?: return@mapNotNull null
                val fixed = r.fixed ?: return@mapNotNull null
                MistakeReviewResult(id, fixed)
            },
            comprehensionScore = dto.comprehensionScore?.coerceIn(0, 100),
            ieltsEvaluation = ielts
        )
    }

    private const val MAX_NEW_FACTS = 3
    private const val MAX_FACT_LENGTH = 120

    /** Summary used when the lesson was too short or the AI could not be reached. */
    fun fallback(reasonVi: String, currentLevel: DifficultyLevel = DifficultyLevel.INTERMEDIATE) = SessionSummary(
        fluencyScore = null,
        grammarScore = null,
        vocabularyScore = null,
        newWords = emptyList(),
        corrections = emptyList(),
        encouragement = reasonVi,
        nextSuggestion = "Hãy thử một buổi dài hơn để nhận đánh giá chi tiết.",
        levelRecommendation = LevelRecommendation(
            direction = LevelAdjustmentDirection.KEEP,
            targetLevel = currentLevel,
            reasonVi = "Hãy hoàn thành thêm các bài học để nhận đánh giá lộ trình tăng giảm cấp độ.",
            reasonEn = "Complete more sessions to evaluate level adjustment."
        )
    )

    /**
     * Generates a pedagogical, high-quality fallback summary locally when cloud generative AI fails.
     * Uses actual practice sentences, pronunciation attempts, and speech turns instead of empty error cards.
     */
    fun createLocalFallbackSummary(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        attempts: List<PronunciationAttempt>
    ): SessionSummary {
        val currentLevel = lesson.level
        if (lesson.mode == SessionMode.REPEAT_AFTER_ME && attempts.isNotEmpty()) {
            val bySentence = attempts.groupBy { PronunciationDrill.key(it.target) }
            val totalSentences = bySentence.size
            val passedSentences = bySentence.values.count { tries -> tries.any { it.passed } }
            val pronScore = PronunciationDrill.score(attempts) ?: 0

            // Extract failed sentences for corrections
            val failedGroups = bySentence.values.filter { tries -> tries.none { it.passed } }
            val corrections = failedGroups.take(5).map { tries ->
                val lastAttempt = tries.last()
                val problemNote = when {
                    lastAttempt.problemWords.isNotEmpty() ->
                        "Lưu ý phát âm từ: ${lastAttempt.problemWords.joinToString()}"
                    lastAttempt.modelNotes.isNotBlank() ->
                        lastAttempt.modelNotes
                    else ->
                        "Cần phát âm rõ ràng và liền mạch cả câu."
                }
                Correction(
                    original = lastAttempt.heard.ifBlank { "(chưa nghe rõ)" },
                    corrected = lastAttempt.target,
                    explanation = problemNote
                )
            }

            val fluency = ((pronScore + 5) / 10).coerceIn(1, 10)
            val grammar = 9 // Pre-scripted drill targets
            val vocabulary = 8

            val encouragement = when {
                pronScore >= 85 -> "Bạn đã hoàn thành xuất sắc bài luyện phát âm với $passedSentences/$totalSentences câu chuẩn xác ($pronScore%). Phản xạ ngữ âm rất tốt!"
                pronScore >= 60 -> "Bạn đã nỗ lực hoàn thành $passedSentences/$totalSentences câu đạt chuẩn ($pronScore%). Hãy chú ý luyện thêm các câu chưa đạt để ngữ điệu tự nhiên hơn nhé!"
                else -> "Bạn đã kiên trì hoàn thành $totalSentences câu luyện nói. Hãy tiếp tục luyện tập từng câu để cải thiện âm đuôi và sự lưu loát!"
            }

            val nextSuggestion = if (corrections.isNotEmpty()) {
                "Luyện tập lại câu: \"${corrections.first().corrected}\" để phát âm chuẩn xác hơn."
            } else {
                "Thử sức với các câu dài hơn hoặc chuyển sang chủ đề tiếp theo để mở rộng vốn từ."
            }

            val recommendation = LevelEvaluator.evaluateSession(
                currentLevel = currentLevel,
                fluencyScore = fluency,
                grammarScore = grammar,
                vocabularyScore = vocabulary,
                pronunciationScore = pronScore,
                correctionsCount = corrections.size,
                learnerTurns = attempts.size
            )

            return SessionSummary(
                fluencyScore = fluency,
                grammarScore = grammar,
                vocabularyScore = vocabulary,
                newWords = emptyList(),
                corrections = corrections,
                encouragement = encouragement,
                nextSuggestion = nextSuggestion,
                pronunciationScore = pronScore,
                levelRecommendation = recommendation
            )
        }

        val learnerTurns = transcript.filter { it.speaker == Speaker.USER && it.text.trim().isNotBlank() }
        if (learnerTurns.size >= 2) {
            val totalWords = learnerTurns.sumOf { it.text.split(Regex("\\s+")).filter(String::isNotBlank).size }
            val avgWordsPerTurn = totalWords / learnerTurns.size
            val fluency = when {
                avgWordsPerTurn >= 8 -> 8
                avgWordsPerTurn >= 4 -> 7
                else -> 6
            }
            val grammar = 7
            val vocabulary = 7

            val encouragement = "Bạn đã hoàn thành buổi học với ${learnerTurns.size} lượt đối thoại về chủ đề ${lesson.topic.titleVi}. Nội dung buổi học đã được lưu lại đầy đủ!"
            val nextSuggestion = "Hãy thử mở rộng câu trả lời bằng cách bổ sung thêm lý do hoặc ví dụ cụ thể ở buổi học tới."

            val recommendation = LevelEvaluator.evaluateSession(
                currentLevel = currentLevel,
                fluencyScore = fluency,
                grammarScore = grammar,
                vocabularyScore = vocabulary,
                pronunciationScore = null,
                correctionsCount = 0,
                learnerTurns = learnerTurns.size
            )

            return SessionSummary(
                fluencyScore = fluency,
                grammarScore = grammar,
                vocabularyScore = vocabulary,
                newWords = emptyList(),
                corrections = emptyList(),
                encouragement = encouragement,
                nextSuggestion = nextSuggestion,
                pronunciationScore = null,
                levelRecommendation = recommendation
            )
        }

        return fallback(
            reasonVi = "Buổi học hơi ngắn nên chưa đủ dữ liệu để chấm điểm. Bạn đã bắt đầu rất tốt!",
            currentLevel = currentLevel
        )
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }

    @Serializable
    private data class SummaryDto(
        @SerialName("fluency_score") val fluencyScore: Int? = null,
        @SerialName("grammar_score") val grammarScore: Int? = null,
        @SerialName("vocabulary_score") val vocabularyScore: Int? = null,
        @SerialName("new_words") val newWords: List<WordDto> = emptyList(),
        val corrections: List<CorrectionDto> = emptyList(),
        @SerialName("encouragement_vi") val encouragementVi: String = "",
        @SerialName("next_suggestion_vi") val nextSuggestionVi: String = "",
        @SerialName("level_recommendation_direction") val levelRecommendationDirection: String? = null,
        @SerialName("recommended_level") val recommendedLevel: String? = null,
        @SerialName("level_recommendation_reason_vi") val levelRecommendationReasonVi: String? = null,
        @SerialName("level_recommendation_reason_en") val levelRecommendationReasonEn: String? = null,
        @SerialName("learner_facts") val learnerFacts: List<String> = emptyList(),
        @SerialName("mistake_results") val mistakeResults: List<MistakeResultDto> = emptyList(),
        @SerialName("comprehension_score") val comprehensionScore: Int? = null,
        @SerialName("ielts_evaluation") val ieltsEvaluation: IeltsEvaluationDto? = null
    )

    @Serializable
    private data class IeltsEvaluationDto(
        val fc: Float? = null,
        val lr: Float? = null,
        val gra: Float? = null,
        val p: Float? = null,
        @SerialName("overall_band") val overallBand: Float? = null,
        @SerialName("feedback_vi") val feedbackVi: String = "",
        @SerialName("feedback_en") val feedbackEn: String = ""
    )

    @Serializable
    private data class MistakeResultDto(
        val id: Long? = null,
        val fixed: Boolean? = null
    )

    @Serializable
    private data class WordDto(
        val word: String = "",
        @SerialName("meaning_vi") val meaningVi: String = "",
        val example: String = "",
        @SerialName("is_collocation") val isCollocation: Boolean = false
    )

    @Serializable
    private data class CorrectionDto(
        val original: String = "",
        val corrected: String = "",
        @SerialName("explanation_vi") val explanationVi: String = ""
    )
}
