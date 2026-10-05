package com.speakdrive.ai.evaluation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SentenceEvaluatorTest {

    private val evaluator = SentenceEvaluator()

    @Test
    fun `exact word match succeeds`() {
        val result = evaluator.evaluate("appreciate", "I really appreciate your kind help today.")
        assertThat(result.containsTargetWord).isTrue()
        assertThat(result.status).isEqualTo(SentenceStatus.EXCELLENT)
        assertThat(result.wordCount).isAtLeast(6)
    }

    @Test
    fun `inflected past tense word matches`() {
        val result = evaluator.evaluate("appreciate", "She appreciated all the support from her team.")
        assertThat(result.containsTargetWord).isTrue()
        assertThat(result.status).isEqualTo(SentenceStatus.EXCELLENT)
    }

    @Test
    fun `plural or third person -s matches`() {
        val result = evaluator.evaluate("opportunity", "There are many opportunities in modern tech.")
        assertThat(result.containsTargetWord).isTrue()
        assertThat(result.status).isEqualTo(SentenceStatus.EXCELLENT)
    }

    @Test
    fun `missing target word returns MISSING_WORD status`() {
        val result = evaluator.evaluate("destination", "I am going to work by car.")
        assertThat(result.containsTargetWord).isFalse()
        assertThat(result.status).isEqualTo(SentenceStatus.MISSING_WORD)
    }

    @Test
    fun `short sentence returns TOO_SHORT status`() {
        val result = evaluator.evaluate("destination", "Final destination.")
        assertThat(result.containsTargetWord).isTrue()
        assertThat(result.status).isEqualTo(SentenceStatus.TOO_SHORT)
    }

    @Test
    fun `empty input returns EMPTY status`() {
        val result = evaluator.evaluate("destination", "   ")
        assertThat(result.status).isEqualTo(SentenceStatus.EMPTY)
        assertThat(result.containsTargetWord).isFalse()
    }
}
