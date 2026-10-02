package com.speakdrive.ai.pronunciation

import com.speakdrive.ai.live.LiveTool
import com.speakdrive.ai.live.LiveToolParam
import java.util.Locale

/**
 * The "repeat after me" drill: the tool the model must call after every attempt, and the
 * instructions it gets back. The app's verdict is final, so the AI cannot flatter.
 */
object PronunciationDrill {

    const val CHECK_ATTEMPT_FUNCTION = "check_attempt"

    val checkAttemptTool = LiveTool(
        name = CHECK_ATTEMPT_FUNCTION,
        description = "Grades the learner's attempt at repeating a sentence. Call it right after EVERY attempt, " +
            "before saying anything about the attempt, then follow the returned final_verdict and instruction exactly.",
        parameters = listOf(
            LiveToolParam("target_sentence", LiveToolParam.Type.STRING, "Exactly the sentence the learner was asked to repeat."),
            LiveToolParam(
                "verdict",
                LiveToolParam.Type.STRING,
                "Your own strict judgement from the AUDIO: \"correct\" only if every word and sound was clearly right, otherwise \"needs_work\"."
            ),
            LiveToolParam(
                "problem_words",
                LiveToolParam.Type.STRING_LIST,
                "Words that were mispronounced, missing, changed or unclear. Empty only if the verdict is correct.",
                optional = true
            ),
            LiveToolParam(
                "problem_notes",
                LiveToolParam.Type.STRING,
                "Short note on what was wrong, e.g. \"dropped the final t in 'want'; 'th' in 'three' sounded like 't'\".",
                optional = true
            )
        )
    )

    /** The result sent back to the model, including what it must say next. */
    fun toolResponse(attempt: PronunciationAttempt): Map<String, Any> {
        val wordsToFix = attempt.problemWords
        val misheard = attempt.words.filter { it.status != WordStatus.OK }
            .joinToString("; ") { if (it.status == WordStatus.MISSING) "'${it.word}' was not heard" else "'${it.word}' sounded like '${it.heardAs}'" }
        val lastAttempt = attempt.attemptNumber >= PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE
        val instruction = when {
            attempt.heard.isBlank() ->
                "You could not hear the learner. Ask them to repeat the sentence a little louder. Do not grade it."
            attempt.passed ->
                "The attempt is correct. Confirm it honestly in a few words (for example \"Correct.\" or \"Clear and accurate.\"), " +
                    "no exaggerated praise, then give the next sentence."
            lastAttempt ->
                "The attempt is NOT correct. Do not call it correct, good or close. Say honestly that it still needs practice, " +
                    "name the words to work on (${wordsToFix.joinToString()}), say each one slowly once, tell them it will come " +
                    "back in a later review, then give the next sentence."
            else ->
                "The attempt is NOT correct. Do not call it correct, good, great or close. Name the exact words to fix " +
                    "(${wordsToFix.joinToString()}). For each, say the word slowly and clearly twice and describe the sound in " +
                    "one short phrase. Then ask the learner to repeat the whole sentence again."
        }
        return buildMap {
            put("final_verdict", if (attempt.passed) "correct" else "needs_work")
            put("heard_by_speech_recognition", attempt.heard)
            put("word_accuracy_percent", attempt.accuracyPercent)
            put("words_to_fix", wordsToFix)
            if (misheard.isNotEmpty()) put("speech_recognition_differences", misheard)
            put("attempt_number", attempt.attemptNumber)
            put("max_attempts", PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE)
            put("instruction", instruction)
        }
    }

    /** Pulls the sentence out of "Repeat after me: I'd like a window seat, please." */
    fun extractTarget(aiText: String): String? {
        val match = TARGET_REGEX.find(aiText) ?: return null
        return match.groupValues[1].trim().trim('"', '“', '”', '\'').takeIf { it.split(' ').size >= 2 }
    }

    /** Share of sentences the learner eventually got right, 0–100; null if nothing was attempted. */
    fun score(attempts: List<PronunciationAttempt>): Int? {
        val bySentence = attempts.groupBy { key(it.target) }
        if (bySentence.isEmpty()) return null
        val passed = bySentence.values.count { tries -> tries.any { it.passed } }
        return 100 * passed / bySentence.size
    }

    fun key(sentence: String): String =
        sentence.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val TARGET_REGEX = Regex("(?i)repeat after me\\W*(.+)")
}
