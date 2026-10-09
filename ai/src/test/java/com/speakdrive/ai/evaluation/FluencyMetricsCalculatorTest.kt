package com.speakdrive.ai.evaluation

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import org.junit.Test

class FluencyMetricsCalculatorTest {

    @Test
    fun `empty transcript returns zero metrics`() {
        val result = FluencyMetricsCalculator.calculate(emptyList(), 0)
        assertThat(result.wordsPerMinute).isEqualTo(0)
        assertThat(result.fillerWordsCount).isEqualTo(0)
        assertThat(result.fillerWordsRatio).isEqualTo(0f)
        assertThat(result.meanLengthOfUtterance).isEqualTo(0f)
        assertThat(result.vietnameseWordsRatio).isEqualTo(0f)
    }

    @Test
    fun `calculates WPM correctly based on active duration`() {
        // 60 words in 30 seconds (0.5 minute) -> 120 WPM
        val words = (1..60).joinToString(" ") { "word$it" }
        val transcript = listOf(
            TranscriptTurn(1, Speaker.USER, words, 1000)
        )
        val result = FluencyMetricsCalculator.calculate(transcript, 30_000)
        assertThat(result.wordsPerMinute).isEqualTo(120)
        assertThat(result.meanLengthOfUtterance).isEqualTo(60.0f)
    }

    @Test
    fun `counts single and multi-word filler words`() {
        val transcript = listOf(
            TranscriptTurn(1, Speaker.USER, "Um, I think, you know, we should like go now, actually.", 1000)
        )
        // Words: Um (1), I, think, you know (1 multi-word), we, should, like (1), go, now, actually (1) -> 4 fillers
        val result = FluencyMetricsCalculator.calculate(transcript, 10_000)
        assertThat(result.fillerWordsCount).isAtLeast(3)
        assertThat(result.fillerWordsRatio).isGreaterThan(0f)
    }

    @Test
    fun `detects Vietnamese words and calculates ratio`() {
        val transcript = listOf(
            TranscriptTurn(1, Speaker.USER, "I want to say xin chào and cảm ơn bạn very much", 1000)
        )
        val result = FluencyMetricsCalculator.calculate(transcript, 10_000)
        assertThat(result.vietnameseWordsRatio).isGreaterThan(0.2f)
    }
}
