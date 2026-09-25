package com.classicfoo.tetris.engine

const val BOARD_WIDTH = 10
const val BOARD_HEIGHT = 20
const val PREVIEW_SIZE = 5

enum class Tetromino {
    I, O, T, J, L, S, Z,
}

enum class Rotation {
    SPAWN, RIGHT, REVERSE, LEFT;

    fun clockwise(): Rotation = entries[(ordinal + 1) % entries.size]

    fun counterClockwise(): Rotation = entries[(ordinal + entries.size - 1) % entries.size]
}

enum class GameStatus {
    RUNNING, PAUSED, GAME_OVER,
}

enum class GameEvent {
    NONE, MOVE, ROTATE, SOFT_DROP, HARD_DROP, HOLD, LAND, LINE_CLEAR, T_SPIN, PERFECT_CLEAR, PAUSE, RESUME, GAME_OVER,
}

enum class TSpinKind {
    NONE, MINI, FULL,
}

data class Cell(val x: Int, val y: Int)

data class ActivePiece(
    val type: Tetromino,
    val rotation: Rotation,
    val x: Int,
    val y: Int,
)

sealed interface GameAction {
    data object MoveLeft : GameAction
    data object MoveRight : GameAction
    data object SoftDrop : GameAction
    data object HardDrop : GameAction
    data object RotateClockwise : GameAction
    data object RotateCounterClockwise : GameAction
    data object Hold : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction
    data class Tick(val millis: Long) : GameAction
}

data class GameState(
    val board: List<List<Tetromino?>>,
    val current: ActivePiece,
    val next: List<Tetromino>,
    val hold: Tetromino?,
    val holdUsed: Boolean,
    val score: Long,
    val lines: Int,
    val level: Int,
    val combo: Int,
    val backToBack: Boolean,
    val status: GameStatus,
    val ghostY: Int,
    val lastEvent: GameEvent = GameEvent.NONE,
    val lastLines: Int = 0,
    val clearSequence: Long = 0L,
    val lastClearedRows: List<Int> = emptyList(),
    val lastScoreDelta: Long = 0,
    val lastTSpin: TSpinKind = TSpinKind.NONE,
    val perfectClear: Boolean = false,
)
