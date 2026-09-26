package com.classicfoo.tetris.engine

import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineClearStagingTest {
    @Test
    fun `completed rows stay visible while clear is staged`() {
        val engine = GameEngine(seed = 101L)
        replaceState(engine, clearReadyState(engine.state, includeSurvivor = true))

        val staged = engine.dispatch(GameAction.HardDrop)

        assertTrue(staged.isClearing)
        assertEquals(GameStatus.RUNNING, staged.status)
        assertEquals(1L, staged.clearSequence)
        assertEquals(listOf(BOARD_HEIGHT - 1), staged.lastClearedRows)
        assertEquals(0, staged.lines)
        assertEquals(0L, staged.lastScoreDelta)
        assertEquals(BOARD_HEIGHT, staged.current.y)
        assertTrue(staged.board[BOARD_HEIGHT - 1].all { it != null })
        assertEquals(Tetromino.Z, staged.board[BOARD_HEIGHT - 2][0])
    }

    @Test
    fun `movement and drop inputs are ignored and only tick advances clearing`() {
        val engine = GameEngine(seed = 102L)
        replaceState(engine, clearReadyState(engine.state, includeSurvivor = true))
        val staged = engine.dispatch(GameAction.HardDrop)

        listOf(
            GameAction.MoveLeft,
            GameAction.MoveRight,
            GameAction.SoftDrop,
            GameAction.HardDrop,
            GameAction.RotateClockwise,
            GameAction.RotateCounterClockwise,
            GameAction.Hold,
            GameAction.Pause,
        ).forEach(engine::dispatch)

        assertEquals(staged, engine.state)

        val halfway = engine.dispatch(GameAction.Tick(GameEngine.CLEAR_DURATION_MS - 1))
        assertTrue(halfway.isClearing)
        assertEquals(GameEngine.CLEAR_DURATION_MS - 1, halfway.clearElapsedMs)
        assertTrue(halfway.board[BOARD_HEIGHT - 1].all { it != null })
        assertEquals(staged.score, halfway.score)
    }

    @Test
    fun `clear completion compacts rows scores and spawns once`() {
        val engine = GameEngine(seed = 103L)
        replaceState(engine, clearReadyState(engine.state, includeSurvivor = true))
        engine.dispatch(GameAction.HardDrop)

        val completed = engine.dispatch(GameAction.Tick(GameEngine.CLEAR_DURATION_MS))

        assertFalse(completed.isClearing)
        assertEquals(GameStatus.RUNNING, completed.status)
        assertEquals(1, completed.lines)
        assertEquals(100L, completed.lastScoreDelta)
        assertEquals(100L, completed.score)
        assertEquals(GameEvent.LINE_CLEAR, completed.lastEvent)
        assertEquals(listOf(BOARD_HEIGHT - 1), completed.lastClearedRows)
        assertEquals(Tetromino.T, completed.current.type)
        assertNotEquals(BOARD_HEIGHT, completed.current.y)
        assertTrue(completed.board[BOARD_HEIGHT - 1].count { it != null } == 1)
        assertEquals(Tetromino.Z, completed.board[BOARD_HEIGHT - 1][0])
    }

    @Test
    fun `game over is decided after the staged clear completes`() {
        val engine = GameEngine(seed = 104L)
        replaceState(engine, clearReadyState(engine.state, includeSurvivor = false, blockNextSpawn = true))

        val staged = engine.dispatch(GameAction.HardDrop)
        assertTrue(staged.isClearing)
        assertEquals(GameStatus.RUNNING, staged.status)

        val completed = engine.dispatch(GameAction.Tick(GameEngine.CLEAR_DURATION_MS))

        assertFalse(completed.isClearing)
        assertEquals(GameStatus.GAME_OVER, completed.status)
        assertEquals(GameEvent.GAME_OVER, completed.lastEvent)
        assertEquals(1, completed.lines)
        assertEquals(100L, completed.lastScoreDelta)
    }

    private fun clearReadyState(
        state: GameState,
        includeSurvivor: Boolean,
        blockNextSpawn: Boolean = false,
    ): GameState {
        val empty = List<Tetromino?>(BOARD_WIDTH) { null }
        val bottom = List(BOARD_WIDTH) { x -> if (x < 6) Tetromino.Z else null }
        val survivor = List(BOARD_WIDTH) { x -> if (includeSurvivor && x == 0) Tetromino.Z else null }
        val spawnBlock = List(BOARD_WIDTH) { x -> if (blockNextSpawn && x == 5) Tetromino.T else null }
        val board = List(BOARD_HEIGHT) { y ->
            when (y) {
                0 -> spawnBlock
                BOARD_HEIGHT - 2 -> survivor
                BOARD_HEIGHT - 1 -> bottom
                else -> empty
            }
        }
        val ids = BoardRules.emptyPieceIds()

        return state.copy(
            board = board,
            boardPieceIds = ids,
            current = ActivePiece(Tetromino.I, Rotation.SPAWN, x = 6, y = BOARD_HEIGHT - 2),
            next = List(PREVIEW_SIZE) { Tetromino.T },
            hold = null,
            holdUsed = false,
            status = GameStatus.RUNNING,
            isClearing = false,
            clearElapsedMs = 0L,
            lastEvent = GameEvent.NONE,
            lastLines = 0,
            lastClearedRows = emptyList(),
            lastScoreDelta = 0L,
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
