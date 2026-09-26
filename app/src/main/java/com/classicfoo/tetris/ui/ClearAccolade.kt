package com.classicfoo.tetris.ui

/**
 * Short, readable feedback for a line-clear event.
 *
 * The fallback deliberately remains celebratory but neutral so malformed or future
 * line counts never leak an empty label into the HUD.
 */
enum class ClearAccolade(val label: String) {
    SINGLE("SINGLE!"),
    DOUBLE("DOUBLE!"),
    TRIPLE("TRIPLE!"),
    TETRIS("TETRIS!"),
    CLEAR("CLEAR!");

    companion object {
        fun forLines(linesCleared: Int): ClearAccolade = when (linesCleared) {
            1 -> SINGLE
            2 -> DOUBLE
            3 -> TRIPLE
            4 -> TETRIS
            else -> CLEAR
        }
    }
}
