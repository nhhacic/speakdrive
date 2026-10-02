package com.speakdrive.ai.summary

import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.TranscriptTurn

/** Produces the end-of-lesson report (scores, new words, corrections). */
interface SummaryGenerator {
    suspend fun summarize(lesson: ActiveLesson, transcript: List<TranscriptTurn>): SessionSummary
}
