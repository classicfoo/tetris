package com.classicfoo.tetris.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

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
    private val horizontalStepPx: Float = 28f,
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
    private var horizontalSteps = 0
    private var softDropSteps = 0
    private var holdSent = false
    private var maxDownVelocity = 0f
    private var lastPoint: GesturePoint? = null

    fun onDown(point: GesturePoint): List<GestureCommand> {
        down = point
        lastPoint = point
        mode = Mode.NONE
        horizontalSteps = 0
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
        if (mode == Mode.NONE && max(abs(dx), abs(dy)) >= touchSlopPx) {
            mode = if (abs(dx) >= abs(dy)) Mode.HORIZONTAL else Mode.VERTICAL
        }

        return when (mode) {
            Mode.HORIZONTAL -> horizontalCommands(dx)
            Mode.VERTICAL -> verticalCommands(previous, point, dy)
            Mode.NONE -> emptyList()
        }
    }

    fun onUp(point: GesturePoint, viewWidth: Float): List<GestureCommand> {
        val start = down ?: return emptyList()
        val dx = point.x - start.x
        val dy = point.y - start.y
        val duration = (point.timeMs - start.timeMs).coerceAtLeast(1L)
        val commands = mutableListOf<GestureCommand>()

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
            Mode.HORIZONTAL -> commands += horizontalCommands(dx)
            Mode.VERTICAL -> if (dy >= hardDropDistancePx &&
                (maxDownVelocity >= hardDropVelocityPxPerSecond || dy * 1_000f / duration >= hardDropVelocityPxPerSecond || duration <= hardSwipeDurationMs)
            ) {
                commands += GestureCommand.HardDrop
            } else if (dy <= -holdDistancePx && !holdSent) {
                commands += GestureCommand.Hold
            } else if (dy > touchSlopPx && softDropSteps == 0) {
                val targetSteps = floor((dy - touchSlopPx) / softDropStepPx).toInt()
                repeat(targetSteps) { commands += GestureCommand.SoftDrop }
            }
        }
        reset()
        return commands
    }

    fun cancel() {
        reset()
    }

    private fun horizontalCommands(dx: Float): List<GestureCommand> {
        val distance = abs(dx)
        val targetSteps = floor(((distance - touchSlopPx) / horizontalStepPx).coerceAtLeast(0f)).toInt() +
            if (distance >= touchSlopPx) 1 else 0
        if (targetSteps <= horizontalSteps) return emptyList()
        val command = if (dx >= 0f) GestureCommand.MoveRight else GestureCommand.MoveLeft
        return buildList {
            repeat(targetSteps - horizontalSteps) { add(command) }
            horizontalSteps = targetSteps
        }
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
        if (dy <= 0f) return emptyList()
        val deltaTime = (point.timeMs - previous.timeMs).coerceAtLeast(1L)
        val deltaY = point.y - previous.y
        if (deltaY > 0f) {
            maxDownVelocity = max(maxDownVelocity, deltaY * 1_000f / deltaTime)
        }
        if (maxDownVelocity >= hardDropVelocityPxPerSecond) return emptyList()
        val targetSteps = floor(((dy - touchSlopPx) / softDropStepPx).coerceAtLeast(0f)).toInt()
        if (targetSteps <= softDropSteps) return emptyList()
        val commands = buildList {
            repeat(targetSteps - softDropSteps) { add(GestureCommand.SoftDrop) }
        }
        softDropSteps = targetSteps
        return commands
    }

    private fun reset() {
        down = null
        lastPoint = null
        mode = Mode.NONE
        horizontalSteps = 0
        softDropSteps = 0
        holdSent = false
        maxDownVelocity = 0f
    }
}
