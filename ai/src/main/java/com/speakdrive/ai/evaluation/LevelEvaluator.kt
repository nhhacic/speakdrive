package com.speakdrive.ai.evaluation

import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation

/**
 * Evaluates learner performance and generates pedagogical recommendations
 * to level up, level down, or maintain current difficulty for optimal progress.
 */
object LevelEvaluator {

    /**
     * Evaluates a single session's scores and generates a level recommendation.
     */
    fun evaluateSession(
        currentLevel: DifficultyLevel,
        fluencyScore: Int?,
        grammarScore: Int?,
        vocabularyScore: Int?,
        pronunciationScore: Int? = null,
        correctionsCount: Int = 0,
        learnerTurns: Int = 0
    ): LevelRecommendation {
        val scores = listOfNotNull(fluencyScore, grammarScore, vocabularyScore)
        if (scores.isEmpty() || learnerTurns < 2) {
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.KEEP,
                targetLevel = currentLevel,
                reasonVi = "Hãy hoàn thành thêm các buổi học để nhận đánh giá chi tiết về lộ trình nâng cấp độ.",
                reasonEn = "Complete more sessions to receive detailed level advancement recommendations."
            )
        }

        val avgScore = scores.average()
        val isFlawlessPronunciation = pronunciationScore == null || pronunciationScore >= 75

        // Criteria for LEVEL_UP:
        // Average score >= 8.2, minimal corrections (<= 2), good pronunciation, and next level exists
        val canLevelUp = currentLevel.nextLevel() != null &&
            avgScore >= 8.2 &&
            correctionsCount <= 2 &&
            isFlawlessPronunciation

        if (canLevelUp) {
            val target = currentLevel.nextLevel()!!
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.LEVEL_UP,
                targetLevel = target,
                reasonVi = "Bạn đã thể hiện xuất sắc ở cấp độ ${currentLevel.getLabel(true)} (${currentLevel.cefr}) với phản xạ lưu loát và chuẩn xác. Đã đến lúc thử thách với ${target.getLabel(true)} (${target.cefr})!",
                reasonEn = "You have demonstrated excellent fluency and accuracy at ${currentLevel.displayName} (${currentLevel.cefr}). It's time to advance to ${target.displayName} (${target.cefr})!"
            )
        }

        // Criteria for LEVEL_DOWN:
        // Average score <= 4.5, or grammar <= 4 with high corrections (>= 4), and previous level exists
        val canLevelDown = currentLevel.previousLevel() != null &&
            (avgScore <= 4.5 || ((grammarScore ?: 5) <= 4 && correctionsCount >= 4) || ((fluencyScore ?: 5) <= 4 && avgScore <= 5.0))

        if (canLevelDown) {
            val target = currentLevel.previousLevel()!!
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.LEVEL_DOWN,
                targetLevel = target,
                reasonVi = "Cấp độ hiện tại đang hơi quá sức và gặp nhiều trở ngại ngữ pháp. Hãy chuyển sang ${target.getLabel(true)} (${target.cefr}) để xây chắc nền tảng và tự tin hơn!",
                reasonEn = "The current level seems challenging with several grammar obstacles. Switching to ${target.displayName} (${target.cefr}) will help you build solid confidence and fundamentals!"
            )
        }

        // Optimal learning zone -> KEEP
        return LevelRecommendation(
            direction = LevelAdjustmentDirection.KEEP,
            targetLevel = currentLevel,
            reasonVi = "Cấp độ ${currentLevel.getLabel(true)} (${currentLevel.cefr}) đang là vùng rèn luyện lý tưởng nhất cho bạn. Hãy tiếp tục duy trì phong độ!",
            reasonEn = "The ${currentLevel.displayName} (${currentLevel.cefr}) level is currently your optimal learning zone. Keep up the great practice!"
        )
    }

    /**
     * Evaluates a trend across multiple recent sessions to provide a longitudinal roadmap recommendation.
     */
    fun evaluateTrend(
        currentLevel: DifficultyLevel,
        recentAverages: List<Double>,
        recentCorrections: List<Int>
    ): LevelRecommendation {
        if (recentAverages.size < 2) {
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.KEEP,
                targetLevel = currentLevel,
                reasonVi = "Cần thêm dữ liệu buổi học để đánh giá lộ trình dài hạn.",
                reasonEn = "Need more session data to evaluate long-term roadmap."
            )
        }

        val overallAvg = recentAverages.average()
        val totalCorrections = recentCorrections.sum()

        if (currentLevel.nextLevel() != null && overallAvg >= 8.2 && totalCorrections <= recentCorrections.size * 2) {
            val target = currentLevel.nextLevel()!!
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.LEVEL_UP,
                targetLevel = target,
                reasonVi = "Bạn liên tục đạt kết quả xuất sắc trong các buổi học gần đây. Lộ trình học tập khuyến nghị bạn nên thăng cấp lên ${target.getLabel(true)} (${target.cefr})!",
                reasonEn = "You have consistently achieved outstanding results across recent sessions. We recommend advancing to ${target.displayName} (${target.cefr})!"
            )
        }

        // The summary lists at most five corrections, so a long, good lesson can still reach four or five:
        // many corrections only count against the learner when the scores are mediocre too.
        val manyCorrections = totalCorrections >= recentCorrections.size * 4 && overallAvg < 6.5
        if (currentLevel.previousLevel() != null && (overallAvg <= 4.8 || manyCorrections)) {
            val target = currentLevel.previousLevel()!!
            return LevelRecommendation(
                direction = LevelAdjustmentDirection.LEVEL_DOWN,
                targetLevel = target,
                reasonVi = "Dữ liệu các buổi học gần đây cho thấy việc điều chỉnh về ${target.getLabel(true)} (${target.cefr}) sẽ giúp bạn củng cố kiến thức và nói lưu loát hơn.",
                reasonEn = "Based on recent sessions, adjusting to ${target.displayName} (${target.cefr}) will strengthen your fundamentals and improve fluency."
            )
        }

        return LevelRecommendation(
            direction = LevelAdjustmentDirection.KEEP,
            targetLevel = currentLevel,
            reasonVi = "Bạn đang tiến bộ rất đều đặn ở cấp độ ${currentLevel.getLabel(true)}. Hãy tiếp tục hoàn thành lộ trình hiện tại nhé!",
            reasonEn = "You are progressing steadily at ${currentLevel.displayName}. Continue with your current roadmap!"
        )
    }
}
