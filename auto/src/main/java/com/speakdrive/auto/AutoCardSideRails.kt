package com.speakdrive.auto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The two side rails of the repeat-drill artwork: whose turn it is on the left, how the sentence
 * is going on the right.
 *
 * Android Auto shows the same artwork twice. The full Now Playing screen shows the whole square,
 * scaled down, with the title below it; the media card next to the map scales it to the card's
 * height and crops the sides, so only about x 125..475 of the 600 px survives there. The rails sit
 * in the strips only the full screen shows, so they fill the square there and vanish on the card,
 * where the sentence stays alone. Like the rest of the artwork they stay inside the panel above
 * [AutoCardArtworkGenerator.PANEL_BOTTOM]: on a wider media card they may show, but never under
 * Android Auto's own title and controls.
 */
internal object AutoCardSideRails {

    /** Nothing is drawn between these two x positions; that column belongs to the sentence. */
    const val LEFT_RAIL_END = 116
    const val RIGHT_RAIL_START = 484

    fun draw(canvas: Canvas, status: DrillStatus, size: Int = CARD_SIZE) {
        val scale = size / CARD_SIZE.toFloat()
        canvas.save()
        canvas.scale(scale, scale)

        val turnColor = turnColor(status.turn)
        drawRail(canvas, RectF(RAIL_INSET, RAIL_INSET, LEFT_RAIL_END - RAIL_BORDER, RAIL_BOTTOM), turnColor)
        val (turnLine1, turnLine2) = turnLabel(status.turn)
        drawHeader(canvas, LEFT_CENTER_X, "LƯỢT NÓI")
        drawBadge(canvas, LEFT_CENTER_X, turnColor) { cx, cy, paint -> drawTurnIcon(canvas, status.turn, cx, cy, paint) }
        drawTwoLines(canvas, LEFT_CENTER_X, turnLine1, turnLine2, COLOR_TEXT, turnColor)
        drawDivider(canvas, LEFT_CENTER_X)
        val tries = status.attemptNumber ?: 0
        drawHeader(canvas, LEFT_CENTER_X, "ĐÃ THỬ", BOTTOM_HEADER_Y)
        drawBigNumber(canvas, LEFT_CENTER_X, "$tries/${status.maxAttempts}")
        drawAttemptDots(canvas, status)

        val verdictColor = verdictColor(status)
        drawRail(canvas, RectF(RIGHT_RAIL_START + RAIL_BORDER, RAIL_INSET, CARD_SIZE - RAIL_INSET, RAIL_BOTTOM), verdictColor)
        drawHeader(canvas, RIGHT_CENTER_X, "PHÁT ÂM")
        drawScoreRing(canvas, status.accuracyPercent, verdictColor)
        val (verdictLine1, verdictLine2) = verdictLabel(status)
        drawTwoLines(canvas, RIGHT_CENTER_X, verdictLine1, verdictLine2, COLOR_TEXT, verdictColor)
        drawDivider(canvas, RIGHT_CENTER_X)
        drawHeader(canvas, RIGHT_CENTER_X, "CÂU ĐẠT", BOTTOM_HEADER_Y)
        drawBigNumber(canvas, RIGHT_CENTER_X, if (status.sentences == 0) "0" else "${status.passedSentences}/${status.sentences}")
        drawProgressBar(canvas, status)

        canvas.restore()
    }

    internal fun turnLabel(turn: DrillTurn): Pair<String, String> = when (turn) {
        DrillTurn.AI_SPEAKING -> "AI" to "ĐANG ĐỌC"
        DrillTurn.YOUR_TURN -> "ĐẾN LƯỢT" to "BẠN NÓI"
        DrillTurn.YOU_SPEAKING -> "ĐANG NGHE" to "BẠN NÓI"
        DrillTurn.GRADING -> "ĐANG" to "CHẤM ĐIỂM"
        DrillTurn.PAUSED -> "TẠM" to "DỪNG"
        DrillTurn.CONNECTING -> "ĐANG" to "KẾT NỐI"
    }

