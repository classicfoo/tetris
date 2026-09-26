package com.classicfoo.tetris.engine

import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEnginePieceOwnershipTest {
    @Test
    fun `adjacent same-type tetrominos retain different owner ids`() {
        val engine = GameEngine(seed = 71L)
        replaceState(
            engine,
            engine.state.copy(
                board = BoardRules.emptyBoard(),
                boardPieceIds = BoardRules.emptyPieceIds(),
                current = ActivePiece(Tetromino.O, Rotation.SPAWN, x = 0, y = 0),
                next = List(PREVIEW_SIZE) { Tetromino.O },
                status = GameStatus.RUNNING,
            ),
        )

        engine.dispatch(GameAction.HardDrop)
        val first = engine.state
        assertEquals(setOf(1L), first.boardPieceIds.flatten().filterNotNull().toSet())

        replaceState(
            engine,
            first.copy(
                current = ActivePiece(Tetromino.O, Rotation.SPAWN, x = 2, y = 0),
                next = List(PREVIEW_SIZE) { Tetromino.O },
            ),
        )
        val second = engine.dispatch(GameAction.HardDrop)
        val lockedIds = second.boardPieceIds.flatten().filterNotNull()

        assertEquals(setOf(1L, 2L), lockedIds.toSet())
        assertEquals(1L, second.boardPieceIds[BOARD_HEIGHT - 1][2])
        assertEquals(2L, second.boardPieceIds[BOARD_HEIGHT - 1][3])
        assertNotEquals(
            second.boardPieceIds[BOARD_HEIGHT - 1][2],
            second.boardPieceIds[BOARD_HEIGHT - 1][3],
        )
        assertEquals(4, lockedIds.count { it == 1L })
        assertEquals(4, lockedIds.count { it == 2L })
    }

    @Test
    fun `restart clears ownership grid and future ids remain fresh`() {
        val engine = GameEngine(seed = 72L)
        replaceState(
            engine,
            engine.state.copy(
                board = BoardRules.emptyBoard(),
                boardPieceIds = BoardRules.emptyPieceIds(),
                current = ActivePiece(Tetromino.O, Rotation.SPAWN, x = 0, y = 0),
                next = List(PREVIEW_SIZE) { Tetromino.O },
            ),
        )

        engine.dispatch(GameAction.HardDrop)
        val firstId = engine.state.boardPieceIds.flatten().filterNotNull().toSet().single()

        val restarted = engine.dispatch(GameAction.Restart)
        assertTrue(restarted.boardPieceIds.all { row -> row.all { it == null } })

        replaceState(
            engine,
            restarted.copy(
                current = ActivePiece(Tetromino.O, Rotation.SPAWN, x = 0, y = 0),
                next = List(PREVIEW_SIZE) { Tetromino.O },
            ),
        )
        val afterRestart = engine.dispatch(GameAction.HardDrop)
        val secondId = afterRestart.boardPieceIds.flatten().filterNotNull().toSet().single()

        assertTrue(secondId > firstId)
    }

    private fun replaceState(engine: GameEngine, state: GameState) {
        stateField().set(engine, state)
    }

    private fun stateField(): Field = GameEngine::class.java.getDeclaredField("state").apply {
        isAccessible = true
    }
}
