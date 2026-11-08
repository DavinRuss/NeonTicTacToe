package com.neon.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The game board. Draws a glass card, a glowing grid that draws itself in, animated X / O marks,
 * and an animated win line. All animation is time based, so there are no animator objects to leak.
 */
class BoardView(context: Context) : View(context) {

    /** Called with the tapped cell index (0..8) when the finger lifts on a cell. */
    var onCellTap: ((Int) -> Unit)? = null
    var inputEnabled = true

    private val marks = IntArray(9)
    private val markStart = LongArray(9)
    private val gridStart = SystemClock.uptimeMillis()
    private var winLine: IntArray? = null
    private var winStart = 0L
    private var oldMarks: IntArray? = null
    private var oldWin: IntArray? = null
    private var fadeStart = 0L
    private var pressedCell = -1

    private val stroke = Glyphs.newPaint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tmp = RectF()
    private var cardShader: Shader? = null

    private var side = 0f
    private var left = 0f
    private var top = 0f
    private var gx = 0f
    private var gy = 0f
    private var grid = 0f
    private var cell = 0f

    // --- public API -------------------------------------------------------------------------

    fun place(index: Int, mark: Int) {
        marks[index] = mark
        markStart[index] = SystemClock.uptimeMillis()
        invalidate()
    }

    fun showWin(line: IntArray) {
        winLine = line
        winStart = SystemClock.uptimeMillis() + 350L // let the last mark finish drawing first
        invalidate()
    }

    /** Clears the board; the old marks fade away. */
    fun reset() {
        if (marks.any { it != EMPTY }) {
            oldMarks = marks.copyOf()
            oldWin = winLine
            fadeStart = SystemClock.uptimeMillis()
        }
        marks.fill(EMPTY)
        winLine = null
        pressedCell = -1
        invalidate()
    }

