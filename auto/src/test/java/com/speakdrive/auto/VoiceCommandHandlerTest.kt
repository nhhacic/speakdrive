package com.speakdrive.auto

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.DifficultyLevel
import org.junit.Test

class VoiceCommandHandlerTest {

    private val topics = TopicManager()
    private val handler = VoiceCommandHandler(topics)

    @Test
    fun `no query resumes the last lesson`() {
        assertThat(handler.resolve(null)).isEqualTo(MediaIds.RESUME)
        assertThat(handler.resolve("")).isEqualTo(MediaIds.RESUME)
        assertThat(handler.resolve("English practice")).isEqualTo(MediaIds.RESUME)
    }

    @Test
    fun `topic queries pick the topic`() {
        assertThat(handler.resolve("travel English")).isEqualTo(MediaIds.topic("travel"))
        assertThat(handler.resolve("play job interview practice on SpeakDrive")).isEqualTo(MediaIds.topic("interview"))
        assertThat(handler.resolve("bài học phỏng vấn")).isEqualTo(MediaIds.topic("interview"))
        assertThat(handler.resolve("bác sĩ y khoa")).isEqualTo(MediaIds.topic("medical_expert"))
        assertThat(handler.resolve("lập trình phần mềm")).isEqualTo(MediaIds.topic("tech_it"))
        assertThat(handler.resolve("kỹ thuật xây dựng công trình")).isEqualTo(MediaIds.topic("civil_engineering"))
        assertThat(handler.resolve("quy hoạch giao thông hạ tầng")).isEqualTo(MediaIds.topic("transport_engineering"))
    }

    @Test
    fun `level words are kept with the topic`() {
        assertThat(handler.resolve("easy travel English")).isEqualTo(MediaIds.topic("travel", DifficultyLevel.BEGINNER))
        assertThat(handler.resolve("elementary travel English")).isEqualTo(MediaIds.topic("travel", DifficultyLevel.ELEMENTARY))
        assertThat(handler.resolve("pre-intermediate business lesson")).isEqualTo(MediaIds.topic("work", DifficultyLevel.PRE_INTERMEDIATE))
        assertThat(handler.resolve("upper-intermediate travel English")).isEqualTo(MediaIds.topic("travel", DifficultyLevel.UPPER_INTERMEDIATE))
        assertThat(handler.resolve("advanced business lesson")).isEqualTo(MediaIds.topic("work", DifficultyLevel.ADVANCED))
    }

    @Test
    fun `a level alone selects the level`() {
        assertThat(handler.resolve("hard English lesson")).isEqualTo(MediaIds.level(DifficultyLevel.ADVANCED))
        assertThat(handler.resolve("elementary English lesson")).isEqualTo(MediaIds.level(DifficultyLevel.ELEMENTARY))
        assertThat(handler.resolve("pre-intermediate English lesson")).isEqualTo(MediaIds.level(DifficultyLevel.PRE_INTERMEDIATE))
    }

    @Test
    fun `review and random`() {
        assertThat(handler.resolve("review my vocabulary")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("ôn tập từ vựng")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("mở sổ từ vựng")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("học từ mới")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("practice vocabulary")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("random topic")).isEqualTo(MediaIds.RANDOM)
    }

    @Test
    fun `ielts speaking voice queries`() {
        assertThat(handler.resolve("luyện thi ielts")).isEqualTo(MediaIds.IELTS)
        assertThat(handler.resolve("thi ielts")).isEqualTo(MediaIds.IELTS)
        assertThat(handler.resolve("ielts speaking")).isEqualTo(MediaIds.IELTS)
        assertThat(handler.resolve("practice ielts on SpeakDrive")).isEqualTo(MediaIds.IELTS)
    }

    @Test
    fun `vocabulary pronunciation and sentence challenge commands`() {
        assertThat(handler.resolve("luyện phát âm từ vựng")).isEqualTo(MediaIds.vocabMode("pronunciation"))
        assertThat(handler.resolve("vocab pronunciation")).isEqualTo(MediaIds.vocabMode("pronunciation"))
        assertThat(handler.resolve("luyện đặt câu")).isEqualTo(MediaIds.vocabMode("sentence"))
        assertThat(handler.resolve("practice making sentences")).isEqualTo(MediaIds.vocabMode("sentence"))
    }

