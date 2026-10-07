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
                "List of exact target words from target_sentence that were mispronounced, missing, changed or unclear. Always list the target words as spelled in target_sentence. Empty only if the verdict is correct.",
                optional = true
            ),
            LiveToolParam(
                "problem_notes",
                LiveToolParam.Type.STRING,
                "Short note on what was wrong, e.g. \"The learner said 'project' instead of 'budget'\". If a word was replaced or mispronounced, use format: said '<heard>' instead of '<target>'.",
                optional = true
            )
        )
    )

    /** The result sent back to the model, including what it must say next. */
    fun toolResponse(attempt: PronunciationAttempt): Map<String, Any> {
        val wordsToFix = attempt.problemWords
        val azureProblems = attempt.azure?.describeProblems(attempt.strictness).orEmpty()
        val misheard = attempt.words.filter { it.status != WordStatus.OK }
            .joinToString("; ") { if (it.status == WordStatus.MISSING) "'${it.word}' was not heard" else "'${it.word}' sounded like '${it.heardAs}'" }
        val lastAttempt = attempt.attemptNumber >= PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE
        val instruction = when {
            attempt.heard.isBlank() ->
                "You could not hear the learner. Ask them to repeat the sentence a little louder. Do not grade it."
            attempt.passed ->
                "The attempt is correct. Confirm it honestly in a few words (for example \"Correct.\" or \"Clear and accurate.\"), " +
                    "no exaggerated praise, then introduce the next sentence (provide a fresh, brand new sentence that has not been used yet in this session). You MUST ALWAYS start the next sentence with: \"Repeat after me: <sentence>\"."
            lastAttempt ->
                "The attempt is NOT correct. Do not call it correct, good or close. Say honestly that it still needs practice, " +
                    "name the words to work on (${wordsToFix.joinToString()}), say each one slowly once, tell them it will come " +
                    "back in a later review, then introduce the next sentence (provide a fresh, brand new sentence). You MUST ALWAYS start the next sentence with: \"Repeat after me: <sentence>\"."
            else ->
                "The attempt is NOT correct. Do not call it correct, good, great or close. Name the exact words to fix " +
                    "(${wordsToFix.joinToString()}). For each, say the word slowly and clearly twice and describe the sound in " +
                    "one short phrase" + (if (azureProblems.isNotEmpty()) ", focusing on the weak sounds listed in azure_weak_sounds" else "") +
                    ". Then ask the learner to repeat the whole sentence again using: \"Repeat after me: <sentence>\"."
        }
        return buildMap {
            put("final_verdict", if (attempt.passed) "correct" else "needs_work")
            put("heard_by_speech_recognition", attempt.heard)
            put("word_accuracy_percent", attempt.accuracyPercent)
            put("words_to_fix", wordsToFix)
            if (misheard.isNotEmpty()) put("speech_recognition_differences", misheard)
            attempt.azure?.let { azure ->
                put(
                    "azure_scores",
                    mapOf(
                        "pronunciation" to azure.pronunciationScore,
                        "accuracy" to azure.accuracyScore,
                        "fluency" to azure.fluencyScore,
                        "completeness" to azure.completenessScore
                    )
                )
                if (azureProblems.isNotEmpty()) put("azure_weak_sounds", azureProblems)
            }
            put("attempt_number", attempt.attemptNumber)
            put("max_attempts", PronunciationGrader.MAX_ATTEMPTS_PER_SENTENCE)
            put("instruction", instruction)
        }
    }

    /**
     * Extracts the target practice sentence from what the AI coach said.
     * Supports various phrasing styles: "Repeat after me:", "Next sentence:", "Let's try:",
     * "the whole sentence again:", quoted sentences, acknowledge + sentence, and trims trailing prompt fillers.
     */
    fun extractTarget(aiText: String): String? {
        val trimmed = aiText.trim()
        if (trimmed.isBlank()) return null

        // 1. Explicit drill introduction prefixes (checking backwards from the latest prefix)
        val prefixMatches = PREFIX_KEYWORD_REGEX.findAll(trimmed).toList()
        for (prefixMatch in prefixMatches.reversed()) {
            val candidate = trimmed.substring(prefixMatch.range.last + 1)
            // If the candidate contains a quoted sentence (e.g. "He's still trying to solve that mystery..."),
            // prioritize the quoted text as the target.
            val quoteInCandidate = QUOTE_REGEX.findAll(candidate)
                .map { it.groupValues[1].trim() }
                .filter { quote ->
                    val w = quote.split(Regex("\\s+")).filter(String::isNotBlank)
                    w.size in 2..35
                }
                .lastOrNull()
            if (quoteInCandidate != null) {
                val cleanedQuote = cleanTargetSentence(quoteInCandidate)
                if (cleanedQuote != null) return cleanedQuote
            }

            val cleaned = cleanTargetSentence(candidate)
            if (cleaned != null) return cleaned
        }

        // 2. Multi-word quoted sentences (e.g. "I'd like a window seat, please.")
        // Filters out single words quoted during pronunciation feedback like "solve,"
        val quoteMatches = QUOTE_REGEX.findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .filter { quote ->
                val w = quote.split(Regex("\\s+")).filter(String::isNotBlank)
                w.size in 2..35
            }
            .toList()
        if (quoteMatches.isNotEmpty()) {
            val candidate = quoteMatches.last()
            val cleaned = cleanTargetSentence(candidate)
            if (cleaned != null) return cleaned
        }

        // 3. Short acknowledge/praise followed immediately by a target sentence
        // e.g. "Clear and accurate. Where is the check-in desk?"
        val ackMatch = ACK_THEN_SENTENCE_REGEX.find(trimmed)
        if (ackMatch != null) {
            val candidate = ackMatch.groupValues[1]
            val cleaned = cleanTargetSentence(candidate)
            if (cleaned != null) return cleaned
        }

        return null
    }

    /**
     * Cleans up the extracted candidate sentence by removing surrounding quotes,
     * leading punctuation/dashes, leading prompt clauses ("the whole sentence again:"),
     * and trailing filler phrases like "Now your turn" or "Go ahead".
     */
    fun cleanTargetSentence(raw: String): String? {
        var text = raw.trim().trim('"', '“', '”')
        // Strip leading colons, hyphens, dashes, commas
        text = text.replace(Regex("^[:\\-—,\\s]+"), "").trim()
        // Strip leading prompt clauses like "the whole sentence again:" or "saying:"
        text = text.replace(
            Regex(
                "^(?:(?:now\\s+)?(?:let's\\s+)?(?:try|say|repeat|practice)?\\s*(?:the\\s+)?(?:whole|full)\\s+sentence(?:\\s+again)?|" +
                    "once\\s+more|one\\s+more\\s+time|" +
                    "(?:thử|lặp|nhắc|đọc|nói)\\s+lại\\s+cả\\s+câu|" +
                    "saying|repeating|to say)[:\\s]+",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()
        if (text.isBlank()) return null

        // If the text ends with a colon, it's an intro clause (e.g. streaming "the whole sentence again:"), NOT a sentence
        if (text.endsWith(":") || text.replace(Regex("[\\s\"“”']+$"), "").endsWith(":")) return null

        // Strip inline trailing filler patterns like "... please go ahead" or "... now your turn"
        text = text.replace(INLINE_TRAILING_FILLER_REGEX, "").trim()

        // If there are multiple sentences separated by punctuation (. ! ?), check and drop trailing filler sentences
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.toMutableList()
        while (sentences.isNotEmpty()) {
            val last = sentences.last().trim()
            if (isFillerSentence(last)) {
                sentences.removeAt(sentences.lastIndex)
            } else {
                break
            }
        }
        val result = if (sentences.isNotEmpty()) sentences.joinToString(" ") else text
        val cleaned = result.trim().trim('"', '“', '”').trim()
        if (cleaned.endsWith(":") || cleaned.replace(Regex("[\\s\"“”']+$"), "").endsWith(":")) return null
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        return cleaned.takeIf { words.size in 2..35 }
    }

    private fun isFillerSentence(sentence: String): Boolean =
        FILLER_PATTERNS.any { it.matches(sentence) }

    /** Share of sentences the learner eventually got right, 0–100; null if nothing was attempted. */
    fun score(attempts: List<PronunciationAttempt>): Int? {
        val bySentence = attempts.groupBy { key(it.target) }
        if (bySentence.isEmpty()) return null
        val passed = bySentence.values.count { tries -> tries.any { it.passed } }
        return 100 * passed / bySentence.size
    }

    fun scorePercent(attempts: List<PronunciationAttempt>): Int? = score(attempts)

    fun key(sentence: String): String =
        sentence.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val PREFIX_KEYWORD_REGEX = Regex(
        "(?iu)(?:" +
            "repeat after me|" +
            "repeat with me|" +
            "repeat this(?: sentence| phrase)?|" +
            "please repeat(?: after me)?|" +
            "now repeat(?: after me)?|" +
            "say after me|" +
            "say with me|" +
            "say this|" +
            "now say|" +
            "please say|" +
            "listen and repeat|" +
            "(?:the |our |your |here is |here's )?next (?:sentence|phrase)(?: is)?|" +
            "(?:the |here is |here's )?next one(?: is)?|" +
            "next up(?: is)?|" +
            "another sentence(?: is)?|" +
            "another one(?: is)?|" +
            "let's (?:try|do|practice)(?: the next sentence| this sentence| this phrase| another one| the whole sentence(?: again)?| the full sentence(?: again)?)?|" +
            "(?:now )?try (?:this sentence|this phrase|this one|this|saying|repeating|the whole sentence(?: again)?|the full sentence(?: again)?)|" +
            "(?:now )?(?:it'?s )?your turn(?: to say)?|" +
            "(?:now )?(?:the )?(?:whole|full) sentence(?: again)?|" +
            "once more(?: time)?|" +
            "one more time|" +
            "now try|" +
            "how about (?:this|this one|this sentence|this phrase)?|" +
            "(?:can|could) you (?:say|repeat)(?!\\s+(?:that|it|again)\\b)|" +
            "(?:nhắc|nhac) lại(?: theo tôi| theo toi)?|" +
            "(?:lặp|lap) lại(?: theo tôi| theo toi)?|" +
            "(?:[đĐ]ọc|doc) theo (?:tôi|toi)|" +
            "(?:nói|noi) theo (?:tôi|toi)|" +
            "(?:câu|cau) (?:tiếp theo|tiep theo|kế tiếp|ke tiep)(?: là| la)?|" +
            "(?:thử|thu) câu này|" +
            "(?:thử|thu|lặp|lap|nhắc|nhac|[đĐ]ọc|doc|nói|noi) lại cả câu|" +
            "(?:một|mot) lần nữa|" +
            "(?:hãy|hay) (?:nói|noi|[đĐ]ọc|doc)" +
        ")"
    )

    private val QUOTE_REGEX = Regex("[\"“]([^\"”\\n]{6,150})[\"”]")

    private val ACK_THEN_SENTENCE_REGEX = Regex(
        "(?i)^(?:clear and accurate|correct|great(?: job)?|good(?: job)?|well done|that's right|perfect|nice job|excellent|awesome|nice|fantastic|spot on)[.!:,\\s]+" +
            "[\"“]?([A-Z][^\"”\\n]+)[\"”]?"
    )

    private val FILLER_PATTERNS = listOf(
        Regex("(?i)^\\s*(?:now\\s+)?(?:it'?s\\s+)?your\\s+turn(?:\\s+now)?\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:please\\s+)?go\\s+ahead\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:can|could)\\s+you\\s+(?:say|repeat|try)(?:\\s+that|\\s+it)?\\s*[.!?]?$"),
        Regex("(?i)^\\s*take\\s+your\\s+time\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:whenever|when)\\s+you'?re\\s+ready\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:are\\s+you\\s+)?ready\\s*[.!?]?$"),
        Regex("(?i)^\\s*try\\s+(?:it|that)(?:\\s+now)?\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:say\\s+it|speak)\\s+clearly\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:slowly\\s+and\\s+clearly|word\\s+by\\s+word)\\s*[.!?]?$"),
        Regex("(?i)^\\s*(?:đến\\s+lượt\\s+bạn|bạn\\s+thử\\s+xem|thử\\s+nói\\s+xem|bạn\\s+nói\\s+đi)\\s*[.!?]?$")
    )

    private val INLINE_TRAILING_FILLER_REGEX = Regex(
        "(?i)[,\\s]+(?:now\\s+your\\s+turn|your\\s+turn(?:\\s+now)?|go\\s+ahead|please\\s+go\\s+ahead|take\\s+your\\s+time|whenever\\s+you'?re\\s+ready|ready\\??|(?:can|could)\\s+you\\s+(?:say|repeat|try)(?:\\s+that|\\s+it)?|đến\\s+lượt\\s+bạn)\\s*[.!?]*$"
    )
}
