package com.neon.tictactoe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Palette + small helpers
// ---------------------------------------------------------------------------------------------

object Palette {
    val X = 0xFFFF6B81.toInt()
    val O = 0xFFFFD166.toInt()
    val ACCENT_A = 0xFF7C4DFF.toInt()
    val ACCENT_B = 0xFF3D8BFF.toInt()
    val CORAL_A = 0xFFFF6B81.toInt()
    val CORAL_B = 0xFFFF8E53.toInt()
    val BG_TOP = 0xFF121A52.toInt()
    val BG_MID = 0xFF2A1B6B.toInt()
    val BG_BOTTOM = 0xFF0A0F2E.toInt()
    val GLASS = 0x1FFFFFFF
    val GLASS_STROKE = 0x33FFFFFF
    val TEXT_DIM = 0xB3FFFFFF.toInt()

    fun mark(m: Int) = if (m == MARK_X) X else O
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

fun glassDrawable(ctx: Context, radiusDp: Float, fill: Int = Palette.GLASS, stroke: Int = Palette.GLASS_STROKE): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = ctx.dp(radiusDp)
        setColor(fill)
        setStroke(ctx.dpi(1), stroke)
    }

fun gradientDrawable(ctx: Context, radiusDp: Float, a: Int, b: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = ctx.dp(radiusDp)
    }

/** Slight "squish" while a view is pressed. Never consumes the touch. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressScale() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

fun primaryButton(ctx: Context, text: String, a: Int = Palette.ACCENT_A, b: Int = Palette.ACCENT_B): TextView =
    TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.04f
        gravity = Gravity.CENTER
        setPadding(ctx.dpi(24), ctx.dpi(16), ctx.dpi(24), ctx.dpi(16))
        background = gradientDrawable(ctx, 18f, a, b)
        isClickable = true
        pressScale()
    }

fun glassButton(ctx: Context, text: String): TextView =
    TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(ctx.dpi(20), ctx.dpi(14), ctx.dpi(20), ctx.dpi(14))
        background = glassDrawable(ctx, 18f)
        isClickable = true
        pressScale()
    }

/**
 * Draws edge-to-edge (required on Android 15+) and moves [target]'s padding so content stays
 * clear of the status bar, navigation bar and display cutout.
 */
@Suppress("DEPRECATION")
fun Activity.setupEdgeToEdge(root: View, target: View) {
    val l = target.paddingLeft
    val t = target.paddingTop
    val r = target.paddingRight
    val b = target.paddingBottom
    if (Build.VERSION.SDK_INT >= 30) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    root.setOnApplyWindowInsetsListener { _, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            target.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
        } else {
            target.setPadding(
                l + insets.systemWindowInsetLeft, t + insets.systemWindowInsetTop,
                r + insets.systemWindowInsetRight, b + insets.systemWindowInsetBottom
            )
        }
        insets
    }
}

// ---------------------------------------------------------------------------------------------
// Glow-stroked X / O glyphs shared by the board, tiles, badges and the logo
// ---------------------------------------------------------------------------------------------

object Glyphs {
    // (width multiplier, alpha multiplier): soft halo -> tight halo -> core stroke.
    private val LAYERS = arrayOf(floatArrayOf(3.2f, 0.09f), floatArrayOf(2.1f, 0.20f), floatArrayOf(1f, 1f))

    fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun glowLine(c: Canvas, p: Paint, color: Int, width: Float, alpha: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        for (l in LAYERS) {
            p.color = color
            p.alpha = (255f * alpha * l[1]).toInt().coerceIn(0, 255)
            p.strokeWidth = width * l[0]
            c.drawLine(x1, y1, x2, y2, p)
        }
    }

    private fun glowArc(c: Canvas, p: Paint, color: Int, width: Float, alpha: Float, rect: RectF, start: Float, sweep: Float) {
        for (l in LAYERS) {
            p.color = color
            p.alpha = (255f * alpha * l[1]).toInt().coerceIn(0, 255)
            p.strokeWidth = width * l[0]
            c.drawArc(rect, start, sweep, false, p)
        }
    }

