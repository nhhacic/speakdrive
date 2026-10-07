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
        drillTarget: String?,
        drillTargetTranslation: String? = null
    ): ByteArray? {
        val cacheKey = "${lesson.sessionId}:$state:${lastAiText.orEmpty()}:${drillTarget.orEmpty()}:${drillTargetTranslation.orEmpty()}"
        if (cacheKey == lastKey && lastArtwork != null) {
            return lastArtwork
        }

        return try {
            val bytes = renderBitmapBytes(lesson, state, lastAiText, drillTarget, drillTargetTranslation)
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
        drillTarget: String?,
        drillTargetTranslation: String? = null
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

        // Content is intentionally constrained to the top safe zone (y: 16f -> 295f).
        // The bottom half (y: 300f -> 600f) is left clean and uncluttered so Android Auto's
        // native Title, Subtitle and Media Playback Controls render without text overlap.
        if (hasTarget) {
            renderRepeatFocusScreen(canvas, width, lesson, drillTarget, drillTargetTranslation)
        } else {
            renderGeneralConversationScreen(canvas, width, lesson, lastAiText, isStory)
        }

        // 2. Encode to PNG ByteArray
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    /**
     * Dedicated glanceable screen when a repeat drill target exists.
     * High-contrast, ultra-clear hero card positioned in the top safe zone.
     * Features pure white text for maximum daylight visibility and yellow subtitle for translated meaning.
     */
    private fun renderRepeatFocusScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        targetText: String,
        translationText: String? = null
    ) {
        val padX = 20f
        val boxWidth = width - 2 * padX

        // Hero Box in Top Safe Zone (height 16f -> 295f = 279f)
        val heroBoxTop = 16f
        val heroBoxBottom = 295f
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

        // 1. Tag / Badge at top of Hero Box
        val badgeText = "🎯 LẶP LẠI THEO AI"
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_LABEL
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val badgeY = heroBoxTop + 26f
        canvas.drawText(badgeText, width / 2f, badgeY, badgePaint)

        // Inner layout width with comfortable margin
        val innerPadX = 18f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)
        val contentTopY = heroBoxTop + 36f
        val contentBottomY = heroBoxBottom - 12f
        val maxAvailableHeight = contentBottomY - contentTopY

        val hasTranslation = !translationText.isNullOrBlank()

        if (!hasTranslation) {
            // Adaptive Font Size: pick the largest font size that fits without truncation
            val candidateFontSizes = floatArrayOf(36f, 32f, 28f, 25f, 22f, 20f)
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
                    .setLineSpacing(5f, 1.15f)
                    .setMaxLines(7)
                    .build()

                if (layout.height <= maxAvailableHeight || fontSize == candidateFontSizes.last()) {
                    chosenLayout = layout
                    break
                }
            }

            // Vertically center the text inside the hero card content area
            val layout = chosenLayout!!
            val textY = contentTopY + ((maxAvailableHeight - layout.height) / 2f).coerceAtLeast(0f)

            canvas.save()
            canvas.translate(padX + innerPadX, textY)
            layout.draw(canvas)
            canvas.restore()
        } else {
            // Both English target sentence and Translation subtitle
            val translation = translationText
            val spacing = 10f

            // Candidate font size pairs: (English size, Translation size)
            val sizePairs = listOf(
                30f to 20f,
                27f to 19f,
                24f to 18f,
                22f to 16f,
                20f to 15f
            )

            var chosenEnglishLayout: StaticLayout? = null
            var chosenTransLayout: StaticLayout? = null

            for ((enSize, trSize) in sizePairs) {
                val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TARGET_TEXT
                    textSize = enSize
                    typeface = Typeface.DEFAULT_BOLD
                }
                val enLayout = StaticLayout.Builder.obtain(targetText, 0, targetText.length, enPaint, textLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(4f, 1.15f)
                    .setMaxLines(4)
                    .build()

                val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TARGET_SUBTITLE
                    textSize = trSize
                    typeface = Typeface.DEFAULT
                }
                val trLayout = StaticLayout.Builder.obtain(translation, 0, translation.length, trPaint, textLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(3f, 1.15f)
                    .setMaxLines(3)
                    .build()

                val totalH = enLayout.height + spacing + trLayout.height
                if (totalH <= maxAvailableHeight || enSize == sizePairs.last().first) {
                    chosenEnglishLayout = enLayout
                    chosenTransLayout = trLayout
                    break
                }
            }

            val enLayout = chosenEnglishLayout!!
            val trLayout = chosenTransLayout!!
            val totalHeight = enLayout.height + spacing + trLayout.height
            val startY = contentTopY + ((maxAvailableHeight - totalHeight) / 2f).coerceAtLeast(0f)

            // Draw English Target
            canvas.save()
            canvas.translate(padX + innerPadX, startY)
            enLayout.draw(canvas)
            canvas.restore()

            // Draw Translation Subtitle
            val transY = startY + enLayout.height + spacing
            canvas.save()
            canvas.translate(padX + innerPadX, transY)
            trLayout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Clean conversation/story card in the top safe zone when no specific drill target is active.
     */
    private fun renderGeneralConversationScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        lastAiText: String?,
        isStory: Boolean
    ) {
        val padX = 24f
        val boxWidth = width - 2 * padX

        // Central Card in Top Safe Zone (height ~275f)
        val mainBoxTop = 20f
        val mainBoxBottom = 295f
        val mainBoxHeight = mainBoxBottom - mainBoxTop
        val mainBoxRect = RectF(padX, mainBoxTop, width - padX, mainBoxBottom)
        val boxRadius = 24f

        val mainBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BOX_BG
            style = Paint.Style.FILL
        }
        val mainBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBgPaint)
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBorderPaint)

        val innerPadX = 22f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)

        // 1. Content label inside top of card
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val label = if (isStory) "📖 NỘI DUNG CÂU CHUYỆN:" else "🤖 GIA SƯ AI NÓI:"
        canvas.drawText(label, padX + innerPadX, mainBoxTop + 28f, labelPaint)

        // 2. AI Content / Story Text
        val aiContent = lastAiText?.takeIf { it.isNotBlank() }
            ?: if (isStory) "Đang chuẩn bị câu chuyện thú vị cho bạn…" else "Đang kết nối với gia sư AI…"

        val aiTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 21f
            typeface = Typeface.DEFAULT
        }
        val maxAvailableContentHeight = mainBoxHeight - 48f
        val aiLayout = StaticLayout.Builder.obtain(aiContent, 0, aiContent.length, aiTextPaint, textLayoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(4f, 1.15f)
            .setMaxLines(6)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(padX + innerPadX, mainBoxTop + 42f)
        aiLayout.draw(canvas)
        canvas.restore()
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

        // High contrast automotive palette for repeat target hero section (glanceable in daylight)
        const val COLOR_TARGET_HERO_BG = 0xFF0D1527.toInt() // Deep Slate / Midnight Navy (replaces muddy dark amber)
        const val COLOR_TARGET_BORDER = 0xFF38BDF8.toInt()  // Vibrant Sky Cyan 400
        const val COLOR_TARGET_LABEL = 0xFF38BDF8.toInt()   // Sky 400
        const val COLOR_TARGET_TEXT = 0xFFFFFFFF.toInt()    // Pure Crisp White (maximum contrast > 18:1)
        const val COLOR_TARGET_SUBTITLE = 0xFFFDE047.toInt() // Vibrant Yellow 300 for translated meaning subtitle

        // Status dot colors
        const val COLOR_STATUS_ACTIVE = 0xFF22C55E.toInt()     // Green 500
        const val COLOR_STATUS_CONNECTING = 0xFFEAB308.toInt() // Yellow 500
        const val COLOR_STATUS_PAUSED = 0xFFF97316.toInt()     // Orange 500
        const val COLOR_STATUS_MUTED = 0xFF64748B.toInt()      // Slate 500
    }
}
