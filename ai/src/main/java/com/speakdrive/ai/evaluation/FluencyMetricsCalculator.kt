package com.speakdrive.ai.evaluation

import com.speakdrive.ai.model.ObjectiveFluencyMetrics
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import java.util.Locale

/**
 * Calculates objective fluency metrics from user speech transcript turns:
 * 1. Words Per Minute (WPM)
 * 2. Filler words count & ratio ("uh", "um", "er", "ah", "like", "you know", "i mean", "actually", "basically", "so yeah")
 * 3. Mean Length of Utterance (MLU) in words
 * 4. Vietnamese words ratio (Vietnamese diacritics and common words)
 */
object FluencyMetricsCalculator {

    private val FILLER_WORDS = setOf(
        "uh", "um", "er", "ah", "like", "actually", "basically", "literally"
    )

    private val MULTI_WORD_FILLERS = listOf(
        "you know",
        "i mean",
        "sort of",
        "kind of",
        "so yeah",
        "and yeah"
    )

    // Regex to detect Vietnamese characters with diacritics
    private val VIETNAMESE_DIACRITICS_REGEX = Regex("[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]", RegexOption.IGNORE_CASE)

    // Common non-accented Vietnamese words when learners type or STT transcribes without accents
    private val COMMON_UNACCENTED_VIETNAMESE = setOf(
        "toi", "ban", "anh", "chi", "em", "khong", "dung", "roi", "nhung", "thi", "la", "ma",
        "sao", "gi", "the", "vay", "di", "ve", "an", "uong", "biet", "chua", "co"
    )

    /**
     * Calculates the metrics for a completed or in-progress session.
     *
     * @param transcript list of transcript turns
     * @param activeDurationMs duration spent actively in conversation (excluding pauses)
     */
    fun calculate(
        transcript: List<TranscriptTurn>,
        activeDurationMs: Long
    ): ObjectiveFluencyMetrics {
        val userTurns = transcript.filter { it.speaker == Speaker.USER }
        if (userTurns.isEmpty()) {
            return ObjectiveFluencyMetrics(
                wordsPerMinute = 0,
                fillerWordsCount = 0,
                fillerWordsRatio = 0f,
                meanLengthOfUtterance = 0f,
                vietnameseWordsRatio = 0f
            )
        }

        var totalWords = 0
        var fillerCount = 0
        var vietnameseWordsCount = 0

        for (turn in userTurns) {
            val text = turn.text.lowercase(Locale.ROOT)
            val textWithoutMultiFillers = text

            // 1. Check multi-word fillers
            for (filler in MULTI_WORD_FILLERS) {
                var index = 0
                while (true) {
                    val found = textWithoutMultiFillers.indexOf(filler, startIndex = index)
                    if (found == -1) break
                    val before = if (found > 0) textWithoutMultiFillers[found - 1] else ' '
                    val after = if (found + filler.length < textWithoutMultiFillers.length) textWithoutMultiFillers[found + filler.length] else ' '
                    if (!before.isLetter() && !after.isLetter()) {
                        fillerCount++
                    }
                    index = found + filler.length
                }
            }

            // 2. Tokenize words
            val rawWords = text.split(Regex("[^\\p{L}\\p{Nd}'-]+")).filter { it.isNotBlank() }
            totalWords += rawWords.size

            for (word in rawWords) {
                val cleanWord = word.trim('\'', '-')
                if (cleanWord.isBlank()) continue

                if (FILLER_WORDS.contains(cleanWord)) {
                    fillerCount++
                }

                if (VIETNAMESE_DIACRITICS_REGEX.containsMatchIn(cleanWord) || COMMON_UNACCENTED_VIETNAMESE.contains(cleanWord)) {
                    vietnameseWordsCount++
                }
            }
        }

        val durationMinutes = if (activeDurationMs > 0) {
            activeDurationMs / 60000.0
        } else {
            (userTurns.size * 10) / 60.0
        }

        val wpm = if (durationMinutes > 0.05) {
            (totalWords / durationMinutes).toInt().coerceAtMost(300)
        } else {
            0
        }

        val fillerRatio = if (totalWords > 0) {
            (fillerCount.toFloat() / totalWords).coerceIn(0f, 1f)
        } else {
            0f
        }

        val mlu = if (userTurns.isNotEmpty()) {
            (totalWords.toFloat() / userTurns.size)
        } else {
            0f
        }

        val vietnameseRatio = if (totalWords > 0) {
            (vietnameseWordsCount.toFloat() / totalWords).coerceIn(0f, 1f)
        } else {
            0f
        }

        return ObjectiveFluencyMetrics(
            wordsPerMinute = wpm,
            fillerWordsCount = fillerCount,
            fillerWordsRatio = (fillerRatio * 100).toInt() / 100f,
            meanLengthOfUtterance = (mlu * 10).toInt() / 10f,
            vietnameseWordsRatio = (vietnameseRatio * 100).toInt() / 100f
        )
    }
}
