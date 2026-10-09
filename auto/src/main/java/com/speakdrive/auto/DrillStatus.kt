package com.speakdrive.auto

import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.speakdrive.ai.pronunciation.PronunciationGrader

/** Whose turn it is in a repeat drill, as the car screen shows it. */
enum class DrillTurn { CONNECTING, AI_SPEAKING, YOUR_TURN, YOU_SPEAKING, GRADING, PAUSED }

/**
 * Live progress of a repeat-after-me drill, drawn around the sentence on the Android Auto
 * Now Playing artwork: whose turn it is, how the last try at the sentence on screen went, and how
 * many sentences have passed so far. Mirrors the phone's drill card (see ConversationViewModel).
 */
data class DrillStatus(
    val turn: DrillTurn,
    /** Score of the latest graded try at the sentence on screen; null before the first try. */
    val accuracyPercent: Int? = null,
    val attemptNumber: Int? = null,
    val passed: Boolean? = null,
    val maxAttempts: Int = PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE,
    val passedSentences: Int = 0,
    /** Sentences tried at least once in this lesson. */
    val sentences: Int = 0
) {
    companion object {
        /** Null when there is no sentence to repeat, so the artwork keeps its plain layout. */
        fun of(
            state: ConversationState,
            activeSpeaker: Speaker?,
            isAiThinking: Boolean,
            drillTarget: String?,
            attempts: List<PronunciationAttempt>
        ): DrillStatus? {
            if (drillTarget.isNullOrBlank()) return null
            val turn = when (state) {
                ConversationState.ACTIVE -> when {
                    activeSpeaker == Speaker.AI -> DrillTurn.AI_SPEAKING
                    isAiThinking -> DrillTurn.GRADING
                    activeSpeaker == Speaker.USER -> DrillTurn.YOU_SPEAKING
                    else -> DrillTurn.YOUR_TURN
                }
                ConversationState.PAUSED -> DrillTurn.PAUSED
                else -> DrillTurn.CONNECTING
            }
            val bySentence = attempts.groupBy { PronunciationDrill.key(it.target) }
            // Only grade the sentence on screen: a new sentence starts with an empty score.
            val last = bySentence[PronunciationDrill.key(drillTarget)]?.lastOrNull()
            return DrillStatus(
                turn = turn,
                accuracyPercent = last?.accuracyPercent,
                attemptNumber = last?.attemptNumber,
                passed = last?.passed,
                passedSentences = bySentence.values.count { tries -> tries.any { it.passed } },
                sentences = bySentence.size
            )
        }
    }
}
