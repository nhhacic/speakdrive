package com.speakdrive.ai.evaluation

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import org.junit.Test

class LevelEvaluatorTest {

    @Test
    fun `high scores with few corrections triggers LEVEL_UP`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.ELEMENTARY,
            fluencyScore = 9,
            grammarScore = 9,
            vocabularyScore = 8,
            pronunciationScore = 85,
            correctionsCount = 1,
            learnerTurns = 5
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.LEVEL_UP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(result.reasonVi).contains("Tiền trung cấp")
    }

    @Test
    fun `already at ADVANCED level does not LEVEL_UP further`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.ADVANCED,
            fluencyScore = 10,
            grammarScore = 10,
            vocabularyScore = 10,
            pronunciationScore = 100,
            correctionsCount = 0,
            learnerTurns = 8
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.KEEP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.ADVANCED)
    }

    @Test
    fun `low scores with multiple grammar errors triggers LEVEL_DOWN`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.INTERMEDIATE,
            fluencyScore = 4,
            grammarScore = 3,
            vocabularyScore = 4,
            pronunciationScore = 60,
            correctionsCount = 5,
            learnerTurns = 4
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.LEVEL_DOWN)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(result.reasonVi).contains("Tiền trung cấp")
    }

    @Test
    fun `already at BEGINNER level does not LEVEL_DOWN further`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.BEGINNER,
            fluencyScore = 3,
            grammarScore = 3,
            vocabularyScore = 3,
            pronunciationScore = 40,
            correctionsCount = 6,
            learnerTurns = 3
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.KEEP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.BEGINNER)
    }

    @Test
    fun `moderate balanced performance recommends KEEP`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.INTERMEDIATE,
            fluencyScore = 7,
            grammarScore = 6,
            vocabularyScore = 7,
            pronunciationScore = 75,
            correctionsCount = 2,
            learnerTurns = 5
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.KEEP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.INTERMEDIATE)
    }

    @Test
    fun `too few learner turns recommends KEEP with encouragement`() {
        val result = LevelEvaluator.evaluateSession(
            currentLevel = DifficultyLevel.ELEMENTARY,
            fluencyScore = 9,
            grammarScore = 9,
            vocabularyScore = 9,
            pronunciationScore = 90,
            correctionsCount = 0,
            learnerTurns = 1
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.KEEP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.ELEMENTARY)
    }

    @Test
    fun `longitudinal trend evaluation recommends level up on consistent high scores`() {
        val result = LevelEvaluator.evaluateTrend(
            currentLevel = DifficultyLevel.PRE_INTERMEDIATE,
            recentAverages = listOf(8.5, 8.8, 9.0),
            recentCorrections = listOf(1, 0, 1)
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.LEVEL_UP)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.INTERMEDIATE)
    }

    @Test
    fun `longitudinal trend evaluation recommends level down on persistent struggles`() {
        val result = LevelEvaluator.evaluateTrend(
            currentLevel = DifficultyLevel.UPPER_INTERMEDIATE,
            recentAverages = listOf(4.2, 4.5, 4.0),
            recentCorrections = listOf(5, 4, 6)
        )

        assertThat(result.direction).isEqualTo(LevelAdjustmentDirection.LEVEL_DOWN)
        assertThat(result.targetLevel).isEqualTo(DifficultyLevel.INTERMEDIATE)
    }
}
