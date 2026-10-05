package com.speakdrive.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.SessionMode
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates dynamic high-contrast card artwork for the Android Auto Now Playing screen.
 * Displays the repeat target sentence with maximum glanceability and adaptive font sizing for safe driving.
 */
@Singleton
class AutoCardArtworkGenerator @Inject constructor() {

    private var lastKey: String? = null
    private var lastArtwork: ByteArray? = null

    /**
     * Creates a 600x600 PNG byte array rendering the current speaking lesson card.
     * Returns cached bytes if inputs are identical to save CPU.
     */
    @Synchronized
    fun generateCard(
        lesson: ActiveLesson,
        state: ConversationState,
        lastAiText: String?,
        drillTarget: String?
    ): ByteArray? {
        val cacheKey = "${lesson.sessionId}:$state:${lastAiText.orEmpty()}:${drillTarget.orEmpty()}"
        if (cacheKey == lastKey && lastArtwork != null) {
            return lastArtwork
        }

        return try {
            val bytes = renderBitmapBytes(lesson, state, lastAiText, drillTarget)
            lastKey = cacheKey
            lastArtwork = bytes
            bytes
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to render Android Auto card artwork: ${e.message}")
            null
        }
    }

    private fun renderBitmapBytes(
        lesson: ActiveLesson,
        state: ConversationState,
        lastAiText: String?,
        drillTarget: String?
    ): ByteArray {
        val width = CARD_SIZE
        val height = CARD_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Overall dark card background (Deep Obsidian Slate for automotive glare reduction)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD_BG
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val hasTarget = !drillTarget.isNullOrBlank()
        val isStory = lesson.mode == SessionMode.STORY_LISTENING

        if (hasTarget) {
            renderRepeatFocusScreen(canvas, width, height, lesson, state, drillTarget)
        } else {
            renderGeneralConversationScreen(canvas, width, height, lesson, state, lastAiText, isStory)
        }

        // 2. Encode to PNG ByteArray
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    /**
     * Dedicated glanceable screen when a repeat drill target exists.
     * Strips away clutter and dedicates almost the entire card to the target sentence.
     */
    private fun renderRepeatFocusScreen(
        canvas: Canvas,
        width: Int,
        height: Int,
        lesson: ActiveLesson,
        state: ConversationState,
        targetText: String
    ) {
        val padX = 28f
        val boxWidth = width - 2 * padX

        // 1. Top Minimal Header: Pill Tag & Compact Topic
        val tagTop = 26f
        val tagBottom = 64f
        val tagRect = RectF(padX, tagTop, padX + 210f, tagBottom)
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TAG_REPEAT_BG
            style = Paint.Style.FILL
        }
        val tagBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(tagRect, 12f, 12f, tagBgPaint)
        canvas.drawRoundRect(tagRect, 12f, 12f, tagBorderPaint)

        val tagTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_LABEL
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🎯 REPEAT / NÓI LẠI", tagRect.centerX(), tagTop + 26f, tagTextPaint)

        // Topic in top-right corner (compact, no heavy brand or divider)
        val topicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val topicTitle = "${lesson.topic.emoji} ${lesson.titleVi}"
        canvas.drawText(topicTitle, width - padX, 48f, topicPaint)

        // 2. Center Hero Box for the Repeat Target Sentence
        val heroBoxTop = 78f
        val heroBoxBottom = 526f
        val heroBoxHeight = heroBoxBottom - heroBoxTop
        val heroBoxRect = RectF(padX, heroBoxTop, width - padX, heroBoxBottom)
        val heroRadius = 24f

        val heroBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_HERO_BG
            style = Paint.Style.FILL
        }
        val heroBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawRoundRect(heroBoxRect, heroRadius, heroRadius, heroBgPaint)
        canvas.drawRoundRect(heroBoxRect, heroRadius, heroRadius, heroBorderPaint)

