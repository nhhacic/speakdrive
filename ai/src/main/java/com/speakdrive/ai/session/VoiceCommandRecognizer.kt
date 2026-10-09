package com.speakdrive.ai.session

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.LearnerSettings
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.ai.pronunciation.PronunciationGrader

/** Which setting or action a voice command (or Gemini tool call) is about. */
enum class CommandCategory {
    SESSION_MODE, LEVEL, VIETNAMESE_HELP, APP_LANGUAGE, STORY_STYLE, STRICTNESS, BARGE_IN, RANDOM_VOICE,
    VOICE, STORY_DURATION, MULTI_VOICE, STORY_NAVIGATION, DRILL_NAVIGATION, DRILL_LENGTH, DRILL_CATEGORY,
    LEVEL_RECOMMENDATION, ADAPTIVE_LEVEL, VOLUME, AUTO_PAUSE, SUBTITLES, LEARNER_MEMORY,
    PRACTICE_REMINDER, STREAK_FREEZE, OFFLINE_PRACTICE, AUTO_START_IN_CAR, DAILY_GOAL, AZURE_SCORING, SCREEN_AWAKE,
    BETTER_PHRASING;

    companion object {
        /** The category a Gemini Live tool works on, so the fallback never applies the same change twice. */
        fun forTool(name: String): CommandCategory? = when (name) {
            VoiceSettingsTools.SWITCH_SESSION_MODE_FUNCTION -> SESSION_MODE
            VoiceSettingsTools.SET_DIFFICULTY_LEVEL_FUNCTION -> LEVEL
            VoiceSettingsTools.SET_VIETNAMESE_HELP_FUNCTION -> VIETNAMESE_HELP
            VoiceSettingsTools.SET_APP_LANGUAGE_FUNCTION -> APP_LANGUAGE
            VoiceSettingsTools.SET_STORYTELLING_STYLE_FUNCTION -> STORY_STYLE
            VoiceSettingsTools.SET_PRONUNCIATION_STRICTNESS_FUNCTION -> STRICTNESS
            VoiceSettingsTools.SET_BARGE_IN_FUNCTION -> BARGE_IN
            VoiceSettingsTools.SET_RANDOM_VOICE_FUNCTION -> RANDOM_VOICE
            VoiceSettingsTools.SET_VOICE_FUNCTION -> VOICE
            VoiceSettingsTools.SET_STORY_DURATION_FUNCTION -> STORY_DURATION
            VoiceSettingsTools.SET_MULTI_VOICE_FUNCTION -> MULTI_VOICE
            VoiceSettingsTools.NEXT_STORY_FUNCTION,
            VoiceSettingsTools.REPLAY_STORY_FUNCTION,
            VoiceSettingsTools.RESUME_STORY_FUNCTION -> STORY_NAVIGATION
            VoiceSettingsTools.SKIP_DRILL_SENTENCE_FUNCTION,
            VoiceSettingsTools.REPEAT_DRILL_SENTENCE_FUNCTION -> DRILL_NAVIGATION
            VoiceSettingsTools.SET_DRILL_SENTENCE_LENGTH_FUNCTION -> DRILL_LENGTH
            VoiceSettingsTools.SET_DRILL_CATEGORY_FUNCTION -> DRILL_CATEGORY
            VoiceSettingsTools.APPLY_LEVEL_RECOMMENDATION_FUNCTION -> LEVEL_RECOMMENDATION
            VoiceSettingsTools.SET_ADAPTIVE_LEVEL_FUNCTION -> ADAPTIVE_LEVEL
            VoiceSettingsTools.SET_AI_VOLUME_FUNCTION -> VOLUME
            VoiceSettingsTools.SET_AUTO_PAUSE_WHEN_UNFOCUSED_FUNCTION -> AUTO_PAUSE
            VoiceSettingsTools.SET_TRANSLATION_SUBTITLES_FUNCTION -> SUBTITLES
            VoiceSettingsTools.SET_LEARNER_MEMORY_FUNCTION -> LEARNER_MEMORY
            VoiceSettingsTools.SET_PRACTICE_REMINDER_FUNCTION -> PRACTICE_REMINDER
            VoiceSettingsTools.SET_STREAK_FREEZE_FUNCTION -> STREAK_FREEZE
            VoiceSettingsTools.SET_AUTO_START_IN_CAR_FUNCTION -> AUTO_START_IN_CAR
            VoiceSettingsTools.SET_DAILY_GOAL_FUNCTION -> DAILY_GOAL
            VoiceSettingsTools.SET_AZURE_SCORING_FUNCTION -> AZURE_SCORING
            VoiceSettingsTools.SET_SCREEN_AWAKE_FUNCTION -> SCREEN_AWAKE
            VoiceSettingsTools.SET_OFFLINE_PRACTICE_FUNCTION -> OFFLINE_PRACTICE
            VoiceSettingsTools.SET_BETTER_PHRASING_FUNCTION -> BETTER_PHRASING
            else -> null
        }
    }
}

