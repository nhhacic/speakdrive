package com.speakdrive.ai.summary

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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiSummaryGenerator @Inject constructor() : SummaryGenerator {

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = BuildConfig.TEXT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = summarySchema
                temperature = 0.4f
            }
        )
    }

    override suspend fun summarize(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        attempts: List<PronunciationAttempt>
    ): SessionSummary {
        val learnerTurns = transcript.count { it.speaker == Speaker.USER && it.text.split(' ').size >= 2 }
        if (learnerTurns < MIN_LEARNER_TURNS) {
            return SummaryParser.fallback("Buổi học hơi ngắn nên chưa đủ dữ liệu để chấm điểm. Bạn đã bắt đầu rất tốt!")
        }
        val response = model.generateContent(PromptTemplates.summaryPrompt(lesson, transcript, attempts))
        return SummaryParser.parse(response.text ?: error("Empty summary response"))
    }

    private companion object {
        const val MIN_LEARNER_TURNS = 2

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
                "next_suggestion_vi" to Schema.string()
            )
        )
    }
}
