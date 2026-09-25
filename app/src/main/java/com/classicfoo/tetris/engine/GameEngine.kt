package com.classicfoo.tetris.engine

import kotlin.math.max
import kotlin.math.pow

class GameEngine(seed: Long = System.nanoTime()) {
    private val initialSeed = seed
    private var restartCount = 0L
    private var bag = PieceBag(java.util.Random(seed))
    private var fallAccumulatorMs = 0L
    private var lockElapsedMs = 0L
    private var lockResets = 0
    private var lastActionWasRotation = false
    private var clearSequenceCounter = 0L

    var state: GameState = createInitialState()
        private set

    fun dispatch(action: GameAction): GameState {
        when (action) {
            GameAction.Restart -> restart()
            GameAction.MoveLeft -> move(-1)
            GameAction.MoveRight -> move(1)
            GameAction.SoftDrop -> softDrop()
            GameAction.HardDrop -> hardDrop()
            GameAction.RotateClockwise -> rotate(clockwise = true)
            GameAction.RotateCounterClockwise -> rotate(clockwise = false)
            GameAction.Hold -> hold()
            GameAction.Pause -> if (state.status == GameStatus.RUNNING) {
                state = state.copy(status = GameStatus.PAUSED, lastEvent = GameEvent.PAUSE)
            }
            GameAction.Resume -> if (state.status == GameStatus.PAUSED) {
                state = state.copy(status = GameStatus.RUNNING, lastEvent = GameEvent.RESUME)
            }
            is GameAction.Tick -> tick(action.millis)
        }
        state = state.copy(ghostY = BoardRules.ghostY(state.board, state.current))
        return state
    }

    private fun createInitialState(): GameState {
        val currentType = bag.next()
        val preview = List(PREVIEW_SIZE) { bag.next() }
        val current = spawn(currentType)
        return GameState(
            board = BoardRules.emptyBoard(),
            current = current,
            next = preview,
            hold = null,
            holdUsed = false,
            score = 0,
            lines = 0,
            level = 1,
            combo = -1,
            backToBack = false,
            status = GameStatus.RUNNING,
            ghostY = current.y,
            clearSequence = clearSequenceCounter,
        )
    }

    private fun restart() {
        restartCount++
        bag = PieceBag(java.util.Random(initialSeed + restartCount))
        fallAccumulatorMs = 0
        lockElapsedMs = 0
        lockResets = 0
        lastActionWasRotation = false
        state = createInitialState()
    }

    private fun spawn(type: Tetromino): ActivePiece {
        return ActivePiece(
            type = type,
            rotation = Rotation.SPAWN,
            x = (BOARD_WIDTH - PieceDefinitions.width(type)) / 2,
            y = 0,
        )
    }

    private fun move(dx: Int) {
        if (state.status != GameStatus.RUNNING) return
        val grounded = !BoardRules.canPlace(state.board, state.current.copy(y = state.current.y + 1))
        val candidate = state.current.copy(x = state.current.x + dx)
        if (!BoardRules.canPlace(state.board, candidate)) return

        state = state.copy(current = candidate, lastEvent = GameEvent.MOVE)
        lastActionWasRotation = false
        if (grounded && lockResets < MAX_LOCK_RESETS) {
            lockElapsedMs = 0
            lockResets++
        }
    }

    private fun rotate(clockwise: Boolean) {
        if (state.status != GameStatus.RUNNING) return
        val from = state.current.rotation
        val to = if (clockwise) from.clockwise() else from.counterClockwise()
        val grounded = !BoardRules.canPlace(state.board, state.current.copy(y = state.current.y + 1))

        for (kick in kickTests(state.current.type, from, to)) {
            val candidate = state.current.copy(
                rotation = to,
                x = state.current.x + kick.x,
                y = state.current.y + kick.y,
            )
            if (BoardRules.canPlace(state.board, candidate)) {
                state = state.copy(current = candidate, lastEvent = GameEvent.ROTATE)
                lastActionWasRotation = true
                if (grounded && lockResets < MAX_LOCK_RESETS) {
                    lockElapsedMs = 0
                    lockResets++
                }
                return
            }
        }
    }

    private fun softDrop() {
        if (state.status != GameStatus.RUNNING) return
        val candidate = state.current.copy(y = state.current.y + 1)
        if (BoardRules.canPlace(state.board, candidate)) {
            state = state.copy(current = candidate, score = state.score + 1, lastEvent = GameEvent.SOFT_DROP)
            lastActionWasRotation = false
        }
    }

    private fun hardDrop() {
        if (state.status != GameStatus.RUNNING) return
        val distance = BoardRules.ghostY(state.board, state.current) - state.current.y
        state = state.copy(
            current = state.current.copy(y = state.current.y + distance),
            score = state.score + distance * 2L,
            lastEvent = GameEvent.HARD_DROP,
        )
        lockCurrent()
    }

