package com.speakdrive.auto

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/**
 * Generates the Now Playing artwork for Android Auto. What it shows depends on the lesson:
 * - a sentence to repeat (shadowing): the whole sentence and its Vietnamese meaning, as large as fits;
 * - a story: a wordless illustration of the story, so there is nothing to read while driving;
 * - a conversation: the latest lines of the AI and the learner as chat bubbles, newest at the bottom.
 *
 * Android Auto does not always show this square as-is. In the split-screen media card it scales the
 * image to the card's height and centre-crops the sides, then lays its own app icon, title, subtitle
 * and controls over it. All text is therefore kept inside [SAFE_LEFT]..[SAFE_RIGHT] ×
 * [SAFE_TOP]..[SAFE_BOTTOM].
 */
@Singleton
class AutoCardArtworkGenerator @Inject constructor() {

    private var lastContent: CardContent? = null
    private var lastArtwork: ByteArray? = null

    /**
     * Creates a 600x600 PNG of the current lesson card. Returns the cached bytes when the card would
     * look the same, so Android Auto is not sent a new image for every transcript update.
     */
    @Synchronized
    fun generateCard(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        drillTarget: String?,
        drillTargetTranslation: String? = null
    ): ByteArray? {
        val content = cardContentFor(lesson, transcript, drillTarget, drillTargetTranslation)
        if (content == lastContent && lastArtwork != null) {
            return lastArtwork
        }

        return try {
            val bitmap = renderCard(content)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            val bytes = stream.toByteArray()
            lastContent = content
            lastArtwork = bytes
            bytes
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to render Android Auto card artwork: ${e.message}")
            null
        }
    }

    /** What the card shows; equal contents draw identical cards. */
    internal sealed interface CardContent {
        data class Repeat(val target: String, val translation: String?) : CardContent
        data class Story(val scene: StoryScene) : CardContent
        data class Chat(val lines: List<ChatLine>) : CardContent
    }

    internal data class ChatLine(val fromLearner: Boolean, val text: String)

    internal fun cardContentFor(
        lesson: ActiveLesson,
        transcript: List<TranscriptTurn>,
        drillTarget: String?,
        drillTargetTranslation: String?
    ): CardContent = when {
        // Stories never show text, even if a sentence to repeat turns up.
        lesson.mode == SessionMode.STORY_LISTENING -> CardContent.Story(StoryScenes.sceneFor(lesson))
        !drillTarget.isNullOrBlank() -> CardContent.Repeat(drillTarget.trim(), drillTargetTranslation)
        else -> CardContent.Chat(
            transcript
                .filter { it.text.isNotBlank() }
                .takeLast(MAX_CHAT_LINES)
                .map { ChatLine(fromLearner = it.speaker == Speaker.USER, text = it.text.trim()) }
        )
    }

    internal fun renderCard(content: CardContent): Bitmap {
        val bitmap = createBitmap(CARD_SIZE, CARD_SIZE)
        val canvas = Canvas(bitmap)
        when (content) {
            is CardContent.Repeat -> {
                val card = layoutRepeatCard(content.target, content.translation)
                drawBackground(canvas, COLOR_ACCENT, glowCenterY = blockTop(card) + card.height / 2f)
                drawBlocks(canvas, card)
            }
            is CardContent.Story -> drawStoryScene(canvas, content.scene)
            is CardContent.Chat -> {
                drawBackground(canvas, COLOR_ACCENT, glowCenterY = (SAFE_TOP + SAFE_BOTTOM) / 2f)
                drawChat(canvas, layoutChat(content.lines))
            }
        }
        return bitmap
    }

    // ---------------------------------------------------------------------------------------------
    // Repeat drill
    // ---------------------------------------------------------------------------------------------