/** A voice command recognised from the learner's transcript by the client-side safety net. */
sealed interface VoiceCommand {
    val category: CommandCategory

    data class SwitchMode(val mode: SessionMode) : VoiceCommand {
        override val category get() = CommandCategory.SESSION_MODE
    }
    data class SetLevel(val level: DifficultyLevel) : VoiceCommand {
        override val category get() = CommandCategory.LEVEL
    }
    data class SetVietnameseHelp(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.VIETNAMESE_HELP
    }
    data class SetAppLanguage(val language: AppLanguage) : VoiceCommand {
        override val category get() = CommandCategory.APP_LANGUAGE
    }
    data class SetStorytellingStyle(val style: StorytellingStyle) : VoiceCommand {
        override val category get() = CommandCategory.STORY_STYLE
    }
    data class SetStrictness(val strictness: PronunciationStrictness) : VoiceCommand {
        override val category get() = CommandCategory.STRICTNESS
    }
    data class SetBargeIn(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.BARGE_IN
    }
    data class SetRandomVoice(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.RANDOM_VOICE
    }
    data class SetVoice(val voice: AiVoice) : VoiceCommand {
        override val category get() = CommandCategory.VOICE
    }
    data class SetStoryDuration(val duration: StoryDuration) : VoiceCommand {
        override val category get() = CommandCategory.STORY_DURATION
    }
    data class SetMultiVoice(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.MULTI_VOICE
    }
    data object ResumeStory : VoiceCommand {
        override val category get() = CommandCategory.STORY_NAVIGATION
    }
    data object NextStory : VoiceCommand {
        override val category get() = CommandCategory.STORY_NAVIGATION
    }
    data object ReplayStory : VoiceCommand {
        override val category get() = CommandCategory.STORY_NAVIGATION
    }
    data object RepeatDrillSentence : VoiceCommand {
        override val category get() = CommandCategory.DRILL_NAVIGATION
    }
    data object SkipDrillSentence : VoiceCommand {
        override val category get() = CommandCategory.DRILL_NAVIGATION
    }
    data class SetDrillLength(val length: DrillSentenceLength) : VoiceCommand {
        override val category get() = CommandCategory.DRILL_LENGTH
    }
    data class SetDrillCategory(val drillCategory: DrillCategory) : VoiceCommand {
        override val category get() = CommandCategory.DRILL_CATEGORY
    }
    data class AnswerLevelRecommendation(val accept: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.LEVEL_RECOMMENDATION
    }
    data class SetAdaptiveLevel(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.ADAPTIVE_LEVEL
    }
    data class SetVolume(val volume: Int) : VoiceCommand {
        override val category get() = CommandCategory.VOLUME
    }
    data class SetAutoPause(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.AUTO_PAUSE
    }
    data class SetSubtitles(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.SUBTITLES
    }
    data class SetLearnerMemory(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.LEARNER_MEMORY
    }
    /** [minuteOfDay] null keeps the current time; [LearnerSettings.REMINDER_AUTO] follows the learner's habit. */
    data class SetPracticeReminder(val enabled: Boolean, val minuteOfDay: Int?) : VoiceCommand {
        override val category get() = CommandCategory.PRACTICE_REMINDER
    }
    data class SetStreakFreeze(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.STREAK_FREEZE
    }
    data class SetAutoStartInCar(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.AUTO_START_IN_CAR
    }
    data class SetDailyGoal(val minutes: Int) : VoiceCommand {
        override val category get() = CommandCategory.DAILY_GOAL
    }
    data class SetAzureScoring(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.AZURE_SCORING
    }
    data class SetScreenAwake(val mode: ScreenAwakeMode) : VoiceCommand {
        override val category get() = CommandCategory.SCREEN_AWAKE
    }
    data class SetOfflinePractice(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.OFFLINE_PRACTICE
    }
    data class SetBetterPhrasing(val enabled: Boolean) : VoiceCommand {
        override val category get() = CommandCategory.BETTER_PHRASING
    }
}