    private fun hold() {
        if (state.status != GameStatus.RUNNING || state.holdUsed) return
        val nextCurrent = state.hold?.let(::spawn) ?: spawn(state.next.first())
        val nextQueue = if (state.hold == null) {
            state.next.drop(1) + bag.next()
        } else {
            state.next
        }
        state = state.copy(
            current = nextCurrent,
            next = nextQueue,
            hold = state.current.type,
            holdUsed = true,
            lastEvent = GameEvent.HOLD,
        )
        fallAccumulatorMs = 0
        lockElapsedMs = 0
        lockResets = 0
        lastActionWasRotation = false
    }

    private fun tick(millis: Long) {
        if (state.status != GameStatus.RUNNING) return
        val elapsed = millis.coerceIn(0, 1_000)
        fallAccumulatorMs += elapsed
        var gravity = gravityDelayMs(state.level)
        while (fallAccumulatorMs >= gravity && state.status == GameStatus.RUNNING) {
            fallAccumulatorMs -= gravity
            val candidate = state.current.copy(y = state.current.y + 1)
            if (BoardRules.canPlace(state.board, candidate)) {
                state = state.copy(current = candidate, lastEvent = GameEvent.NONE)
            } else {
                break
            }
            gravity = gravityDelayMs(state.level)
        }

        if (state.status != GameStatus.RUNNING) return
        val grounded = !BoardRules.canPlace(state.board, state.current.copy(y = state.current.y + 1))
        if (grounded) {
            lockElapsedMs += elapsed
            if (lockElapsedMs >= LOCK_DELAY_MS) lockCurrent()
        } else {
            lockElapsedMs = 0
        }
    }

    private fun lockCurrent() {
        val tSpin = detectTSpin()
        val locked = BoardRules.lock(state.board, state.current)
        val clearResult = BoardRules.clearLinesWithRows(locked)
        val clearedBoard = clearResult.board
        val linesCleared = clearResult.count
        if (linesCleared > 0) clearSequenceCounter++
        val perfectClear = linesCleared > 0 && BoardRules.isEmpty(clearedBoard)
        val difficult = (tSpin != TSpinKind.NONE && linesCleared > 0) || linesCleared == 4
        val delta = Scoring.scoreDelta(
            lines = linesCleared,
            level = state.level,
            tSpin = tSpin,
            comboBefore = state.combo,
            backToBackBefore = state.backToBack,
            perfectClear = perfectClear,
        )
        val combo = if (linesCleared > 0) {
            if (state.combo < 0) 0 else state.combo + 1
        } else {
            -1
        }
        val backToBack = when {
            difficult -> true
            linesCleared > 0 -> false
            else -> state.backToBack
        }
        val nextCurrent = spawn(state.next.first())
        val nextQueue = state.next.drop(1) + bag.next()
        val gameOver = !BoardRules.canPlace(clearedBoard, nextCurrent)

        state = state.copy(
            board = clearedBoard,
            current = nextCurrent,
            next = nextQueue,
            holdUsed = false,
            score = state.score + delta,
            lines = state.lines + linesCleared,
            level = (state.lines + linesCleared) / 10 + 1,
            combo = combo,
            backToBack = backToBack,
            status = if (gameOver) GameStatus.GAME_OVER else GameStatus.RUNNING,
            clearSequence = clearSequenceCounter,
            lastEvent = when {
                gameOver -> GameEvent.GAME_OVER
                perfectClear -> GameEvent.PERFECT_CLEAR
                tSpin != TSpinKind.NONE -> GameEvent.T_SPIN
                linesCleared > 0 -> GameEvent.LINE_CLEAR
                else -> GameEvent.LAND
            },
            lastLines = linesCleared,
            lastClearedRows = clearResult.clearedRows,
            lastScoreDelta = delta,
            lastTSpin = tSpin,
            perfectClear = perfectClear,
        )
        fallAccumulatorMs = 0
        lockElapsedMs = 0
        lockResets = 0
        lastActionWasRotation = false
    }

    private fun detectTSpin(): TSpinKind {
        if (!lastActionWasRotation || state.current.type != Tetromino.T) return TSpinKind.NONE
        val pivotX = state.current.x + 1
        val pivotY = state.current.y + 1
        val corners = listOf(
            Cell(pivotX - 1, pivotY - 1),
            Cell(pivotX + 1, pivotY - 1),
            Cell(pivotX - 1, pivotY + 1),
            Cell(pivotX + 1, pivotY + 1),
        )
        val occupied = corners.count { cell ->
            cell.x !in 0 until BOARD_WIDTH || cell.y !in 0 until BOARD_HEIGHT || state.board[cell.y][cell.x] != null
        }
        if (occupied < 3) return TSpinKind.NONE

        val front = when (state.current.rotation) {
            Rotation.SPAWN -> listOf(corners[0], corners[1])
            Rotation.RIGHT -> listOf(corners[1], corners[3])
            Rotation.REVERSE -> listOf(corners[2], corners[3])
            Rotation.LEFT -> listOf(corners[0], corners[2])
        }
        val frontOccupied = front.count { cell ->
            cell.x !in 0 until BOARD_WIDTH || cell.y !in 0 until BOARD_HEIGHT || state.board[cell.y][cell.x] != null
        }
        return if (occupied == 3 && frontOccupied < 2) TSpinKind.MINI else TSpinKind.FULL
    }

