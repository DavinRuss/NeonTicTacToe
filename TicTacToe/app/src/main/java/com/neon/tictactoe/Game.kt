package com.neon.tictactoe

import kotlin.random.Random

/** Cell / mark values. */
const val EMPTY = 0
const val MARK_X = 1
const val MARK_O = 2

fun otherMark(m: Int) = if (m == MARK_X) MARK_O else MARK_X

object Rules {
    val LINES: Array<IntArray> = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
    )

    /** Returns the winning line (3 cell indexes) or null. */
    fun winnerLine(b: IntArray): IntArray? {
        for (l in LINES) {
            val v = b[l[0]]
            if (v != EMPTY && v == b[l[1]] && v == b[l[2]]) return l
        }
        return null
    }

    fun isFull(b: IntArray): Boolean = b.none { it == EMPTY }
}

/** Difficulty levels for the computer opponent. */
const val EASY = 0
const val MEDIUM = 1
const val HARD = 2

object Ai {
    /** Picks a cell index for [me]. The board must have at least one empty cell. */
    fun move(board: IntArray, me: Int, level: Int, rnd: Random = Random.Default): Int {
        val empties = (0..8).filter { board[it] == EMPTY }
        require(empties.isNotEmpty()) { "No moves available" }
        return when (level) {
            EASY -> empties.random(rnd)
            MEDIUM -> mediumMove(board, me, empties, rnd)
            else -> hardMove(board, me, empties, rnd)
        }
    }

    private fun mediumMove(board: IntArray, me: Int, empties: List<Int>, rnd: Random): Int {
        // Sometimes plays carelessly so the player can win.
        if (rnd.nextInt(100) < 30) return empties.random(rnd)
        findLineCompletion(board, me)?.let { return it }
        findLineCompletion(board, otherMark(me))?.let { return it }
        if (board[4] == EMPTY) return 4
        return empties.random(rnd)
    }

    /** A cell that completes a line for [mark], or null. */
    private fun findLineCompletion(board: IntArray, mark: Int): Int? {
        for (l in Rules.LINES) {
            val vals = intArrayOf(board[l[0]], board[l[1]], board[l[2]])
            if (vals.count { it == mark } == 2 && vals.count { it == EMPTY } == 1) {
                return l[vals.indexOf(EMPTY)]
            }
        }
        return null
    }

    private fun hardMove(board: IntArray, me: Int, empties: List<Int>, rnd: Random): Int {
        val b = board.copyOf()
        var best = Int.MIN_VALUE
        val bestMoves = ArrayList<Int>()
        for (i in empties) {
            b[i] = me
            val score = minimax(b, otherMark(me), me, 1, -100, 100)
            b[i] = EMPTY
            if (score > best) {
                best = score
                bestMoves.clear()
                bestMoves.add(i)
            } else if (score == best) {
                bestMoves.add(i)
            }
        }
        return bestMoves.random(rnd)
    }

    private fun minimax(b: IntArray, turn: Int, me: Int, depth: Int, alpha0: Int, beta0: Int): Int {
        val line = Rules.winnerLine(b)
        if (line != null) return if (b[line[0]] == me) 10 - depth else depth - 10
        if (Rules.isFull(b)) return 0
        var alpha = alpha0
        var beta = beta0
        if (turn == me) {
            var best = -100
            for (i in 0..8) {
                if (b[i] != EMPTY) continue
                b[i] = turn
                val s = minimax(b, otherMark(turn), me, depth + 1, alpha, beta)
                b[i] = EMPTY
                if (s > best) best = s
                if (best > alpha) alpha = best
                if (beta <= alpha) break
            }
            return best
        } else {
            var best = 100
            for (i in 0..8) {
                if (b[i] != EMPTY) continue
                b[i] = turn
                val s = minimax(b, otherMark(turn), me, depth + 1, alpha, beta)
                b[i] = EMPTY
                if (s < best) best = s
                if (best < beta) beta = best
                if (beta <= alpha) break
            }
            return best
        }
    }
}
