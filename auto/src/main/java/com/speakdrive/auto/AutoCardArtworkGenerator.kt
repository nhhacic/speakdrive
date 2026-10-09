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
     * High-contrast, ultra-clear hero card strictly constrained to the top safe zone (y: 12f -> 290f).
     * The bottom half (y: 295f -> 600f) is left completely clear for Android Auto's native Title,
     * Subtitle, and Transport Controls (Play/Pause/Next) to render without overlapping.
     * Features pure crisp white text, adaptive font sizing, and vibrant yellow translation subtitle.
     */
    private fun renderRepeatFocusScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        targetText: String,
        translationText: String? = null
    ) {
        val padX = 8f
        val boxWidth = width - 2 * padX

        // Full-frame Hero Card that fills the entire 600x600 album artwork canvas seamlessly
        val heroBoxTop = 8f
        val heroBoxBottom = 592f
        val heroBoxRect = RectF(padX, heroBoxTop, width - padX, heroBoxBottom)
        val heroRadius = 24f

        val heroBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_HERO_BG
            style = Paint.Style.FILL
        }
        val heroBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TARGET_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(heroBoxRect, heroRadius, heroRadius, heroBgPaint)
        canvas.drawRoundRect(heroBoxRect, heroRadius, heroRadius, heroBorderPaint)

        // 1. Pill Badge at top center - pushed down to y=85f to avoid top scrim & overlay buttons
        val badgeTop = 85f
        val badgeBottom = badgeTop + 32f
        val badgeWidth = 210f
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
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(badgeRect, 16f, 16f, pillBgPaint)
        canvas.drawRoundRect(badgeRect, 16f, 16f, pillBorderPaint)

        val badgeText = "🎯 LẶP LẠI THEO AI"
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(badgeText, width / 2f, badgeTop + 21f, badgePaint)

        // Center Safe Column: Strictly constrained to 400px width centered at 100px.
        // Android Auto center-crops square artwork in vertical split-screen cards, clipping ~85px on each side.
        // Keeping text strictly within [100px .. 500px] guarantees zero word clipping on any head unit!
        val textLayoutWidth = 400
        val textStartX = (width - textLayoutWidth) / 2f // Exactly 100px margin on each side

        // Vertical safe budget below top badge and above bottom widget boundary
        val contentTopY = badgeBottom + 16f // 133f
        val contentBottomY = 545f
        val maxAvailableHeight = contentBottomY - contentTopY

        val hasTranslation = !translationText.isNullOrBlank()
        val translation = if (hasTranslation) {
            if (translationText!!.startsWith("🇻🇳")) translationText else "🇻🇳 $translationText"
        } else {
            "🗣️ Lắng nghe và nhắc lại theo AI"
        }
        val spacing = 18f

        // Candidate font size pairs: (English size, Translation size)
        // Scaled to fit within 400px width with clean line breaks
        val sizePairs = listOf(
            25f to 18f,
            22.5f to 16.5f,
            20f to 15f,
            18f to 13.5f,
            16f to 12f,
            14.5f to 11f
        )

        var chosenEnglishLayout: StaticLayout? = null
        var chosenTransLayout: StaticLayout? = null

        for ((enSize, trSize) in sizePairs) {
            val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TARGET_TEXT
                textSize = enSize
                typeface = Typeface.DEFAULT_BOLD
                isFakeBoldText = true
                setShadowLayer(5f, 0f, 2f, 0xCC000000.toInt())
            }
            val enLayout = StaticLayout.Builder.obtain(targetText, 0, targetText.length, enPaint, textLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(4f, 1.15f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(5)
                .build()

            val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (hasTranslation) COLOR_TARGET_SUBTITLE else COLOR_TEXT_MUTED
                textSize = trSize
                typeface = Typeface.DEFAULT_BOLD
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 1.5f, 0xCC000000.toInt())
            }
            val trLayout = StaticLayout.Builder.obtain(translation, 0, translation.length, trPaint, textLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(4f, 1.15f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(5)
                .build()

            var hasOverflow = false
            for (i in 0 until enLayout.lineCount) {
                if (enLayout.getLineWidth(i) > textLayoutWidth) {
                    hasOverflow = true
                    break
                }
            }
            if (!hasOverflow) {
                for (i in 0 until trLayout.lineCount) {
                    if (trLayout.getLineWidth(i) > textLayoutWidth) {
                        hasOverflow = true
                        break
                    }
                }
            }

            val totalH = enLayout.height + spacing + trLayout.height
            if ((!hasOverflow && totalH <= maxAvailableHeight) || enSize == sizePairs.last().first) {
                chosenEnglishLayout = enLayout
                chosenTransLayout = trLayout
                break
            }
        }

        val enLayout = chosenEnglishLayout!!
        val trLayout = chosenTransLayout!!
        val totalH = enLayout.height + spacing + trLayout.height
        val startY = contentTopY + ((maxAvailableHeight - totalH) / 2f).coerceAtLeast(0f)

        // Draw English Target (Centered vertically & horizontally within safe column)
        canvas.save()
        canvas.translate(textStartX, startY)
        enLayout.draw(canvas)
        canvas.restore()

        // Draw Translation Subtitle directly below English target
        val transY = startY + enLayout.height + spacing
        canvas.save()
        canvas.translate(textStartX, transY)
        trLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * Clean conversation/story card that fills the artwork canvas when no specific drill target is active.
     */
    private fun renderGeneralConversationScreen(
        canvas: Canvas,
        width: Int,
        lesson: ActiveLesson,
        lastAiText: String?,
        isStory: Boolean
    ) {
        val padX = 8f
        val boxWidth = width - 2 * padX

        // Full-frame Card (y: 8f -> 592f = 584f height)
        val mainBoxTop = 8f
        val mainBoxBottom = 592f
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

        val textLayoutWidth = 400
        val textStartX = (width - textLayoutWidth) / 2f

        // 1. Content label inside top of card (below top scrim)
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStory) COLOR_AI_LABEL else COLOR_FREE_LABEL
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val label = if (isStory) "📖 NỘI DUNG CÂU CHUYỆN:" else "🤖 GIA SƯ AI NÓI:"
        canvas.drawText(label, textStartX, 95f, labelPaint)

        // 2. AI Content / Story Text
        val aiContent = lastAiText?.takeIf { it.isNotBlank() }
            ?: if (isStory) "Đang chuẩn bị câu chuyện thú vị cho bạn…" else "Đang kết nối với gia sư AI…"

        val maxAvailableContentHeight = 420f
        val candidateFontSizes = floatArrayOf(21f, 18.5f, 16.5f, 15f, 13.5f)
        var chosenAiLayout: StaticLayout? = null

        for (fontSize in candidateFontSizes) {
            val aiTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = fontSize
                typeface = Typeface.DEFAULT
                isFakeBoldText = (fontSize <= 17f)
                setShadowLayer(4f, 0f, 1.5f, 0x80000000.toInt())
            }
            val aiLayout = StaticLayout.Builder.obtain(aiContent, 0, aiContent.length, aiTextPaint, textLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(4f, 1.15f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
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
        canvas.translate(textStartX, 115f)
        aiLayout.draw(canvas)
        canvas.restore()
    }

    private companion object {
        const val TAG = "AutoCardArtwork"
        const val CARD_SIZE = 600

        // High contrast dark automotive palette
        const val COLOR_CARD_BG = 0xFF0B0F19.toInt()       // Deep Obsidian Slate
        const val COLOR_BOX_BG = 0xFF141F36.toInt()        // Rich Deep Slate Blue
        const val COLOR_BOX_BORDER = 0xFF2B3A52.toInt()    // Slate 700

        const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()  // Pure White
        const val COLOR_TEXT_MUTED = 0xFF94A3B8.toInt()    // Slate 400

        // Tag backgrounds
        const val COLOR_TAG_REPEAT_BG = 0x33F59E0B // 20% amber
        const val COLOR_TAG_STORY_BG = 0x3338BDF8  // 20% sky
        const val COLOR_TAG_FREE_BG = 0x33A78BFA   // 20% purple

        const val COLOR_AI_LABEL = 0xFF38BDF8.toInt()      // Sky 400
        const val COLOR_FREE_LABEL = 0xFFA78BFA.toInt()    // Purple 400

        // High contrast automotive palette for repeat target hero section (glanceable through car scrim)
        const val COLOR_TARGET_HERO_BG = 0xFF132247.toInt() // Rich Luminous Navy Slate (contrasts against card bg)
        const val COLOR_TARGET_BORDER = 0xFF38BDF8.toInt()  // Vibrant Sky Cyan 400
        const val COLOR_TARGET_LABEL = 0xFF38BDF8.toInt()   // Sky 400
        const val COLOR_TARGET_TEXT = 0xFFFFFFFF.toInt()    // Pure Crisp White (> 21:1 contrast)
        const val COLOR_TARGET_SUBTITLE = 0xFFFDE047.toInt() // Vibrant Yellow 300 for translated meaning
        const val COLOR_BADGE_BG = 0x3338BDF8               // 20% Sky 400 pill badge background

        // Status dot colors
        const val COLOR_STATUS_ACTIVE = 0xFF22C55E.toInt()     // Green 500
        const val COLOR_STATUS_CONNECTING = 0xFFEAB308.toInt() // Yellow 500
        const val COLOR_STATUS_PAUSED = 0xFFF97316.toInt()     // Orange 500
        const val COLOR_STATUS_MUTED = 0xFF64748B.toInt()      // Slate 500
    }
}
