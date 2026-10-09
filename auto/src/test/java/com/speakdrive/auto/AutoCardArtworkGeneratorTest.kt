package com.speakdrive.auto

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Scenario
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.auto.AutoCardArtworkGenerator.CardContent
import com.speakdrive.auto.AutoCardArtworkGenerator.ChatLine
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Native graphics: real text measuring and drawing, so the layout and pixel checks mean something.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoCardArtworkGeneratorTest {

    private val topics = TopicManager()
    private val generator = AutoCardArtworkGenerator()

    private fun lesson(topicId: String, mode: SessionMode, scenario: Scenario? = null) =
        ActiveLesson("session_$topicId", topics.getTopicById(topicId)!!, scenario, DifficultyLevel.INTERMEDIATE, mode, 0L, emptyList())

    private fun turns(vararg lines: Pair<Speaker, String>) =
        lines.mapIndexed { index, (speaker, text) -> TranscriptTurn(index.toLong(), speaker, text, index.toLong()) }

    @Test
    fun `generates png for a repeat drill`() {
        val bytes = generator.generateCard(
            lesson("travel", SessionMode.REPEAT_AFTER_ME),
            turns(Speaker.AI to "Repeat after me: I would like a window seat, please."),
            drillTarget = "I would like a window seat, please.",
            drillTargetTranslation = "Tôi muốn một ghế cạnh cửa sổ."
        )

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isGreaterThan(100)
    }

    @Test
    fun `reuses the image when the card would look the same`() {
        val lesson = lesson("work", SessionMode.REPEAT_AFTER_ME)

        val first = generator.generateCard(lesson, turns(Speaker.AI to "Repeat after me: Let's meet."), "Let's meet.")
        // A new transcript line does not change a repeat card.
        val second = generator.generateCard(lesson, turns(Speaker.AI to "Repeat after me: Let's meet.", Speaker.USER to "Let's meet."), "Let's meet.")

        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `story card is a picture that ignores the transcript`() {
        val story = lesson("story_science", SessionMode.STORY_LISTENING)

        val first = generator.generateCard(story, turns(Speaker.AI to "Once upon a time in a small laboratory..."), null)
        val second = generator.generateCard(story, turns(Speaker.AI to "Alexander Fleming came back from holiday."), null)

        assertThat(first).isNotNull()
        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `story card never shows text, even with a sentence to repeat`() {
        val content = generator.cardContentFor(lesson("story_history", SessionMode.STORY_LISTENING), emptyList(), "Repeat this.", "Lặp lại câu này.")

        assertThat(content).isInstanceOf(CardContent.Story::class.java)
    }

    @Test
    fun `conversation card keeps the latest lines of both speakers`() {
        val transcript = (1..10).map { TranscriptTurn(it.toLong(), if (it % 2 == 0) Speaker.USER else Speaker.AI, "Line $it", it.toLong()) } +
            TranscriptTurn(11, Speaker.USER, "   ", 11)

        val content = generator.cardContentFor(lesson("food", SessionMode.FREE_TALK), transcript, null, null)

        assertThat(content).isEqualTo(
            CardContent.Chat(
                listOf(
                    ChatLine(fromLearner = false, text = "Line 5"),
                    ChatLine(fromLearner = true, text = "Line 6"),
                    ChatLine(fromLearner = false, text = "Line 7"),
                    ChatLine(fromLearner = true, text = "Line 8"),
                    ChatLine(fromLearner = false, text = "Line 9"),
                    ChatLine(fromLearner = true, text = "Line 10")
                )
            )
        )
    }

    @Test
    fun `chat keeps the newest line at the bottom and drops lines that scrolled away`() {
        val lines = (1..6).map {
            ChatLine(fromLearner = it % 2 == 0, text = "Message $it is a fairly long sentence that needs a few lines in the bubble.")
        }

        val bubbles = generator.layoutChat(lines)

        assertThat(bubbles.last().text).isEqualTo(lines.last().text)
        assertThat(bubbles.last().fromLearner).isTrue()
        assertThat(bubbles.size).isLessThan(lines.size)
        // Everything below the oldest, partly hidden bubble fits whole.
        val belowOldest = bubbles.drop(1).sumOf { it.height + 12 }
        assertThat(belowOldest).isAtMost(AutoCardArtworkGenerator.SAFE_HEIGHT)
        for (bubble in bubbles) {
            assertThat(bubble.width).isAtMost(AutoCardArtworkGenerator.SAFE_WIDTH)
        }
    }

    @Test
    fun `repeat card keeps the whole sentence and the whole translation`() {
        for ((sentence, translation) in REPEAT_SAMPLES) {
            val card = generator.layoutRepeatCard(sentence, translation)

            assertThat(card.height).isAtMost(AutoCardArtworkGenerator.SAFE_HEIGHT)
            for (block in card.blocks) {
                val layout = block.layout
                for (line in 0 until layout.lineCount) {
                    assertWithMessage("ellipsis in \"${layout.text}\"").that(layout.getEllipsisCount(line)).isEqualTo(0)
                    assertThat(layout.getLineWidth(line)).isAtMost(AutoCardArtworkGenerator.SAFE_WIDTH.toFloat())
                }
            }
            val drawnText = card.blocks.joinToString("\n") { it.layout.text.toString() }
            assertThat(drawnText).contains(sentence)
            assertThat(drawnText).contains(translation)
        }
    }

    @Test
    fun `typical repeat sentence is drawn large`() {
        val card = generator.layoutRepeatCard(
            "Fair enough, let's move forward together.",
            "🇻🇳 Hợp lý đấy, chúng ta cùng tiến hành thôi."
        )

        assertThat(card.mainTextSize).isAtLeast(32f)
        // The flag is already in Android Auto's own subtitle; on the artwork it only costs width.
        assertThat(card.blocks.last().layout.text.toString()).isEqualTo("Hợp lý đấy, chúng ta cùng tiến hành thôi.")
    }

    @Test
    fun `every curated story has its own picture`() {
        val storyScenarios = topics.getAllTopics().filter { it.id.startsWith("story_") }.flatMap { it.scenarios }

        assertThat(storyScenarios).isNotEmpty()
        for (scenario in storyScenarios) {
            assertWithMessage(scenario.id).that(StoryScenes.BY_SCENARIO).containsKey(scenario.id)
        }
    }

    @Test
    fun `made-up stories get a picture from their title`() {
        fun sceneFor(titleEn: String, titleVi: String) = StoryScenes.sceneFor(
            lesson("story_famous", SessionMode.STORY_LISTENING, Scenario("dynamic_story_1", titleVi, titleEn, "storyteller", "listener"))
        )

        assertThat(sceneFor("Story: A journey to Mars", "Chuyến đi tới sao Hỏa").backdrop).isEqualTo(Backdrop.SPACE)
        assertThat(sceneFor("AI Personalized Story", "Truyện cổ tích về con rồng").heroes).contains("🐉")
        // Nothing to go on: the topic's own picture.
        assertThat(sceneFor("Surprise Story", "Truyện bất ngờ").heroes).containsExactly("🌟")
    }

    @Test
    fun `safe box and panel fit every measured media card`() {
        // Desktop Head Unit: the card shows x 124..476, Android Auto's title starts at y 419.
        // The learner's car: the card shows x 30..570, the title starts at y 350.
        assertThat(AutoCardArtworkGenerator.SAFE_LEFT).isAtLeast(124 + 8)
        assertThat(AutoCardArtworkGenerator.SAFE_RIGHT).isAtMost(476 - 8)
        assertThat(AutoCardArtworkGenerator.PANEL_BOTTOM).isAtMost(350 - 8)
        assertThat(AutoCardArtworkGenerator.SAFE_BOTTOM).isLessThan(AutoCardArtworkGenerator.PANEL_BOTTOM)
        // The sentence column stays clear of the drill status rails.
        assertThat(AutoCardArtworkGenerator.SAFE_LEFT).isGreaterThan(AutoCardSideRails.LEFT_RAIL_END)
        assertThat(AutoCardArtworkGenerator.SAFE_RIGHT).isLessThan(AutoCardSideRails.RIGHT_RAIL_START)
    }

    @Test
    fun `text stays inside the safe box`() {
        val previewDir = File("build/artwork-preview").apply { mkdirs() }
        val longTurn = "Albert Einstein was working in the Swiss patent office, and every evening he wrote about light, " +
            "time and space until the small kitchen table was covered with notes."

        val cards = REPEAT_SAMPLES.mapIndexed { index, (sentence, translation) ->
            "repeat_$index" to CardContent.Repeat(sentence, translation)
        } + listOf(
            "repeat_no_translation" to CardContent.Repeat("Where is the gate?", null),
            "chat_short" to CardContent.Chat(listOf(ChatLine(false, "What kind of food do you like?"), ChatLine(true, "I like pho."))),
            "chat_long" to CardContent.Chat(
                listOf(
                    ChatLine(false, "Tell me about your weekend."),
                    ChatLine(true, "I went to the beach with my family and we ate a lot of seafood."),
                    ChatLine(false, "That sounds lovely! What was your favourite dish, and would you go back again?"),
                    ChatLine(true, "Grilled squid, and yes, definitely next summer.")
                )
            ),
            "chat_one_long_turn" to CardContent.Chat(listOf(ChatLine(false, "$longTurn $longTurn"))),
            "chat_empty" to CardContent.Chat(emptyList())
        )

        for ((name, content) in cards) {
            val bitmap = generator.renderCard(content)
            // Kept under build/ so the cards can be eyeballed after a test run.
            FileOutputStream(File(previewDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertWithMessage(name).that(inkOutsideSafeBox(bitmap)).isEmpty()
            assertWithMessage("$name has no text at all").that(inkPixelCount(bitmap)).isGreaterThan(40)
        }
    }

    @Test
    fun `renders a picture for every story`() {
        val previewDir = File("build/artwork-preview").apply { mkdirs() }
        val scenes = StoryScenes.BY_SCENARIO.entries.filterIndexed { index, _ -> index % 4 == 0 }

        for ((id, scene) in scenes) {
            val bitmap = generator.renderCard(CardContent.Story(scene))
            FileOutputStream(File(previewDir, "$id.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertThat(bitmap.width).isEqualTo(AutoCardArtworkGenerator.CARD_SIZE)
        }
    }

    @Test
    fun `nothing is painted under Android Auto's title and controls`() {
        val cards = listOf(
            CardContent.Repeat(REPEAT_SAMPLES.last().first, REPEAT_SAMPLES.last().second),
            CardContent.Chat(listOf(ChatLine(false, "How was your day?"), ChatLine(true, "Great, thanks!"))),
            CardContent.Story(StoryScenes.BY_SCENARIO.getValue("story_jan_baalsrud")),
            CardContent.Story(StoryScenes.BY_SCENARIO.getValue("story_pyramids"))
        )

        for (content in cards) {
            val bitmap = generator.renderCard(content)
            val top = AutoCardArtworkGenerator.PANEL_BOTTOM
            val pixels = IntArray(bitmap.width * (bitmap.height - top))
            bitmap.getPixels(pixels, 0, bitmap.width, 0, top, bitmap.width, bitmap.height - top)
            assertWithMessage(content.toString()).that(pixels.distinct()).containsExactly(AutoCardArtworkGenerator.COLOR_OFF_PANEL)
        }
    }

    /** Bright pixels (text, divider, bubbles) outside the safe box, with a few pixels of slack for anti-aliasing. */
    private fun inkOutsideSafeBox(bitmap: Bitmap): List<String> {
        val slack = 4
        val outside = mutableListOf<String>()
        forEachInkPixel(bitmap) { x, y ->
            val inside = x >= AutoCardArtworkGenerator.SAFE_LEFT - slack && x < AutoCardArtworkGenerator.SAFE_RIGHT + slack &&
                y >= AutoCardArtworkGenerator.SAFE_TOP - slack && y < AutoCardArtworkGenerator.SAFE_BOTTOM + slack
            if (!inside && outside.size < 10) outside += "($x, $y)"
        }
        return outside
    }

    private fun inkPixelCount(bitmap: Bitmap): Int {
        var count = 0
        forEachInkPixel(bitmap) { _, _ -> count++ }
        return count
    }

    private inline fun forEachInkPixel(bitmap: Bitmap, action: (Int, Int) -> Unit) {
        val width = bitmap.width
        val pixels = IntArray(width * bitmap.height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val luma = 0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
            if (luma > 110) action(i % width, i / width)
        }
    }

    private companion object {
        val REPEAT_SAMPLES = listOf(
            "Hello, how are you?" to "Xin chào, bạn khỏe không?",
            "Fair enough, let's move forward together." to "Hợp lý đấy, chúng ta cùng tiến hành thôi.",
            "I would like a window seat, please." to "Tôi muốn một ghế cạnh cửa sổ, làm ơn.",
            "Could you please tell me how much it costs to take a taxi from the international airport to the central railway station during rush hour?" to
                "Bạn có thể vui lòng cho tôi biết đi taxi từ sân bay quốc tế đến ga tàu trung tâm vào giờ cao điểm thì tốn bao nhiêu tiền không?"
        )
    }
}
