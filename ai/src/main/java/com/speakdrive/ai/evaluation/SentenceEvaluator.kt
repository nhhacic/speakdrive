package com.speakdrive.ai.evaluation

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class SentenceStatus {
    EXCELLENT,
    GOOD,
    TOO_SHORT,
    MISSING_WORD,
    EMPTY
}

data class SentenceEvaluationResult(
    val status: SentenceStatus,
    val feedbackVi: String,
    val feedbackEn: String,
    val nativeUpgrade: String? = null,
    val containsTargetWord: Boolean = false,
    val wordCount: Int = 0
)

/**
 * Evaluates student sentences constructed with vocabulary words.
 * Verifies presence of target word (including inflections and conjugations),
 * checks grammatical structure, and offers native speaker phrasing upgrades.
 */
@Singleton
class SentenceEvaluator @Inject constructor() {

    fun evaluate(targetWord: String, sentence: String): SentenceEvaluationResult {
        val trimmedWord = targetWord.trim().lowercase(Locale.US)
        val trimmedSentence = sentence.trim()

        if (trimmedSentence.isBlank()) {
            return SentenceEvaluationResult(
                status = SentenceStatus.EMPTY,
                feedbackVi = "Hãy nhập hoặc nói một câu tiếng Anh có chứa từ \"$targetWord\".",
                feedbackEn = "Please enter or speak an English sentence containing \"$targetWord\".",
                containsTargetWord = false,
                wordCount = 0
            )
        }

        val words = trimmedSentence.split(Regex("[^a-zA-Z0-9'-]+"))
            .filter { it.isNotBlank() }
        val wordCount = words.size

        val containsWord = containsTargetWordOrInflection(trimmedWord, words)

        if (!containsWord) {
            return SentenceEvaluationResult(
                status = SentenceStatus.MISSING_WORD,
                feedbackVi = "Câu của bạn chưa chứa từ vựng \"$targetWord\" (hoặc các biến thể thì/dạng từ). Hãy thử lại nhé!",
                feedbackEn = "Your sentence does not contain the word \"$targetWord\" (or its variations). Give it another try!",
                containsTargetWord = false,
                wordCount = wordCount
            )
        }

        if (wordCount < 3) {
            return SentenceEvaluationResult(
                status = SentenceStatus.TOO_SHORT,
                feedbackVi = "Câu hơi ngắn ($wordCount từ). Hãy thêm chủ ngữ hoặc ngữ cảnh cụ thể (tối thiểu 3-4 từ) để luyện phản xạ tốt hơn!",
                feedbackEn = "The sentence is quite short ($wordCount words). Try expanding with a subject or context for better practice!",
                containsTargetWord = true,
                wordCount = wordCount
            )
        }

        // Generate polished native upgrade
        val upgrade = generateNativeUpgrade(trimmedWord, trimmedSentence)

        val status = if (wordCount >= 6) SentenceStatus.EXCELLENT else SentenceStatus.GOOD
        val feedbackVi = if (status == SentenceStatus.EXCELLENT) {
            "Xuất sắc! Câu của bạn rất hoàn chỉnh, tự nhiên và áp dụng chính xác từ \"$targetWord\"."
        } else {
            "Rất tốt! Bạn đã sử dụng từ \"$targetWord\" đúng ngữ cảnh."
        }
        val feedbackEn = if (status == SentenceStatus.EXCELLENT) {
            "Excellent! Your sentence is complete, natural, and accurately uses \"$targetWord\"."
        } else {
            "Great job! You used \"$targetWord\" correctly in context."
        }

        return SentenceEvaluationResult(
            status = status,
            feedbackVi = feedbackVi,
            feedbackEn = feedbackEn,
            nativeUpgrade = upgrade,
            containsTargetWord = true,
            wordCount = wordCount
        )
    }

    /**
     * Checks if any word in the sentence matches [targetWord] or common English inflections:
     * - Plural / 3rd person singular: -s, -es, -ies
     * - Past tense / Participle: -ed, -d, -ied
     * - Continuous / Gerund: -ing
     * - Adverb / Adjective: -ly, -er, -est, -ful, -able, -tion, -ment
     */
    fun containsTargetWordOrInflection(target: String, sentenceWords: List<String>): Boolean {
        val normTarget = target.lowercase(Locale.US)
        val targetStem = getStem(normTarget)

        for (w in sentenceWords) {
            val normW = w.lowercase(Locale.US)
            if (normW == normTarget) return true

            val wordStem = getStem(normW)
            if (wordStem == targetStem) return true

            // Check prefix stem match
            if (targetStem.length >= 3 && (normW.startsWith(targetStem) || (wordStem.length >= 3 && normTarget.startsWith(wordStem)))) {
                return true
            }
        }
        return false
    }

    private fun getStem(word: String): String {
        var stem = word
        val suffixes = listOf("ingly", "fully", "ation", "ment", "ness", "able", "ible", "ying", "ing", "ies", "ied", "ed", "es", "ly", "er", "est", "s", "d", "y")
        for (suffix in suffixes) {
            if (stem.length > suffix.length + 2 && stem.endsWith(suffix)) {
                stem = stem.removeSuffix(suffix)
                break
            }
        }
        return stem
    }

    private fun generateNativeUpgrade(target: String, userSentence: String): String {
        val s = userSentence.trim().trimEnd('.', '!', '?')
        val firstCharCapitalized = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

        // If the user's sentence does not end with punctuation, add it
        val cleanSentence = "$firstCharCapitalized."

        // Example native conversational upgrade
        return "💡 Gợi ý nâng cao từ người bản xứ: \"Honestly, $s whenever possible!\" hoặc \"$cleanSentence\""
    }
}
