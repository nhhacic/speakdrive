package com.speakdrive.ai.live

import com.speakdrive.ai.BuildConfig
import com.speakdrive.ai.model.SessionMode

/** Which Gemini Live model a lesson uses (set in gradle.properties). */
object LiveModels {
    /**
     * Models for [mode], best first. Conversation lessons try the newer model and fall back to the
     * older one if it is refused. The pronunciation drill stays on the older model: the newer one
     * runs tool calls in the background by default, so the AI would give its feedback before the
     * app has graded the attempt, and the Firebase SDK cannot ask it to wait.
     */
    fun forMode(
        mode: SessionMode,
        conversationModel: String = BuildConfig.LIVE_MODEL_CONVERSATION,
        drillModel: String = BuildConfig.LIVE_MODEL
    ): List<String> = when (mode) {
        SessionMode.REPEAT_AFTER_ME -> listOf(drillModel)
        else -> listOf(conversationModel, drillModel).filter { it.isNotBlank() }.distinct()
    }
}