    /**
     * Repeat drill: a small label, the English sentence in big white letters, a short divider and
     * the Vietnamese meaning in yellow. Picks the largest size at which the whole sentence and the
     * whole translation fit, so neither is cut off.
     */
    internal fun layoutRepeatCard(targetText: String, translationText: String?): CardLayout {
        val target = targetText.trim()
        val translation = translationText
            ?.trim()
            ?.removePrefix(VN_FLAG)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val translationColor = if (translation != null) COLOR_TRANSLATION else COLOR_TEXT_MUTED
        val secondLine = translation ?: NO_TRANSLATION_HINT
        val label = labelLayout(REPEAT_LABEL, COLOR_ACCENT)

        for (targetSize in TARGET_TEXT_SIZES) {
            val card = repeatCard(
                label,
                textLayout(target, targetPaint(targetSize)),
                textLayout(secondLine, secondaryPaint(translationSizeFor(targetSize), translationColor)),
                targetSize
            )
            if (card.height <= SAFE_HEIGHT) return card
        }

        // Only reachable for paragraph-long "sentences": clamp both to the space left.
        val targetSize = TARGET_TEXT_SIZES.last()
        val textBudget = SAFE_HEIGHT - label.height - LABEL_GAP - DIVIDER_GAP
        val translationLayout = textLayout(
            secondLine,
            secondaryPaint(translationSizeFor(targetSize), translationColor),
            maxHeight = (textBudget * 0.4f).toInt()
        )
        val targetLayout = textLayout(target, targetPaint(targetSize), maxHeight = textBudget - translationLayout.height)
        return repeatCard(label, targetLayout, translationLayout, targetSize)
    }

    private fun repeatCard(label: StaticLayout, target: StaticLayout, translation: StaticLayout, targetSize: Float) =
        CardLayout(
            blocks = listOf(
                CardBlock(label, gapBefore = 0),
                CardBlock(target, gapBefore = LABEL_GAP),
                CardBlock(translation, gapBefore = DIVIDER_GAP, dividerBefore = true)
            ),
            mainTextSize = targetSize
        )