    private fun kickTests(type: Tetromino, from: Rotation, to: Rotation): List<Cell> {
        if (type == Tetromino.O) return listOf(Cell(0, 0))
        val kicks = if (type == Tetromino.I) I_KICKS else JLSTZ_KICKS
        return kicks[from to to] ?: listOf(Cell(0, 0))
    }

    private fun gravityDelayMs(level: Int): Long {
        val seconds = (0.8 - 0.007 * (level - 1)).pow(level - 1)
        return max(50L, (seconds * 1_000).toLong())
    }

    companion object {
        const val LOCK_DELAY_MS = 500L
        const val MAX_LOCK_RESETS = 15

        private val JLSTZ_KICKS = mapOf(
            (Rotation.SPAWN to Rotation.RIGHT) to listOf(Cell(0, 0), Cell(-1, 0), Cell(-1, 1), Cell(0, -2), Cell(-1, -2)),
            (Rotation.RIGHT to Rotation.SPAWN) to listOf(Cell(0, 0), Cell(1, 0), Cell(1, -1), Cell(0, 2), Cell(1, 2)),
            (Rotation.RIGHT to Rotation.REVERSE) to listOf(Cell(0, 0), Cell(1, 0), Cell(1, -1), Cell(0, 2), Cell(1, 2)),
            (Rotation.REVERSE to Rotation.RIGHT) to listOf(Cell(0, 0), Cell(-1, 0), Cell(-1, 1), Cell(0, -2), Cell(-1, -2)),
            (Rotation.REVERSE to Rotation.LEFT) to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(0, -2), Cell(1, -2)),
            (Rotation.LEFT to Rotation.REVERSE) to listOf(Cell(0, 0), Cell(-1, 0), Cell(-1, -1), Cell(0, 2), Cell(-1, 2)),
            (Rotation.LEFT to Rotation.SPAWN) to listOf(Cell(0, 0), Cell(-1, 0), Cell(-1, -1), Cell(0, 2), Cell(-1, 2)),
            (Rotation.SPAWN to Rotation.LEFT) to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(0, -2), Cell(1, -2)),
        )

        private val I_KICKS = mapOf(
            (Rotation.SPAWN to Rotation.RIGHT) to listOf(Cell(0, 0), Cell(-2, 0), Cell(1, 0), Cell(-2, -1), Cell(1, 2)),
            (Rotation.RIGHT to Rotation.SPAWN) to listOf(Cell(0, 0), Cell(2, 0), Cell(-1, 0), Cell(2, 1), Cell(-1, -2)),
            (Rotation.RIGHT to Rotation.REVERSE) to listOf(Cell(0, 0), Cell(-1, 0), Cell(2, 0), Cell(-1, 2), Cell(2, -1)),
            (Rotation.REVERSE to Rotation.RIGHT) to listOf(Cell(0, 0), Cell(1, 0), Cell(-2, 0), Cell(1, -2), Cell(-2, 1)),
            (Rotation.REVERSE to Rotation.LEFT) to listOf(Cell(0, 0), Cell(2, 0), Cell(-1, 0), Cell(2, 1), Cell(-1, -2)),
            (Rotation.LEFT to Rotation.REVERSE) to listOf(Cell(0, 0), Cell(-2, 0), Cell(1, 0), Cell(-2, -1), Cell(1, 2)),
            (Rotation.LEFT to Rotation.SPAWN) to listOf(Cell(0, 0), Cell(1, 0), Cell(-2, 0), Cell(1, -2), Cell(-2, 1)),
            (Rotation.SPAWN to Rotation.LEFT) to listOf(Cell(0, 0), Cell(-1, 0), Cell(2, 0), Cell(-1, 2), Cell(2, -1)),
        )
    }
}

internal object Scoring {
    fun scoreDelta(
        lines: Int,
        level: Int,
        tSpin: TSpinKind,
        comboBefore: Int,
        backToBackBefore: Boolean,
        perfectClear: Boolean,
    ): Long {
        val base = when (tSpin) {
            TSpinKind.FULL -> when (lines) {
                0 -> 400
                1 -> 800
                2 -> 1_200
                else -> 1_600
            }
            TSpinKind.MINI -> when (lines) {
                0 -> 100
                else -> 200
            }
            TSpinKind.NONE -> when (lines) {
                1 -> 100
                2 -> 300
                3 -> 500
                4 -> 800
                else -> 0
            }
        }.toLong() * level

        val difficult = (tSpin != TSpinKind.NONE && lines > 0) || lines == 4
        val backToBackBonus = if (difficult && backToBackBefore) base / 2 else 0
        val comboBonus = if (lines > 0 && comboBefore >= 0) {
            50L * (comboBefore + 1) * level
        } else {
            0
        }
        val perfectClearBonus = if (perfectClear) 3_500L * level else 0
        return base + backToBackBonus + comboBonus + perfectClearBonus
    }
}
