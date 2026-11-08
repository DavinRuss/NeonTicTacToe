package com.neon.tictactoe

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import kotlin.math.min
import kotlin.random.Random

/** The game screen: score cards, board, status line and round controls. */
class GameActivity : Activity() {

    companion object {
        const val EXTRA_VS_COMPUTER = "vsComputer"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_P1_NAME = "p1Name"
        const val EXTRA_P2_NAME = "p2Name"
        const val EXTRA_P1_MARK = "p1Mark"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val handler = Handler(Looper.getMainLooper())
    private val cells = IntArray(9)

    private lateinit var board: BoardView
    private lateinit var status: TextView
    private lateinit var roundLabel: TextView
    private lateinit var againBtn: TextView
    private lateinit var card1: ScoreCard
    private lateinit var card2: ScoreCard
    private lateinit var drawCard: ScoreCard
    private lateinit var confetti: ConfettiView

    private var vsComputer = true
    private var difficulty = MEDIUM
    private var p1Mark = MARK_X
    private var p2Mark = MARK_O
    private var name1 = "Player 1"
    private var name2 = "Player 2"

    private var current = MARK_X
    private var round = 1
    private var score1 = 0
    private var score2 = 0
    private var draws = 0
    private var over = false

    private var exitDialog: Dialog? = null

    private val computerMove = Runnable {
        if (over || !isComputerTurn()) return@Runnable
        play(Ai.move(cells, p2Mark, difficulty))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vsComputer = intent.getBooleanExtra(EXTRA_VS_COMPUTER, true)
        difficulty = intent.getIntExtra(EXTRA_DIFFICULTY, MEDIUM)
        p1Mark = intent.getIntExtra(EXTRA_P1_MARK, MARK_X)
        p2Mark = otherMark(p1Mark)
        name1 = intent.getStringExtra(EXTRA_P1_NAME).orEmpty().ifBlank { if (vsComputer) "You" else "Player 1" }
        name2 = if (vsComputer) "Computer" else intent.getStringExtra(EXTRA_P2_NAME).orEmpty().ifBlank { "Player 2" }

        val root = FrameLayout(this)
        root.addView(AuroraBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(12), dpi(20), dpi(16))
        }
        val maxWidth = min(resources.displayMetrics.widthPixels, dpi(560))
        root.addView(content, FrameLayout.LayoutParams(maxWidth, MATCH, Gravity.CENTER_HORIZONTAL))
        setupEdgeToEdge(root, content)

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val menuBtn = glassButton(this, "‹  Menu").apply {
            textSize = 14f
            setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
            setOnClickListener { showExitDialog() }
        }
        top.addView(menuBtn, LinearLayout.LayoutParams(WRAP, WRAP))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        roundLabel = TextView(this).apply {
            setTextColor(Palette.TEXT_DIM)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        top.addView(roundLabel, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        // Score cards
        card1 = ScoreCard(this, p1Mark, name1)
        card2 = ScoreCard(this, p2Mark, name2)
        drawCard = ScoreCard(this, EMPTY, "Draws")
        val scores = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val gap = dpi(5)
        scores.addView(card1, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, gap, 0) })
        scores.addView(drawCard, LinearLayout.LayoutParams(0, WRAP, 0.72f).apply { setMargins(gap, 0, gap, 0) })
        scores.addView(card2, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(gap, 0, 0, 0) })
        content.addView(scores, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(14) })

        // Status line
        status = TextView(this).apply {
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minHeight = dpi(36)
        }
        content.addView(status, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(18); bottomMargin = dpi(10) })

        // Board
        board = BoardView(this)
        board.onCellTap = { onCellTapped(it) }
        content.addView(board, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Play again
        againBtn = primaryButton(this, "PLAY AGAIN", Palette.CORAL_A, Palette.CORAL_B).apply {
            visibility = View.INVISIBLE
            setOnClickListener {
                round++
                startRound()
            }
        }
        content.addView(againBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(12) })

        confetti = ConfettiView(this)
        root.addView(confetti, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                showExitDialog()
            }
        }

        startRound()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) showExitDialog() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        exitDialog?.dismiss()
        super.onDestroy()
    }

    // --- game flow --------------------------------------------------------------------------

    private fun isComputerTurn() = vsComputer && !over && current == p2Mark

    private fun startRound() {
        handler.removeCallbacksAndMessages(null)
        cells.fill(EMPTY)
        board.reset()
        over = false
        againBtn.animate().cancel()
        againBtn.visibility = View.INVISIBLE
        roundLabel.text = "ROUND $round"
        // The starting player alternates every round.
        current = if (round % 2 == 1) p1Mark else p2Mark
        updateTurnUi()
        maybeComputerMove()
    }

    private fun nameOf(mark: Int) = if (mark == p1Mark) name1 else name2

    private fun updateTurnUi() {
        card1.setActive(!over && current == p1Mark)
        card2.setActive(!over && current == p2Mark)
        if (isComputerTurn()) {
            setStatus("$name2 is thinking…", Palette.mark(current))
        } else {
            setStatus("${nameOf(current)}'s turn", Palette.mark(current))
        }
    }

    private fun setStatus(text: String, color: Int) {
        status.text = text
        status.setTextColor(color)
        status.alpha = 0f
        status.animate().alpha(1f).setDuration(220).start()
    }

    private fun maybeComputerMove() {
        board.inputEnabled = !isComputerTurn()
        if (!isComputerTurn()) return
        handler.postDelayed(computerMove, 550L + Random.nextLong(450L))
    }

    private fun onCellTapped(i: Int) {
        if (over || isComputerTurn() || cells[i] != EMPTY) return
        board.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        play(i)
    }

    private fun play(i: Int) {
        if (cells[i] != EMPTY) return
        cells[i] = current
        board.place(i, current)
        val line = Rules.winnerLine(cells)
        when {
            line != null -> endRound(line)
            Rules.isFull(cells) -> endRound(null)
            else -> {
                current = otherMark(current)
                updateTurnUi()
                maybeComputerMove()
            }
        }
    }

    private fun endRound(line: IntArray?) {
        over = true
        board.inputEnabled = false
        card1.setActive(false)
        card2.setActive(false)

        if (line != null) {
            val winner = cells[line[0]]
            board.showWin(line)
            if (winner == p1Mark) {
                score1++
                card1.setScore(score1)
            } else {
                score2++
                card2.setScore(score2)
            }
            val humanWon = !vsComputer || winner == p1Mark
            setStatus(if (humanWon) "${nameOf(winner)} wins! 🎉" else "${nameOf(winner)} wins!", Palette.mark(winner))
            board.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            )
            if (humanWon) handler.postDelayed({ confetti.burst() }, 400L)
        } else {
            draws++
            drawCard.setScore(draws)
            setStatus("It's a draw!", Color.WHITE)
        }

        againBtn.alpha = 0f
        againBtn.visibility = View.VISIBLE
        againBtn.animate().alpha(1f).setStartDelay(800L).setDuration(300L).start()
    }

    // --- exit dialog ------------------------------------------------------------------------

    private fun showExitDialog() {
        if (exitDialog?.isShowing == true) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24), dpi(24), dpi(24), dpi(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28f)
                setColor(0xFF1B2160.toInt())
                setStroke(dpi(1), 0x44FFFFFF)
            }
        }
        card.addView(TextView(this).apply {
            text = "Leave this game?"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        card.addView(TextView(this).apply {
            text = "Your scores will be lost."
            setTextColor(Palette.TEXT_DIM)
            textSize = 15f
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(8); bottomMargin = dpi(22) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(glassButton(this, "Stay").apply { setOnClickListener { dialog.dismiss() } },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dpi(6) })
        buttons.addView(primaryButton(this, "Leave", Palette.CORAL_A, Palette.CORAL_B).apply {
            textSize = 16f
            setPadding(dpi(20), dpi(14), dpi(20), dpi(14))
            setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dpi(6) })
        card.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(min(resources.displayMetrics.widthPixels - dpi(48), dpi(360)), WRAP)
        }
        exitDialog = dialog
        dialog.show()
    }
}
