package com.speakdrive.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.SessionMode
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Native graphics: real text measuring and drawing, so the pixel checks mean something.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoCardSideRailsTest {

    private val samples = listOf(
        "ai_speaking" to DrillStatus(DrillTurn.AI_SPEAKING),
        "your_turn_after_fail" to DrillStatus(DrillTurn.YOUR_TURN, accuracyPercent = 57, attemptNumber = 1, passed = false, passedSentences = 2, sentences = 3),
        "you_speaking" to DrillStatus(DrillTurn.YOU_SPEAKING, accuracyPercent = 80, attemptNumber = 2, passed = false, passedSentences = 2, sentences = 3),
        "grading" to DrillStatus(DrillTurn.GRADING, accuracyPercent = 80, attemptNumber = 2, passed = false, passedSentences = 2, sentences = 3),
        "passed" to DrillStatus(DrillTurn.AI_SPEAKING, accuracyPercent = 100, attemptNumber = 3, passed = true, passedSentences = 3, sentences = 3),
        "paused" to DrillStatus(DrillTurn.PAUSED, passedSentences = 12, sentences = 15),
        "connecting" to DrillStatus(DrillTurn.CONNECTING)
    )

    @Test
    fun `rails never paint the sentence column`() {
        for ((name, status) in samples) {
            val bitmap = render(status)
            val inColumn = inkInside(bitmap, Rect(AutoCardSideRails.LEFT_RAIL_END + 1, 0, AutoCardSideRails.RIGHT_RAIL_START - 1, 600))
            assertWithMessage(name).that(inColumn).isEqualTo(0)
            assertWithMessage("$name left rail").that(inkInside(bitmap, Rect(0, 0, AutoCardSideRails.LEFT_RAIL_END, 600))).isGreaterThan(2_000)
            assertWithMessage("$name right rail").that(inkInside(bitmap, Rect(AutoCardSideRails.RIGHT_RAIL_START, 0, 600, 600))).isGreaterThan(2_000)
        }
    }

    @Test
    fun `rails are wholly hidden or wholly shown on every measured media card`() {
        // DHU card shows about x 124..476: both rails stay out of it.
        assertThat(AutoCardSideRails.LEFT_RAIL_END).isAtMost(124)
        assertThat(AutoCardSideRails.RIGHT_RAIL_START).isAtLeast(476)
        // The learner's car shows about x 30..570, title from y 350: both rails fit inside it.
        assertThat(AutoCardSideRails.RAIL_OUTER).isAtLeast(30 + 4)
        assertThat(600 - AutoCardSideRails.RAIL_OUTER).isAtMost(570 - 4)
        assertThat(AutoCardSideRails.RAIL_BOTTOM).isLessThan(350f)
        // Below the app icon and page dots.
        assertThat(AutoCardSideRails.RAIL_TOP).isAtLeast(56f)
    }

    @Test
    fun `rails draw nothing outside their boxes`() {
        for ((name, status) in samples) {
            val bitmap = render(status)
            val outerRight = 600 - AutoCardSideRails.RAIL_OUTER
            assertWithMessage("$name left of the left rail").that(inkInside(bitmap, Rect(0, 0, AutoCardSideRails.RAIL_OUTER - 2, 600))).isEqualTo(0)
            assertWithMessage("$name right of the right rail").that(inkInside(bitmap, Rect(outerRight + 2, 0, 600, 600))).isEqualTo(0)
            assertWithMessage("$name above the rails").that(inkInside(bitmap, Rect(0, 0, 600, AutoCardSideRails.RAIL_TOP.toInt() - 2))).isEqualTo(0)
            assertWithMessage("$name below the rails").that(inkInside(bitmap, Rect(0, AutoCardSideRails.RAIL_BOTTOM.toInt() + 2, 600, 600))).isEqualTo(0)
        }
    }

    @Test
    fun `labels fit every turn`() {
        for (turn in DrillTurn.entries) {
            val (first, second) = AutoCardSideRails.turnLabel(turn)
            assertThat(first).isNotEmpty()
            assertThat(second).isNotEmpty()
        }
        assertThat(AutoCardSideRails.turnColor(DrillTurn.YOUR_TURN)).isNotEqualTo(AutoCardSideRails.turnColor(DrillTurn.AI_SPEAKING))
    }

    @Test
    fun `verdict colour separates passed close and failed tries`() {
        val passed = AutoCardSideRails.verdictColor(DrillStatus(DrillTurn.YOUR_TURN, 100, 1, true))
        val close = AutoCardSideRails.verdictColor(DrillStatus(DrillTurn.YOUR_TURN, 80, 1, false))
        val failed = AutoCardSideRails.verdictColor(DrillStatus(DrillTurn.YOUR_TURN, 30, 1, false))
        val none = AutoCardSideRails.verdictColor(DrillStatus(DrillTurn.YOUR_TURN))

        assertThat(setOf(passed, close, failed, none)).hasSize(4)
    }

    @Test
    fun `saves previews for both Android Auto layouts`() {
        val dir = File("build/rails-preview").apply { mkdirs() }
        for ((name, status) in samples) {
            val bitmap = render(status)
            save(bitmap, File(dir, "$name.png"))
            // Full Now Playing screen: the whole square, about 388 px wide on the DHU.
            save(Bitmap.createScaledBitmap(bitmap, 388, 388, true), File(dir, "${name}_fullscreen.png"))
            // Media card beside the map: scaled to the card height, centre-cropped (DHU 512x870, car 740x822).
            save(mediaCard(bitmap, 512, 870), File(dir, "${name}_card.png"))
            save(mediaCard(bitmap, 740, 822), File(dir, "${name}_car.png"))
        }
    }

    @Test
    fun `repeat card keeps its sentence column untouched and adds the rails`() {
        val generator = AutoCardArtworkGenerator()
        val sentence = "She described the project's broad scope."
        val translation = "Cô ấy đã mô tả phạm vi rộng lớn của dự án."
        val status = DrillStatus(DrillTurn.YOUR_TURN, accuracyPercent = 57, attemptNumber = 1, passed = false, passedSentences = 2, sentences = 3)

        val plain = generator.renderCard(AutoCardArtworkGenerator.CardContent.Repeat(sentence, translation))
        val withRails = generator.renderCard(AutoCardArtworkGenerator.CardContent.Repeat(sentence, translation, status))

        var changedInColumn = 0
        for (y in 0 until 600) {
            for (x in AutoCardSideRails.LEFT_RAIL_END + 1 until AutoCardSideRails.RIGHT_RAIL_START - 1) {
                if (plain.getPixel(x, y) != withRails.getPixel(x, y)) changedInColumn++
            }
        }
        assertThat(changedInColumn).isEqualTo(0)
        // Like the rest of the artwork, nothing under Android Auto's title and controls.
        val top = AutoCardArtworkGenerator.PANEL_BOTTOM
        val below = IntArray(600 * (600 - top))
        withRails.getPixels(below, 0, 600, 0, top, 600, 600 - top)
        assertThat(below.distinct()).containsExactly(AutoCardArtworkGenerator.COLOR_OFF_PANEL)
        assertThat(inkInside(withRails, Rect(0, 0, AutoCardSideRails.LEFT_RAIL_END, 600)))
            .isGreaterThan(inkInside(plain, Rect(0, 0, AutoCardSideRails.LEFT_RAIL_END, 600)))

        val dir = File("build/rails-preview").apply { mkdirs() }
        save(withRails, File(dir, "full_card.png"))
        save(Bitmap.createScaledBitmap(withRails, 388, 388, true), File(dir, "full_card_fullscreen.png"))
        save(mediaCard(withRails, 512, 870), File(dir, "full_card_card.png"))
        save(mediaCard(withRails, 740, 822), File(dir, "full_card_car.png"))
    }

    private fun mediaCard(artwork: Bitmap, width: Int, height: Int): Bitmap {
        val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(card).drawBitmap(artwork, Rect(0, 0, 600, 600), Rect((width - height) / 2, 0, (width + height) / 2, height), null)
        return card
    }

    @Test
    fun `a new turn changes the artwork but the same turn is cached`() {
        val generator = AutoCardArtworkGenerator()
        val topic = TopicManager().getAllTopics().first()
        val lesson = ActiveLesson("rails", topic, null, DifficultyLevel.INTERMEDIATE, SessionMode.REPEAT_AFTER_ME, 0L, emptyList())
        fun card(turn: DrillTurn) = generator.generateCard(lesson, emptyList(), "Where is the gate?", "Cổng ở đâu?", DrillStatus(turn))

        val aiSpeaking = card(DrillTurn.AI_SPEAKING)
        assertThat(card(DrillTurn.AI_SPEAKING)).isSameInstanceAs(aiSpeaking)
        assertThat(card(DrillTurn.YOUR_TURN)).isNotEqualTo(aiSpeaking)
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(status: DrillStatus): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        AutoCardSideRails.draw(canvas, status)
        return bitmap
    }

    private fun inkInside(bitmap: Bitmap, area: Rect): Int {
        var count = 0
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 60) count++
            }
        }
        return count
    }
}