/** What the recognizer needs to know about the lesson when the learner finishes speaking. */
data class CommandContext(
    val state: ConversationState,
    val mode: SessionMode?,
    val currentLevel: DifficultyLevel,
    val settings: LearnerSettings,
    /** The sentence the learner is practising in repeat-after-me mode. */
    val drillTarget: String? = null,
    /** What the AI said last, so a learner echoing it is not taken as a command. */
    val lastAiText: String? = null,
    /** True while a level change suggested by the last lesson summary waits for an answer. */
    val hasPendingLevelRecommendation: Boolean = false
)

/**
 * Decides whether a finished learner utterance is really a command for the app, using the phrase
 * parsers in [VoiceCommandParser] plus context. The parsers alone match any sentence that happens to
 * contain a command phrase ("Tôi đang học tiếng Anh", "I make Korean food", a drill sentence such
 * as "Could you say that again?"), which used to change settings in the middle of a conversation.
 *
 * Rules, in order:
 * - only while the lesson is ACTIVE and for utterances of at most [MAX_COMMAND_WORDS] words;
 * - never when the learner is repeating the drill sentence or echoing the AI;
 * - the utterance must be short ([SHORT_UTTERANCE_WORDS] words) or start like a request
 *   ("bật…", "chuyển…", "cho tôi…", "turn…", "can you…");
 * - settings that belong to another mode (story, drill) need the request form;
 * - switching modes needs an explicit switch ("chuyển sang…", "switch to…") or a very short request;
 * - "đồng ý" only answers a level recommendation that is actually pending.
 */
object VoiceCommandRecognizer {
    const val MAX_COMMAND_WORDS = 12
    const val SHORT_UTTERANCE_WORDS = 4
    private const val MODE_SWITCH_SHORT_WORDS = 3
    private const val DRILL_ECHO_SIMILARITY = 0.5
    private const val AI_ECHO_SIMILARITY = 0.7