    @Test
    fun `pronunciation practice, with or without a topic`() {
        assertThat(handler.resolve("pronunciation practice")).isEqualTo(MediaIds.PRONUNCIATION)
        assertThat(handler.resolve("luyện shadowing")).isEqualTo(MediaIds.PRONUNCIATION)
        assertThat(handler.resolve("chuyển sang shadowing")).isEqualTo(MediaIds.PRONUNCIATION)
        assertThat(handler.resolve("tập phát âm")).isEqualTo(MediaIds.PRONUNCIATION)
        assertThat(handler.resolve("repeat after me travel English")).isEqualTo(MediaIds.pronunciation("travel"))
        assertThat(handler.resolve("luyện phát âm phỏng vấn")).isEqualTo(MediaIds.pronunciation("interview"))
        assertThat(handler.resolve("luyện shadowing phỏng vấn")).isEqualTo(MediaIds.pronunciation("interview"))
    }

    @Test
    fun `roleplay picks a scenario of the topic`() {
        val target = MediaIds.parse(handler.resolve("restaurant role play"))
        assertThat(target).isInstanceOf(MediaTarget.Scenario::class.java)
        val scenarioId = (target as MediaTarget.Scenario).scenarioId
        assertThat(topics.getScenario(scenarioId)?.first?.id).isEqualTo("food")
    }

    @Test
    fun `dynamic roleplay query generates dynamic scenario`() {
        val target = MediaIds.parse(handler.resolve("roleplay ca phẫu thuật tim"))
        assertThat(target).isInstanceOf(MediaTarget.Scenario::class.java)
        val scenarioId = (target as MediaTarget.Scenario).scenarioId
        val (topic, scenario) = topics.getScenario(scenarioId)!!
        assertThat(topic.id).isEqualTo("medical_expert")
        assertThat(scenario.customContext).isNotNull()
    }

    @Test
    fun `story voice queries pick stories`() {
        assertThat(handler.resolve("tell me a story")).isEqualTo(MediaIds.STORY_RECOMMENDED)
        assertThat(handler.resolve("kể chuyện tiếng Anh")).isEqualTo(MediaIds.STORY_RECOMMENDED)
        assertThat(handler.resolve("nghe một câu chuyện ngẫu nhiên")).isEqualTo(MediaIds.STORY_RANDOM)
        assertThat(handler.resolve("random story")).isEqualTo(MediaIds.STORY_RANDOM)
        assertThat(handler.resolve("kể chuyện khoa học")).contains("story_science")
    }

    @Test
    fun `resume story voice queries resolve to STORY_RESUME`() {
        assertThat(handler.resolve("tiếp tục câu chuyện")).isEqualTo(MediaIds.STORY_RESUME)
        assertThat(handler.resolve("kể tiếp chuyện hôm trước")).isEqualTo(MediaIds.STORY_RESUME)
        assertThat(handler.resolve("bat tiep truyen dang nghe do")).isEqualTo(MediaIds.STORY_RESUME)
        assertThat(handler.resolve("resume story")).isEqualTo(MediaIds.STORY_RESUME)
        assertThat(handler.resolve("continue last story")).isEqualTo(MediaIds.STORY_RESUME)
    }

    @Test
    fun `web search story voice queries resolve dynamic scenario`() {
        val resolved = handler.resolve("kể chuyện về tàu Titanic")
        assertThat(resolved).contains("story_search_")
        val target = MediaIds.parse(resolved)
        assertThat(target).isInstanceOf(MediaTarget.Story::class.java)
    }

    @Test
    fun `mistake review queries open mistake review, not vocabulary`() {
        assertThat(handler.resolve("review my mistakes")).isEqualTo(MediaIds.MISTAKES)
        assertThat(handler.resolve("ôn lỗi sai")).isEqualTo(MediaIds.MISTAKES)
        assertThat(handler.resolve("on lai loi cu")).isEqualTo(MediaIds.MISTAKES)
        assertThat(handler.resolve("review vocabulary")).isEqualTo(MediaIds.REVIEW)
        assertThat(handler.resolve("ôn tập từ vựng")).isEqualTo(MediaIds.REVIEW)
    }
}