    /** [progress] 0..1 draws the glyph stroke by stroke. [rect] is scratch space. */
    fun drawMark(c: Canvas, p: Paint, mark: Int, cx: Float, cy: Float, r: Float, progress: Float, width: Float, alpha: Float, rect: RectF) {
        if (progress <= 0f || alpha <= 0f) return
        val color = Palette.mark(mark)
        if (mark == MARK_X) {
            val p1 = (progress / 0.5f).coerceIn(0f, 1f)
            val p2 = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (p1 > 0f) glowLine(c, p, color, width, alpha, cx - r, cy - r, cx - r + 2f * r * p1, cy - r + 2f * r * p1)
            if (p2 > 0f) glowLine(c, p, color, width, alpha, cx + r, cy - r, cx + r - 2f * r * p2, cy - r + 2f * r * p2)
        } else {
            rect.set(cx - r, cy - r, cx + r, cy + r)
            glowArc(c, p, color, width, alpha, rect, -90f, 360f * progress)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Views
// ---------------------------------------------------------------------------------------------

/** Slowly drifting colour blobs over a deep gradient. */
class AuroraBackground(context: Context) : View(context) {
    private class Blob(val color: Int, val periodMs: Long, val phase: Float) {
        var shader: Shader? = null
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgShader: Shader? = null
    private var radius = 0f
    private val blobs = arrayOf(
        Blob(0xFF7C4DFF.toInt(), 23000L, 0f),
        Blob(0xFFFF5C93.toInt(), 31000L, 2.1f),
        Blob(0xFF2BB5FF.toInt(), 27000L, 4.2f)
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgShader = LinearGradient(
            0f, 0f, w * 0.4f, h.toFloat(),
            intArrayOf(Palette.BG_TOP, Palette.BG_MID, Palette.BG_BOTTOM), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        radius = maxOf(w, h) * 0.55f
        for (b in blobs) {
            b.shader = RadialGradient(0f, 0f, radius, (b.color and 0x00FFFFFF) or 0x55000000, b.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w, h, paint)
        val now = SystemClock.uptimeMillis()
        for (b in blobs) {
            val t = (now % b.periodMs).toFloat() / b.periodMs * (2f * PI.toFloat())
            val cx = w * (0.5f + 0.4f * sin(t + b.phase))
            val cy = h * (0.5f + 0.35f * cos(t * 1f + b.phase * 2f))
            paint.shader = b.shader
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radius, paint)
            canvas.restore()
        }
        postInvalidateOnAnimation()
    }
}

/** Falling confetti. Touches pass straight through to the views below. */
class ConfettiView(context: Context) : View(context) {
    private class P(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        var rot: Float, val vr: Float, val w: Float, val h: Float, val color: Int, val phase: Float
    )

    private val ps = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = 0L
    private val colors = intArrayOf(
        Palette.X, Palette.O, 0xFF4DD0E1.toInt(), 0xFFB388FF.toInt(), 0xFF69F0AE.toInt(), Color.WHITE
    )

    fun burst() {
        if (width == 0 || height == 0) return
        val d = resources.displayMetrics.density
        repeat(110) {
            ps.add(
                P(
                    x = Random.nextFloat() * width,
                    y = -Random.nextFloat() * height * 0.35f - 20f * d,
                    vx = (Random.nextFloat() - 0.5f) * 120f * d,
                    vy = (160f + Random.nextFloat() * 280f) * d,
                    rot = Random.nextFloat() * 360f,
                    vr = (Random.nextFloat() - 0.5f) * 720f,
                    w = (6f + Random.nextFloat() * 6f) * d,
                    h = (3f + Random.nextFloat() * 4f) * d,
                    color = colors[Random.nextInt(colors.size)],
                    phase = Random.nextFloat() * 6.28f
                )
            )
        }
        last = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (ps.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - last) / 1000f).coerceIn(0f, 0.05f)
        last = now
        val d = resources.displayMetrics.density
        val it = ps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += 60f * d * dt
            p.x += (p.vx + sin(now / 300f + p.phase) * 40f * d) * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
            if (p.y > height + 40f * d) {
                it.remove()
            } else {
                paint.color = p.color
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rot)
                canvas.drawRect(-p.w / 2f, -p.h / 2f, p.w / 2f, p.h / 2f, paint)
                canvas.restore()
            }
        }
        if (ps.isNotEmpty()) postInvalidateOnAnimation()
    }
}

/** App-icon style logo shown on the setup screen. */
class LogoView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Glyphs.newPaint()
    private val rect = RectF()
    private val tmp = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val left = (width - s) / 2f
        val top = (height - s) / 2f
        rect.set(left, top, left + s, top + s)
        fill.shader = LinearGradient(left, top, left + s, top + s, 0xFF2E7BFF.toInt(), 0xFF5B2BE0.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * 0.24f, s * 0.24f, fill)
        fill.shader = null

        val pad = s * 0.19f
        val g = s - 2f * pad
        val cell = g / 3f
        val gx = left + pad
        val gy = top + pad
        val lineColor = 0xFFC7D2FF.toInt()
        for (k in 1..2) {
            Glyphs.glowLine(canvas, stroke, lineColor, s * 0.018f, 0.7f, gx + cell * k, gy, gx + cell * k, gy + g)
            Glyphs.glowLine(canvas, stroke, lineColor, s * 0.018f, 0.7f, gx, gy + cell * k, gx + g, gy + cell * k)
        }
        // Same pattern as the launcher icon.
        val pattern = intArrayOf(MARK_X, EMPTY, MARK_O, EMPTY, MARK_O, EMPTY, MARK_X, EMPTY, MARK_X)
        for (i in 0..8) {
            val m = pattern[i]
            if (m == EMPTY) continue
            Glyphs.drawMark(
                canvas, stroke, m, gx + cell * (i % 3 + 0.5f), gy + cell * (i / 3 + 0.5f),
                cell * 0.24f, 1f, cell * 0.1f, 1f, tmp
            )
        }
    }
}

