package com.speakdrive.ai.pronunciation

import java.util.Locale

enum class WordStatus {
    /** Heard as expected. */
    OK,

    /** Heard as a different word, e.g. "tree" for "three". */
    WRONG,

    /** Not heard at all. */
    MISSING
}

data class WordResult(val word: String, val status: WordStatus, val heardAs: String? = null)

/** One "repeat after me" attempt, judged by both the AI (from the audio) and the transcript. */
data class PronunciationAttempt(
    val target: String,
    val heard: String,
    val words: List<WordResult>,
    /** Share of target words the speech recogniser heard correctly, 0–100. */
    val accuracyPercent: Int,
    /** What the AI judged from the audio before seeing the transcript comparison. */
    val modelSaidCorrect: Boolean,
    val modelProblemWords: List<String>,
    val modelNotes: String,
    val attemptNumber: Int,
    val passed: Boolean,
    val timestamp: Long
) {
    /** Words the learner should work on, from either judge. */
    val problemWords: List<String>
        get() = (modelProblemWords + words.filter { it.status != WordStatus.OK }.map { it.word })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
}

/**
 * Objective half of the pronunciation check. The live model listens to the audio and gives its
 * own verdict; this compares the speech-recognition transcript with the target sentence word by
 * word. An attempt passes only when **both** agree, so the AI cannot praise an attempt the
 * transcript shows was wrong, and a clean transcript cannot override a problem the AI heard.
 */
object PronunciationGrader {

    const val MAX_ATTEMPTS_PER_SENTENCE = 3

    /** Sentences this long may have one recognition mismatch and still pass (recognisers make mistakes too). */
    private const val LONG_SENTENCE_WORDS = 10

    fun grade(
        target: String,
        heard: String,
        modelSaidCorrect: Boolean,
        modelProblemWords: List<String>,
        modelNotes: String,
        attemptNumber: Int,
        timestamp: Long
    ): PronunciationAttempt {
        val words = compare(target, heard)
        val mismatches = words.count { it.status != WordStatus.OK }
        val accuracy = if (words.isEmpty()) 0 else (100 * words.count { it.status == WordStatus.OK } / words.size)
        val allowedMismatches = if (words.size >= LONG_SENTENCE_WORDS) 1 else 0
        val passed = modelSaidCorrect &&
            modelProblemWords.none { it.isNotBlank() } &&
            heard.isNotBlank() &&
            words.none { it.status == WordStatus.MISSING } &&
            mismatches <= allowedMismatches
        return PronunciationAttempt(
            target = target.trim(),
            heard = heard.trim(),
            words = words,
            accuracyPercent = accuracy,
            modelSaidCorrect = modelSaidCorrect,
            modelProblemWords = modelProblemWords.filter { it.isNotBlank() },
            modelNotes = modelNotes.trim(),
            attemptNumber = attemptNumber,
            passed = passed,
            timestamp = timestamp
        )
    }

    /** Aligns the heard words to the target words (edit distance); extra heard words are ignored. */
    fun compare(target: String, heard: String): List<WordResult> {
        val targetTokens = tokenize(target)
        val heardTokens = tokenize(heard)
        val expected = targetTokens.map { it.normalized }
        val actual = heardTokens.map { it.normalized }

        // cost[i][j] = edits to turn expected[i..] into actual[j..]
        val cost = Array(expected.size + 1) { IntArray(actual.size + 1) }
        for (i in expected.size downTo 0) {
            for (j in actual.size downTo 0) {
                cost[i][j] = when {
                    i == expected.size -> actual.size - j
                    j == actual.size -> expected.size - i
                    expected[i] == actual[j] -> cost[i + 1][j + 1]
                    else -> 1 + minOf(cost[i + 1][j + 1], cost[i + 1][j], cost[i][j + 1])
                }
            }
        }

        val results = mutableListOf<WordResult>()
        var i = 0
        var j = 0
        while (i < expected.size) {
            when {
                j < actual.size && expected[i] == actual[j] && cost[i][j] == cost[i + 1][j + 1] -> {
                    results += WordResult(targetTokens[i].display, WordStatus.OK)
                    i++; j++
                }
                j < actual.size && cost[i][j] == 1 + cost[i + 1][j + 1] -> {
                    results += WordResult(targetTokens[i].display, WordStatus.WRONG, heardTokens[j].display)
                    i++; j++
                }
                cost[i][j] == 1 + cost[i + 1][j] -> {
                    results += WordResult(targetTokens[i].display, WordStatus.MISSING)
                    i++
                }
                else -> j++ // an extra heard word
            }
        }
        return results
    }

    private data class Token(val display: String, val normalized: String)

    private fun tokenize(text: String): List<Token> =
        text.replace('’', '\'')
            .split(Regex("[\\s\\-–—]+"))
            .map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' } }
            .filter { it.isNotEmpty() }
            .flatMap { raw ->
                val lower = raw.lowercase(Locale.US).trim('\'')
                val expanded = CONTRACTIONS[lower] ?: NUMBERS[lower]?.let { listOf(it) } ?: listOf(lower)
                // "I'm" shows as one word but compares as "i am"; keep the original for display.
                expanded.mapIndexed { index, part -> Token(if (index == 0) raw else "", part) }
            }
            .let(::mergeDisplay)

    /** Gives expanded contraction parts a readable display ("I'm" → "I'm", "(I'm)"). */
    private fun mergeDisplay(tokens: List<Token>): List<Token> {
        var lastDisplay = ""
        return tokens.map { token ->
            if (token.display.isNotEmpty()) {
                lastDisplay = token.display
                token
            } else {
                token.copy(display = lastDisplay)
            }
        }
    }

    private val CONTRACTIONS = mapOf(
        "i'm" to listOf("i", "am"), "you're" to listOf("you", "are"), "we're" to listOf("we", "are"),
        "they're" to listOf("they", "are"), "he's" to listOf("he", "is"), "she's" to listOf("she", "is"),
        "it's" to listOf("it", "is"), "that's" to listOf("that", "is"), "there's" to listOf("there", "is"),
        "what's" to listOf("what", "is"), "where's" to listOf("where", "is"), "let's" to listOf("let", "us"),
        "i've" to listOf("i", "have"), "you've" to listOf("you", "have"), "we've" to listOf("we", "have"),
        "they've" to listOf("they", "have"), "i'll" to listOf("i", "will"), "you'll" to listOf("you", "will"),
        "we'll" to listOf("we", "will"), "they'll" to listOf("they", "will"), "it'll" to listOf("it", "will"),
        "i'd" to listOf("i", "would"), "you'd" to listOf("you", "would"), "we'd" to listOf("we", "would"),
        "don't" to listOf("do", "not"), "doesn't" to listOf("does", "not"), "didn't" to listOf("did", "not"),
        "isn't" to listOf("is", "not"), "aren't" to listOf("are", "not"), "wasn't" to listOf("was", "not"),
        "weren't" to listOf("were", "not"), "haven't" to listOf("have", "not"), "hasn't" to listOf("has", "not"),
        "can't" to listOf("can", "not"), "cannot" to listOf("can", "not"), "won't" to listOf("will", "not"),
        "wouldn't" to listOf("would", "not"), "shouldn't" to listOf("should", "not"), "couldn't" to listOf("could", "not"),
        "okay" to listOf("ok")
    )

    private val NUMBERS = mapOf(
        "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four", "5" to "five", "6" to "six",
        "7" to "seven", "8" to "eight", "9" to "nine", "10" to "ten", "11" to "eleven", "12" to "twelve",
        "15" to "fifteen", "20" to "twenty", "30" to "thirty"
    )
}
