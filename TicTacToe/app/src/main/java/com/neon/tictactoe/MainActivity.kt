package com.neon.tictactoe

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/** Setup screen: opponent, difficulty, names and symbol. */
class MainActivity : Activity() {

    private var vsComputer = true
    private var difficulty = MEDIUM
    private var p1Symbol = MARK_X

    private var logoAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        vsComputer = prefs.getBoolean("vsComputer", true)
        difficulty = prefs.getInt("difficulty", MEDIUM)
        p1Symbol = prefs.getInt("p1Symbol", MARK_X)

        val root = FrameLayout(this)
        root.addView(AuroraBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpi(24), dpi(16), dpi(24), dpi(24))
        }
        val maxWidth = min(resources.displayMetrics.widthPixels, dpi(520))
        scroll.addView(content, FrameLayout.LayoutParams(maxWidth, WRAP, Gravity.CENTER_HORIZONTAL))
        setupEdgeToEdge(root, scroll)

        // Logo + title
        val logo = LogoView(this)
        content.addView(logo, LinearLayout.LayoutParams(dpi(112), dpi(112)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        logoAnimator = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -dp(6f)).apply {
            duration = 2200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val title = TextView(this).apply {
            text = "TIC TAC TOE"
            textSize = 34f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dpi(16), 0, 0)
            addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                val w = (r - l).toFloat()
                if (w > 0f && (v as TextView).paint.shader == null) {
                    v.paint.shader = LinearGradient(0f, 0f, w, 0f, Palette.X, Palette.O, Shader.TileMode.CLAMP)
                    v.invalidate()
                }
            }
        }
        content.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(TextView(this).apply {
            text = "Pick your battle"
            setTextColor(Palette.TEXT_DIM)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpi(4), 0, dpi(8))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        // Opponent
        content.addView(sectionLabel("OPPONENT"), LinearLayout.LayoutParams(MATCH, WRAP))
        val diffLabel = sectionLabel("DIFFICULTY")
        val diffSeg = Segmented(this, listOf("Easy", "Medium", "Hard"), difficulty) { difficulty = it }
        val p2Edit = nameField("Player 2 name", EditorInfo.IME_ACTION_DONE)
        val symbolLabel = sectionLabel("YOUR SYMBOL")
        val namesLabel = sectionLabel("PLAYERS")
        val p1Edit = nameField("Your name", EditorInfo.IME_ACTION_NEXT)
        p1Edit.setText(prefs.getString("p1", ""))
        p2Edit.setText(prefs.getString("p2", ""))

        fun applyMode() {
            val vis = if (vsComputer) View.VISIBLE else View.GONE
            diffLabel.visibility = vis
            diffSeg.visibility = vis
            p2Edit.visibility = if (vsComputer) View.GONE else View.VISIBLE
            p1Edit.hint = if (vsComputer) "Your name" else "Player 1 name"
            symbolLabel.text = if (vsComputer) "YOUR SYMBOL" else "PLAYER 1 SYMBOL"
            namesLabel.text = if (vsComputer) "NAME" else "PLAYERS"
        }

        val modeSeg = Segmented(this, listOf("vs Computer", "vs Friend"), if (vsComputer) 0 else 1) {
            vsComputer = it == 0
            applyMode()
        }
        content.addView(modeSeg, LinearLayout.LayoutParams(MATCH, WRAP))

        content.addView(diffLabel, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(diffSeg, LinearLayout.LayoutParams(MATCH, WRAP))

        // Names
        content.addView(namesLabel, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(p1Edit, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(p2Edit, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(10) })

        // Symbol
        content.addView(symbolLabel, LinearLayout.LayoutParams(MATCH, WRAP))
        val tileX = SymbolTile(this, MARK_X)
        val tileO = SymbolTile(this, MARK_O)
        fun refreshTiles() {
            tileX.chosen = p1Symbol == MARK_X
            tileO.chosen = p1Symbol == MARK_O
        }
        tileX.setOnClickListener { p1Symbol = MARK_X; refreshTiles() }
        tileO.setOnClickListener { p1Symbol = MARK_O; refreshTiles() }
        tileX.pressScale()
        tileO.pressScale()
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tiles.addView(tileX, LinearLayout.LayoutParams(0, dpi(84), 1f).apply { rightMargin = dpi(6) })
        tiles.addView(tileO, LinearLayout.LayoutParams(0, dpi(84), 1f).apply { leftMargin = dpi(6) })
        content.addView(tiles, LinearLayout.LayoutParams(MATCH, WRAP))

        // Start
        val start = primaryButton(this, "START GAME", Palette.CORAL_A, Palette.CORAL_B)
        start.setOnClickListener {
            val n1 = p1Edit.text.toString().trim()
            val n2 = p2Edit.text.toString().trim()
            prefs.edit()
                .putBoolean("vsComputer", vsComputer).putInt("difficulty", difficulty)
                .putInt("p1Symbol", p1Symbol).putString("p1", n1).putString("p2", n2).apply()
            startActivity(Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_VS_COMPUTER, vsComputer)
                putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty)
                putExtra(GameActivity.EXTRA_P1_NAME, n1)
                putExtra(GameActivity.EXTRA_P2_NAME, n2)
                putExtra(GameActivity.EXTRA_P1_MARK, p1Symbol)
            })
        }
        content.addView(start, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(28) })

        applyMode()
        refreshTiles()
        setContentView(root)
    }

    override fun onDestroy() {
        logoAnimator?.cancel()
        super.onDestroy()
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0x99FFFFFF.toInt())
        textSize = 12f
        letterSpacing = 0.14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dpi(4), dpi(20), 0, dpi(8))
    }

    private fun nameField(hintText: String, ime: Int) = EditText(this).apply {
        hint = hintText
        setHintTextColor(0x80FFFFFF.toInt())
        setTextColor(Color.WHITE)
        textSize = 16f
        background = glassDrawable(this@MainActivity, 18f)
        setPadding(dpi(18), dpi(14), dpi(18), dpi(14))
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = ime
        filters = arrayOf(InputFilter.LengthFilter(14))
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
