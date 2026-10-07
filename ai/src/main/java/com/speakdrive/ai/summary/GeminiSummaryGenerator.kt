package com.speakdrive.ai.summary

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.speakdrive.ai.BuildConfig
import com.speakdrive.ai.PromptTemplates
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationDrill
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiSummaryGenerator @Inject constructor() : SummaryGenerator {

    private val candidateModels = listOf(
        BuildConfig.TEXT_MODEL,
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    ).filter { it.isNotBlank() }.distinct()

    override suspend fun summarize(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        attempts: List<PronunciationAttempt>
    ): SessionSummary {
        val learnerTurns = transcript.count { it.speaker == Speaker.USER && it.text.split(' ').size >= 2 }
        val hasSufficientAttempts = attempts.size >= MIN_DRILL_ATTEMPTS
        val hasSufficientTurns = learnerTurns >= MIN_LEARNER_TURNS

        if (!hasSufficientTurns && !hasSufficientAttempts) {
            return SummaryParser.fallback(
                reasonVi = "Buổi học hơi ngắn nên chưa đủ dữ liệu để chấm điểm. Bạn đã bắt đầu rất tốt!",
                currentLevel = lesson.level
            )
        }

        val prompt = PromptTemplates.summaryPrompt(lesson, transcript, attempts)
        val pronScore: Int? = if (attempts.isNotEmpty()) PronunciationDrill.score(attempts) else null
        val effectiveTurns = learnerTurns.coerceAtLeast(attempts.size)

        for (modelName in candidateModels) {
            // Attempt 1: with responseSchema (structured JSON)
            try {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = summarySchema
                        temperature = 0.4f
                    }
                )
                val response = model.generateContent(prompt)
                val raw = response.text
                if (!raw.isNullOrBlank()) {
                    return SummaryParser.parse(
                        raw = raw,
                        currentLevel = lesson.level,
                        pronunciationScore = pronScore,
                        learnerTurns = effectiveTurns
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Summary generation with $modelName (with schema) failed: ${e.message}")
            }

            // Attempt 2: without responseSchema (JSON mime type only, raw JSON extracted by parser)
            try {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        temperature = 0.4f
                    }
                )
                val response = model.generateContent(prompt)
                val raw = response.text
                if (!raw.isNullOrBlank()) {
                    return SummaryParser.parse(
                        raw = raw,
                        currentLevel = lesson.level,
                        pronunciationScore = pronScore,
                        learnerTurns = effectiveTurns
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Summary generation with $modelName (without schema) failed: ${e.message}")
            }
        }

        // When all cloud models fail, gracefully produce an intelligent pedagogical fallback summary
        Log.w(TAG, "All cloud models failed to generate summary; creating local fallback summary")
        return SummaryParser.createLocalFallbackSummary(lesson, transcript, attempts)
    }

    private companion object {
        const val TAG = "GeminiSummary"
        const val MIN_LEARNER_TURNS = 2
        const val MIN_DRILL_ATTEMPTS = 2

        val summarySchema = Schema.obj(
            mapOf(
                "fluency_score" to Schema.integer(),
                "grammar_score" to Schema.integer(),
                "vocabulary_score" to Schema.integer(),
                "new_words" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "word" to Schema.string(),
                            "meaning_vi" to Schema.string(),
                            "example" to Schema.string()
                        )
                    )
                ),
                "corrections" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "original" to Schema.string(),
                            "corrected" to Schema.string(),
                            "explanation_vi" to Schema.string()
                        )
                    )
                ),
                "encouragement_vi" to Schema.string(),
                "next_suggestion_vi" to Schema.string(),
                "level_recommendation_direction" to Schema.string(),
                "recommended_level" to Schema.string(),
                "level_recommendation_reason_vi" to Schema.string(),
                "level_recommendation_reason_en" to Schema.string()
            )
        )
    }
}