    // --- layout -----------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        side = min(w, h).toFloat()
        left = (w - side) / 2f
        top = (h - side) / 2f
        val pad = side * 0.07f
        gx = left + pad
        gy = top + pad
        grid = side - 2f * pad
        cell = grid / 3f
        cardShader = LinearGradient(0f, top, 0f, top + side, 0x2EFFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP)
    }

    // --- drawing ----------------------------------------------------------------------------

    private fun ease(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

    private fun backOut(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun cx(i: Int) = gx + cell * (i % 3 + 0.5f)
    private fun cy(i: Int) = gy + cell * (i / 3 + 0.5f)

    override fun onDraw(canvas: Canvas) {
        if (side <= 0f) return
        val now = SystemClock.uptimeMillis()
        var animating = false

        // Glass card.
        rect.set(left, top, left + side, top + side)
        val radius = side * 0.08f
        fill.style = Paint.Style.FILL
        fill.shader = cardShader
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = context.dp(1.5f)
        fill.color = 0x40FFFFFF
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.style = Paint.Style.FILL

        // Pressed-cell highlight.
        if (inputEnabled && pressedCell in 0..8 && marks[pressedCell] == EMPTY) {
            val inset = cell * 0.06f
            tmp.set(
                gx + cell * (pressedCell % 3) + inset, gy + cell * (pressedCell / 3) + inset,
                gx + cell * (pressedCell % 3 + 1) - inset, gy + cell * (pressedCell / 3 + 1) - inset
            )
            fill.color = 0x22FFFFFF
            canvas.drawRoundRect(tmp, cell * 0.14f, cell * 0.14f, fill)
        }

        // Grid that draws itself in.
        val gp = ((now - gridStart) / 800f).coerceIn(0f, 1f)
        if (gp < 1f) animating = true
        val gridColor = 0xFFA8B8FF.toInt()
        val gridWidth = side * 0.014f
        for (k in 0..3) {
            val lp = ease(gp * 1.5f - k * 0.15f)
            if (lp <= 0f) continue
            if (k < 2) {
                val x = gx + cell * (k + 1)
                Glyphs.glowLine(canvas, stroke, gridColor, gridWidth, 0.85f, x, gy, x, gy + grid * lp)
            } else {
                val y = gy + cell * (k - 1)
                Glyphs.glowLine(canvas, stroke, gridColor, gridWidth, 0.85f, gx, y, gx + grid * lp, y)
            }
        }

        // Marks from the previous round fading out.
        val om = oldMarks
        if (om != null) {
            val t = (now - fadeStart) / 280f
            if (t >= 1f) {
                oldMarks = null
                oldWin = null
            } else {
                animating = true
                val a = 1f - t
                for (i in 0..8) if (om[i] != EMPTY) drawMarkAt(canvas, i, om[i], 1f, 1f - 0.15f * t, a)
                val ow = oldWin
                if (ow != null) drawWin(canvas, ow, Palette.mark(om[ow[0]]), 1f, a)
            }
        }

        // Current marks.
        val wl = winLine
        val winT = if (wl != null) now - winStart else -1L
        for (i in 0..8) {
            val m = marks[i]
            if (m == EMPTY) continue
            val t = ((now - markStart[i]) / 420f).coerceIn(0f, 1f)
            if (t < 1f) animating = true
            var scale = 0.7f + 0.3f * backOut(t)
            var alpha = 1f
            if (wl != null) {
                if (i in wl) {
                    if (winT > 0L) {
                        val k = (1f - winT / 2400f).coerceIn(0f, 1f)
                        scale *= 1f + 0.08f * k * sin(winT / 130f)
                    }
                } else {
                    alpha = 1f - 0.65f * (winT / 400f).coerceIn(0f, 1f)
                }
            }
            drawMarkAt(canvas, i, m, ease(t), scale, alpha)
        }

        if (wl != null) {
            drawWin(canvas, wl, Palette.mark(marks[wl[0]]), ease(winT / 500f), 1f)
            if (winT < 2600L) animating = true
        }

        if (animating) postInvalidateOnAnimation()
    }

    private fun drawMarkAt(c: Canvas, i: Int, mark: Int, progress: Float, scale: Float, alpha: Float) {
        Glyphs.drawMark(c, stroke, mark, cx(i), cy(i), cell * 0.25f * scale, progress, cell * 0.095f, alpha, tmp)
    }

    private fun drawWin(c: Canvas, line: IntArray, color: Int, progress: Float, alpha: Float) {
        if (progress <= 0f) return
        val ax = cx(line[0])
        val ay = cy(line[0])
        val bx = cx(line[2])
        val by = cy(line[2])
        val len = hypot(bx - ax, by - ay)
        val ux = (bx - ax) / len
        val uy = (by - ay) / len
        val ext = cell * 0.32f
        val x1 = ax - ux * ext
        val y1 = ay - uy * ext
        val x2 = bx + ux * ext
        val y2 = by + uy * ext
        val ex = x1 + (x2 - x1) * progress
        val ey = y1 + (y2 - y1) * progress
        Glyphs.glowLine(c, stroke, color, side * 0.026f, alpha, x1, y1, ex, ey)
        stroke.color = Color.WHITE
        stroke.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        stroke.strokeWidth = side * 0.010f
        c.drawLine(x1, y1, ex, ey, stroke)
    }

    // --- touch ------------------------------------------------------------------------------

    private fun cellAt(x: Float, y: Float): Int {
        if (cell <= 0f || x < gx || y < gy || x >= gx + grid || y >= gy + grid) return -1
        val col = ((x - gx) / cell).toInt().coerceIn(0, 2)
        val row = ((y - gy) / cell).toInt().coerceIn(0, 2)
        return row * 3 + col
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inputEnabled) return false
                pressedCell = cellAt(e.x, e.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val c = cellAt(e.x, e.y)
                if (c != pressedCell) {
                    pressedCell = c
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val c = cellAt(e.x, e.y)
                pressedCell = -1
                invalidate()
                if (c >= 0) {
                    performClick()
                    onCellTap?.invoke(c)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCell = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
