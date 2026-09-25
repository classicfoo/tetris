package com.classicfoo.tetris.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRulesTest {
    @Test
    fun `detailed line clearing reports original row indices in order`() {
        val full = List<Tetromino?>(BOARD_WIDTH) { Tetromino.I }
        val board = List(BOARD_HEIGHT) { row ->
            when (row) {
                0, 5, BOARD_HEIGHT - 1 -> full
                else -> List(BOARD_WIDTH) { null }
            }
        }

        val detailed = BoardRules.clearLinesWithRows(board)

        assertEquals(listOf(0, 5, BOARD_HEIGHT - 1), detailed.clearedRows)
        assertEquals(3, detailed.count)
        assertTrue(detailed.board.all { row -> row.all { it == null } })
    }

    @Test
    fun `legacy line clearing pair remains equivalent to detailed result`() {
        val full = List<Tetromino?>(BOARD_WIDTH) { Tetromino.Z }
        val board = List(BOARD_HEIGHT) { row ->
            if (row == BOARD_HEIGHT - 1) full else List(BOARD_WIDTH) { null }
        }

        val detailed = BoardRules.clearLinesWithRows(board)
        val legacy = BoardRules.clearLines(board)

        assertEquals(detailed.board, legacy.first)
        assertEquals(detailed.count, legacy.second)
    }
}
