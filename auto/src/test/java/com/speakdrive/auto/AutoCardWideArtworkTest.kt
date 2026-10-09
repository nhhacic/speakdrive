package com.speakdrive.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.auto.AutoCardArtworkGenerator.CardContent
import com.speakdrive.auto.AutoCardArtworkGenerator.ChatLine
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoCardWideArtworkTest {

    private val generator = AutoCardArtworkGenerator()
    private val wide = AutoCardArtworkGenerator.WIDE_WIDTH
    private val size = AutoCardArtworkGenerator.CARD_SIZE
    private val side = (wide - size) / 2

    private val cards = listOf(
        "repeat" to CardContent.Repeat(
            "We could postpone system maintenance until next week if you prefer.",
            "Chúng ta có thể hoãn việc bảo trì hệ thống sang tuần sau nếu bạn thích.",
            DrillStatus(DrillTurn.YOUR_TURN, accuracyPercent = 57, attemptNumber = 1, passed = false, passedSentences = 2, sentences = 3)
        ),
        "chat" to CardContent.Chat(listOf(ChatLine(false, "How was your day?"), ChatLine(true, "Great, thanks!"))),
        "story" to CardContent.Story(StoryScenes.BY_SCENARIO.getValue("story_pyramids"))
    )

    @Test
    fun `android auto gets a wide image`() {
        val topic = TopicManager().getAllTopics().first()
        val lesson = ActiveLesson("wide", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())

        val bytes = generator.generateCard(lesson, emptyList(), "Where is the gate?", "Cổng ở đâu?")!!
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertThat(image.width).isEqualTo(wide)
        assertThat(image.height).isEqualTo(size)
    }

    @Test
    fun `the middle of the wide image is the square card`() {
        for ((name, content) in cards) {
            val square = generator.renderCard(content)
            val image = generator.widen(square)
            val expected = IntArray(size * size)
            val actual = IntArray(size * size)
            square.getPixels(expected, 0, size, 0, 0, size, size)
            image.getPixels(actual, 0, size, side, 0, size, size)
            assertWithMessage(name).that(actual.contentEquals(expected)).isTrue()
        }
    }

    @Test
    fun `wings keep the panel rule across the whole width`() {
        for ((name, content) in cards) {
            val image = generator.widen(generator.renderCard(content))
            val top = AutoCardArtworkGenerator.PANEL_BOTTOM
            val below = IntArray(wide * (size - top))
            image.getPixels(below, 0, wide, 0, top, wide, size - top)
            assertWithMessage(name).that(below.distinct()).containsExactly(AutoCardArtworkGenerator.COLOR_OFF_PANEL)
            // The panel edge runs the full width, wings included.
            assertWithMessage("$name edge").that(image.getPixel(10, top - 1)).isEqualTo(image.getPixel(side + 1, top - 1))
            assertWithMessage("$name edge").that(image.getPixel(wide - 10, top - 1)).isEqualTo(image.getPixel(side + size - 2, top - 1))
        }
    }

    @Test
    fun `saves previews of the wide artwork`() {
        val dir = File("build/wide-preview").apply { mkdirs() }
        for ((name, content) in cards) {
            val image = generator.widen(generator.renderCard(content))
            save(image, File(dir, "$name.png"))
            // Full Now Playing screen, if Android Auto keeps the ratio: about 860x430 on the DHU.
            save(Bitmap.createScaledBitmap(image, 860, 430, true), File(dir, "${name}_fullscreen.png"))
            // Media card beside the map: scaled to the card height (870 px), centre-cropped to 512 px.
            val card = Bitmap.createBitmap(512, 870, Bitmap.Config.ARGB_8888)
            val scaledWidth = wide * 870 / size
            Canvas(card).drawBitmap(image, Rect(0, 0, wide, size), Rect((512 - scaledWidth) / 2, 0, (512 + scaledWidth) / 2, 870), null)
            save(card, File(dir, "${name}_card.png"))
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
