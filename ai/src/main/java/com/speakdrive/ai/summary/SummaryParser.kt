package com.speakdrive.ai.summary

import com.speakdrive.ai.model.Correction
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

    fun parse(raw: String): SessionSummary {
        val body = extractJsonObject(raw) ?: throw IllegalArgumentException("No JSON object in summary response")
        val dto = json.decodeFromString(SummaryDto.serializer(), body)
        return SessionSummary(
            fluencyScore = dto.fluencyScore?.coerceIn(1, 10),
            grammarScore = dto.grammarScore?.coerceIn(1, 10),
            vocabularyScore = dto.vocabularyScore?.coerceIn(1, 10),
            newWords = dto.newWords
                .filter { it.word.isNotBlank() }
                .distinctBy { it.word.trim().lowercase() }
                .map { NewWord(it.word.trim(), it.meaningVi.trim(), it.example.trim()) },
            corrections = dto.corrections
                .filter { it.original.isNotBlank() && it.corrected.isNotBlank() }
                .map { Correction(it.original.trim(), it.corrected.trim(), it.explanationVi.trim()) },
            encouragement = dto.encouragementVi.trim(),
            nextSuggestion = dto.nextSuggestionVi.trim()
        )
    }

    /** Summary used when the lesson was too short or the AI could not be reached. */
    fun fallback(reasonVi: String) = SessionSummary(
        fluencyScore = null,
        grammarScore = null,
        vocabularyScore = null,
        newWords = emptyList(),
        corrections = emptyList(),
        encouragement = reasonVi,
        nextSuggestion = "Hãy thử một buổi dài hơn để nhận đánh giá chi tiết."
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
        @SerialName("next_suggestion_vi") val nextSuggestionVi: String = ""
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
