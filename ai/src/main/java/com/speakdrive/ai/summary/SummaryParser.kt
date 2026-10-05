package com.speakdrive.ai.summary

import com.speakdrive.ai.evaluation.LevelEvaluator
import com.speakdrive.ai.model.Correction
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.NewWord
import com.speakdrive.ai.model.SessionSummary
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
            .map { NewWord(it.word.trim(), it.meaningVi.trim(), it.example.trim()) }
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

        return SessionSummary(
            fluencyScore = fluency,
            grammarScore = grammar,
            vocabularyScore = vocab,
            newWords = newWords,
            corrections = corrections,
            encouragement = dto.encouragementVi.trim(),
            nextSuggestion = dto.nextSuggestionVi.trim(),
            pronunciationScore = pronunciationScore,
            levelRecommendation = recommendation
        )
    }

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
        @SerialName("level_recommendation_reason_en") val levelRecommendationReasonEn: String? = null
    )

    @Serializable
    private data class WordDto(
        val word: String = "",
        @SerialName("meaning_vi") val meaningVi: String = "",
        val example: String = ""
    )

    @Serializable
    private data class CorrectionDto(
        val original: String = "",
        val corrected: String = "",
        @SerialName("explanation_vi") val explanationVi: String = ""
    )
}