        // Inner layout width with comfortable margin
        val innerPadX = 26f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)
        val maxAvailableTextHeight = heroBoxHeight - 40f

        // Adaptive Font Size: pick the largest font size that fits 100% without truncation
        val candidateFontSizes = floatArrayOf(40f, 36f, 32f, 28f, 25f, 22f)
        var chosenLayout: StaticLayout? = null

        for (fontSize in candidateFontSizes) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TARGET_TEXT
                textSize = fontSize
                typeface = Typeface.DEFAULT_BOLD
            }
            val layout = StaticLayout.Builder.obtain(targetText, 0, targetText.length, paint, textLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(6f, 1.15f)
                .setMaxLines(10)
                .build()

            if (layout.height <= maxAvailableTextHeight || fontSize == candidateFontSizes.last()) {
                chosenLayout = layout
                break
            }
        }

        // Vertically center the text inside the hero card
        val layout = chosenLayout!!
        val textY = heroBoxTop + ((heroBoxHeight - layout.height) / 2f).coerceAtLeast(16f)

        canvas.save()
        canvas.translate(padX + innerPadX, textY)
        layout.draw(canvas)
        canvas.restore()

        // 3. Clean, Compact Status Footer
        renderStatusFooter(canvas, width, state, hasTarget = true, isStory = false)
    }

    /**
     * Clean conversation/story screen when no specific drill repeat target is active.
     */
    private fun renderGeneralConversationScreen(
        canvas: Canvas,
        width: Int,
        height: Int,
        lesson: ActiveLesson,
        state: ConversationState,
        lastAiText: String?,
        isStory: Boolean
    ) {
        val padX = 28f
        val boxWidth = width - 2 * padX

        // 1. Header Pill Tag
        val tagTop = 26f
        val tagBottom = 64f
        val tagRect = RectF(padX, tagTop, padX + 220f, tagBottom)
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_TAG_STORY_BG else COLOR_TAG_FREE_BG
            style = Paint.Style.FILL
        }
        val tagBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(tagRect, 12f, 12f, tagBgPaint)
        canvas.drawRoundRect(tagRect, 12f, 12f, tagBorderPaint)

        val tagTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val tagText = if (isStory) "🎧 KỂ CHUYỆN (STORY)" else "💬 TRÒ CHUYỆN TỰ DO"
        canvas.drawText(tagText, tagRect.centerX(), tagTop + 26f, tagTextPaint)

        // Topic on top right
        val topicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val topicTitle = "${lesson.topic.emoji} ${lesson.titleVi}"
        canvas.drawText(topicTitle, width - padX, 48f, topicPaint)

        // 2. Large Central Card for AI Speech or Story
        val mainBoxTop = 78f
        val mainBoxBottom = 526f
        val mainBoxRect = RectF(padX, mainBoxTop, width - padX, mainBoxBottom)
        val boxRadius = 24f

        val mainBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BOX_BG
            style = Paint.Style.FILL
        }
        val mainBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BOX_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBgPaint)
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBorderPaint)

        val innerPadX = 26f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)

        // Content label
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_TEXT_MUTED
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val label = if (isStory) "📖 NỘI DUNG CÂU CHUYỆN:" else "🤖 GIA SƯ AI NÓI:"
        canvas.drawText(label, padX + innerPadX, mainBoxTop + 36f, labelPaint)

        val aiContent = lastAiText?.takeIf { it.isNotBlank() }
            ?: if (isStory) "Đang chuẩn bị câu chuyện thú vị cho bạn…" else "Đang kết nối với gia sư AI…"

        val aiTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 24f
            typeface = Typeface.DEFAULT
        }
        val aiLayout = StaticLayout.Builder.obtain(aiContent, 0, aiContent.length, aiTextPaint, textLayoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(4f, 1.15f)
            .setMaxLines(7)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(padX + innerPadX, mainBoxTop + 54f)
        aiLayout.draw(canvas)
        canvas.restore()

        // Hint prompt at the bottom inside card
        val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val hintText = if (isStory) {
            "💡 Nói 'Next' hoặc bấm nút Next trên vô lăng để đổi chuyện"
        } else {
            "💡 Trả lời tự nhiên bằng tiếng Anh khi AI dừng lời"
        }
        canvas.drawText(hintText, padX + innerPadX, mainBoxBottom - 22f, hintPaint)

        // 3. Status Footer
        renderStatusFooter(canvas, width, state, hasTarget = false, isStory = isStory)
    }

    private fun renderStatusFooter(
        canvas: Canvas,
        width: Int,
        state: ConversationState,
        hasTarget: Boolean,
        isStory: Boolean
    ) {
        val footerY = 565f
        val dotRadius = 6.5f
        val dotX = 40f

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (state) {
                ConversationState.ACTIVE -> COLOR_STATUS_ACTIVE
                ConversationState.CONNECTING, ConversationState.RECONNECTING -> COLOR_STATUS_CONNECTING
                ConversationState.PAUSED -> COLOR_STATUS_PAUSED
                else -> COLOR_STATUS_MUTED
            }
            style = Paint.Style.FILL
        }
        canvas.drawCircle(dotX, footerY - 4f, dotRadius, dotPaint)

        val statusText = when (state) {
            ConversationState.CONNECTING -> "Đang kết nối AI…"
            ConversationState.ACTIVE -> when {
                hasTarget -> "🎙️ Đang nghe bạn lặp lại câu trên..."
                isStory -> "Đang phát câu chuyện • Micro sẵn sàng"
                else -> "🎙️ Micro đang mở • Nói tự nhiên"
            }
            ConversationState.PAUSED -> "Tạm dừng bài học"
            ConversationState.RECONNECTING -> "Đang kết nối lại…"
            ConversationState.WAITING_FOR_NETWORK -> "Chờ kết nối mạng…"
            ConversationState.ENDING -> "Đang tổng kết buổi học…"
            else -> "SpeakDrive"
        }
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (hasTarget && state == ConversationState.ACTIVE) COLOR_TARGET_LABEL else COLOR_TEXT_MUTED
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(statusText, dotX + 16f, footerY, statusPaint)
    }

    private companion object {
        const val TAG = "AutoCardArtwork"
        const val CARD_SIZE = 600

        // High contrast dark automotive palette
        const val COLOR_CARD_BG = 0xFF0B0F19.toInt()       // Deep Obsidian Slate
        const val COLOR_BOX_BG = 0xFF161F30.toInt()        // Slate 850
        const val COLOR_BOX_BORDER = 0xFF2B3A52.toInt()    // Slate 700

        const val COLOR_TEXT_PRIMARY = 0xFFF8FAFC.toInt()  // Slate 50
        const val COLOR_TEXT_MUTED = 0xFF94A3B8.toInt()    // Slate 400

        // Tag backgrounds
        const val COLOR_TAG_REPEAT_BG = 0x33F59E0B // 20% amber
        const val COLOR_TAG_STORY_BG = 0x3338BDF8  // 20% sky
        const val COLOR_TAG_FREE_BG = 0x33A78BFA   // 20% purple

        const val COLOR_AI_LABEL = 0xFF38BDF8.toInt()      // Sky 400
        const val COLOR_FREE_LABEL = 0xFFA78BFA.toInt()    // Purple 400

        // Highlight colors for repeat target hero section
        const val COLOR_TARGET_HERO_BG = 0xFF1C170E.toInt() // Deep Warm Amber Dark
        const val COLOR_TARGET_BORDER = 0xFFF59E0B.toInt()  // Amber 500
        const val COLOR_TARGET_LABEL = 0xFFFBBF24.toInt()   // Amber 400
        const val COLOR_TARGET_TEXT = 0xFFFEF08A.toInt()    // Yellow 200 (Highly visible & readable)

        // Status dot colors
        const val COLOR_STATUS_ACTIVE = 0xFF22C55E.toInt()     // Green 500
        const val COLOR_STATUS_CONNECTING = 0xFFEAB308.toInt() // Yellow 500
        const val COLOR_STATUS_PAUSED = 0xFFF97316.toInt()     // Orange 500
        const val COLOR_STATUS_MUTED = 0xFF64748B.toInt()      // Slate 500
    }
}
