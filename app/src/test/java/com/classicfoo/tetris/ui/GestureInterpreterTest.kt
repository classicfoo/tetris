package com.classicfoo.tetris.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInterpreterTest {
    @Test
    fun `short right swipe produces one right move`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 200f, 0L))
        val commands = interpreter.onMove(point(140f, 200f, 100L))

        assertEquals(listOf(GestureCommand.MoveRight), commands)
        assertTrue(interpreter.onUp(point(140f, 200f, 120L), viewWidth = 400f).isEmpty())
    }

    @Test
    fun `short left swipe produces one left move`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(300f, 200f, 0L))
        val commands = interpreter.onMove(point(260f, 200f, 100L))

        assertEquals(listOf(GestureCommand.MoveLeft), commands)
        assertTrue(interpreter.onUp(point(260f, 200f, 120L), viewWidth = 400f).isEmpty())
    }

    @Test
    fun `long right swipe emits one command for every crossed step`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 200f, 0L))
        val commands = interpreter.onMove(point(210f, 200f, 100L))

        assertEquals(
            listOf(
                GestureCommand.MoveRight,
                GestureCommand.MoveRight,
                GestureCommand.MoveRight,
                GestureCommand.MoveRight,
            ),
            commands,
        )
    }

    @Test
    fun `long left swipe emits multiple left commands`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(300f, 200f, 0L))
        val commands = interpreter.onMove(point(190f, 200f, 100L))

        assertEquals(4, commands.size)
        assertTrue(commands.all { it == GestureCommand.MoveLeft })
    }

    @Test
    fun `tap on left half rotates counter clockwise`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 200f, 0L))
        val commands = interpreter.onUp(point(100f, 200f, 120L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.RotateCounterClockwise), commands)
    }

    @Test
    fun `tap on right half rotates clockwise`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(300f, 200f, 0L))
        val commands = interpreter.onUp(point(300f, 200f, 120L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.RotateClockwise), commands)
    }

    @Test
    fun `slow downward drag emits soft drop commands without hard drop`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(200f, 100f, 0L))
        val first = interpreter.onMove(point(200f, 150f, 500L))
        val second = interpreter.onMove(point(200f, 180f, 800L))
        val release = interpreter.onUp(point(200f, 180f, 900L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.SoftDrop), first)
        assertEquals(listOf(GestureCommand.SoftDrop), second)
        assertTrue(release.isEmpty())
    }

    @Test
    fun `fast downward swipe emits exactly one hard drop`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(200f, 100f, 0L))
        assertTrue(interpreter.onMove(point(200f, 210f, 100L)).isEmpty())
        assertTrue(interpreter.onMove(point(200f, 280f, 150L)).isEmpty())
        val release = interpreter.onUp(point(200f, 280f, 180L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.HardDrop), release)
    }

    @Test
    fun `upward swipe emits hold exactly once`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(200f, 300f, 0L))
        val first = interpreter.onMove(point(200f, 220f, 100L))
        val second = interpreter.onMove(point(200f, 150f, 180L))
        val release = interpreter.onUp(point(200f, 120f, 240L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.Hold), first)
        assertTrue(second.isEmpty())
        assertTrue(release.isEmpty())
    }

    @Test
    fun `cancel discards an in progress gesture`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 200f, 0L))
        interpreter.onMove(point(160f, 200f, 100L))
        interpreter.cancel()

        assertTrue(interpreter.onUp(point(200f, 200f, 160L), viewWidth = 400f).isEmpty())

        interpreter.onDown(point(100f, 200f, 200L))
        assertEquals(
            listOf(GestureCommand.RotateCounterClockwise),
            interpreter.onUp(point(100f, 200f, 260L), viewWidth = 400f),
        )
    }

    @Test
    fun `small diagonal movement remains a tap and uses the starting half`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 100f, 0L))
        interpreter.onMove(point(112f, 112f, 100L))
        val commands = interpreter.onUp(point(112f, 112f, 120L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.RotateCounterClockwise), commands)
    }

    @Test
    fun `larger diagonal movement selects the dominant horizontal axis`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 100f, 0L))
        val commands = interpreter.onMove(point(190f, 150f, 100L))

        assertEquals(3, commands.size)
        assertTrue(commands.all { it == GestureCommand.MoveRight })
        assertTrue(interpreter.onUp(point(190f, 150f, 120L), viewWidth = 400f).isEmpty())
    }

    @Test
    fun `tap at the center boundary belongs to the right half`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(200f, 200f, 0L))
        val commands = interpreter.onUp(point(200f, 200f, 120L), viewWidth = 400f)

        assertEquals(listOf(GestureCommand.RotateClockwise), commands)
    }

    @Test
    fun `movement beyond tap duration does not rotate`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(100f, 200f, 0L))
        val commands = interpreter.onUp(point(100f, 200f, 301L), viewWidth = 400f)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun `vertical movement below hold distance does not hold`() {
        val interpreter = GestureInterpreter()

        interpreter.onDown(point(200f, 300f, 0L))
        val commands = interpreter.onMove(point(200f, 245f, 100L))

        assertTrue(commands.isEmpty())
        assertTrue(interpreter.onUp(point(200f, 245f, 120L), viewWidth = 400f).isEmpty())
    }

    private fun point(x: Float, y: Float, timeMs: Long) = GesturePoint(x, y, timeMs)
}
