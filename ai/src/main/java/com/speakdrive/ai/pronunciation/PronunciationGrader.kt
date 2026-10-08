package com.speakdrive.ai.pronunciation

import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.PronunciationStrictness
import java.util.Locale

enum class WordStatus {
    /** Heard as expected. */
    OK,

    /** Heard as a different word, e.g. "tree" for "three". */
    WRONG,

    /** Not heard at all. */
    MISSING
}

data class WordResult(
    val word: String,
    val status: WordStatus,
    val heardAs: String? = null,
    /** Azure accuracy for this word (0–100) when Azure grading is on. */
    val azureScore: Int? = null,
    /** Azure error type (Mispronunciation, Omission…) when Azure flagged the word. */
    val azureError: String? = null,
    /** Azure judged this word as needing work (bad word score, error type or a weak sound). */
    val azureFlagged: Boolean = false,
    /** The AI model judged this word as having pronunciation or replacement issues from live audio. */
    val modelFlagged: Boolean = false
) {
    /** True if any judge found a problem with this word. */
    val isProblem: Boolean
        get() = status != WordStatus.OK || azureFlagged || modelFlagged
}

/** One "repeat after me" attempt, judged by both the AI (from the audio) and the transcript. */
data class PronunciationAttempt(
    val target: String,
    val heard: String,
    val words: List<WordResult>,
    /** Share of target words pronounced correctly without problems, 0–100. */
    val accuracyPercent: Int,
    /** What the AI judged from the audio before seeing the transcript comparison. */
    val modelSaidCorrect: Boolean,
    val modelProblemWords: List<String>,
    val modelNotes: String,
    val attemptNumber: Int,
    val passed: Boolean,
    val timestamp: Long,
    /** Azure Pronunciation Assessment, when it was switched on and reachable. */
    val azure: AzureAssessment? = null,
    /** Why Azure grading was skipped although it is switched on, e.g. a network error. */
    val azureError: String? = null,
    val strictness: PronunciationStrictness = PronunciationStrictness.AUTO
) {
    /** Words the learner should work on, from any judge. */
    val problemWords: List<String>
        get() {
            val resolved = strictness.resolveForLevel(DifficultyLevel.INTERMEDIATE)
            return (modelProblemWords + words.filter { it.isProblem }.map { it.word } + azure?.problemWords(resolved).orEmpty().map { it.word })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.US) }
        }
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
        timestamp: Long,
        azure: AzureAssessment? = null,
        azureError: String? = null,
        strictness: PronunciationStrictness = PronunciationStrictness.AUTO,
        level: DifficultyLevel = DifficultyLevel.INTERMEDIATE
    ): PronunciationAttempt {
        val effectiveStrictness = strictness.resolveForLevel(level)
        val comparedWords = compare(target, heard)
        val withAzure = attachAzureScores(comparedWords, azure, effectiveStrictness)
        val words = attachModelProblems(withAzure, modelProblemWords, modelNotes)
        val mismatches = words.count { it.isProblem }
        val accuracy = if (words.isEmpty()) 0 else (100 * words.count { !it.isProblem } / words.size)
        val allowedMismatches = when (effectiveStrictness) {
            PronunciationStrictness.BEGINNER -> maxOf(2, words.size / 2)
            PronunciationStrictness.ELEMENTARY -> maxOf(1, words.size / 3)
            PronunciationStrictness.PRE_INTERMEDIATE -> if (words.size >= 6) 2 else 1
            PronunciationStrictness.INTERMEDIATE -> if (words.size >= 8) 1 else 0
            PronunciationStrictness.UPPER_INTERMEDIATE -> if (words.size >= 12) 1 else 0
            PronunciationStrictness.ADVANCED -> 0
            else -> if (words.size >= 8) 1 else 0
        }
        val allowedMissing = when (effectiveStrictness) {
            PronunciationStrictness.BEGINNER -> maxOf(1, words.size / 3)
            PronunciationStrictness.ELEMENTARY -> 1
            PronunciationStrictness.PRE_INTERMEDIATE -> if (words.size >= 8) 1 else 0
            PronunciationStrictness.INTERMEDIATE -> 0
            PronunciationStrictness.UPPER_INTERMEDIATE -> 0
            PronunciationStrictness.ADVANCED -> 0
            else -> 0
        }
        val passed = modelSaidCorrect &&
            modelProblemWords.none { it.isNotBlank() } &&
            heard.isNotBlank() &&
            words.count { it.status == WordStatus.MISSING } <= allowedMissing &&
            mismatches <= allowedMismatches &&
            (azure == null || azure.isPassed(effectiveStrictness))
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
            timestamp = timestamp,
            azure = azure,
            azureError = azureError,
            strictness = strictness
        )
    }

    /**
     * Puts Azure's per-word scores next to the target words. Azure reports the reference words in
     * order (plus inserted extras), so they are matched in sequence by spelling.
     */
    private fun attachAzureScores(
        words: List<WordResult>,
        azure: AzureAssessment?,
        strictness: PronunciationStrictness = PronunciationStrictness.AUTO
    ): List<WordResult> {
        if (azure == null) return words
        val reference = azure.words.filter { it.errorType != AzureWord.ERROR_INSERTION }
        var index = 0
        var lastDisplay: String? = null
        var lastMatch: AzureWord? = null
        return words.map { word ->
            // Expanded contractions ("I'd" -> i, would) share one Azure word.
            val match = if (word.word == lastDisplay) {
                lastMatch
            } else {
                val key = simplify(word.word)
                val found = (index until reference.size).firstOrNull { simplify(reference[it].word) == key }
                found?.let { index = it + 1; reference[it] }
            }
            lastDisplay = word.word
            lastMatch = match
            if (match == null) {
                word
            } else {
                word.copy(
                    azureScore = match.accuracy,
                    azureError = match.errorType.takeIf { it != "None" },
                    azureFlagged = match.needsWork(strictness)
                )
            }
        }
    }

    /**
     * Tags words that the AI model identified as having issues (mispronounced, replaced, dropped)
     * either in [modelProblemWords] or described in [modelNotes].
     */
    private fun attachModelProblems(
        words: List<WordResult>,
        modelProblemWords: List<String>,
        modelNotes: String
    ): List<WordResult> {
        if (words.isEmpty()) return words

        val targetHeardPairs = mutableMapOf<String, String>() // targetKey -> heardWord
        val targetProblems = mutableSetOf<String>() // targetKey

        // 1. Parse modelNotes
        if (modelNotes.isNotBlank()) {
            // Pattern: said 'project' instead of 'budget'
            val saidInsteadRegex = Regex(
                """(?:said|pronounced|spoke|used)\s+['"“]([^'"”]+)['"”]\s+instead\s+of\s+['"“]([^'"”]+)['"”]""",
                RegexOption.IGNORE_CASE
            )
            saidInsteadRegex.findAll(modelNotes).forEach { match ->
                val heard = match.groupValues[1].trim()
                val target = match.groupValues[2].trim()
                val key = simplify(target)
                if (key.isNotEmpty()) {
                    targetProblems += key
                    if (heard.isNotEmpty()) targetHeardPairs[key] = heard
                }
            }

            // Pattern: 'budget' sounded like 'project' / was pronounced as 'project'
            val soundedLikeRegex = Regex(
                """['"“]([^'"”]+)['"”]\s+(?:sounded\s+like|was\s+pronounced\s+as)\s+['"“]([^'"”]+)['"”]""",
                RegexOption.IGNORE_CASE
            )
            soundedLikeRegex.findAll(modelNotes).forEach { match ->
                val target = match.groupValues[1].trim()
                val heard = match.groupValues[2].trim()
                val key = simplify(target)
                if (key.isNotEmpty()) {
                    targetProblems += key
                    if (heard.isNotEmpty()) targetHeardPairs[key] = heard
                }
            }

            // Pattern: instead of 'budget'
            val insteadOfRegex = Regex("""instead\s+of\s+['"“]([^'"”]+)['"”]""", RegexOption.IGNORE_CASE)
            insteadOfRegex.findAll(modelNotes).forEach { match ->
                val target = match.groupValues[1].trim()
                val key = simplify(target)
                if (key.isNotEmpty()) targetProblems += key
            }

            // Pattern: mispronounced 'budget', trouble with 'budget', dropped the final 't' in 'budget'
            val mispronouncedRegex = Regex(
                """(?:mispronounced|unclear|trouble\s+with|problem\s+with|dropped.*in)\s+['"“]([^'"”]+)['"”]""",
                RegexOption.IGNORE_CASE
            )
            mispronouncedRegex.findAll(modelNotes).forEach { match ->
                val target = match.groupValues[1].trim()
                val key = simplify(target)
                if (key.isNotEmpty()) targetProblems += key
            }
        }

        // 2. Parse modelProblemWords
        val targetKeys = words.map { simplify(it.word) }.toSet()
        val problemTokens = modelProblemWords
            .flatMap { it.split(Regex("[\\s,;]+")) }
            .map { simplify(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        problemTokens.forEach { token ->
            if (token in targetKeys) {
                targetProblems += token
            }
        }

        // If modelProblemWords has an outside word (e.g. "project") and we know the target problem word
        val outsideWords = problemTokens.filter { it !in targetKeys }
        if (outsideWords.isNotEmpty() && targetProblems.size == 1) {
            val targetKey = targetProblems.first()
            if (!targetHeardPairs.containsKey(targetKey)) {
                targetHeardPairs[targetKey] = outsideWords.first()
            }
        }

        // Fallback: if targetProblems is still empty, look for any quoted target words in modelNotes
        if (targetProblems.isEmpty() && modelNotes.isNotBlank()) {
            val quotedWordsRegex = Regex("""['"“]([^'"”]+)['"”]""")
            quotedWordsRegex.findAll(modelNotes).forEach { match ->
                val quoted = simplify(match.groupValues[1].trim())
                if (quoted in targetKeys) {
                    targetProblems += quoted
                }
            }
        }

        if (targetProblems.isEmpty()) return words

        return words.map { word ->
            val key = simplify(word.word)
            if (key in targetProblems) {
                word.copy(
                    modelFlagged = true,
                    heardAs = word.heardAs ?: targetHeardPairs[key]
                )
            } else {
                word
            }
        }
    }

    private fun simplify(word: String) = word.lowercase(Locale.US).filter { it.isLetterOrDigit() || it == '\'' }

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

    /**
     * How much of [heard] matches [target] word for word, from 0 to 1 (words heard in place divided
     * by the longer of the two). Used to tell a learner repeating a sentence from a spoken command.
     */
    fun similarity(target: String, heard: String): Double {
        val targetWords = tokenize(target).size
        val heardWords = tokenize(heard).size
        val longer = maxOf(targetWords, heardWords)
        if (longer == 0) return 0.0
        val matched = compare(target, heard).count { it.status == WordStatus.OK }
        return matched.toDouble() / longer
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
