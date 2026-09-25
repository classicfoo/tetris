package com.classicfoo.tetris.engine

import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineClearSequenceTest {
    @Test
    fun `clear sequence increments only for clears and survives restart`() {
        val engine = GameEngine(seed = 41L)

        assertEquals(0L, engine.state.clearSequence)
        engine.dispatch(GameAction.MoveLeft)
        engine.dispatch(GameAction.Tick(16L))
        assertEquals(0L, engine.state.clearSequence)

        engine.dispatch(GameAction.HardDrop)
        assertEquals(0L, engine.state.clearSequence)
        assertTrue(engine.state.lastClearedRows.isEmpty())

        replaceState(engine, clearingState(engine.state))
        val firstClear = engine.dispatch(GameAction.HardDrop)

        assertEquals(1L, firstClear.clearSequence)
        assertEquals(listOf(BOARD_HEIGHT - 1), firstClear.lastClearedRows)
        assertEquals(1, firstClear.lastLines)

        val restarted = engine.dispatch(GameAction.Restart)
        assertEquals(1L, restarted.clearSequence)
        assertTrue(restarted.lastClearedRows.isEmpty())

        replaceState(engine, clearingState(engine.state))
        val secondClear = engine.dispatch(GameAction.HardDrop)

        assertEquals(2L, secondClear.clearSequence)
        assertEquals(listOf(BOARD_HEIGHT - 1), secondClear.lastClearedRows)
    }

    private fun clearingState(state: GameState): GameState {
        val bottom = List<Tetromino?>(BOARD_WIDTH) { column ->
            if (column < BOARD_WIDTH - 4) Tetromino.Z else null
        }
        val marker = List<Tetromino?>(BOARD_WIDTH) { column ->
            if (column == 0) Tetromino.T else null
        }
        val board = List(BOARD_HEIGHT) { row ->
            when (row) {
                0 -> marker
                BOARD_HEIGHT - 1 -> bottom
                else -> List(BOARD_WIDTH) { null }
            }
        }
        return state.copy(
            board = board,
            current = ActivePiece(Tetromino.I, Rotation.SPAWN, BOARD_WIDTH - 4, BOARD_HEIGHT - 2),
            status = GameStatus.RUNNING,
            lastEvent = GameEvent.NONE,
            lastLines = 0,
            lastClearedRows = emptyList(),
            perfectClear = false,
        )
    }

    private fun replaceState(engine: GameEngine, state: GameState) {
        stateField().set(engine, state)
    }

    private fun stateField(): Field = GameEngine::class.java.getDeclaredField("state").apply {
        isAccessible = true
    }
}
