package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.GameEngine
import com.classicfoo.tetris.engine.GameEvent
import com.classicfoo.tetris.engine.GameState
import com.classicfoo.tetris.engine.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearFlashControllerTest {
    @Test
    fun `new clear sequence starts one fading row pulse`() {
        val controller = ClearFlashController()

        assertFalse(controller.observe(state(0), nowMs = 0L).active)
        val start = controller.observe(
            state(1, rows = listOf(17, 18, 19), event = GameEvent.LINE_CLEAR),
            nowMs = 100L,
        )
        val tick = controller.observe(
            state(1, rows = listOf(17, 18, 19), event = GameEvent.LINE_CLEAR),
            nowMs = 150L,
        )

        assertTrue(start.active)
        assertEquals(listOf(17, 18, 19), start.rows)
        assertEquals(56, start.alpha)
        assertEquals(36, tick.alpha)
        assertTrue(tick.alpha < start.alpha)
    }

    @Test
    fun `pulse expires exactly at configured duration and does not retrigger on Tick`() {
        val controller = ClearFlashController()

        controller.observe(state(0), nowMs = 0L)
        controller.observe(state(1, rows = listOf(19)), nowMs = 10L)

        assertTrue(controller.observe(state(1, rows = listOf(19)), nowMs = 149L).active)
        assertFalse(controller.observe(state(1, rows = listOf(19)), nowMs = 150L).active)
    }

    @Test
    fun `pause game over and lifecycle cancellation do not replay a consumed sequence`() {
        val controller = ClearFlashController()

        controller.observe(state(0), nowMs = 0L)
        assertTrue(controller.observe(state(1, rows = listOf(19)), nowMs = 10L).active)

        assertFalse(controller.observe(state(1, status = GameStatus.PAUSED, rows = listOf(19)), nowMs = 20L).active)
        assertFalse(controller.observe(state(1, rows = listOf(19)), nowMs = 30L).active)

        assertTrue(controller.observe(state(2, rows = listOf(16, 17, 18, 19)), nowMs = 40L).active)
        controller.cancel()
        assertFalse(controller.frame(50L).active)
        assertFalse(controller.observe(state(2, rows = listOf(16, 17, 18, 19)), nowMs = 60L).active)

        assertFalse(controller.observe(state(2, status = GameStatus.GAME_OVER, rows = listOf(16, 17, 18, 19)), nowMs = 70L).active)
        assertTrue(controller.observe(state(3, rows = listOf(19), event = GameEvent.PERFECT_CLEAR), nowMs = 80L).active)
    }

    @Test
    fun `lifecycle cancellation consumes a sequence that has not drawn yet`() {
        val controller = ClearFlashController()

        controller.cancel(consumedSequence = 1L)

        assertFalse(controller.observe(state(1, rows = listOf(19)), nowMs = 10L).active)
        assertTrue(controller.observe(state(2, rows = listOf(18)), nowMs = 20L).active)
    }

    @Test
    fun `tetris and perfect clear are each one sequence pulse`() {
        val controller = ClearFlashController()

        controller.observe(state(0), nowMs = 0L)
        val tetris = controller.observe(
            state(1, rows = listOf(16, 17, 18, 19), event = GameEvent.LINE_CLEAR),
            nowMs = 10L,
        )
        val perfectClear = controller.observe(
            state(2, rows = listOf(19), event = GameEvent.PERFECT_CLEAR),
            nowMs = 200L,
        )

        assertEquals(listOf(16, 17, 18, 19), tetris.rows)
        assertEquals(listOf(19), perfectClear.rows)
        assertEquals(56, perfectClear.alpha)
    }

    private fun state(
        sequence: Long,
        status: GameStatus = GameStatus.RUNNING,
        rows: List<Int> = emptyList(),
        event: GameEvent = GameEvent.NONE,
    ): GameState = GameEngine(seed = 9L).state.copy(
        status = status,
        clearSequence = sequence,
        lastClearedRows = rows,
        lastLines = rows.size,
        lastEvent = event,
    )
}
