package com.speakdrive.ai

import com.speakdrive.ai.model.ReviewMistake
import com.speakdrive.ai.model.LearnerMemory
import com.speakdrive.ai.model.Correction
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import org.junit.Test

class PromptTemplatesTest {

    private val topics = TopicManager()

    private fun lesson(
        mode: SessionMode = SessionMode.FREE_TALK,
        level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        scenarioId: String? = null,
        words: List<ReviewWord> = emptyList()
    ): ActiveLesson {
        val scenario = topics.getScenario(scenarioId)
        return ActiveLesson(
            sessionId = "s1",
            topic = scenario?.first ?: topics.getTopicById("travel")!!,
            scenario = scenario?.second,
            level = level,
            mode = mode,
            startedAt = 0,
            reviewWords = words
        )
    }

    @Test
    fun `always includes the driving safety rules`() {
        val prompt = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings())
        assertThat(prompt).contains("DRIVING")
        assertThat(prompt).contains("Never ask the learner to look at")
        assertThat(prompt).contains(PromptTemplates.END_LESSON_FUNCTION)
    }

    @Test
    fun `reflects the level`() {
        val beginner = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.BEGINNER), LearnerSettings())
        val elementary = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.ELEMENTARY), LearnerSettings())
        val preIntermediate = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.PRE_INTERMEDIATE), LearnerSettings())
        val intermediate = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.INTERMEDIATE), LearnerSettings())
        val upperIntermediate = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.UPPER_INTERMEDIATE), LearnerSettings())
        val advanced = PromptTemplates.buildSystemInstruction(lesson(level = DifficultyLevel.ADVANCED), LearnerSettings())

        assertThat(beginner).contains("BEGINNER (CEFR A1)")
        assertThat(elementary).contains("ELEMENTARY (CEFR A2)")
        assertThat(preIntermediate).contains("PRE-INTERMEDIATE (CEFR A2–B1)")
        assertThat(intermediate).contains("INTERMEDIATE (CEFR B1)")
        assertThat(upperIntermediate).contains("UPPER-INTERMEDIATE (CEFR B2)")
        assertThat(advanced).contains("ADVANCED (CEFR C1–C2)")
    }

    @Test
    fun `vietnamese help follows the setting`() {
        val allowed = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(allowVietnameseHelp = true))
        val englishOnly = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(allowVietnameseHelp = false))
        assertThat(allowed).contains("explanation in Vietnamese")
        assertThat(englishOnly).contains("Always speak English")
    }

    @Test
    fun `roleplay names both roles`() {
        val prompt = PromptTemplates.buildSystemInstruction(lesson(SessionMode.ROLEPLAY, scenarioId = "food_order"), LearnerSettings())
        assertThat(prompt).contains("a waiter at a busy restaurant")
        assertThat(prompt).contains("a customer ordering dinner")
    }

    @Test
    fun `vocabulary review lists the words`() {
        val prompt = PromptTemplates.buildSystemInstruction(
            lesson(SessionMode.VOCAB_REVIEW, words = listOf(ReviewWord("itinerary", "lịch trình"))),
            LearnerSettings()
        )
        assertThat(prompt).contains("itinerary (lịch trình)")
    }

    @Test
    fun `recap is added only when reconnecting`() {
        val turns = listOf(TranscriptTurn(1, Speaker.USER, "I visited Hue", 0))
        val fresh = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings())
        val resumed = PromptTemplates.buildSystemInstruction(lesson(), LearnerSettings(), recap = turns)
        assertThat(fresh).doesNotContain("CONVERSATION SO FAR")
        assertThat(resumed).contains("CONVERSATION SO FAR")
        assertThat(resumed).contains("Learner: I visited Hue")
    }

    @Test
    fun `transcript formatting keeps the most recent turns`() {
        val turns = (1..100).map { TranscriptTurn(it.toLong(), if (it % 2 == 0) Speaker.AI else Speaker.USER, "turn number $it", 0) }
        val text = PromptTemplates.formatTranscript(turns, maxChars = 200)
        assertThat(text.length).isAtMost(220)
        assertThat(text).contains("turn number 100")
        assertThat(text).doesNotContain("turn number 1\n")
    }

    @Test
    fun `summary prompt asks for the expected JSON fields`() {
        val prompt = PromptTemplates.summaryPrompt(lesson(), listOf(TranscriptTurn(1, Speaker.USER, "hello", 0)))
        listOf("fluency_score", "grammar_score", "vocabulary_score", "new_words", "corrections", "encouragement_vi", "next_suggestion_vi")
            .forEach { assertThat(prompt).contains(it) }
    }

    @Test
    fun `repeatAfterMeRules limits sentence length to 5-8 words when isCarConnected is true and mode is AUTO_ON_CAR`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.ADVANCED)
        val prompt = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.AUTO_ON_CAR,
            isCarConnected = true
        )
        assertThat(prompt).contains("CRITICAL DRIVING SAFETY CONSTRAINTS (SHORT REPETITION SENTENCES)")
        assertThat(prompt).contains("6 to 8 words (never exceed 8 words)")
        assertThat(prompt).doesNotContain("10 to 18 words")
    }

    @Test
    fun `repeatAfterMeRules limits sentence length when mode is ALWAYS_SHORT even without car connection`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.UPPER_INTERMEDIATE)
        val prompt = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.ALWAYS_SHORT,
            isCarConnected = false
        )
        assertThat(prompt).contains("CRITICAL DRIVING SAFETY CONSTRAINTS (SHORT REPETITION SENTENCES)")
        assertThat(prompt).contains("6 to 8 words")
        assertThat(prompt).doesNotContain("9 to 15 words")
    }

    @Test
    fun `repeatAfterMeRules uses standard length when isCarConnected is false and mode is AUTO_ON_CAR`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.ADVANCED)
        val prompt = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.AUTO_ON_CAR,
            isCarConnected = false
        )
        assertThat(prompt).doesNotContain("CRITICAL DRIVING SAFETY CONSTRAINTS")
        assertThat(prompt).contains("10 to 18 words")
    }

    @Test
    fun `repeatAfterMeRules uses standard length when mode is STANDARD even if isCarConnected is true`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.INTERMEDIATE)
        val prompt = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.STANDARD,
            isCarConnected = true
        )
        assertThat(prompt).doesNotContain("CRITICAL DRIVING SAFETY CONSTRAINTS")
        assertThat(prompt).contains("7 to 12 words")
    }

    @Test
    fun `buildSystemInstruction passes isCarConnected and drillSentenceLength`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.ADVANCED)
        val inCarPrompt = PromptTemplates.buildSystemInstruction(
            lesson = drillLesson,
            settings = LearnerSettings(drillSentenceLength = DrillSentenceLength.AUTO_ON_CAR),
            isCarConnected = true
        )
        assertThat(inCarPrompt).contains("CRITICAL DRIVING SAFETY CONSTRAINTS")
        assertThat(inCarPrompt).contains("6 to 8 words (never exceed 8 words)")

        val notInCarPrompt = PromptTemplates.buildSystemInstruction(
            lesson = drillLesson,
            settings = LearnerSettings(drillSentenceLength = DrillSentenceLength.AUTO_ON_CAR),
            isCarConnected = false
        )
        assertThat(notInCarPrompt).doesNotContain("CRITICAL DRIVING SAFETY CONSTRAINTS")
        assertThat(notInCarPrompt).contains("10 to 18 words")
    }

    @Test
    fun `carConnectionSwitchMessage and drillSentenceLengthSwitchMessage generate expected directives`() {
        val carConnectedMsg = PromptTemplates.carConnectionSwitchMessage(true, DrillSentenceLength.AUTO_ON_CAR)
        assertThat(carConnectedMsg).contains("Android Auto")
        assertThat(carConnectedMsg).contains("5 to 8 words maximum")

        val carDisconnectedMsg = PromptTemplates.carConnectionSwitchMessage(false, DrillSentenceLength.AUTO_ON_CAR)
        assertThat(carDisconnectedMsg).contains("standard length")

        val shortSwitchMsg = PromptTemplates.drillSentenceLengthSwitchMessage(DrillSentenceLength.ALWAYS_SHORT, false)
        assertThat(shortSwitchMsg).contains("SHORT (5 to 8 words maximum)")

        val standardSwitchMsg = PromptTemplates.drillSentenceLengthSwitchMessage(DrillSentenceLength.STANDARD, true)
        assertThat(standardSwitchMsg).contains("STANDARD")
    }

    @Test
    fun `storyListeningRules integrates level adaptation directives for each CEFR level`() {
        val beginnerLesson = lesson(SessionMode.STORY_LISTENING, level = DifficultyLevel.BEGINNER, scenarioId = "story_shackleton")
        val beginnerRules = PromptTemplates.storyListeningRules(beginnerLesson)
        assertThat(beginnerRules).contains("STORY LEVEL ADAPTATION: BEGINNER (CEFR A1)")
        assertThat(beginnerRules).contains("A1 core 500–800 words")
        assertThat(beginnerRules).contains("under 10–12 words")
        assertThat(beginnerRules).contains("Speak slowly and enunciate every syllable")

        val advancedLesson = lesson(SessionMode.STORY_LISTENING, level = DifficultyLevel.ADVANCED, scenarioId = "story_shackleton")
        val advancedRules = PromptTemplates.storyListeningRules(advancedLesson)
        assertThat(advancedRules).contains("STORY LEVEL ADAPTATION: ADVANCED (CEFR C1–C2)")
        assertThat(advancedRules).contains("literary brilliance")
        assertThat(advancedRules).contains("complex narrative architecture")

        val intermediateLesson = lesson(SessionMode.STORY_LISTENING, level = DifficultyLevel.INTERMEDIATE, scenarioId = "story_shackleton")
        val intermediateRules = PromptTemplates.storyListeningRules(intermediateLesson)
        assertThat(intermediateRules).contains("STORY LEVEL ADAPTATION: INTERMEDIATE (CEFR B1)")
        assertThat(intermediateRules).contains("natural idioms")
    }

    @Test
    fun `storyLevelAdaptation covers all difficulty levels comprehensively`() {
        DifficultyLevel.entries.forEach { level ->
            val guidance = PromptTemplates.storyLevelAdaptation(level)
            assertThat(guidance).contains(level.displayName.uppercase())
            assertThat(guidance).contains(level.cefr)
            assertThat(guidance).contains("Vocabulary")
            assertThat(guidance).contains("Sentence Structure")
            assertThat(guidance).contains("Pacing")
            assertThat(guidance).contains("Interactive Mode Questions")
            assertThat(guidance).contains("Closing Takeaway")
        }
    }

    @Test
    fun `storyListeningRules enforces anti-summary and scene enactment directives`() {
        val storyLesson = lesson(SessionMode.STORY_LISTENING, level = DifficultyLevel.INTERMEDIATE, scenarioId = "story_shackleton")
        val rules = PromptTemplates.storyListeningRules(storyLesson)
        assertThat(rules).contains("STRICT ANTI-SUMMARY & IMMERSIVE DRAMA DIRECTIVE")
        assertThat(rules).contains("SUMMARIZING IS STRICTLY FORBIDDEN")
        assertThat(rules).contains("MANDATORY DIRECT CHARACTER DIALOGUE")
        assertThat(rules).contains("SHOW, DON'T TELL")
        assertThat(rules).contains("VOCAL FOLEY & SOUND EFFECTS")

        val kickoff = PromptTemplates.kickoffMessage(storyLesson)
        assertThat(kickoff).contains("PERFORM Chapter One")
        assertThat(kickoff).contains("ABSOLUTELY DO NOT SUMMARIZE")

        val continueMsg = PromptTemplates.CONTINUE_STORY_MESSAGE
        assertThat(continueMsg).contains("DO NOT SUMMARIZE OR RUSH TO THE END")
    }

    @Test
    fun `repeatAfterMeRules includes diversity engine and anti-repetition instructions with seed sentences`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.INTERMEDIATE)
        val seedSentences = listOf("Could I have the check, please?", "I really enjoyed the meal.")
        val rules = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.STANDARD,
            isCarConnected = false,
            sampleDrillSentences = seedSentences
        )

        assertThat(rules).contains("DIVERSITY & ENDLESS VARIETY ENGINE")
        assertThat(rules).contains("SEED PRACTICE SENTENCES")
        assertThat(rules).contains("Could I have the check, please?")
        assertThat(rules).contains("NEVER repeat a sentence")
        assertThat(rules).contains("CONTINUOUS DYNAMIC CREATION")
    }

    @Test
    fun `repeatAfterMeRules instructs AI to dynamically create sentences when no seed sentences provided`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.BEGINNER)
        val rules = PromptTemplates.repeatAfterMeRules(
            lesson = drillLesson,
            drillSentenceLength = DrillSentenceLength.STANDARD,
            isCarConnected = false,
            sampleDrillSentences = emptyList()
        )

        assertThat(rules).contains("DIVERSITY & ENDLESS VARIETY ENGINE")
        assertThat(rules).contains("Actively compose FRESH, ENGAGING, AUTHENTIC practice sentences")
        assertThat(rules).contains("NEVER repeat a sentence")
    }

    @Test
    fun `resumeMessage formats mode-aware resume prompt correctly`() {
        val drillLesson = lesson(SessionMode.REPEAT_AFTER_ME, level = DifficultyLevel.INTERMEDIATE)
        val drillWithTarget = PromptTemplates.resumeMessage(drillLesson, "I would like to check in.")
        assertThat(drillWithTarget).contains("Repeat-After-Me / Pronunciation Shadowing drill mode")
        assertThat(drillWithTarget).contains("I would like to check in.")
        assertThat(drillWithTarget).contains("Do NOT switch to open conversation")

        val drillWithoutTarget = PromptTemplates.resumeMessage(drillLesson, null)
        assertThat(drillWithoutTarget).contains("say the next drill sentence clearly")
        assertThat(drillWithoutTarget).contains("Do NOT switch to open conversation")

        val roleplayLesson = lesson(SessionMode.ROLEPLAY, level = DifficultyLevel.INTERMEDIATE, scenarioId = "check_in")
        val roleplayMsg = PromptTemplates.resumeMessage(roleplayLesson)
        assertThat(roleplayMsg).contains("Resume your roleplay")
        assertThat(roleplayMsg).contains("Stay in character")

        val freeTalkLesson = lesson(SessionMode.FREE_TALK, level = DifficultyLevel.INTERMEDIATE)
        val freeTalkMsg = PromptTemplates.resumeMessage(freeTalkLesson)
        assertThat(freeTalkMsg).isEqualTo(PromptTemplates.RESUME_MESSAGE)
    }

    private fun memoryLesson(
        mode: SessionMode,
        memory: LearnerMemory?,
        mistakes: List<ReviewMistake> = emptyList()
    ): ActiveLesson {
        val topic = TopicManager().getTopicById("travel")!!
        return ActiveLesson(
            sessionId = "s",
            topic = topic,
            scenario = null,
            level = DifficultyLevel.INTERMEDIATE,
            mode = mode,
            startedAt = 0,
            reviewWords = emptyList(),
            reviewMistakes = mistakes,
            learnerMemory = memory
        )
    }

    private val sampleMemory = LearnerMemory(
        recurringMistakes = listOf(Correction("She don't like it", "She doesn't like it", "")),
        weakWords = listOf("comfortable", "three"),
        facts = listOf("Works as a nurse in Da Nang")
    )

    @Test
    fun `memory notes are added as background, never as a list to read out`() {
        val instruction = PromptTemplates.buildSystemInstruction(memoryLesson(SessionMode.FREE_TALK, sampleMemory), LearnerSettings())

        assertThat(instruction).contains("WHAT YOU REMEMBER FROM EARLIER LESSONS")
        assertThat(instruction).contains("Works as a nurse in Da Nang")
        assertThat(instruction).contains("She doesn't like it")
        assertThat(instruction).contains("never read these notes aloud")
    }

    @Test
    fun `memory notes depend on the mode and are left out when memory is off or empty`() {
        val drill = PromptTemplates.learnerMemoryRules(sampleMemory, SessionMode.REPEAT_AFTER_ME)!!
        assertThat(drill).contains("comfortable")
        assertThat(drill).contains("drill sentences")
        assertThat(drill).doesNotContain("nurse")

        assertThat(PromptTemplates.learnerMemoryRules(sampleMemory, SessionMode.STORY_LISTENING)).isNull()
        assertThat(PromptTemplates.learnerMemoryRules(null, SessionMode.FREE_TALK)).isNull()
        assertThat(PromptTemplates.learnerMemoryRules(LearnerMemory.EMPTY, SessionMode.FREE_TALK)).isNull()
    }

    @Test
    fun `mistake review lists the mistakes and starts with the first one`() {
        val mistakes = listOf(
            ReviewMistake(3, "I go there yesterday", "I went there yesterday", "Quá khứ của go là went."),
            ReviewMistake(4, "She don't like it", "She doesn't like it", "")
        )
        val lesson = memoryLesson(SessionMode.MISTAKE_REVIEW, null, mistakes)

        val instruction = PromptTemplates.buildSystemInstruction(lesson, LearnerSettings())
        assertThat(instruction).contains("MISTAKE REVIEW COACH")
        assertThat(instruction).contains("Mistake 3: the learner said \"I go there yesterday\"")
        assertThat(instruction).contains("Never invent mistakes")
        assertThat(PromptTemplates.kickoffMessage(lesson)).contains("Last time you said: I go there yesterday")
        assertThat(PromptTemplates.resumeMessage(lesson)).contains("Mistake Review")
        assertThat(lesson.titleVi).isEqualTo("Ôn lỗi sai")
    }

    @Test
    fun `summary asks for facts only when memory is on and for results only in mistake review`() {
        val withMemory = PromptTemplates.summaryPrompt(memoryLesson(SessionMode.FREE_TALK, sampleMemory), emptyList())
        assertThat(withMemory).contains("learner_facts: up to 3 NEW")
        assertThat(withMemory).contains("Skip facts already known: Works as a nurse in Da Nang")
        assertThat(withMemory).contains("mistake_results: always an empty array")

        val withoutMemory = PromptTemplates.summaryPrompt(memoryLesson(SessionMode.FREE_TALK, null), emptyList())
        assertThat(withoutMemory).contains("learner_facts: always an empty array")

        val review = PromptTemplates.summaryPrompt(
            memoryLesson(SessionMode.MISTAKE_REVIEW, null, listOf(ReviewMistake(3, "I goed", "I went", ""))),
            emptyList()
        )
        assertThat(review).contains("mistake_results: one entry per mistake")
        assertThat(review).contains("Mistake 3: \"I goed\" -> \"I went\"")
    }

    @Test
    fun `storyListeningRules contains mandatory 3-question comprehension quiz at end of story`() {
        val storyLesson = lesson(SessionMode.STORY_LISTENING, level = DifficultyLevel.INTERMEDIATE)
        val prompt = PromptTemplates.storyListeningRules(storyLesson)
        assertThat(prompt).contains("MANDATORY STORY COMPREHENSION QUIZ AT END OF STORY")
        assertThat(prompt).contains("brief 3-question listening comprehension quiz")
        assertThat(prompt).contains("Ask one short, clear question at a time")
    }

    @Test
    fun `summaryPrompt requests is_collocation and comprehension_score appropriately`() {
        val storyLesson = lesson(SessionMode.STORY_LISTENING)
        val storyPrompt = PromptTemplates.summaryPrompt(storyLesson, emptyList())
        assertThat(storyPrompt).contains("is_collocation")
        assertThat(storyPrompt).contains("comprehension_score: an integer from 0 to 100")

        val freeTalkLesson = lesson(SessionMode.FREE_TALK)
        val freeTalkPrompt = PromptTemplates.summaryPrompt(freeTalkLesson, emptyList())
        assertThat(freeTalkPrompt).contains("is_collocation")
        assertThat(freeTalkPrompt).contains("comprehension_score: null")
    }

    @Test
    fun `unanswered turn message repeats what the learner said`() {
        val talk = PromptTemplates.unansweredTurnMessage(SessionMode.FREE_TALK, "  I think it was pirates. ")
        assertThat(talk).contains("They said: \"I think it was pirates.\"")
        assertThat(talk).contains("Reply to them now")

        val drill = PromptTemplates.unansweredTurnMessage(SessionMode.REPEAT_AFTER_ME, "I need three tickets")
        assertThat(drill).contains("Call check_attempt for this attempt now")

        assertThat(PromptTemplates.unansweredTurnMessage(SessionMode.FREE_TALK, " "))
            .isEqualTo(PromptTemplates.UNANSWERED_PROMPT_MESSAGE)
        assertThat(PromptTemplates.unansweredTurnMessage(SessionMode.ROLEPLAY, "x".repeat(2_000)).length).isLessThan(900)
    }

    @Test
    fun `difficultyLevelSwitchMessage adapts to story and conversation modes`() {
        val storyMsg = PromptTemplates.difficultyLevelSwitchMessage(DifficultyLevel.BEGINNER, SessionMode.STORY_LISTENING)
        assertThat(storyMsg).contains("Story difficulty level changed to Beginner (A1)")
        assertThat(storyMsg).contains("narrating the next events of the story")

        val convoMsg = PromptTemplates.difficultyLevelSwitchMessage(DifficultyLevel.ADVANCED, SessionMode.FREE_TALK)
        assertThat(convoMsg).contains("Difficulty level changed to Advanced (C1–C2)")
        assertThat(convoMsg).contains("upcoming turns")
    }

    @Test
    fun `vietnameseHelpSwitchMessage generates correct directives`() {
        val enabledMsg = PromptTemplates.vietnameseHelpSwitchMessage(true)
        assertThat(enabledMsg).contains("Vietnamese help has been enabled")

        val disabledMsg = PromptTemplates.vietnameseHelpSwitchMessage(false)
        assertThat(disabledMsg).contains("English only mode has been enabled")
    }

    @Test
    fun `driving rules describe only the declared tools and hand the rest to the app`() {
        val declared = setOf(
            com.speakdrive.ai.session.VoiceSettingsTools.SET_AI_VOLUME_FUNCTION,
            com.speakdrive.ai.session.VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION
        )
        val rules = PromptTemplates.drivingContextRules(declared)
        assertThat(rules).contains("DRIVING SAFETY RULES")
        assertThat(rules).contains(com.speakdrive.ai.session.VoiceSettingsTools.SET_AI_VOLUME_FUNCTION)
        assertThat(rules).contains(com.speakdrive.ai.session.VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION)
        assertThat(rules).doesNotContain(com.speakdrive.ai.session.VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION)
        assertThat(rules).contains("the app applies it itself")
        assertThat(rules).contains(PromptTemplates.END_LESSON_FUNCTION)

        val everything = PromptTemplates.drivingContextRules()
        assertThat(everything).contains(com.speakdrive.ai.session.VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION)
        assertThat(everything).doesNotContain("the app applies it itself")
    }
}