    internal fun verdictLabel(status: DrillStatus): Pair<String, String> = when (status.passed) {
        true -> "ĐÃ" to "ĐẠT"
        false -> "CHƯA" to "ĐẠT"
        null -> "CHƯA" to "CHẤM"
    }

    internal fun turnColor(turn: DrillTurn): Int = when (turn) {
        DrillTurn.AI_SPEAKING -> COLOR_SKY
        DrillTurn.YOUR_TURN -> COLOR_GREEN
        DrillTurn.YOU_SPEAKING -> COLOR_GREEN_LIGHT
        DrillTurn.GRADING -> COLOR_AMBER
        DrillTurn.PAUSED -> COLOR_ORANGE
        DrillTurn.CONNECTING -> COLOR_SLATE
    }

    internal fun verdictColor(status: DrillStatus): Int {
        val score = status.accuracyPercent
        return when {
            status.passed == true -> COLOR_GREEN
            score == null -> COLOR_SLATE
            score >= PASSABLE_SCORE -> COLOR_AMBER
            else -> COLOR_RED
        }
    }

    private fun drawRail(canvas: Canvas, rect: RectF, color: Int) {
        // Opaque base, so the artwork's panel and its edge line do not show through the tint.
        canvas.drawRoundRect(rect, RAIL_RADIUS, RAIL_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = COLOR_RAIL_BASE })
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, RAIL_FILL_ALPHA) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(color, RAIL_BORDER_ALPHA)
            style = Paint.Style.STROKE
            strokeWidth = RAIL_BORDER
        }
        canvas.drawRoundRect(rect, RAIL_RADIUS, RAIL_RADIUS, fill)
        canvas.drawRoundRect(rect, RAIL_RADIUS, RAIL_RADIUS, border)
    }

    private fun drawHeader(canvas: Canvas, centerX: Float, text: String, baseline: Float = TOP_HEADER_Y) {
        canvas.drawText(text, centerX, baseline, fittedPaint(text, HEADER_SIZE, COLOR_MUTED))
    }

    private inline fun drawBadge(canvas: Canvas, centerX: Float, color: Int, icon: (Float, Float, Paint) -> Unit) {
        canvas.drawCircle(centerX, BADGE_CENTER_Y, BADGE_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = COLOR_ICON
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        icon(centerX, BADGE_CENTER_Y, iconPaint)
    }

    /** Simple vector glyphs: emoji render differently (or not at all) from one head unit to the next. */
    private fun drawTurnIcon(canvas: Canvas, turn: DrillTurn, cx: Float, cy: Float, paint: Paint) {
        when (turn) {
            DrillTurn.AI_SPEAKING -> {
                paint.style = Paint.Style.FILL
                val speaker = Path().apply {
                    moveTo(cx - 22f, cy - 8f)
                    lineTo(cx - 12f, cy - 8f)
                    lineTo(cx + 1f, cy - 19f)
                    lineTo(cx + 1f, cy + 19f)
                    lineTo(cx - 12f, cy + 8f)
                    lineTo(cx - 22f, cy + 8f)
                    close()
                }
                canvas.drawPath(speaker, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4.5f
                canvas.drawArc(RectF(cx - 9f, cy - 11f, cx + 13f, cy + 11f), -50f, 100f, false, paint)
                canvas.drawArc(RectF(cx - 15f, cy - 21f, cx + 23f, cy + 21f), -50f, 100f, false, paint)
            }
            DrillTurn.YOUR_TURN, DrillTurn.YOU_SPEAKING -> {
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(cx - 9f, cy - 24f, cx + 9f, cy + 4f), 9f, 9f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4.5f
                canvas.drawArc(RectF(cx - 16f, cy - 16f, cx + 16f, cy + 12f), 0f, 180f, false, paint)
                canvas.drawLine(cx, cy + 12f, cx, cy + 21f, paint)
                canvas.drawLine(cx - 10f, cy + 21f, cx + 10f, cy + 21f, paint)
            }
            DrillTurn.GRADING, DrillTurn.CONNECTING -> {
                paint.style = Paint.Style.FILL
                for (dx in floatArrayOf(-15f, 0f, 15f)) canvas.drawCircle(cx + dx, cy, 6f, paint)
            }
            DrillTurn.PAUSED -> {
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(cx - 14f, cy - 17f, cx - 4f, cy + 17f), 3f, 3f, paint)
                canvas.drawRoundRect(RectF(cx + 4f, cy - 17f, cx + 14f, cy + 17f), 3f, 3f, paint)
            }
        }
    }

    private fun drawScoreRing(canvas: Canvas, score: Int?, color: Int) {
        val ring = RectF(
            RIGHT_CENTER_X - BADGE_RADIUS + RING_STROKE / 2, BADGE_CENTER_Y - BADGE_RADIUS + RING_STROKE / 2,
            RIGHT_CENTER_X + BADGE_RADIUS - RING_STROKE / 2, BADGE_CENTER_Y + BADGE_RADIUS - RING_STROKE / 2
        )
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE
            strokeCap = Paint.Cap.ROUND
            this.color = COLOR_TRACK
        }
        canvas.drawArc(ring, 0f, 360f, false, stroke)
        if (score != null && score > 0) {
            stroke.color = color
            canvas.drawArc(ring, -90f, 360f * score.coerceAtMost(100) / 100f, false, stroke)
        }
        val text = score?.toString() ?: "—"
        val paint = fittedPaint(text, SCORE_SIZE, if (score == null) COLOR_MUTED else COLOR_TEXT, maxWidth = RING_TEXT_WIDTH)
        canvas.drawText(text, RIGHT_CENTER_X, BADGE_CENTER_Y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun drawTwoLines(canvas: Canvas, centerX: Float, first: String, second: String, firstColor: Int, secondColor: Int) {
        canvas.drawText(first, centerX, LABEL_LINE1_Y, fittedPaint(first, LABEL_SIZE, firstColor))
        canvas.drawText(second, centerX, LABEL_LINE2_Y, fittedPaint(second, LABEL_SIZE, secondColor))
    }

    private fun drawDivider(canvas: Canvas, centerX: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TRACK }
        canvas.drawRoundRect(
            RectF(centerX - DIVIDER_WIDTH / 2, DIVIDER_Y - 1.5f, centerX + DIVIDER_WIDTH / 2, DIVIDER_Y + 1.5f),
            1.5f, 1.5f, paint
        )
    }

    private fun drawBigNumber(canvas: Canvas, centerX: Float, text: String) {
        canvas.drawText(text, centerX, BIG_NUMBER_Y, fittedPaint(text, BIG_NUMBER_SIZE, COLOR_TEXT))
    }

    /** One dot per allowed try: red for a failed try, green for the one that passed, hollow for tries left. */
    private fun drawAttemptDots(canvas: Canvas, status: DrillStatus) {
        val count = status.maxAttempts.coerceAtLeast(1)
        val tries = (status.attemptNumber ?: 0).coerceAtMost(count)
        val gap = DOT_GAP
        val totalWidth = count * DOT_RADIUS * 2 + (count - 1) * gap
        var cx = LEFT_CENTER_X - totalWidth / 2 + DOT_RADIUS
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val hollow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = COLOR_MUTED
        }
        for (index in 0 until count) {
            if (index < tries) {
                fill.color = if (index == tries - 1 && status.passed == true) COLOR_GREEN else COLOR_RED
                canvas.drawCircle(cx, PROGRESS_Y, DOT_RADIUS, fill)
            } else {
                canvas.drawCircle(cx, PROGRESS_Y, DOT_RADIUS - 1.5f, hollow)
            }
            cx += DOT_RADIUS * 2 + gap
        }
    }

    private fun drawProgressBar(canvas: Canvas, status: DrillStatus) {
        val left = RIGHT_CENTER_X - BAR_WIDTH / 2
        val track = RectF(left, PROGRESS_Y - BAR_HEIGHT / 2, left + BAR_WIDTH, PROGRESS_Y + BAR_HEIGHT / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TRACK }
        canvas.drawRoundRect(track, BAR_HEIGHT / 2, BAR_HEIGHT / 2, paint)
        if (status.sentences > 0 && status.passedSentences > 0) {
            val fraction = (status.passedSentences.toFloat() / status.sentences).coerceIn(0f, 1f)
            paint.color = COLOR_GREEN
            canvas.drawRoundRect(
                RectF(track.left, track.top, track.left + BAR_HEIGHT.coerceAtLeast(BAR_WIDTH * fraction), track.bottom),
                BAR_HEIGHT / 2, BAR_HEIGHT / 2, paint
            )
        }
    }

    /** A bold centred paint, shrunk until [text] fits the rail. */
    private fun fittedPaint(text: String, size: Float, color: Int, maxWidth: Float = TEXT_MAX_WIDTH): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = size
        }
        while (paint.textSize > MIN_TEXT_SIZE && paint.measureText(text) > maxWidth) {
            paint.textSize -= 1f
        }
        return paint
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private const val CARD_SIZE = 600
    private const val RAIL_INSET = 6f
    private const val RAIL_BOTTOM = AutoCardArtworkGenerator.PANEL_BOTTOM - 8f
    private const val RAIL_RADIUS = 22f
    private const val RAIL_BORDER = 3f
    private const val RAIL_FILL_ALPHA = 0x2E
    private const val RAIL_BORDER_ALPHA = 0xB3

    // The border stroke straddles the rect, so the rects stop a stroke short of the column.
    private const val LEFT_CENTER_X = (RAIL_INSET + LEFT_RAIL_END - RAIL_BORDER) / 2f
    private const val RIGHT_CENTER_X = (RIGHT_RAIL_START + RAIL_BORDER + CARD_SIZE - RAIL_INSET) / 2f
    private const val TEXT_MAX_WIDTH = LEFT_RAIL_END - RAIL_BORDER - RAIL_INSET - 12f

    // Turn and score in the top half of the rail, tries and progress in the bottom half.
    private const val TOP_HEADER_Y = 46f
    private const val BADGE_CENTER_Y = 110f
    private const val BADGE_RADIUS = 42f
    private const val LABEL_LINE1_Y = 188f
    private const val LABEL_LINE2_Y = 216f
    private const val DIVIDER_Y = 244f
    private const val BOTTOM_HEADER_Y = 282f
    private const val BIG_NUMBER_Y = 334f
    private const val PROGRESS_Y = 370f

    private const val HEADER_SIZE = 17f
    private const val LABEL_SIZE = 25f
    private const val SCORE_SIZE = 32f
    private const val BIG_NUMBER_SIZE = 42f
    private const val MIN_TEXT_SIZE = 12f
    private const val RING_STROKE = 8f
    private const val RING_TEXT_WIDTH = 60f
    private const val DIVIDER_WIDTH = 56f
    private const val DOT_RADIUS = 10f
    private const val DOT_GAP = 8f
    private const val BAR_WIDTH = 84f
    private const val BAR_HEIGHT = 12f

    /** Below a pass, a score this high is "close": amber rather than red. */
    private const val PASSABLE_SCORE = 70

    private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_MUTED = 0xFF94A3B8.toInt()      // Slate 400
    private const val COLOR_ICON = 0xFF0B0F19.toInt()       // Card background, so the glyph reads as a cut-out
    private const val COLOR_TRACK = 0x33FFFFFF
    private const val COLOR_RAIL_BASE = 0xFF0E1626.toInt()
    private const val COLOR_SKY = 0xFF38BDF8.toInt()        // AI speaking
    private const val COLOR_GREEN = 0xFF22C55E.toInt()      // Your turn / passed
    private const val COLOR_GREEN_LIGHT = 0xFF4ADE80.toInt() // Listening to you
    private const val COLOR_AMBER = 0xFFF59E0B.toInt()      // Grading / close
    private const val COLOR_ORANGE = 0xFFF97316.toInt()     // Paused
    private const val COLOR_RED = 0xFFEF4444.toInt()        // Failed try
    private const val COLOR_SLATE = 0xFF64748B.toInt()      // Connecting / not graded yet
}
