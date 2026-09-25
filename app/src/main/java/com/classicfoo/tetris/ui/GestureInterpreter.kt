package com.classicfoo.tetris.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

sealed interface GestureCommand {
    data object MoveLeft : GestureCommand
    data object MoveRight : GestureCommand
    data object SoftDrop : GestureCommand
    data object HardDrop : GestureCommand
    data object RotateClockwise : GestureCommand
    data object RotateCounterClockwise : GestureCommand
    data object Hold : GestureCommand
}

data class GesturePoint(
    val x: Float,
    val y: Float,
    val timeMs: Long,
)

/**
 * Converts touch movement into deterministic, game-level commands.
 * It deliberately has no Android dependencies so gesture behavior can be unit-tested.
 */
class GestureInterpreter(
    private val touchSlopPx: Float = 18f,
    private val horizontalStepPx: Float = 22f,
    private val reversalHysteresisPx: Float = min(horizontalStepPx * 0.25f, touchSlopPx * 0.5f),
    private val softDropStepPx: Float = 24f,
    private val holdDistancePx: Float = 68f,
    private val hardDropDistancePx: Float = 76f,
    private val hardDropVelocityPxPerSecond: Float = 900f,
    private val tapDurationMs: Long = 300L,
    private val hardSwipeDurationMs: Long = 260L,
) {
    private enum class Mode { NONE, HORIZONTAL, VERTICAL }

    private var down: GesturePoint? = null
    private var mode = Mode.NONE
    private var horizontalDirection = 0
    private var horizontalRemainderPx = 0f
    private var reverseRemainderPx = 0f
    private var softDropSteps = 0
    private var holdSent = false
    private var maxDownVelocity = 0f
    private var lastPoint: GesturePoint? = null

    fun onDown(point: GesturePoint): List<GestureCommand> {
        down = point
        lastPoint = point
        mode = Mode.NONE
        horizontalDirection = 0
        horizontalRemainderPx = 0f
        reverseRemainderPx = 0f
        softDropSteps = 0
        holdSent = false
        maxDownVelocity = 0f
        return emptyList()
    }

    fun onMove(point: GesturePoint): List<GestureCommand> {
        val start = down ?: return emptyList()
        val previous = lastPoint ?: start
        lastPoint = point
        val dx = point.x - start.x
        val dy = point.y - start.y
        val previousMode = mode
        if (mode == Mode.NONE && max(abs(dx), abs(dy)) >= touchSlopPx) {
            mode = if (abs(dx) >= abs(dy)) Mode.HORIZONTAL else Mode.VERTICAL
        }

        return when (mode) {
            Mode.HORIZONTAL -> if (previousMode == Mode.HORIZONTAL) {
                horizontalCommandsForDelta(point.x - previous.x)
            } else {
                horizontalCommandsFromTotal(dx)
            }
            Mode.VERTICAL -> verticalCommands(previous, point, dy)
            Mode.NONE -> emptyList()
        }
    }

    fun onUp(point: GesturePoint, viewWidth: Float): List<GestureCommand> {
        val start = down ?: return emptyList()
        val previous = lastPoint ?: start
        val dx = point.x - start.x
        val dy = point.y - start.y
        val duration = (point.timeMs - start.timeMs).coerceAtLeast(1L)
        val commands = mutableListOf<GestureCommand>()
        val previousMode = mode

        if (mode == Mode.NONE && max(abs(dx), abs(dy)) >= touchSlopPx) {
            mode = if (abs(dx) >= abs(dy)) Mode.HORIZONTAL else Mode.VERTICAL
        }

        when (mode) {
            Mode.NONE -> if (max(abs(dx), abs(dy)) < touchSlopPx && duration <= tapDurationMs) {
                commands += if (start.x < viewWidth / 2f) {
                    GestureCommand.RotateCounterClockwise
                } else {
                    GestureCommand.RotateClockwise
                }
            }
            Mode.HORIZONTAL -> {
                commands += if (previousMode == Mode.HORIZONTAL) {
                    horizontalCommandsForDelta(point.x - previous.x)
                } else {
                    horizontalCommandsFromTotal(dx)
                }
            }
            Mode.VERTICAL -> {
                updateDownVelocity(previous, point)
                if (holdSent) {
                    // Hold is terminal for the gesture; do not turn a later rebound into a drop.
                } else if (dy >= hardDropDistancePx &&
                    (maxDownVelocity >= hardDropVelocityPxPerSecond ||
                        dy * 1_000f / duration >= hardDropVelocityPxPerSecond ||
                        duration <= hardSwipeDurationMs)
                ) {
                    commands += GestureCommand.HardDrop
                } else {
                    commands += verticalCommands(previous, point, dy)
                }
            }
        }
        reset()
        return commands
    }

    fun cancel() {
        reset()
    }

    private fun horizontalCommandsFromTotal(dx: Float): List<GestureCommand> {
        val distanceAfterSlop = (abs(dx) - touchSlopPx).coerceAtLeast(0f)
        if (distanceAfterSlop <= 0f) return emptyList()

        horizontalDirection = if (dx >= 0f) 1 else -1
        horizontalRemainderPx = distanceAfterSlop
        reverseRemainderPx = 0f
        return emitHorizontalSteps()
    }

    private fun horizontalCommandsForDelta(deltaX: Float): List<GestureCommand> {
        val distance = abs(deltaX)
        if (distance <= 0f) return emptyList()

        if (horizontalDirection == 0) {
            val distanceAfterSlop = (distance - touchSlopPx).coerceAtLeast(0f)
            if (distanceAfterSlop <= 0f) return emptyList()
            horizontalDirection = if (deltaX >= 0f) 1 else -1
            horizontalRemainderPx = distanceAfterSlop
            return emitHorizontalSteps()
        }

        val direction = if (deltaX >= 0f) 1 else -1
        if (direction != horizontalDirection) {
            reverseRemainderPx += distance
            if (reverseRemainderPx < reversalHysteresisPx) return emptyList()

            horizontalDirection = direction
            horizontalRemainderPx = reverseRemainderPx - reversalHysteresisPx
            reverseRemainderPx = 0f
        } else {
            reverseRemainderPx = 0f
            horizontalRemainderPx += distance
        }
        return emitHorizontalSteps()
    }

    private fun emitHorizontalSteps(): List<GestureCommand> {
        val count = floor(horizontalRemainderPx / horizontalStepPx).toInt()
        if (count <= 0) return emptyList()

        horizontalRemainderPx -= count * horizontalStepPx
        val command = if (horizontalDirection > 0) GestureCommand.MoveRight else GestureCommand.MoveLeft
        return List(count) { command }
    }

    private fun verticalCommands(
        previous: GesturePoint,
        point: GesturePoint,
        dy: Float,
    ): List<GestureCommand> {
        if (dy <= -holdDistancePx && !holdSent) {
            holdSent = true
            return listOf(GestureCommand.Hold)
        }
        if (holdSent || dy <= 0f) return emptyList()
        updateDownVelocity(previous, point)
        if (maxDownVelocity >= hardDropVelocityPxPerSecond) return emptyList()
        val targetSteps = floor(((dy - touchSlopPx) / softDropStepPx).coerceAtLeast(0f)).toInt()
        if (targetSteps <= softDropSteps) return emptyList()
        val commands = buildList {
            repeat(targetSteps - softDropSteps) { add(GestureCommand.SoftDrop) }
        }
        softDropSteps = targetSteps
        return commands
    }

    private fun updateDownVelocity(previous: GesturePoint, point: GesturePoint) {
        val deltaTime = (point.timeMs - previous.timeMs).coerceAtLeast(1L)
        val deltaY = point.y - previous.y
        if (deltaY > 0f) {
            maxDownVelocity = max(maxDownVelocity, deltaY * 1_000f / deltaTime)
        }
    }

    private fun reset() {
        down = null
        lastPoint = null
        mode = Mode.NONE
        horizontalDirection = 0
        horizontalRemainderPx = 0f
        reverseRemainderPx = 0f
        softDropSteps = 0
        holdSent = false
        maxDownVelocity = 0f
    }
}