    private fun drawBlocks(canvas: Canvas, card: CardLayout) {
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ACCENT }
        var y = blockTop(card).toFloat()
        for (block in card.blocks) {
            if (block.dividerBefore) {
                val centerX = CARD_SIZE / 2f
                val centerY = y + block.gapBefore / 2f
                canvas.drawRoundRect(
                    RectF(
                        centerX - DIVIDER_WIDTH / 2f, centerY - DIVIDER_THICKNESS / 2f,
                        centerX + DIVIDER_WIDTH / 2f, centerY + DIVIDER_THICKNESS / 2f
                    ),
                    DIVIDER_THICKNESS / 2f, DIVIDER_THICKNESS / 2f, dividerPaint
                )
            }
            y += block.gapBefore
            canvas.withTranslation(SAFE_LEFT.toFloat(), y) { block.layout.draw(this) }
            y += block.layout.height
        }
    }

    /** Top of the text stack, centred vertically inside the safe box. */
    private fun blockTop(card: CardLayout): Int = SAFE_TOP + max(0, (SAFE_HEIGHT - card.height) / 2)

    private fun labelLayout(text: String, color: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = LABEL_TEXT_SIZE
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        return textLayout(text, paint, maxHeight = (LABEL_TEXT_SIZE * 1.6f).toInt())
    }

    /** The English sentence: white and extra heavy, so it survives Android Auto's blur and scrim. */
    private fun targetPaint(size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 2f, COLOR_SHADOW)
    }

    /** Translation and chat text: plain bold, which keeps Vietnamese diacritics crisp. */
    private fun secondaryPaint(size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 2f, COLOR_SHADOW)
    }

    private fun translationSizeFor(targetSize: Float) = max(targetSize * TRANSLATION_SCALE, MIN_TRANSLATION_SIZE)

    // ---------------------------------------------------------------------------------------------
    // Conversation
    // ---------------------------------------------------------------------------------------------

    /**
     * Chat bubbles for the latest lines, oldest first. Only lines that can still show are laid out;
     * the oldest one may be taller than the space left and is cut off at the top when drawn.
     */
    internal fun layoutChat(lines: List<ChatLine>): List<ChatBubble> {
        val shown = lines.ifEmpty { listOf(ChatLine(fromLearner = false, text = CHAT_PLACEHOLDER)) }
        val bubbles = ArrayDeque<ChatBubble>()
        var used = 0
        for (line in shown.asReversed()) {
            if (used >= SAFE_HEIGHT) break
            val paint = secondaryPaint(CHAT_TEXT_SIZE, COLOR_TEXT)
            val bubble = ChatBubble(
                textLayout(line.text, paint, width = BUBBLE_TEXT_WIDTH, alignment = Layout.Alignment.ALIGN_NORMAL),
                line.fromLearner
            )
            bubbles.addFirst(bubble)
            used += bubble.height + BUBBLE_GAP
        }
        return bubbles.toList()
    }

    /**
     * Stacks the bubbles up from the bottom of the safe box like a chat that has scrolled to its
     * newest line. Whatever reaches past the top is clipped and faded out.
     */
    private fun drawChat(canvas: Canvas, bubbles: List<ChatBubble>) {
        val box = RectF(SAFE_LEFT.toFloat(), SAFE_TOP.toFloat(), SAFE_RIGHT.toFloat(), SAFE_BOTTOM.toFloat())
        val layer = canvas.saveLayer(box, null)
        canvas.clipRect(box)

        val aiFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_AI_BUBBLE }
        val learnerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_LEARNER_BUBBLE }
        var bottom = box.bottom
        for (bubble in bubbles.asReversed()) {
            val top = bottom - bubble.height
            val left = if (bubble.fromLearner) box.right - bubble.width else box.left
            canvas.drawRoundRect(
                RectF(left, top, left + bubble.width, bottom),
                BUBBLE_RADIUS, BUBBLE_RADIUS,
                if (bubble.fromLearner) learnerFill else aiFill
            )
            canvas.withTranslation(left + BUBBLE_PAD_X, top + BUBBLE_PAD_Y) { bubble.layout.draw(this) }
            bottom = top - BUBBLE_GAP
            if (bottom < box.top) break
        }

        if (bottom + BUBBLE_GAP < box.top) {
            val fade = Paint().apply {
                shader = LinearGradient(0f, box.top, 0f, box.top + FADE_HEIGHT, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            canvas.drawRect(box.left, box.top, box.right, box.top + FADE_HEIGHT, fade)
        }
        canvas.restoreToCount(layer)
    }

    internal class ChatBubble(val layout: StaticLayout, val fromLearner: Boolean) {
        val width: Int = ceil((0 until layout.lineCount).maxOf { layout.getLineWidth(it) }).toInt() + 2 * BUBBLE_PAD_X
        val height: Int = layout.height + 2 * BUBBLE_PAD_Y
        val text: String get() = layout.text.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // Story
    // ---------------------------------------------------------------------------------------------

    /** A wordless picture: painted backdrop, a glowing halo, the hero emoji and a few props around it. */
    private fun drawStoryScene(canvas: Canvas, scene: StoryScene) {
        val backdrop = scene.backdrop
        val size = CARD_SIZE.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, size, backdrop.top, backdrop.bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, paint)
        paint.shader = null

        // Seeded from the emoji (not the enum, whose hash changes per process) so a story keeps its sky.
        val random = Random(31L * scene.heroes.hashCode() + scene.props.hashCode())
        when (backdrop) {
            Backdrop.NIGHT -> {
                drawStars(canvas, random, count = 45, maxY = 330f)
                drawCrescentMoon(canvas)
            }
            Backdrop.SPACE -> {
                drawNebula(canvas, backdrop.glow)
                drawStars(canvas, random, count = 110, maxY = size)
            }
            Backdrop.OCEAN -> {
                drawStars(canvas, random, count = 25, maxY = 260f)
                drawWaves(canvas, backdrop.glow)
            }
            Backdrop.SNOW -> {
                drawMountains(canvas, far = 0xFF2C4A70.toInt(), near = 0xFF16273F.toInt(), snowCaps = true)
                drawStars(canvas, random, count = 60, maxY = size, maxRadius = 3f)
            }
            Backdrop.JUNGLE -> {
                drawMountains(canvas, far = 0xFF145040.toInt(), near = 0xFF0A2A20.toInt(), snowCaps = false)
                drawLeaves(canvas)
            }
            Backdrop.DESERT -> {
                drawSun(canvas)
                drawDunes(canvas)
            }
            Backdrop.CITY -> {
                drawStars(canvas, random, count = 20, maxY = 220f)
                drawSkyline(canvas, random)
            }
            Backdrop.LAB -> {
                drawDotGrid(canvas)
                drawOrbits(canvas, backdrop.glow)
            }
        }

        // Halo behind the hero, so the picture reads even after Android Auto dims and blurs it.
        paint.shader = RadialGradient(
            HERO_X, HERO_Y, HALO_RADIUS,
            (backdrop.glow and 0x00FFFFFF) or 0x66000000, backdrop.glow and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(HERO_X, HERO_Y, HALO_RADIUS, paint)

        val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        emojiPaint.textSize = HERO_SIZE
        val hero = scene.heroes.firstOrNull { emojiPaint.hasGlyph(it) } ?: FALLBACK_HERO
        drawEmoji(canvas, emojiPaint, hero, HERO_X, HERO_Y)

        emojiPaint.textSize = PROP_SIZE
        scene.props.filter { emojiPaint.hasGlyph(it) }.zip(PROP_POSITIONS).forEach { (prop, position) ->
            drawEmoji(canvas, emojiPaint, prop, position.first, position.second)
        }
    }

    private fun drawEmoji(canvas: Canvas, paint: TextPaint, emoji: String, centerX: Float, centerY: Float) {
        val metrics = paint.fontMetrics
        canvas.drawText(emoji, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, paint)
    }

    private fun drawStars(canvas: Canvas, random: Random, count: Int, maxY: Float, maxRadius: Float = 2.2f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(count) {
            paint.color = Color.argb(60 + random.nextInt(150), 255, 255, 255)
            canvas.drawCircle(random.nextFloat() * CARD_SIZE, random.nextFloat() * maxY, 0.8f + random.nextFloat() * maxRadius, paint)
        }
    }

    private fun drawCrescentMoon(canvas: Canvas) {
        val moon = Path().apply { addCircle(430f, 92f, 26f, Path.Direction.CW) }
        val bite = Path().apply { addCircle(442f, 84f, 23f, Path.Direction.CW) }
        moon.op(bite, Path.Op.DIFFERENCE)
        canvas.drawPath(moon, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDF3C4.toInt() })
    }

    private fun drawNebula(canvas: Canvas, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(190f, 170f, 260f, (color and 0x00FFFFFF) or 0x55000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(190f, 170f, 260f, paint)
        paint.shader = RadialGradient(440f, 420f, 220f, 0x44EC4899, 0x00EC4899, Shader.TileMode.CLAMP)
        canvas.drawCircle(440f, 420f, 220f, paint)
    }

    private fun drawWaves(canvas: Canvas, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        listOf(430f to 0x55, 485f to 0x40, 540f to 0x30).forEachIndexed { index, (baseY, alpha) ->
            paint.color = (color and 0x00FFFFFF) or (alpha shl 24)
            val path = Path()
            var x = 0f
            path.moveTo(x, baseY)
            while (x <= CARD_SIZE) {
                x += 6f
                path.lineTo(x, baseY + 12f * sin((x / 110f) + index * 1.7f))
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawMountains(canvas: Canvas, far: Int, near: Int, snowCaps: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val farPeaks = listOf(0f to 430f, 110f to 330f, 230f to 400f, 340f to 300f, 470f to 390f, 600f to 320f)
        val nearPeaks = listOf(0f to 470f, 150f to 390f, 290f to 470f, 420f to 380f, 600f to 460f)
        paint.color = far
        canvas.drawPath(ridge(farPeaks), paint)
        if (snowCaps) {
            paint.color = 0xCCE2ECF7.toInt()
            for ((x, y) in farPeaks.drop(1).dropLast(1)) {
                canvas.drawPath(Path().apply {
                    moveTo(x, y)
                    lineTo(x - 22f, y + 24f)
                    lineTo(x + 22f, y + 24f)
                    close()
                }, paint)
            }
        }
        paint.color = near
        canvas.drawPath(ridge(nearPeaks), paint)
    }

    private fun ridge(peaks: List<Pair<Float, Float>>) = Path().apply {
        moveTo(0f, CARD_SIZE.toFloat())
        peaks.forEach { (x, y) -> lineTo(x, y) }
        lineTo(CARD_SIZE.toFloat(), CARD_SIZE.toFloat())
        close()
    }

    private fun drawLeaves(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E4A36.toInt() }
        listOf(
            Triple(40f, 70f, 35f), Triple(80f, 160f, -20f), Triple(560f, 90f, -35f),
            Triple(520f, 190f, 20f), Triple(30f, 300f, 50f), Triple(575f, 320f, -50f)
        ).forEach { (x, y, angle) ->
            canvas.save()
            canvas.rotate(angle, x, y)
            canvas.drawOval(RectF(x - 70f, y - 24f, x + 70f, y + 24f), paint)
            canvas.restore()
        }
    }

    private fun drawSun(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(300f, 440f, 230f, 0x88F59E0B.toInt(), 0x00F59E0B, Shader.TileMode.CLAMP)
        canvas.drawCircle(300f, 440f, 230f, paint)
    }

    private fun drawDunes(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF6B3A16.toInt()
        canvas.drawPath(Path().apply {
            moveTo(0f, 470f)
            quadTo(180f, 380f, 380f, 450f)
            quadTo(500f, 490f, 600f, 420f)
            lineTo(600f, 600f)
            lineTo(0f, 600f)
            close()
        }, paint)
        paint.color = 0xFF3B1F0B.toInt()
        canvas.drawPath(Path().apply {
            moveTo(0f, 540f)
            quadTo(260f, 450f, 600f, 530f)
            lineTo(600f, 600f)
            lineTo(0f, 600f)
            close()
        }, paint)
    }

    private fun drawSkyline(canvas: Canvas, random: Random) {
        val building = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1230.toInt() }
        val window = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = 0f
        while (x < CARD_SIZE) {
            val width = 50f + random.nextInt(40)
            val top = 420f + random.nextInt(110)
            canvas.drawRect(x, top, x + width - 6f, CARD_SIZE.toFloat(), building)
            var wy = top + 14f
            while (wy < CARD_SIZE - 10f) {
                var wx = x + 10f
                while (wx < x + width - 18f) {
                    if (random.nextInt(3) == 0) {
                        window.color = Color.argb(70 + random.nextInt(80), 253, 224, 71)
                        canvas.drawRect(wx, wy, wx + 7f, wy + 9f, window)
                    }
                    wx += 14f
                }
                wy += 18f
            }
            x += width
        }
    }

    private fun drawDotGrid(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E5EEAD4 }
        var y = 15f
        while (y < CARD_SIZE) {
            var x = 15f
            while (x < CARD_SIZE) {
                canvas.drawCircle(x, y, 2f, paint)
                x += 30f
            }
            y += 30f
        }
    }

    private fun drawOrbits(canvas: Canvas, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            this.color = (color and 0x00FFFFFF) or 0x55000000
        }
        for (angle in listOf(-30f, 30f, 90f)) {
            canvas.save()
            canvas.rotate(angle, HERO_X, HERO_Y)
            canvas.drawOval(RectF(HERO_X - 185f, HERO_Y - 62f, HERO_X + 185f, HERO_Y + 62f), paint)
            canvas.restore()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared
    // ---------------------------------------------------------------------------------------------

    private fun drawBackground(canvas: Canvas, accent: Int, glowCenterY: Float) {
        val size = CARD_SIZE.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, size, COLOR_BG_TOP, COLOR_BG_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, paint)

        // A soft glow behind the text lifts it off the background once Android Auto dims the art.
        paint.shader = RadialGradient(
            size / 2f, glowCenterY, GLOW_RADIUS,
            (accent and 0x00FFFFFF) or GLOW_ALPHA, 0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size, size, paint)
    }

    /**
     * Wraps [text] to [width]. With [maxHeight] the text is cut to the lines that fit, ending in an
     * ellipsis.
     */
    // Layout.BREAK_STRATEGY_BALANCED equals LineBreaker's constant, which needs API 29.
    @SuppressLint("WrongConstant")
    private fun textLayout(
        text: String,
        paint: TextPaint,
        width: Int = SAFE_WIDTH,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        maxHeight: Int? = null
    ): StaticLayout {
        fun build(maxLines: Int?) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .apply {
                if (maxLines != null) {
                    setMaxLines(maxLines)
                    setEllipsize(TextUtils.TruncateAt.END)
                }
            }
            .build()

        val full = build(null)
        if (maxHeight == null || full.height <= maxHeight || full.lineCount <= 1) return full
        val lineHeight = full.height / full.lineCount
        return build(max(1, maxHeight / lineHeight))
    }

    internal class CardBlock(val layout: StaticLayout, val gapBefore: Int, val dividerBefore: Boolean = false)

    internal class CardLayout(val blocks: List<CardBlock>, val mainTextSize: Float) {
        val height: Int = blocks.sumOf { it.gapBefore + it.layout.height }
    }

    internal companion object {
        private const val TAG = "AutoCardArtwork"
        const val CARD_SIZE = 600

        // Safe box, measured on the Desktop Head Unit (split-screen media card, about 0.58:1).
        // The card shows only x ≈ 125..475 of the 600 px square; x 150..450 still survives a card as
        // narrow as 1:2. The app icon and page dots cover y < 40, and the native title, subtitle and
        // controls start around y ≈ 415. The full Now Playing screen shows the whole square, and
        // keeps x < 120 and x > 480 free for drill status.
        const val SAFE_LEFT = 150
        const val SAFE_RIGHT = 450
        const val SAFE_TOP = 48
        const val SAFE_BOTTOM = 400
        const val SAFE_WIDTH = SAFE_RIGHT - SAFE_LEFT
        const val SAFE_HEIGHT = SAFE_BOTTOM - SAFE_TOP

        /** Largest first: the first size at which everything fits wins. */
        val TARGET_TEXT_SIZES = floatArrayOf(44f, 41f, 38f, 35f, 32f, 30f, 28f, 26f, 24f, 22f, 20f, 18f)
        private const val TRANSLATION_SCALE = 0.8f
        private const val MIN_TRANSLATION_SIZE = 16f
        private const val LABEL_TEXT_SIZE = 17f
        private const val LINE_SPACING = 1.12f

        private const val LABEL_GAP = 16
        private const val DIVIDER_GAP = 26
        private const val DIVIDER_WIDTH = 56f
        private const val DIVIDER_THICKNESS = 4f
        private const val GLOW_RADIUS = 300f
        private const val GLOW_ALPHA = 0x2E000000

        // Chat: bubbles nearly as wide as the safe box, text big enough to catch at a glance.
        const val MAX_CHAT_LINES = 6
        const val CHAT_TEXT_SIZE = 26f
        private const val BUBBLE_PAD_X = 14
        private const val BUBBLE_PAD_Y = 10
        private const val BUBBLE_TEXT_WIDTH = 270 - 2 * BUBBLE_PAD_X
        private const val BUBBLE_GAP = 12
        private const val BUBBLE_RADIUS = 18f
        private const val FADE_HEIGHT = 56f

        // Story: hero in the middle of the safe box, props tucked into its corners.
        private const val HERO_X = 300f
        private const val HERO_Y = 222f
        private const val HERO_SIZE = 150f
        private const val HALO_RADIUS = 150f
        private const val PROP_SIZE = 58f
        private val PROP_POSITIONS = listOf(192f to 100f, 190f to 345f, 410f to 345f)
        private const val FALLBACK_HERO = "📖"

        private const val REPEAT_LABEL = "🎯 NHẮC LẠI THEO AI"
        private const val NO_TRANSLATION_HINT = "Nghe và nhắc lại"
        private const val CHAT_PLACEHOLDER = "• • •"
        private const val VN_FLAG = "🇻🇳"

        // Bright text on a near-black navy that stays dark under Android Auto's scrim.
        private const val COLOR_BG_TOP = 0xFF132242.toInt()
        private const val COLOR_BG_BOTTOM = 0xFF0A0E18.toInt()
        private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private const val COLOR_TRANSLATION = 0xFFFDE047.toInt()    // Yellow 300
        private const val COLOR_TEXT_MUTED = 0xFFCBD5E1.toInt()     // Slate 300
        private const val COLOR_ACCENT = 0xFF38BDF8.toInt()         // Sky 400
        // Both bubbles carry white text: dark text on a light bubble loses contrast under Android Auto's scrim.
        private const val COLOR_AI_BUBBLE = 0xFF2A3754.toInt()      // Slate navy, lighter than the background
        private const val COLOR_LEARNER_BUBBLE = 0xFF0369A1.toInt() // Sky 700
        private const val COLOR_SHADOW = 0xB3000000.toInt()
    }
}
