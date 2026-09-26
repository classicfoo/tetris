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

    @Test
    fun `ownership ids move with surviving rows and disappear with cleared rows`() {
        val full = List<Tetromino?>(BOARD_WIDTH) { Tetromino.I }
        val board = List(BOARD_HEIGHT) { row ->
            when (row) {
                BOARD_HEIGHT - 3 -> List(BOARD_WIDTH) { column ->
                    if (column == 0) Tetromino.J else null
                }
                BOARD_HEIGHT - 2 -> List(BOARD_WIDTH) { column ->
                    if (column == 0) Tetromino.S else null
                }
                BOARD_HEIGHT - 1 -> full
                else -> List(BOARD_WIDTH) { null }
            }
        }
        val pieceIds = List(BOARD_HEIGHT) { row ->
            when (row) {
                BOARD_HEIGHT - 3 -> List(BOARD_WIDTH) { column -> if (column == 0) 41L else null }
                BOARD_HEIGHT - 2 -> List(BOARD_WIDTH) { column -> if (column == 0) 42L else null }
                BOARD_HEIGHT - 1 -> List(BOARD_WIDTH) { 900L }
                else -> List(BOARD_WIDTH) { null }
            }
        }

        val result = BoardRules.clearLinesWithPieceIds(board, pieceIds)

        assertEquals(1, result.count)
        assertEquals(Tetromino.J, result.board[BOARD_HEIGHT - 2][0])
        assertEquals(41L, result.boardPieceIds[BOARD_HEIGHT - 2][0])
        assertEquals(Tetromino.S, result.board[BOARD_HEIGHT - 1][0])
        assertEquals(42L, result.boardPieceIds[BOARD_HEIGHT - 1][0])
        assertTrue(result.boardPieceIds.flatten().none { it == 900L })
    }
}