/** Pill selector with a gradient highlight on the selected option. */
class Segmented(
    context: Context,
    private val labels: List<String>,
    selected: Int,
    private val onChange: (Int) -> Unit
) : LinearLayout(context) {
    var index = selected
        private set
    private val items = ArrayList<TextView>()

    init {
        orientation = HORIZONTAL
        background = glassDrawable(context, 18f)
        setPadding(context.dpi(4), context.dpi(4), context.dpi(4), context.dpi(4))
        labels.forEachIndexed { i, s ->
            val tv = TextView(context).apply {
                text = s
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, context.dpi(12), 0, context.dpi(12))
                setOnClickListener { select(i) }
            }
            addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            items.add(tv)
        }
        refresh()
    }

    fun select(i: Int) {
        if (i == index) return
        index = i
        refresh()
        onChange(i)
    }

    private fun refresh() {
        items.forEachIndexed { i, tv ->
            if (i == index) {
                tv.background = gradientDrawable(context, 14f, Palette.ACCENT_A, Palette.ACCENT_B)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.background = null
                tv.setTextColor(Palette.TEXT_DIM)
            }
        }
    }
}

/** Selectable tile showing an X or O. */
class SymbolTile(context: Context, private val mark: Int) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Glyphs.newPaint()
    private val rect = RectF()
    private val tmp = RectF()

    var chosen = false
        set(v) {
            field = v
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = context.dp(if (chosen) 2.5f else 1f)
        rect.set(sw, sw, w - sw, h - sw)
        val color = Palette.mark(mark)
        fill.style = Paint.Style.FILL
        fill.color = if (chosen) (color and 0x00FFFFFF) or 0x30000000 else Palette.GLASS
        canvas.drawRoundRect(rect, context.dp(20f), context.dp(20f), fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = sw
        fill.color = if (chosen) color else Palette.GLASS_STROKE
        canvas.drawRoundRect(rect, context.dp(20f), context.dp(20f), fill)
        val r = min(w, h) * 0.22f
        Glyphs.drawMark(canvas, stroke, mark, w / 2f, h / 2f, r, 1f, r * 0.42f, if (chosen) 1f else 0.55f, tmp)
    }
}

/** Small glyph used on score cards. */
class MarkBadge(context: Context, private val mark: Int) : View(context) {
    private val stroke = Glyphs.newPaint()
    private val tmp = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) * 0.26f
        Glyphs.drawMark(canvas, stroke, mark, width / 2f, height / 2f, r, 1f, r * 0.45f, 1f, tmp)
    }
}

/** Score tile: badge, name and score. Highlights while it is that player's turn. */
class ScoreCard(context: Context, private val mark: Int, name: String) : LinearLayout(context) {
    private val bg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dp(18f)
    }
    private val scoreTv: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dpi(8), context.dpi(10), context.dpi(8), context.dpi(10))
        background = bg

        if (mark != EMPTY) {
            addView(MarkBadge(context, mark), LinearLayout.LayoutParams(context.dpi(34), context.dpi(34)))
        }
        addView(TextView(context).apply {
            text = name
            setTextColor(Palette.TEXT_DIM)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scoreTv = TextView(context).apply {
            text = "0"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        addView(scoreTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setActive(false)
    }

    fun setScore(n: Int) {
        scoreTv.text = n.toString()
        scoreTv.scaleX = 1.5f
        scoreTv.scaleY = 1.5f
        scoreTv.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
    }

    fun setActive(active: Boolean) {
        val accent = if (mark == EMPTY) 0xFFFFFFFF.toInt() else Palette.mark(mark)
        bg.setColor(if (active) (accent and 0x00FFFFFF) or 0x28000000 else Palette.GLASS)
        bg.setStroke(context.dpi(if (active) 2 else 1), if (active) accent else Palette.GLASS_STROKE)
        animate().scaleX(if (active) 1.05f else 1f).scaleY(if (active) 1.05f else 1f).alpha(if (active) 1f else 0.8f).setDuration(200).start()
    }
}
