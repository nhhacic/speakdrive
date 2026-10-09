package com.speakdrive.ai.session

import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.LearnerSettings
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.SessionMode
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun `parses Vietnamese difficulty commands with diacritics`() {
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển sang cấp độ khó B1-B2"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("đổi sang mức trung cấp"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển sang cấp độ sơ cấp A2"))
            .isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceCommandParser.parseDifficultyCommand("đổi sang tiền trung cấp"))
            .isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển sang tiền trung cấp A2-B1"))
            .isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển level sang trung cấp trên B2"))
            .isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển sang cấp độ cơ bản A1-A2"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyển level sang nâng cao C1-C2"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("đổi sang mức khó"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("khó hơn đi"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("dễ hơn một chút"))
            .isEqualTo(DifficultyLevel.BEGINNER)
    }

    @Test
    fun `parses Vietnamese difficulty commands without diacritics`() {
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen sang cap do kho b1 b2"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("doi sang muc trung cap"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen sang cap do so cap a2"))
            .isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceCommandParser.parseDifficultyCommand("doi sang tien trung cap"))
            .isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen level sang trung cap tren b2"))
            .isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen sang cap do de a1"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen sang nang cao c1 c2"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("kho hon di"))
            .isEqualTo(DifficultyLevel.ADVANCED)
    }

    @Test
    fun `parses English difficulty commands`() {
        assertThat(VoiceCommandParser.parseDifficultyCommand("switch difficulty to B1-B2"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("change level to intermediate"))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("switch to elementary"))
            .isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceCommandParser.parseDifficultyCommand("change level to pre-intermediate"))
            .isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("switch to upper-intermediate"))
            .isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("switch to beginner"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("change difficulty to advanced"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make it harder"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make it easier"))
            .isEqualTo(DifficultyLevel.BEGINNER)
    }

    @Test
    fun `ignores non-command conversational mentions`() {
        assertThat(VoiceCommandParser.parseDifficultyCommand("I took the B1 exam last week")).isNull()
        assertThat(VoiceCommandParser.parseDifficultyCommand("My friend is a beginner at guitar")).isNull()
        assertThat(VoiceCommandParser.parseDifficultyCommand("Good morning, how are you?")).isNull()
    }

    @Test
    fun `parses Vietnamese help toggle commands`() {
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("bật giải thích tiếng Việt")).isTrue()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("cho phép tiếng Việt")).isTrue()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("turn on Vietnamese help")).isTrue()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("explain in Vietnamese")).isTrue()

        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("tắt tiếng Việt")).isFalse()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("chỉ nói tiếng Anh thôi")).isFalse()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("turn off Vietnamese")).isFalse()
        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("English only please")).isFalse()

        assertThat(VoiceCommandParser.parseVietnameseHelpCommand("I love Vietnamese food")).isNull()
    }

    @Test
    fun `parses storytelling style commands in Vietnamese and English`() {
        // Continuous / Podcast
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chuyển sang chế độ podcast"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chế độ kể liền mạch"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chuyen sang che do podcast"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("switch to podcast mode"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("continuous mode"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS)

        // Interactive
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chuyển sang chế độ tương tác"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chế độ hỏi đáp từng đoạn"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("doi che do tuong tac"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("switch to interactive mode"))
            .isEqualTo(com.speakdrive.ai.model.StorytellingStyle.INTERACTIVE)

        // Irrelevant phrases
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("I like listening to history stories")).isNull()
    }

    @Test
    fun `parses everyday podcast phrasings in Vietnamese and English`() {
        val continuous = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("chuyển sang podcast đi")).isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("kể kiểu podcast cho tôi nghe")).isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("kể liên tục luôn")).isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("ke lien tuc")).isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("please switch to podcast")).isEqualTo(continuous)
    }

    @Test
    fun `stop asking phrases mean podcast mode only during a story`() {
        val continuous = com.speakdrive.ai.model.StorytellingStyle.CONTINUOUS
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("đừng hỏi nữa, kể tiếp đi", inStorySession = true))
            .isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("dung hoi nua", inStorySession = true))
            .isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("Stop asking me questions!", inStorySession = true))
            .isEqualTo(continuous)
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("Don't ask me questions, keep going", inStorySession = true))
            .isEqualTo(continuous)

        // Outside a story these must not silently flip the storytelling setting.
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("đừng hỏi nữa", inStorySession = false)).isNull()
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("stop asking questions", inStorySession = false)).isNull()
        // Talking about podcasts in general is not a command.
        assertThat(VoiceCommandParser.parseStorytellingStyleCommand("my favorite podcast is about cars", inStorySession = false)).isNull()
    }

    @Test
    fun `parses next story commands in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseNextStoryCommand("next")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("tiếp theo")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("đổi chuyện khác")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("doi truyen khac")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("bỏ qua chuyện này")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("skip story")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("next story please")).isTrue()
        assertThat(VoiceCommandParser.parseNextStoryCommand("tell another story")).isTrue()

        assertThat(VoiceCommandParser.parseNextStoryCommand("what is the next word")).isFalse()
    }

    @Test
    fun `parses replay story commands in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseReplayStoryCommand("kể lại từ đầu")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("ke lai")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("nghe lại câu chuyện này")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("bật lại chuyện vừa nãy")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("replay story")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("start the story from beginning")).isTrue()
        assertThat(VoiceCommandParser.parseReplayStoryCommand("restart story")).isTrue()

        assertThat(VoiceCommandParser.parseReplayStoryCommand("can you repeat the last word")).isFalse()
    }

    @Test
    fun `parses pronunciation strictness commands in Vietnamese and English`() {
        // Auto / By level
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm theo cấp độ"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.AUTO)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm tự động"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.AUTO)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham theo trinh do"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.AUTO)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("grade by level"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.AUTO)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("auto strictness"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.AUTO)

        // Beginner (A1)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm theo A1"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.BEGINNER)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm beginner"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.BEGINNER)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham rat de"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.BEGINNER)

        // Elementary (A2 / relaxed)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm dễ tính hơn"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm phát âm dễ hơn"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham de hon"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("lenient pronunciation"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ELEMENTARY)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("relaxed pronunciation mode"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ELEMENTARY)

        // Pre-Intermediate (A2–B1)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm tiền trung cấp"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham theo a2 b1"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.PRE_INTERMEDIATE)

        // Intermediate (B1 / standard)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm tiêu chuẩn"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham binh thuong"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("standard pronunciation"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.INTERMEDIATE)

        // Upper-Intermediate (B2 / strict)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm khắt khe"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm gắt hơn"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham nghiem khac"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("strict pronunciation"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("grade strictly"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.UPPER_INTERMEDIATE)

        // Advanced (C1–C2 / native)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("chấm chuẩn bản xứ"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ADVANCED)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("cham theo c1"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ADVANCED)
        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("native pronunciation"))
            .isEqualTo(com.speakdrive.ai.model.PronunciationStrictness.ADVANCED)

        assertThat(VoiceCommandParser.parsePronunciationStrictnessCommand("Good job on pronunciation")).isNull()
    }

    @Test
    fun `parses barge-in commands in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseBargeInCommand("bật ngắt lời")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("cho phép ngắt lời")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("bật chế độ ngắt lời")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("ngắt lời ai")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("bat barge in")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("enable barge in")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("allow interruption")).isTrue()
        assertThat(VoiceCommandParser.parseBargeInCommand("turn on interruption")).isTrue()

        assertThat(VoiceCommandParser.parseBargeInCommand("tắt ngắt lời")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("không cho ngắt lời")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("tắt chế độ ngắt lời")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("đừng ngắt lời ai")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("tat barge in")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("disable barge in")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("no interruption")).isFalse()
        assertThat(VoiceCommandParser.parseBargeInCommand("turn off interruption")).isFalse()

        assertThat(VoiceCommandParser.parseBargeInCommand("I do not want to stop")).isNull()
    }

    @Test
    fun `parses AI voice commands in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseVoiceCommand("đổi giọng nam"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.PUCK)
        assertThat(VoiceCommandParser.parseVoiceCommand("đổi sang giọng Puck"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.PUCK)
        assertThat(VoiceCommandParser.parseVoiceCommand("giọng Charon nam trầm ấm"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.CHARON)
        assertThat(VoiceCommandParser.parseVoiceCommand("đổi giọng nữ"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.AOEDE)
        assertThat(VoiceCommandParser.parseVoiceCommand("giọng Kore nữ rõ ràng"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.KORE)
        assertThat(VoiceCommandParser.parseVoiceCommand("switch to female voice"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.AOEDE)
        assertThat(VoiceCommandParser.parseVoiceCommand("change voice to Charon"))
            .isEqualTo(com.speakdrive.ai.model.AiVoice.CHARON)

        assertThat(VoiceCommandParser.parseVoiceCommand("your voice sounds great")).isNull()
    }

    @Test
    fun `parses random voice commands in Vietnamese with diacritics`() {
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("chọn giọng ngẫu nhiên")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("đổi giọng ngẫu nhiên")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("bật giọng ngẫu nhiên")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("mỗi bài một giọng")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("chọn giọng bất kỳ")).isTrue()

        assertThat(VoiceCommandParser.parseRandomVoiceCommand("tắt giọng ngẫu nhiên")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("giữ cố định giọng")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("không ngẫu nhiên")).isFalse()
    }

    @Test
    fun `parses random voice commands in Vietnamese without diacritics`() {
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("chon giong ngau nhien")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("doi giong ngau nhien")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("bat giong ngau nhien")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("moi bai mot giong")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("chon giong bat ky")).isTrue()

        assertThat(VoiceCommandParser.parseRandomVoiceCommand("tat giong ngau nhien")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("giu co dinh giong")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("khong ngau nhien")).isFalse()
    }

    @Test
    fun `parses random voice commands in English`() {
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("random voice")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("pick a random voice")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("switch to random voice")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("randomize voices")).isTrue()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("different voice each lesson")).isTrue()

        assertThat(VoiceCommandParser.parseRandomVoiceCommand("turn off random voice")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("disable random voice")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("stop random voice")).isFalse()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("fixed voice")).isFalse()
    }

    @Test
    fun `ignores non-command random mentions`() {
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("I had a random thought")).isNull()
        assertThat(VoiceCommandParser.parseRandomVoiceCommand("That was a voice")).isNull()
    }

    @Test
    fun `parses story duration commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseStoryDurationCommand("chuyện ngắn 5 phút"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("kể chuyện ngắn thôi"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("truyện 5 phút"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)

        assertThat(VoiceCommandParser.parseStoryDurationCommand("chuyện dài 15 phút"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("chuyện dài đầy đủ"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("nghe chuyện 30 phút"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseStoryDurationCommand("chuyen ngan 5 phut"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("truyen ngan"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("chuyen dai 20 phut"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("truyen day du"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)

        // English
        assertThat(VoiceCommandParser.parseStoryDurationCommand("short story 5 minutes"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("5 minute story"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("quick story"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.SHORT_5_MIN)

        assertThat(VoiceCommandParser.parseStoryDurationCommand("full story"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("extended story"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
        assertThat(VoiceCommandParser.parseStoryDurationCommand("10 to 30 minutes"))
            .isEqualTo(com.speakdrive.ai.model.StoryDuration.FULL_10_TO_30_MIN)
    }

    @Test
    fun `parses multi-voice commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("bật lồng tiếng đa giọng")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("kịch truyền thanh đa vai")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("kể nhiều giọng")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("tắt đa giọng")).isFalse()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("kể một giọng thôi")).isFalse()

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("bat da giong")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("kich truyen thanh")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("tat da giong")).isFalse()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("mot giong thoi")).isFalse()

        // English
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("enable multi voice")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("multi voice drama")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("character voices")).isTrue()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("disable multi voice")).isFalse()
        assertThat(VoiceCommandParser.parseMultiVoiceCommand("single voice")).isFalse()
    }

    @Test
    fun `parses resume story commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseResumeStoryCommand("tiếp tục câu chuyện")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("kể tiếp chuyện hôm trước")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("bật tiếp chuyện nghe dở")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("nghe tiếp truyện dở")).isTrue()

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseResumeStoryCommand("tiep tuc cau chuyen")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("ke tiep chuyen hom truoc")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("bat tiep chuyen nghe do")).isTrue()

        // English
        assertThat(VoiceCommandParser.parseResumeStoryCommand("resume story")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("continue last story")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("pick up where we left off")).isTrue()
        assertThat(VoiceCommandParser.parseResumeStoryCommand("resume unfinished story")).isTrue()

        // Irrelevant
        assertThat(VoiceCommandParser.parseResumeStoryCommand("I like this story")).isFalse()
    }

    @Test
    fun `parses app language commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseAppLanguageCommand("đổi ngôn ngữ sang tiếng việt"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("chuyển sang tiếng anh"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("đổi giao diện sang tiếng nhật"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("chuyển ngôn ngữ sang tiếng hàn"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.KOREAN)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("cài tiếng trung"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.CHINESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("chuyển sang tiếng pháp"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.FRENCH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("đổi sang tiếng đức"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.GERMAN)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("đổi sang tiếng tây ban nha"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.SPANISH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("ngôn ngữ mặc định hệ thống"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.SYSTEM)

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseAppLanguageCommand("doi ngon ngu sang tieng viet"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("chuyen sang tieng anh"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("doi sang tieng nhat"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)

        // English
        assertThat(VoiceCommandParser.parseAppLanguageCommand("change language to English"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.ENGLISH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("switch app language to Vietnamese"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.VIETNAMESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("set language to Spanish"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.SPANISH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("change language to Japanese"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.JAPANESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("switch to Korean"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.KOREAN)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("change to Chinese"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.CHINESE)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("switch language to French"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.FRENCH)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("set language to German"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.GERMAN)
        assertThat(VoiceCommandParser.parseAppLanguageCommand("set language to system default"))
            .isEqualTo(com.speakdrive.ai.model.AppLanguage.SYSTEM)

        // Irrelevant phrases
        assertThat(VoiceCommandParser.parseAppLanguageCommand("I want to speak in English today")).isNull()
    }

    @Test
    fun `parses skip drill sentence commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("bỏ qua câu này")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("chuyển sang câu khác")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("câu tiếp theo")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("câu khác đi")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("đổi câu khác")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("luyện câu khác")).isTrue()

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("bo qua cau nay")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("chuyen sang cau khac")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("cau tiep theo")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("bo qua")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("cau khac")).isTrue()

        // English
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("skip")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("next")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("skip sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("skip this sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("next sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("another sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("change sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("more diverse sentences")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("give me a different sentence")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("câu khác phong phú hơn")).isTrue()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("câu mới")).isTrue()

        // Irrelevant phrases
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("I want to repeat after you")).isFalse()
        assertThat(VoiceCommandParser.parseSkipDrillSentenceCommand("The sentence is very difficult")).isFalse()
    }

    @Test
    fun `parses repeat drill sentence commands in Vietnamese and English`() {
        // Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("đọc lại câu này")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("nói lại câu này")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("cho nghe lại")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("lặp lại")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("nhắc lại câu này")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("đọc lại")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("nói lại")).isTrue()

        // Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("doc lai cau nay")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("noi lai cau nay")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("nghe lai")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("lap lai")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("doc lai")).isTrue()

        // English
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("repeat")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("repeat sentence")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("repeat this sentence")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("say again")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("say that again")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("one more time")).isTrue()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("repeat please")).isTrue()

        // Irrelevant phrases
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("I am ready to speak")).isFalse()
        assertThat(VoiceCommandParser.parseRepeatDrillSentenceCommand("The sentence is very difficult")).isFalse()
    }

    @Test
    fun `parses apply level recommendation commands in Vietnamese and English`() {
        // Vietnamese with diacritics - Accept
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("đồng ý tăng cấp độ")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("đồng ý lên cấp")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("chấp nhận gợi ý")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("lên cấp đi")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("đồng ý đổi cấp độ")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("đồng ý")).isTrue()

        // Vietnamese without diacritics - Accept
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("dong y tang cap do")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("dong y len cap")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("chap nhan goi y")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("len cap di")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("doi cap do moi")).isTrue()

        // English - Accept
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("level up")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("accept recommendation")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("yes change level")).isTrue()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("apply new level")).isTrue()

        // Vietnamese with diacritics - Keep / Dismiss
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("giữ nguyên cấp độ")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("không đổi cấp độ")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("ở lại cấp độ này")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("để sau")).isFalse()

        // Vietnamese without diacritics - Keep / Dismiss
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("giu nguyen cap do")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("khong doi cap do")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("de sau")).isFalse()

        // English - Keep / Dismiss
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("keep current level")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("stay at current level")).isFalse()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("no thanks")).isFalse()

        // Unrelated
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("hôm nay trời đẹp")).isNull()
        assertThat(VoiceCommandParser.parseApplyRecommendationCommand("tell me about travel")).isNull()
    }

    @Test
    fun `parses adaptive level toggle commands in Vietnamese and English`() {
        // Enable
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("bật tự động gợi ý cấp độ")).isTrue()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("bat goi y cap do")).isTrue()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("enable adaptive level")).isTrue()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("turn on level recommendation")).isTrue()

        // Disable
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("tắt tự động gợi ý cấp độ")).isFalse()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("tat goi y cap do")).isFalse()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("disable adaptive level")).isFalse()
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("turn off level recommendation")).isFalse()

        // Unrelated
        assertThat(VoiceCommandParser.parseAdaptiveLevelCommand("bật micro")).isNull()
    }

    @Test
    fun `parses drill sentence length commands in Vietnamese and English`() {
        // Auto on car / Driving mode - Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu ngắn khi lái xe"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("rút ngắn câu khi kết nối ô tô"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu ngắn trên xe"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("tự động ngắn khi lái xe"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu ngắn android auto"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        // Auto on car - Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("cau ngan khi lai xe"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("rut ngan cau tren xe"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        // Auto on car - English
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("short sentences for driving"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("auto short on car"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("short sentences android auto"))
            .isEqualTo(DrillSentenceLength.AUTO_ON_CAR)

        // Short mode - Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu ngắn thôi"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("rút ngắn câu lặp lại"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu nhắc lại ngắn gọn"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("nói câu ngắn thôi"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("chế độ câu ngắn"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)

        // Short mode - Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("cau ngan thoi"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("rut ngan cau nhac lai"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)

        // Short mode - English
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("short sentences please"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("shorter drill sentences"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("keep sentences short"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("short repetition"))
            .isEqualTo(DrillSentenceLength.ALWAYS_SHORT)

        // Standard mode - Vietnamese with diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu dài hơn"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("độ dài tiêu chuẩn"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("câu bình thường"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("tắt câu ngắn"))
            .isEqualTo(DrillSentenceLength.STANDARD)

        // Standard mode - Vietnamese without diacritics
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("cau dai hon"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("do dai tieu chuan"))
            .isEqualTo(DrillSentenceLength.STANDARD)

        // Standard mode - English
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("standard sentence length"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("longer drill sentences"))
            .isEqualTo(DrillSentenceLength.STANDARD)
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("normal sentences"))
            .isEqualTo(DrillSentenceLength.STANDARD)

        // Unrelated
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("hôm nay trời đẹp")).isNull()
        assertThat(VoiceCommandParser.parseDrillSentenceLengthCommand("tôi muốn học tiếng anh")).isNull()
    }

    @Test
    fun `parses story difficulty commands in Vietnamese and English`() {
        // Story easier / simpler - Vietnamese
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể dễ hơn đi"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể đơn giản hơn"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyện khó quá"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("truyện khó quá kể đơn giản thôi"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("hạ độ khó"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen kho qua"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("ke de hon"))
            .isEqualTo(DifficultyLevel.BEGINNER)

        // Story easier / simpler - English
        assertThat(VoiceCommandParser.parseDifficultyCommand("make the story easier"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make story simpler"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("tell a simpler story"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("this story is too hard"))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("too difficult"))
            .isEqualTo(DifficultyLevel.BEGINNER)

        // Story harder / more advanced - Vietnamese
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể khó hơn đi"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể nâng cao hơn"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyện dễ quá"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("tăng độ khó câu chuyện"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyen de qua"))
            .isEqualTo(DifficultyLevel.ADVANCED)

        // Story harder / more advanced - English
        assertThat(VoiceCommandParser.parseDifficultyCommand("make the story harder"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make story more advanced"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("tell a more advanced story"))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("this story is too easy"))
            .isEqualTo(DifficultyLevel.ADVANCED)
    }

    @Test
    fun `relative level stepping adjusts smoothly from current level`() {
        // Stepping down from Intermediate
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể dễ hơn đi", currentLevel = DifficultyLevel.INTERMEDIATE))
            .isEqualTo(DifficultyLevel.PRE_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("chuyện khó quá", currentLevel = DifficultyLevel.PRE_INTERMEDIATE))
            .isEqualTo(DifficultyLevel.ELEMENTARY)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make it simpler", currentLevel = DifficultyLevel.ELEMENTARY))
            .isEqualTo(DifficultyLevel.BEGINNER)
        assertThat(VoiceCommandParser.parseDifficultyCommand("easier please", currentLevel = DifficultyLevel.BEGINNER))
            .isNull()

        // Stepping up from Pre-Intermediate
        assertThat(VoiceCommandParser.parseDifficultyCommand("kể nâng cao hơn", currentLevel = DifficultyLevel.PRE_INTERMEDIATE))
            .isEqualTo(DifficultyLevel.INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("make story more advanced", currentLevel = DifficultyLevel.INTERMEDIATE))
            .isEqualTo(DifficultyLevel.UPPER_INTERMEDIATE)
        assertThat(VoiceCommandParser.parseDifficultyCommand("harder please", currentLevel = DifficultyLevel.UPPER_INTERMEDIATE))
            .isEqualTo(DifficultyLevel.ADVANCED)
        assertThat(VoiceCommandParser.parseDifficultyCommand("tăng độ khó", currentLevel = DifficultyLevel.ADVANCED))
            .isNull()
    }

    @Test
    fun `parses drill category commands in Vietnamese and English`() {
        // Vietnamese pitfalls
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("lỗi phát âm người Việt"))
            .isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("luyện âm đuôi"))
            .isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("practice vietnamese pitfalls"))
            .isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("ending sounds"))
            .isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)

        // Driving phrases
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tiếng Anh lái xe"))
            .isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("luyện câu lái xe"))
            .isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("driving phrases"))
            .isEqualTo(DrillCategory.DRIVING_PHRASES)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("english while driving"))
            .isEqualTo(DrillCategory.DRIVING_PHRASES)

        // Conversational reflex
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("phản xạ giao tiếp"))
            .isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("phản xạ hàng ngày"))
            .isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("conversational reflex"))
            .isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("daily reflex"))
            .isEqualTo(DrillCategory.CONVERSATIONAL_REFLEX)

        // Business & work
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tiếng Anh công sở"))
            .isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("chủ đề văn phòng"))
            .isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("business english"))
            .isEqualTo(DrillCategory.BUSINESS_WORK)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("work phrases"))
            .isEqualTo(DrillCategory.BUSINESS_WORK)

        // Travel & daily
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tiếng Anh du lịch"))
            .isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("chủ đề du lịch"))
            .isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("travel phrases"))
            .isEqualTo(DrillCategory.TRAVEL_DAILY)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tourism phrases"))
            .isEqualTo(DrillCategory.TRAVEL_DAILY)

        // All categories
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tất cả chủ đề"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("chủ đề tổng hợp"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("all drill topics"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("all categories"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("luyện phong phú hơn"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("đa dạng hơn"))
            .isEqualTo(DrillCategory.ALL)
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("more variety"))
            .isEqualTo(DrillCategory.ALL)

        // Non-matching phrases
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("thời tiết hôm nay thế nào"))
            .isNull()
        assertThat(VoiceCommandParser.parseDrillCategoryCommand(""))
            .isNull()
    }

    @Test
    fun `parseVocabStudyCommand recognizes Vietnamese and English intents`() {
        // Pronunciation
        assertThat(VoiceCommandParser.parseVocabStudyCommand("luyện phát âm từ vựng"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.PRONUNCIATION)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("phát âm từ mới"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.PRONUNCIATION)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("vocab pronunciation"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.PRONUNCIATION)

        // Sentence making
        assertThat(VoiceCommandParser.parseVocabStudyCommand("luyện đặt câu"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.SENTENCE_MAKING)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("thử thách đặt câu"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.SENTENCE_MAKING)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("practice making sentences"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.SENTENCE_MAKING)

        // Review all
        assertThat(VoiceCommandParser.parseVocabStudyCommand("sổ từ vựng"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.REVIEW_ALL)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("học từ mới"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.REVIEW_ALL)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("ôn tập từ vựng"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.REVIEW_ALL)
        assertThat(VoiceCommandParser.parseVocabStudyCommand("practice vocabulary"))
            .isEqualTo(VoiceCommandParser.VocabStudyIntent.REVIEW_ALL)

        // Non-matching
        assertThat(VoiceCommandParser.parseVocabStudyCommand("kể chuyện cho tôi nghe"))
            .isNull()
    }

    @Test
    fun `parseVolumeCommand recognizes Vietnamese and English volume requests`() {
        // Vietnamese relative down / softer
        assertThat(VoiceCommandParser.parseVolumeCommand("nói nhỏ lại một chút", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("cho nhỏ tiếng lại", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("giảm âm lượng", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("bé lại giùm tôi", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("nói nhỏ thôi", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("nho lai di", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("ha am luong", 80)).isEqualTo(60)

        // Vietnamese relative up / louder
        assertThat(VoiceCommandParser.parseVolumeCommand("nói to lên nhé", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("tăng âm lượng lên", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("cho to tiếng hơn", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("to len ti", 60)).isEqualTo(80)

        // English relative down / softer
        assertThat(VoiceCommandParser.parseVolumeCommand("volume down please", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("speak softer", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("can you be quieter", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("turn down the volume", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("lower volume", 80)).isEqualTo(60)

        // English relative up / louder
        assertThat(VoiceCommandParser.parseVolumeCommand("volume up", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("speak louder please", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("turn up volume", 60)).isEqualTo(80)
        assertThat(VoiceCommandParser.parseVolumeCommand("make it louder", 60)).isEqualTo(80)

        // Explicit percentages and numbers
        assertThat(VoiceCommandParser.parseVolumeCommand("âm lượng 50%", 80)).isEqualTo(50)
        assertThat(VoiceCommandParser.parseVolumeCommand("cho âm lượng 70%", 80)).isEqualTo(70)
        assertThat(VoiceCommandParser.parseVolumeCommand("volume 60", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("set volume to 40%", 80)).isEqualTo(40)

        // Max & min
        assertThat(VoiceCommandParser.parseVolumeCommand("âm lượng tối đa", 60)).isEqualTo(100)
        assertThat(VoiceCommandParser.parseVolumeCommand("max volume", 60)).isEqualTo(100)
        assertThat(VoiceCommandParser.parseVolumeCommand("âm lượng nhỏ nhất", 60)).isEqualTo(10)
        assertThat(VoiceCommandParser.parseVolumeCommand("min volume", 60)).isEqualTo(10)

        // Non-matching
        assertThat(VoiceCommandParser.parseVolumeCommand("hôm nay trời đẹp quá")).isNull()
    }

    @Test
    fun `parseAutoPauseWhenUnfocusedCommand recognizes Vietnamese and English intents`() {
        // Vietnamese enable
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("bật tự động tạm dừng")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tự động tạm dừng khi tắt màn hình")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tạm dừng khi rời app")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tạm dừng khi khóa màn hình")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tự động dừng khi thoát app")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tự động pause khi tắt màn hình")).isTrue()

        // Vietnamese disable
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tắt tự động tạm dừng")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tắt tạm dừng khi tắt màn hình")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("không tạm dừng khi tắt màn hình")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("đừng tạm dừng khi rời app")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tắt tự động pause")).isFalse()

        // English enable
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("enable auto pause")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("turn on auto pause")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("auto pause on screen off")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("pause when screen off")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("pause when leaving app")).isTrue()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("pause when screen locked")).isTrue()

        // English disable
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("disable auto pause")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("turn off auto pause")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("stop auto pause")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("don't pause when screen off")).isFalse()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("keep playing in background")).isFalse()

        // Non-matching
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("hôm nay thời tiết đẹp quá")).isNull()
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("tell me about your day")).isNull()
    }

    @Test
    fun `parseTranslationSubtitlesCommand recognizes Vietnamese and English intents`() {
        // Vietnamese enable
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("bật phụ đề")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("bật phụ đề dịch")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("hiện bản dịch")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("mở phụ đề tiếng việt")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("dịch câu này")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("hien phu de")).isTrue()

        // Vietnamese disable
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("tắt phụ đề")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("tắt phụ đề dịch")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("ẩn bản dịch")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("tat phu de")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("đừng hiện bản dịch")).isFalse()

        // English enable
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("enable subtitles")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("turn on translation")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("show subtitle")).isTrue()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("show translation subtitles")).isTrue()

        // English disable
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("disable subtitles")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("turn off translation")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("hide subtitle")).isFalse()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("no subtitles")).isFalse()

        // Non-matching
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("hôm nay bạn thế nào")).isNull()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("good morning teacher")).isNull()
    }

    @Test
    fun `parseSessionModeCommand recognizes Vietnamese and English intents`() {
        // Vietnamese Shadowing / Repeat after me / Pronunciation
        assertThat(VoiceCommandParser.parseSessionModeCommand("luyện phát âm"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang luyện phát âm"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyen sang phat am"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("tập phát âm"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("luyện shadowing"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang shadowing"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyen qua shadowing"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("nhắc lại theo bạn"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("doc theo ban"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("luyện nói theo"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)

        // English Shadowing
        assertThat(VoiceCommandParser.parseSessionModeCommand("switch to shadowing"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("practice pronunciation"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("repeat after me mode"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)
        assertThat(VoiceCommandParser.parseSessionModeCommand("shadowing mode"))
            .isEqualTo(SessionMode.REPEAT_AFTER_ME)

        // Story Listening
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang kể chuyện"))
            .isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(VoiceCommandParser.parseSessionModeCommand("kể chuyện đi"))
            .isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(VoiceCommandParser.parseSessionModeCommand("tell me a story"))
            .isEqualTo(SessionMode.STORY_LISTENING)
        assertThat(VoiceCommandParser.parseSessionModeCommand("story listening"))
            .isEqualTo(SessionMode.STORY_LISTENING)

        // Roleplay
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang nhập vai"))
            .isEqualTo(SessionMode.ROLEPLAY)
        assertThat(VoiceCommandParser.parseSessionModeCommand("đóng vai tình huống"))
            .isEqualTo(SessionMode.ROLEPLAY)
        assertThat(VoiceCommandParser.parseSessionModeCommand("switch to roleplay"))
            .isEqualTo(SessionMode.ROLEPLAY)

        // Vocab Review
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang ôn từ vựng"))
            .isEqualTo(SessionMode.VOCAB_REVIEW)
        assertThat(VoiceCommandParser.parseSessionModeCommand("ôn tập từ vựng"))
            .isEqualTo(SessionMode.VOCAB_REVIEW)
        assertThat(VoiceCommandParser.parseSessionModeCommand("switch to vocabulary"))
            .isEqualTo(SessionMode.VOCAB_REVIEW)

        // Free talk / Conversation
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang hội thoại"))
            .isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceCommandParser.parseSessionModeCommand("hội thoại tự do"))
            .isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceCommandParser.parseSessionModeCommand("switch to conversation"))
            .isEqualTo(SessionMode.FREE_TALK)
        assertThat(VoiceCommandParser.parseSessionModeCommand("free talk"))
            .isEqualTo(SessionMode.FREE_TALK)

        // Non-matching
        assertThat(VoiceCommandParser.parseSessionModeCommand("hôm nay thời tiết đẹp quá")).isNull()
        assertThat(VoiceCommandParser.parseSessionModeCommand("can you speak louder")).isNull()
    }

    @Test
    fun `everyday sentences with command-like words are not parsed as settings`() {
        // "tiếng" means hour / sound / language: a number next to it is not a volume.
        assertThat(VoiceCommandParser.parseVolumeCommand("tôi lái xe 2 tiếng mỗi ngày", 80)).isNull()
        assertThat(VoiceCommandParser.parseVolumeCommand("anh trai tôi lớn hơn tôi", 80)).isNull()
        assertThat(VoiceCommandParser.parseVolumeCommand("tôi nhớ lại rồi", 80)).isNull()
        assertThat(VoiceCommandParser.parseVolumeCommand("nho lai di", 80)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseVolumeCommand("cho âm lượng 70 phần trăm", 80)).isEqualTo(70)

        assertThat(VoiceCommandParser.parseAppLanguageCommand("Tôi đang học tiếng Anh")).isNull()
        assertThat(VoiceCommandParser.parseAppLanguageCommand("I use English at work")).isNull()
        assertThat(VoiceCommandParser.parseAppLanguageCommand("Tôi đói, tiếng Anh nói thế nào")).isNull()

        assertThat(VoiceCommandParser.parseVoiceCommand("I love Korean food")).isNull()
        assertThat(VoiceCommandParser.parseVoiceCommand("rõ ràng là vậy")).isNull()
        assertThat(VoiceCommandParser.parseVoiceCommand("hôm nay tôi rất vui vẻ")).isNull()

        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tôi đang lái xe")).isNull()
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tôi làm việc ở ngân hàng")).isNull()
        assertThat(VoiceCommandParser.parseDrillCategoryCommand("tôi thích đi du lịch")).isNull()

        // Whole words only.
        assertThat(VoiceCommandParser.parseAutoPauseWhenUnfocusedCommand("the video auto paused")).isNull()
        assertThat(VoiceCommandParser.parseTranslationSubtitlesCommand("there were no translations")).isNull()
    }

    @Test
    fun `mistake review mode is recognised in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang ôn lỗi sai")).isEqualTo(SessionMode.MISTAKE_REVIEW)
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyen sang on loi sai")).isEqualTo(SessionMode.MISTAKE_REVIEW)
        assertThat(VoiceCommandParser.parseSessionModeCommand("ôn lại lỗi cũ")).isEqualTo(SessionMode.MISTAKE_REVIEW)
        assertThat(VoiceCommandParser.parseSessionModeCommand("review my mistakes")).isEqualTo(SessionMode.MISTAKE_REVIEW)
        // Vocabulary review is still vocabulary review.
        assertThat(VoiceCommandParser.parseSessionModeCommand("chuyển sang ôn từ vựng")).isEqualTo(SessionMode.VOCAB_REVIEW)
    }

    @Test
    fun `learner memory can be turned on and off`() {
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("tắt ghi nhớ")).isFalse()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("tat ghi nho")).isFalse()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("đừng ghi nhớ gì về tôi")).isFalse()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("turn off memory")).isFalse()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("don't remember anything about me")).isFalse()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("bật ghi nhớ")).isTrue()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("hãy nhớ về tôi nhé")).isTrue()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("turn on memory")).isTrue()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("I remember my first car")).isNull()
        assertThat(VoiceCommandParser.parseLearnerMemoryCommand("Tôi nhớ lại chuyến đi đó")).isNull()
    }

    @Test
    fun `practice reminder requests in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("tắt nhắc học"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = false))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("tat nhac hoc"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = false))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("bật nhắc học"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = true))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("nhắc học lúc 7 giờ sáng"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = true, minuteOfDay = 7 * 60))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("đặt giờ nhắc 19h30"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = true, minuteOfDay = 19 * 60 + 30))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("nhắc học tự động"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = true, minuteOfDay = LearnerSettings.REMINDER_AUTO))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("turn off reminders"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = false))
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("remind me to practice at 6:30 pm"))
            .isEqualTo(VoiceCommandParser.PracticeReminderRequest(enabled = true, minuteOfDay = 18 * 60 + 30))
    }

    @Test
    fun `everyday reminders and repeat requests are not app reminder commands`() {
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("Can you remind me at 7 to call my mom")).isNull()
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("nhắc lại câu này")).isNull()
        assertThat(VoiceCommandParser.parsePracticeReminderCommand("Tôi quên mất cuộc hẹn lúc 7 giờ")).isNull()
    }

    @Test
    fun `times of day are read from speech`() {
        assertThat(VoiceCommandParser.parseTimeOfDay("7 giờ sáng")).isEqualTo(7 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("7 giờ rưỡi tối")).isEqualTo(19 * 60 + 30)
        assertThat(VoiceCommandParser.parseTimeOfDay("6 giờ chiều")).isEqualTo(18 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("11 giờ đêm")).isEqualTo(23 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("19h")).isEqualTo(19 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("07:45")).isEqualTo(7 * 60 + 45)
        assertThat(VoiceCommandParser.parseTimeOfDay("7 am")).isEqualTo(7 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("12 am")).isEqualTo(0)
        assertThat(VoiceCommandParser.parseTimeOfDay("at 9 in the evening")).isEqualTo(21 * 60)
        // "tôi" (me) folds like "tối" (evening): only the word right after the time counts.
        assertThat(VoiceCommandParser.parseTimeOfDay("lúc 7 giờ cho tôi")).isEqualTo(7 * 60)
        assertThat(VoiceCommandParser.parseTimeOfDay("25 giờ")).isNull()
        assertThat(VoiceCommandParser.parseTimeOfDay("không có giờ nào")).isNull()
    }

    @Test
    fun `streak freeze can be turned on and off`() {
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("tắt bảo toàn chuỗi")).isFalse()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("tat bao toan chuoi")).isFalse()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("bật bảo toàn chuỗi")).isTrue()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("dùng bảo toàn chuỗi")).isNull()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("turn off streak freeze")).isFalse()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("turn on streak freeze")).isTrue()
        assertThat(VoiceCommandParser.parseStreakFreezeCommand("My streak is long")).isNull()
    }

    @Test
    fun `offline practice can be turned on and off`() {
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("tắt luyện offline")).isFalse()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("tat luyen offline")).isFalse()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("mất sóng thì đừng luyện")).isFalse()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("turn off offline practice")).isFalse()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("bật luyện offline")).isTrue()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("turn on offline practice")).isTrue()
        assertThat(VoiceCommandParser.parseOfflinePracticeCommand("I practise offline sometimes")).isNull()
    }

    @Test
    fun `auto start in the car is recognised in Vietnamese with and without diacritics and in English`() {
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("tắt tự động học khi lên xe")).isFalse()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("tat tu dong hoc khi len xe")).isFalse()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("đừng tự bắt đầu khi kết nối ô tô")).isFalse()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("bật tự động bắt đầu khi lên xe")).isTrue()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("bat tu dong bat dau khi ket noi o to")).isTrue()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("don't start lessons automatically in the car")).isFalse()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("turn on auto start in the car")).isTrue()
        assertThat(VoiceCommandParser.parseAutoStartInCarCommand("tôi đang lái xe")).isNull()
    }

    @Test
    fun `daily goal is recognised with numbers and relative changes`() {
        assertThat(VoiceCommandParser.parseDailyGoalCommand("đặt mục tiêu 20 phút mỗi ngày", 15)).isEqualTo(20)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("dat muc tieu 30 phut", 15)).isEqualTo(30)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("set my daily goal to 25 minutes", 15)).isEqualTo(25)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("tăng mục tiêu lên", 15)).isEqualTo(20)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("lower my daily goal", 15)).isEqualTo(10)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("mục tiêu 200 phút", 15)).isEqualTo(60)
        assertThat(VoiceCommandParser.parseDailyGoalCommand("hôm nay tôi học 20 phút", 15)).isNull()
    }

    @Test
    fun `azure scoring needs the word Azure and tells using from stopping`() {
        assertThat(VoiceCommandParser.parseAzureScoringCommand("bật chấm điểm Azure")).isTrue()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("dùng Azure để chấm")).isTrue()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("dừng dùng Azure")).isFalse()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("tat azure")).isFalse()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("turn on Azure scoring")).isTrue()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("stop using Azure")).isFalse()
        assertThat(VoiceCommandParser.parseAzureScoringCommand("bật chấm điểm")).isNull()
    }

    @Test
    fun `screen awake mode is recognised and leaves auto-pause requests alone`() {
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("luôn sáng màn hình")).isEqualTo(ScreenAwakeMode.ALWAYS_ON)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("giu man hinh sang")).isEqualTo(ScreenAwakeMode.ALWAYS_ON)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("cho màn hình tự tắt theo máy")).isEqualTo(ScreenAwakeMode.FOLLOW_SYSTEM)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("tắt màn hình sau 1 phút")).isEqualTo(ScreenAwakeMode.AFTER_1_MINUTE)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("tat man hinh sau 30 giay")).isEqualTo(ScreenAwakeMode.AFTER_30_SECONDS)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("keep the screen on")).isEqualTo(ScreenAwakeMode.ALWAYS_ON)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("turn the screen off after 5 minutes")).isEqualTo(ScreenAwakeMode.AFTER_5_MINUTES)
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("tạm dừng khi tắt màn hình")).isNull()
        assertThat(VoiceCommandParser.parseScreenAwakeCommand("màn hình điện thoại tôi bị vỡ")).isNull()
    }

    @Test
    fun `better phrasing is recognised in Vietnamese and English`() {
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("bật gợi ý nói hay hơn")).isTrue()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("bat goi y noi hay hon")).isTrue()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("hãy gợi ý cách nói tự nhiên hơn")).isTrue()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("turn on better phrasing")).isTrue()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("suggest more natural phrases")).isTrue()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("tắt gợi ý nói hay hơn")).isFalse()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("tat goi y noi tu nhien")).isFalse()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("đừng sửa câu hay hơn")).isFalse()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("turn off better phrasing")).isFalse()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("stop natural suggestions")).isFalse()
        assertThat(VoiceCommandParser.parseBetterPhrasingCommand("hôm nay thời tiết rất đẹp")).isNull()
    }
}
