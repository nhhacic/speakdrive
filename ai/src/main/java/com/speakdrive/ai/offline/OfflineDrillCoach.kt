package com.speakdrive.ai.offline

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.pronunciation.PronunciationAttempt
import com.speakdrive.ai.pronunciation.PronunciationGrader
import com.speakdrive.ai.session.VoiceCommandParser
import com.speakdrive.audio.ListenResult
import com.speakdrive.audio.OfflineSpeech
import kotlinx.coroutines.delay

/** What the offline coach reports back to the lesson. Called on the coach's coroutine. */
interface OfflineDrillListener {
    /** The sentence being practised now, or null when there is none. */
    fun onTarget(target: String?)
    fun onTurn(speaker: Speaker, text: String)
    fun onAttempt(attempt: PronunciationAttempt)

    /** The learner asked to stop the lesson. */
    fun onEndRequested()
}

/**
 * "Repeat after me" without a network: the phone reads a curated sentence aloud, listens with the
 * on-device recogniser and compares the words, like the online drill but without the AI's ear. When
 * the phone cannot listen offline it becomes a shadowing exercise: the learner repeats in the pause.
 * Runs until its coroutine is cancelled (the connection is back, the lesson paused or ended).
 */
class OfflineDrillCoach(
    private val speech: OfflineSpeech,
    private val listener: OfflineDrillListener,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Options(
        val level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        val strictness: PronunciationStrictness = PronunciationStrictness.AUTO,
        /** Speak the instructions in Vietnamese. */
        val vietnamese: Boolean = true
    )

    suspend fun run(sentences: List<String>, options: Options = Options()) {
        if (sentences.isEmpty()) return
        var canListen = speech.canListen()
        say(intro(options.vietnamese, canListen), vietnamese = options.vietnamese)
        var round = 0
        while (round < MAX_SENTENCES) {
            val target = sentences[round % sentences.size]
            round++
            listener.onTarget(target)
            var attempt = 1
            var silences = 0
            var prompt = "Repeat after me: $target"
            while (true) {
                say(prompt, vietnamese = false, record = true)
                if (!canListen) {
                    // Shadowing: leave time to repeat, then move on.
                    delay(SHADOW_PAUSE_MS + target.split(' ').size * SHADOW_MS_PER_WORD)
                    break
                }
                when (val heard = speech.listen(LISTEN_TIMEOUT_MS)) {
                    ListenResult.Unavailable -> {
                        canListen = false
                        say(if (options.vietnamese) CANNOT_LISTEN_VI else CANNOT_LISTEN_EN, vietnamese = options.vietnamese)
                    }
                    ListenResult.Silence -> {
                        silences++
                        if (silences >= MAX_SILENCES) {
                            say("Let's try another one.", vietnamese = false, record = true)
                            break
                        }
                        prompt = "Take your time. Again: $target"
                    }
                    is ListenResult.Heard -> {
                        val text = heard.text.trim()
                        listener.onTurn(Speaker.USER, text)
                        when (commandIn(text, target)) {
                            Command.END -> {
                                listener.onEndRequested()
                                return
                            }
                            Command.SKIP -> break
                            Command.REPEAT -> {
                                prompt = target
                                continue
                            }
                            null -> Unit
                        }
                        val graded = PronunciationGrader.grade(
                            target = target,
                            heard = text,
                            // No AI listens offline: the word comparison is the only judge.
                            modelSaidCorrect = true,
                            modelProblemWords = emptyList(),
                            modelNotes = OFFLINE_NOTE,
                            attemptNumber = attempt,
                            timestamp = clock(),
                            strictness = options.strictness,
                            level = options.level
                        )
                        listener.onAttempt(graded)
                        if (graded.passed) {
                            say(PRAISE[(round + attempt) % PRAISE.size], vietnamese = false, record = true)
                            break
                        }
                        if (attempt >= MAX_TRIES) {
                            say("Good try. Let's move on.", vietnamese = false, record = true)
                            break
                        }
                        attempt++
                        val focus = graded.problemWords.take(2)
                        prompt = if (focus.isEmpty()) {
                            "Almost. Listen again: $target"
                        } else {
                            "Almost. Watch ${focus.joinToString(" and ")}. Again: $target"
                        }
                    }
                }
            }
        }
        listener.onTarget(null)
        say(if (options.vietnamese) DONE_VI else DONE_EN, vietnamese = options.vietnamese)
    }

    private suspend fun say(text: String, vietnamese: Boolean, record: Boolean = false) {
        if (record) listener.onTurn(Speaker.AI, text)
        speech.speak(text, vietnamese)
    }

    private enum class Command { END, SKIP, REPEAT }

    /** Short requests only, and never the drill sentence itself ("Stop the car here" is a sentence to practise). */
    private fun commandIn(text: String, target: String): Command? {
        val folded = TopicManager.normalize(text)
        if (folded.split(' ').size > MAX_COMMAND_WORDS) return null
        if (PronunciationGrader.similarity(target, text) >= TARGET_SIMILARITY) return null
        val padded = " $folded "
        return when {
            END_PHRASES.any { padded.contains(" $it ") } -> Command.END
            VoiceCommandParser.parseSkipDrillSentenceCommand(text) -> Command.SKIP
            VoiceCommandParser.parseRepeatDrillSentenceCommand(text) -> Command.REPEAT
            else -> null
        }
    }

    private fun intro(vietnamese: Boolean, canListen: Boolean): String = when {
        vietnamese && canListen -> INTRO_VI
        vietnamese -> INTRO_VI + " " + SHADOW_ONLY_VI
        canListen -> INTRO_EN
        else -> INTRO_EN + " " + SHADOW_ONLY_EN
    }

    companion object {
        const val OFFLINE_NOTE = "Offline: checked by speech recognition only"
        const val MAX_TRIES = 2
        const val MAX_SILENCES = 2
        const val MAX_SENTENCES = 60
        const val LISTEN_TIMEOUT_MS = 8_000L
        const val SHADOW_PAUSE_MS = 2_000L
        const val SHADOW_MS_PER_WORD = 600L
        private const val MAX_COMMAND_WORDS = 5
        private const val TARGET_SIMILARITY = 0.5

        const val INTRO_VI = "Đang mất sóng. Trong lúc chờ, mình luyện nhắc lại câu tiếng Anh nhé. Nói \"dừng lại\" để kết thúc."
        const val INTRO_EN = "We are offline. Until the connection is back, let's practise repeating sentences. Say \"stop\" to end the lesson."
        const val SHADOW_ONLY_VI = "Máy chưa nghe được khi không có mạng, bạn cứ nói theo trong khoảng lặng."
        const val SHADOW_ONLY_EN = "I cannot check your answers offline, so just repeat in the pause."
        const val CANNOT_LISTEN_VI = "Máy chưa nghe được khi không có mạng. Bạn cứ nói theo trong khoảng lặng nhé."
        const val CANNOT_LISTEN_EN = "I cannot hear you offline. Just repeat in the pause."
        const val DONE_VI = "Hết câu luyện rồi. Mình chờ có mạng lại để tiếp tục nhé."
        const val DONE_EN = "That is all the practice for now. We will continue when the connection is back."

        private val PRAISE = listOf("Great!", "Well done!", "Nice!", "Perfect!")
        private val END_PHRASES = listOf(
            "dung lai", "ket thuc", "ket thuc bai hoc", "dung bai hoc", "thoi dung", "stop", "stop the lesson",
            "end the lesson", "that s enough", "finish"
        )
    }
}