    fun recognize(text: String, ctx: CommandContext): VoiceCommand? {
        if (ctx.state != ConversationState.ACTIVE) return null
        val folded = TopicManager.normalize(text)
        if (folded.isBlank()) return null
        val words = folded.split(' ')
        if (words.size > MAX_COMMAND_WORDS) return null

        val drillTarget = ctx.drillTarget?.takeIf { ctx.mode == SessionMode.REPEAT_AFTER_ME && it.isNotBlank() }
        if (drillTarget != null && PronunciationGrader.similarity(drillTarget, text) >= DRILL_ECHO_SIMILARITY) return null
        val lastAi = ctx.lastAiText?.takeIf { it.isNotBlank() }
        if (lastAi != null && PronunciationGrader.similarity(lastAi, text) >= AI_ECHO_SIMILARITY) return null

        val request = startsLikeRequest(words)
        val lenient = request || words.size <= SHORT_UTTERANCE_WORDS
        val inStory = ctx.mode == SessionMode.STORY_LISTENING
        val inDrill = ctx.mode == SessionMode.REPEAT_AFTER_ME
        val s = ctx.settings

        VoiceCommandParser.parseSessionModeCommand(text)?.let { mode ->
            val explicit = hasExplicitSwitch(folded) || words.size <= MODE_SWITCH_SHORT_WORDS
            if (mode != ctx.mode && explicit && lenient) return VoiceCommand.SwitchMode(mode)
        }
        VoiceCommandParser.parseDifficultyCommand(text, ctx.currentLevel)?.let { level ->
            if (level != ctx.currentLevel && lenient) return VoiceCommand.SetLevel(level)
        }
        VoiceCommandParser.parseVietnameseHelpCommand(text)?.let { enabled ->
            if (enabled != s.allowVietnameseHelp && lenient) return VoiceCommand.SetVietnameseHelp(enabled)
        }
        VoiceCommandParser.parseAppLanguageCommand(text)?.let { language ->
            if (language != s.appLanguage && lenient) return VoiceCommand.SetAppLanguage(language)
        }
        VoiceCommandParser.parseStorytellingStyleCommand(text, inStorySession = inStory)?.let { style ->
            if (style != s.storytellingStyle && (if (inStory) lenient else request)) return VoiceCommand.SetStorytellingStyle(style)
        }
        VoiceCommandParser.parsePronunciationStrictnessCommand(text)?.let { strictness ->
            if (strictness != s.pronunciationStrictness && (if (inDrill) lenient else request)) {
                return VoiceCommand.SetStrictness(strictness)
            }
        }
        VoiceCommandParser.parseBargeInCommand(text)?.let { enabled ->
            if (enabled != s.allowBargeIn && lenient) return VoiceCommand.SetBargeIn(enabled)
        }
        VoiceCommandParser.parseRandomVoiceCommand(text)?.let { enabled ->
            if (enabled != s.randomVoice && lenient) return VoiceCommand.SetRandomVoice(enabled)
        }
        VoiceCommandParser.parseVoiceCommand(text)?.let { voice ->
            if ((voice.id != s.voiceId || s.randomVoice) && lenient) return VoiceCommand.SetVoice(voice)
        }
        VoiceCommandParser.parseStoryDurationCommand(text)?.let { duration ->
            if (duration != s.storyDuration && (if (inStory) lenient else request)) return VoiceCommand.SetStoryDuration(duration)
        }
        VoiceCommandParser.parseMultiVoiceCommand(text)?.let { enabled ->
            if (enabled != s.multiVoiceStorytelling && (if (inStory) lenient else request)) return VoiceCommand.SetMultiVoice(enabled)
        }
        // While a story plays, "kể tiếp" just means "go on": the current story is already the one to continue.
        if (!inStory && request && VoiceCommandParser.parseResumeStoryCommand(text)) return VoiceCommand.ResumeStory
        if (inStory && lenient) {
            if (VoiceCommandParser.parseNextStoryCommand(text)) return VoiceCommand.NextStory
            if (VoiceCommandParser.parseReplayStoryCommand(text)) return VoiceCommand.ReplayStory
        }
        if (inDrill && lenient) {
            if (VoiceCommandParser.parseRepeatDrillSentenceCommand(text)) return VoiceCommand.RepeatDrillSentence
            if (VoiceCommandParser.parseSkipDrillSentenceCommand(text)) return VoiceCommand.SkipDrillSentence
        }
        VoiceCommandParser.parseDrillSentenceLengthCommand(text)?.let { length ->
            if (length != s.drillSentenceLength && (if (inDrill) lenient else request)) return VoiceCommand.SetDrillLength(length)
        }
        VoiceCommandParser.parseDrillCategoryCommand(text)?.let { category ->
            if (category != s.drillCategory && (if (inDrill) lenient else request)) return VoiceCommand.SetDrillCategory(category)
        }
        if (ctx.hasPendingLevelRecommendation && lenient) {
            VoiceCommandParser.parseApplyRecommendationCommand(text)?.let { accept ->
                return VoiceCommand.AnswerLevelRecommendation(accept)
            }
        }
        VoiceCommandParser.parseAdaptiveLevelCommand(text)?.let { enabled ->
            if (enabled != s.adaptiveLevelRecommendation && lenient) return VoiceCommand.SetAdaptiveLevel(enabled)
        }
        VoiceCommandParser.parseVolumeCommand(text, s.aiVolume)?.let { volume ->
            if (volume != s.aiVolume && lenient) return VoiceCommand.SetVolume(volume)
        }
        VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand(text)?.let { enabled ->
            if (enabled != s.autoPauseWhenUnfocused && lenient) return VoiceCommand.SetAutoPause(enabled)
        }
        VoiceCommandParser.parseTranslationSubtitlesCommand(text)?.let { enabled ->
            if (enabled != s.showTranslationSubtitle && lenient) return VoiceCommand.SetSubtitles(enabled)
        }
        VoiceCommandParser.parseLearnerMemoryCommand(text)?.let { enabled ->
            if (enabled != s.rememberLearner && lenient) return VoiceCommand.SetLearnerMemory(enabled)
        }
        VoiceCommandParser.parsePracticeReminderCommand(text)?.let { reminder ->
            val changes = reminder.enabled != s.practiceReminderEnabled ||
                (reminder.minuteOfDay != null && reminder.minuteOfDay != s.practiceReminderMinute)
            if (changes && lenient) return VoiceCommand.SetPracticeReminder(reminder.enabled, reminder.minuteOfDay)
        }
        VoiceCommandParser.parseStreakFreezeCommand(text)?.let { enabled ->
            if (enabled != s.streakFreezeEnabled && lenient) return VoiceCommand.SetStreakFreeze(enabled)
        }
        VoiceCommandParser.parseOfflinePracticeCommand(text)?.let { enabled ->
            if (enabled != s.offlinePracticeEnabled && lenient) return VoiceCommand.SetOfflinePractice(enabled)
        }
        VoiceCommandParser.parseAutoStartInCarCommand(text)?.let { enabled ->
            if (enabled != s.autoStartOnCarConnect && lenient) return VoiceCommand.SetAutoStartInCar(enabled)
        }
        VoiceCommandParser.parseDailyGoalCommand(text, s.dailyGoalMinutes)?.let { minutes ->
            if (minutes != s.dailyGoalMinutes && lenient) return VoiceCommand.SetDailyGoal(minutes)
        }
        VoiceCommandParser.parseAzureScoringCommand(text)?.let { enabled ->
            if (enabled != s.azureEnabled && lenient) return VoiceCommand.SetAzureScoring(enabled)
        }
        VoiceCommandParser.parseScreenAwakeCommand(text)?.let { mode ->
            if (mode != s.screenAwakeMode && lenient) return VoiceCommand.SetScreenAwake(mode)
        }
        VoiceCommandParser.parseBetterPhrasingCommand(text)?.let { enabled ->
            if (enabled != s.betterPhrasingEnabled && lenient) return VoiceCommand.SetBetterPhrasing(enabled)
        }
        return null
    }

