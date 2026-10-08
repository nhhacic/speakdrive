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
     * High-contrast, ultra-clear hero card utilizing the full artwork frame.
     * Features pure crisp white text with drop-shadow for daylight glare resistance,
     * adaptive large font sizing (up to 54f), and vibrant yellow translation subtitle.
     */
    private fun renderRepeatFocusScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        targetText: String,
        translationText: String? = null
    ) {
        val padX = 18f
        val boxWidth = width - 2 * padX

        // Hero Box utilizing the full artwork area (y: 20f -> 576f = 556f height)
        val heroBoxTop = 20f
        val heroBoxBottom = 576f
        val heroBoxRect = RectF(padX, heroBoxTop, width - padX, heroBoxBottom)
        val heroRadius = 28f

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

        // 1. Pill Badge at top center of Hero Box
        val badgeTop = heroBoxTop + 18f
        val badgeBottom = badgeTop + 34f
        val badgeWidth = 240f
        val badgeLeft = (width - badgeWidth) / 2f
        val badgeRight = badgeLeft + badgeWidth
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BADGE_BG
            style = Paint.Style.FILL
        }
        val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        canvas.drawRoundRect(badgeRect, 17f, 17f, pillBgPaint)
        canvas.drawRoundRect(badgeRect, 17f, 17f, pillBorderPaint)

        val badgeText = "🎯 LẶP LẠI THEO AI"
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_LABEL
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(badgeText, width / 2f, badgeTop + 23f, badgePaint)

        // Inner layout width with comfortable margin
        val innerPadX = 20f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)
        val contentTopY = badgeBottom + 16f
        val contentBottomY = heroBoxBottom - 20f
        val maxAvailableHeight = contentBottomY - contentTopY

        val hasTranslation = !translationText.isNullOrBlank()

        if (!hasTranslation) {
            // Adaptive Large Font Size: pick the largest font size that fits without truncation
            val candidateFontSizes = floatArrayOf(54f, 48f, 42f, 38f, 34f, 30f, 26f, 22f)
            var chosenLayout: StaticLayout? = null

            for (fontSize in candidateFontSizes) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TARGET_TEXT
                    textSize = fontSize
                    typeface = Typeface.DEFAULT_BOLD
                    isFakeBoldText = true
                    setShadowLayer(8f, 0f, 2f, 0xB3000000.toInt())
                }
                val layout = StaticLayout.Builder.obtain(targetText, 0, targetText.length, paint, textLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(6f, 1.15f)
                    .setMaxLines(8)
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
            val spacing = 18f

            // Candidate font size pairs: (English size, Translation size)
            val sizePairs = listOf(
                46f to 28f,
                40f to 25f,
                36f to 22f,
                32f to 20f,
                28f to 18f,
                24f to 16f
            )

            var chosenEnglishLayout: StaticLayout? = null
            var chosenTransLayout: StaticLayout? = null

            for ((enSize, trSize) in sizePairs) {
                val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TARGET_TEXT
                    textSize = enSize
                    typeface = Typeface.DEFAULT_BOLD
                    isFakeBoldText = true
                    setShadowLayer(8f, 0f, 2f, 0xB3000000.toInt())
                }
                val enLayout = StaticLayout.Builder.obtain(targetText, 0, targetText.length, enPaint, textLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(5f, 1.15f)
                    .setMaxLines(5)
                    .build()

                val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TARGET_SUBTITLE
                    textSize = trSize
                    typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
                }
                val trLayout = StaticLayout.Builder.obtain(translation, 0, translation.length, trPaint, textLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(4f, 1.15f)
                    .setMaxLines(4)
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
     * Clean conversation/story card utilizing the full artwork frame when no specific drill target is active.
     */
    private fun renderGeneralConversationScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        lastAiText: String?,
        isStory: Boolean
    ) {
        val padX = 18f
        val boxWidth = width - 2 * padX

        // Central Card utilizing the full artwork area (y: 20f -> 576f)
        val mainBoxTop = 20f
        val mainBoxBottom = 576f
        val mainBoxHeight = mainBoxBottom - mainBoxTop
        val mainBoxRect = RectF(padX, mainBoxTop, width - padX, mainBoxBottom)
        val boxRadius = 28f

        val mainBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BOX_BG
            style = Paint.Style.FILL
        }
        val mainBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBgPaint)
        canvas.drawRoundRect(mainBoxRect, boxRadius, boxRadius, mainBorderPaint)

        val innerPadX = 24f
        val textLayoutWidth = (boxWidth - 2 * innerPadX).toInt().coerceAtLeast(100)

        // 1. Content label inside top of card
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val label = if (isStory) "📖 NỘI DUNG CÂU CHUYỆN:" else "🤖 GIA SƯ AI NÓI:"
        canvas.drawText(label, padX + innerPadX, mainBoxTop + 38f, labelPaint)

        // 2. AI Content / Story Text
        val aiContent = lastAiText?.takeIf { it.isNotBlank() }
            ?: if (isStory) "Đang chuẩn bị câu chuyện thú vị cho bạn…" else "Đang kết nối với gia sư AI…"

        val maxAvailableContentHeight = mainBoxHeight - 110f
        val candidateFontSizes = floatArrayOf(32f, 28f, 25f, 22f, 20f)
        var chosenAiLayout: StaticLayout? = null

        for (fontSize in candidateFontSizes) {
            val aiTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = fontSize
                typeface = Typeface.DEFAULT
                isFakeBoldText = (fontSize <= 24f)
                setShadowLayer(4f, 0f, 1f, 0x80000000.toInt())
            }
            val aiLayout = StaticLayout.Builder.obtain(aiContent, 0, aiContent.length, aiTextPaint, textLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(5f, 1.18f)
                .setMaxLines(10)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            if (aiLayout.height <= maxAvailableContentHeight || fontSize == candidateFontSizes.last()) {
                chosenAiLayout = aiLayout
                break
            }
        }

        val aiLayout = chosenAiLayout!!
        canvas.save()
        canvas.translate(padX + innerPadX, mainBoxTop + 60f)
        aiLayout.draw(canvas)
        canvas.restore()

        // 3. Helpful hint at the bottom inside card
        val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val hintText = if (isStory) {
            "💡 Nói 'Next' hoặc bấm nút Next trên vô lăng để đổi chuyện"
        } else {
            "💡 Trả lời tự nhiên bằng tiếng Anh khi AI dừng lời"
        }
        canvas.drawText(hintText, padX + innerPadX, mainBoxBottom - 24f, hintPaint)
    }

    private companion object {
        const val TAG = "AutoCardArtwork"
        const val CARD_SIZE = 600

        // High contrast dark automotive palette
        const val COLOR_CARD_BG = 0xFF0B0F19.toInt()       // Deep Obsidian Slate
        const val COLOR_BOX_BG = 0xFF111827.toInt()        // Slate 900
        const val COLOR_BOX_BORDER = 0xFF2B3A52.toInt()    // Slate 700

        const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()  // Pure White
        const val COLOR_TEXT_MUTED = 0xFF94A3B8.toInt()    // Slate 400

        // Tag backgrounds
        const val COLOR_TAG_REPEAT_BG = 0x33F59E0B // 20% amber
        const val COLOR_TAG_STORY_BG = 0x3338BDF8  // 20% sky
        const val COLOR_TAG_FREE_BG = 0x33A78BFA   // 20% purple

        const val COLOR_AI_LABEL = 0xFF38BDF8.toInt()      // Sky 400
        const val COLOR_FREE_LABEL = 0xFFA78BFA.toInt()    // Purple 400

        // High contrast automotive palette for repeat target hero section (glanceable in daylight)
        const val COLOR_TARGET_HERO_BG = 0xFF0B132B.toInt() // Deep Rich Navy Slate (maximum contrast with text)
        const val COLOR_TARGET_BORDER = 0xFF38BDF8.toInt()  // Vibrant Sky Cyan 400
        const val COLOR_TARGET_LABEL = 0xFF38BDF8.toInt()   // Sky 400
        const val COLOR_TARGET_TEXT = 0xFFFFFFFF.toInt()    // Pure Crisp White (maximum contrast > 21:1)
        const val COLOR_TARGET_SUBTITLE = 0xFFFDE047.toInt() // Vibrant Yellow 300 for translated meaning subtitle
        const val COLOR_BADGE_BG = 0x2638BDF8               // 15% Sky 400 pill badge background

        // Status dot colors
        const val COLOR_STATUS_ACTIVE = 0xFF22C55E.toInt()     // Green 500
        const val COLOR_STATUS_CONNECTING = 0xFFEAB308.toInt() // Yellow 500
        const val COLOR_STATUS_PAUSED = 0xFFF97316.toInt()     // Orange 500
        const val COLOR_STATUS_MUTED = 0xFF64748B.toInt()      // Slate 500
    }
}
