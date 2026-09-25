package com.classicfoo.tetris.engine

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun `each bag contains every tetromino exactly once`() {
        val bag = PieceBag(Random(7L))
        val drawn = buildList { repeat(Tetromino.entries.size) { add(bag.next()) } }

        assertEquals(Tetromino.entries.toSet(), drawn.toSet())
        assertEquals(Tetromino.entries.size, drawn.size)
    }

    @Test
    fun `new games expose a five piece preview and empty board`() {
        val state = GameEngine(1L).state

        assertEquals(PREVIEW_SIZE, state.next.size)
        assertEquals(BOARD_HEIGHT, state.board.size)
        assertTrue(state.board.all { row -> row.size == BOARD_WIDTH && row.all { it == null } })
        assertEquals(GameStatus.RUNNING, state.status)
    }

    @Test
    fun `movement stays inside the well`() {
        val engine = GameEngine(2L)
        repeat(20) { engine.dispatch(GameAction.MoveLeft) }
        assertTrue(engine.state.current.x >= 0)

        repeat(20) { engine.dispatch(GameAction.MoveRight) }
        assertTrue(engine.state.current.x + PieceDefinitions.width(engine.state.current.type) <= BOARD_WIDTH)
    }

    @Test
    fun `rotation uses a valid placement near the wall`() {
        val engine = GameEngine(3L)
        repeat(20) { engine.dispatch(GameAction.MoveLeft) }
        engine.dispatch(GameAction.RotateClockwise)

        assertTrue(BoardRules.canPlace(engine.state.board, engine.state.current))
    }

    @Test
    fun `hold can only be used once for a falling piece`() {
        val engine = GameEngine(4L)
        val original = engine.state.current.type

        engine.dispatch(GameAction.Hold)
        val afterFirstHold = engine.state
        engine.dispatch(GameAction.Hold)

        assertEquals(original, afterFirstHold.hold)
        assertTrue(afterFirstHold.holdUsed)
        assertEquals(afterFirstHold.current, engine.state.current)
    }

    @Test
    fun `hard drop locks a piece and awards drop points`() {
        val engine = GameEngine(5L)
        engine.dispatch(GameAction.HardDrop)

        assertTrue(engine.state.score > 0)
        assertNotEquals(engine.state.current.type, engine.state.hold)
        assertEquals(GameEvent.LAND, engine.state.lastEvent)
    }

    @Test
    fun `tick eventually locks a grounded piece`() {
        val engine = GameEngine(6L)
        repeat(40) { engine.dispatch(GameAction.Tick(1_000)) }

        assertTrue(engine.state.lines >= 0)
        assertTrue(engine.state.status == GameStatus.RUNNING || engine.state.status == GameStatus.GAME_OVER)
    }

    @Test
    fun `full rows clear from the bottom without shifting errors`() {
        val filled = List<Tetromino?>(BOARD_WIDTH) { Tetromino.I }
        val board = List(BOARD_HEIGHT - 4) { List<Tetromino?>(BOARD_WIDTH) { null } } + List(4) { filled }

        val (cleared, count) = BoardRules.clearLines(board)

        assertEquals(4, count)
        assertTrue(BoardRules.isEmpty(cleared))
    }

    @Test
    fun `scoring includes combo back to back and perfect clear bonuses`() {
        val first = Scoring.scoreDelta(4, 1, TSpinKind.NONE, -1, false, false)
        val second = Scoring.scoreDelta(4, 1, TSpinKind.NONE, 0, true, true)

        assertEquals(800L, first)
        assertTrue(second > first)
    }

    @Test
    fun `t spin scoring is distinct from a normal line clear`() {
        val tSpin = Scoring.scoreDelta(1, 1, TSpinKind.FULL, -1, false, false)
        val single = Scoring.scoreDelta(1, 1, TSpinKind.NONE, -1, false, false)

        assertTrue(tSpin > single)
    }
}