    /** True when the utterance opens like a request or instruction rather than a statement. */
    internal fun startsLikeRequest(words: List<String>): Boolean {
        val content = words.dropWhile { it in FILLERS }
        if (content.isEmpty()) return false
        val firstTwo = content.take(2).joinToString(" ")
        return content.first() in REQUEST_OPENERS || firstTwo in REQUEST_OPENER_PAIRS
    }

    private fun hasExplicitSwitch(folded: String): Boolean {
        val padded = " $folded "
        return EXPLICIT_SWITCH_PHRASES.any { padded.contains(" $it ") }
    }

    /** Words that often start an utterance without changing its meaning (folded, no diacritics). */
    private val FILLERS = setOf(
        "ok", "oke", "okay", "uh", "um", "uhm", "ah", "a", "oi", "ui", "thi", "vay", "hey", "hi", "hello",
        "please", "xin", "lam", "on", "ban", "em", "anh", "chi", "ai", "gemini", "nay", "well", "so", "and",
        "yes", "yeah", "va", "gio", "bay", "roi", "nhe", "nha", "thoi"
    )

    /** Verbs a Vietnamese or English request usually starts with (folded, no diacritics). */
    private val REQUEST_OPENERS = setOf(
        "bat", "tat", "mo", "doi", "chuyen", "chinh", "cai", "dat", "tang", "giam", "ha", "cho", "hay", "noi",
        "doc", "ke", "bo", "chon", "dung", "ngung", "luyen", "tap", "nghe", "quay", "hien", "an", "giu", "ap",
        "de", "cham", "luon", "muc", "nhac",
        "switch", "change", "set", "turn", "make", "enable", "disable", "use", "speak", "skip", "repeat", "play",
        "give", "stop", "start", "show", "hide", "keep", "can", "could", "would", "go", "next", "replay",
        "resume", "continue", "lower", "raise", "increase", "decrease", "apply", "accept", "tell", "read", "say",
        "let", "lets", "remind"
    )

    private val REQUEST_OPENER_PAIRS = setOf(
        "toi muon", "minh muon", "em muon", "toi can", "i want", "i d", "i would", "let s", "che do", "tu dong"
    )

    private val EXPLICIT_SWITCH_PHRASES = listOf(
        "chuyen sang", "chuyen qua", "doi sang", "doi qua", "che do", "sang che do", "qua che do",
        "toi muon", "minh muon", "bat dau", "switch to", "change to", "mode", "i want to", "let s", "lets", "start"
    )
}
